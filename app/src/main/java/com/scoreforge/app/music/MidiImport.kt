package com.scoreforge.app.music

import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.roundToInt

/** Result of converting a Standard MIDI File into editable Score Forge project data. */
data class MidiImportResult(
    val snapshot: ScoreProjectSnapshot,
    val importedTrackCount: Int,
    val importedNoteCount: Int,
    val bpm: Int,
    val warnings: List<String>,
) {
    fun statusText(): String = buildString {
        append("Imported ")
        append(importedTrackCount)
        append(if (importedTrackCount == 1) " track" else " tracks")
        append(" • ")
        append(importedNoteCount)
        append(if (importedNoteCount == 1) " note" else " notes")
        append(" • ")
        append(bpm)
        append(" BPM")
        if (warnings.isNotEmpty()) append(" • ${warnings.size} warning${if (warnings.size == 1) "" else "s"}")
    }
}

/**
 * Small dependency-free Standard MIDI File (SMF) importer.
 *
 * Version 1 intentionally targets the information Score Forge can already edit well: note pitch,
 * start, written duration, velocity, track/channel grouping, tempo, time signatures, key signatures, program,
 * bank, volume and pan. Continuous controllers and automation are retained only as warnings until Score Forge
 * has a model that can represent them without silently pretending they were preserved.
 */
object MidiImporter {
    private const val HEADER = "MThd"
    private const val TRACK = "MTrk"
    private const val DEFAULT_BPM = 120
    private const val MAX_BYTES = 16 * 1024 * 1024

    private data class RawNote(
        val sourceTrack: Int,
        val channel: Int,
        val pitch: Int,
        val velocity: Int,
        val startTick: Long,
        val endTick: Long,
    )

    private data class TrackChannelState(
        var name: String = "",
        var bankMsb: Int = 0,
        var bankLsb: Int = 0,
        var program: Int? = null,
        var volume: Int? = null,
        var pan: Int? = null,
    )

    /**
     * Bank Select interpretation advertised by common MIDI reset SysEx messages.
     * MMA is the safe legacy/default behavior Score Forge used before mode detection landed.
     */
    private enum class BankSelectMode {
        MMA,
        GM,
        GS,
        XG,
    }

    private data class TempoEvent(val tick: Long, val microsecondsPerQuarter: Int)

    private data class TimeSignatureEvent(
        val tick: Long,
        val numerator: Int,
        val denominator: Int,
    )

    private data class KeySignatureEvent(
        val tick: Long,
        val fifths: Int,
        val minor: Boolean,
    )

    private data class ParsedMidi(
        val ticksPerQuarter: Int,
        val notes: List<RawNote>,
        val states: Map<Pair<Int, Int>, TrackChannelState>,
        val tempoEvents: List<TempoEvent>,
        val timeSignatureEvents: List<TimeSignatureEvent>,
        val keySignatureEvents: List<KeySignatureEvent>,
        val sourceTrackNames: Map<Int, String>,
        val bankSelectMode: BankSelectMode,
        val warnings: MutableList<String>,
    )

    private data class ImportGroup(
        val sourceTracks: List<Int>,
        val channel: Int,
        val notes: List<RawNote>,
    )

    fun import(bytes: ByteArray, projectName: String = "Imported MIDI"): MidiImportResult {
        require(bytes.isNotEmpty()) { "The selected MIDI file is empty." }
        require(bytes.size <= MAX_BYTES) { "This MIDI file is larger than the current 16 MB import limit." }
        val parsed = parse(bytes)
        require(parsed.notes.isNotEmpty()) { "No playable note events were found in this MIDI file." }

        val bankSelectMode = if (parsed.bankSelectMode == BankSelectMode.MMA) {
            inferBankSelectMode(parsed.states, projectName)
        } else {
            parsed.bankSelectMode
        }
        val warnings = parsed.warnings.toMutableList()
        val tempoChanges = resolveTempos(
            events = parsed.tempoEvents,
            ticksPerQuarter = parsed.ticksPerQuarter,
            warnings = warnings,
        )
        val bpm = tempoChanges.first().bpm
        val timeSignatures = resolveTimeSignatures(
            events = parsed.timeSignatureEvents,
            ticksPerQuarter = parsed.ticksPerQuarter,
            warnings = warnings,
        )
        val keySignatures = resolveKeySignatures(
            events = parsed.keySignatureEvents,
            ticksPerQuarter = parsed.ticksPerQuarter,
            warnings = warnings,
        )
        val sourceGroups = parsed.notes
            .groupBy { it.sourceTrack to it.channel }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))
            .map { (key, rawNotes) ->
                ImportGroup(
                    sourceTracks = listOf(key.first),
                    channel = key.second,
                    notes = rawNotes,
                )
            }

        val grouped = if (sourceGroups.size <= ScoreTracks.MAX_TRACKS) {
            sourceGroups
        } else {
            val byChannel = parsed.notes
                .groupBy { it.channel }
                .entries
                .sortedBy { it.key }
                .map { (channel, rawNotes) ->
                    ImportGroup(
                        sourceTracks = rawNotes.map { it.sourceTrack }.distinct().sorted(),
                        channel = channel,
                        notes = rawNotes,
                    )
                }
            warnings +=
                "${sourceGroups.size} MIDI track/channel groups shared ${byChannel.size} MIDI channels; " +
                    "groups on the same channel were combined so no note tracks were dropped."
            byChannel
        }

        val sourceTrackGroupCounts = sourceGroups
            .flatMap { it.sourceTracks }
            .groupingBy { it }
            .eachCount()

        fun stateFor(group: ImportGroup): TrackChannelState {
            val candidates = group.sourceTracks
                .mapNotNull { parsed.states[it to group.channel] }
            if (candidates.isEmpty()) return TrackChannelState()

            val programs = candidates.mapNotNull { it.program }.distinct()
            val banks = candidates.map { it.bankMsb to it.bankLsb }.distinct()
            if (programs.size > 1) {
                warnings +=
                    "MIDI channel ${group.channel + 1} used multiple programs across source tracks; " +
                        "program ${programs.first() + 1} was used."
            }
            if (banks.size > 1) {
                warnings +=
                    "MIDI channel ${group.channel + 1} used multiple bank selections across source tracks; " +
                        "the first bank was used."
            }

            val bank = banks.firstOrNull() ?: (0 to 0)
            return TrackChannelState(
                bankMsb = bank.first,
                bankLsb = bank.second,
                program = programs.firstOrNull(),
                volume = candidates.mapNotNull { it.volume }.firstOrNull(),
                pan = candidates.mapNotNull { it.pan }.firstOrNull(),
            )
        }

        var quantizedCount = 0
        val tracks = grouped.take(ScoreTracks.MAX_TRACKS).mapIndexedNotNull { index, group ->
            val sourceTrack = group.sourceTracks.first()
            val channel = group.channel
            val state = stateFor(group)
            val events = group.notes
                .sortedWith(compareBy<RawNote> { it.startTick }.thenBy { it.pitch }.thenBy { it.endTick })
                .map { raw ->
                    val rawStart = raw.startTick.toFloat() / parsed.ticksPerQuarter.toFloat()
                    val rawLength = ((raw.endTick - raw.startTick).coerceAtLeast(1L)).toFloat() /
                        parsed.ticksPerQuarter.toFloat()
                    val startBeat = ScoreTimeline.quantizeBeat(rawStart)
                    val written = nearestWrittenDuration(rawLength)
                    if (abs(startBeat - rawStart) > 0.001f || abs(written.beats - rawLength) > 0.001f) {
                        quantizedCount += 1
                    }
                    ScoreNote(
                        midiPitch = raw.pitch.coerceIn(0, 127),
                        duration = written.duration,
                        startBeat = startBeat,
                        velocity = raw.velocity.coerceIn(1, 127),
                        dotted = written.dotted,
                    )
                }

            if (events.isEmpty()) return@mapIndexedNotNull null
            val sourceNames = group.sourceTracks
                .mapNotNull { parsed.sourceTrackNames[it]?.trim()?.takeIf(String::isNotBlank) }
                .distinct()
            val baseName = when {
                channel == 9 && group.sourceTracks.size > 1 -> "Drums"
                sourceNames.size == 1 -> sourceNames.single()
                sourceNames.size in 2..3 -> sourceNames.joinToString(" + ")
                sourceNames.isNotEmpty() -> "MIDI Ch ${channel + 1} (${sourceNames.size} tracks)"
                group.sourceTracks.size == 1 -> "MIDI Track ${sourceTrack + 1}"
                else -> "MIDI Ch ${channel + 1}"
            }
            val channelSuffix = if (
                group.sourceTracks.size == 1 &&
                (sourceTrackGroupCounts[sourceTrack] ?: 0) > 1
            ) {
                " Ch ${channel + 1}"
            } else {
                ""
            }
            val trackName = (baseName + channelSuffix)
                .replace('\t', ' ')
                .replace('\n', ' ')
                .take(80)

            val bank = resolvePresetBank(
                state = state,
                channel = channel,
                mode = bankSelectMode,
            )
            val program = state.program ?: if (bank == 128) 0 else null
            ScoreTrack(
                id = index + 1,
                name = trackName,
                events = events,
                cursorBeat = 0f,
                presetBank = if (program != null || bank != 0) bank else null,
                presetProgram = program,
                volume = state.volume ?: ScoreTrack.DEFAULT_VOLUME,
                pan = state.pan?.let { (it - 64).coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN) }
                    ?: ScoreTrack.CENTER_PAN,
            ).normalized()
        }

        require(tracks.isNotEmpty()) { "No supported MIDI tracks could be imported." }
        if (quantizedCount > 0) {
            warnings += "$quantizedCount note${if (quantizedCount == 1) " was" else "s were"} quantized to Score Forge's 1/16-note notation grid."
        }

        val safeName = ScoreProjectSnapshot.sanitizeProjectName(projectName)
        val first = tracks.first()
        val snapshot = ScoreProjectSnapshot(
            events = first.events,
            bpm = bpm,
            tempoChanges = tempoChanges,
            cursorBeat = first.cursorBeat,
            selectedDuration = NoteDuration.QUARTER,
            selectedDotted = false,
            selectedArticulation = NoteArticulation.NORMAL,
            tracks = tracks,
            activeTrackIndex = 0,
            projectName = safeName,
            timeSignatures = timeSignatures,
            keySignatures = keySignatures,
        )
        return MidiImportResult(
            snapshot = snapshot,
            importedTrackCount = tracks.size,
            importedNoteCount = tracks.sumOf { it.notes.size },
            bpm = bpm,
            warnings = warnings.distinct(),
        )
    }

    private fun inferBankSelectMode(
        states: Map<Pair<Int, Int>, TrackChannelState>,
        projectName: String,
    ): BankSelectMode {
        val xgNameHint = Regex(
            pattern = "(^|[^A-Za-z0-9])XG([^A-Za-z0-9]|$)",
            option = RegexOption.IGNORE_CASE,
        ).containsMatchIn(projectName)
        val xgDrumHint = states.any { (key, state) ->
            key.second == 9 && (state.bankMsb and 0x7F) in setOf(120, 126, 127)
        }
        val xgSfxHint = states.any { (key, state) ->
            key.second != 9 && (state.bankMsb and 0x7F) == 64
        }
        val xgVariationHint = states.any { (key, state) ->
            key.second != 9 &&
                (state.bankMsb and 0x7F) == 0 &&
                (state.bankLsb and 0x7F) != 0
        }
        return if (
            (xgDrumHint && xgSfxHint) ||
            (xgNameHint && (xgDrumHint || xgSfxHint || xgVariationHint))
        ) {
            BankSelectMode.XG
        } else {
            BankSelectMode.MMA
        }
    }

    private fun resolvePresetBank(
        state: TrackChannelState,
        channel: Int,
        mode: BankSelectMode,
    ): Int {
        val msb = state.bankMsb and 0x7F
        val lsb = state.bankLsb and 0x7F
        return when (mode) {
            BankSelectMode.GM -> if (channel == 9) 128 else 0
            BankSelectMode.GS -> if (channel == 9) 128 else msb
            BankSelectMode.XG -> when {
                channel == 9 -> 128
                msb == 120 || msb == 126 || msb == 127 -> 128
                else -> lsb
            }
            BankSelectMode.MMA -> {
                val combined = (msb shl 7) or lsb
                if (channel == 9 && combined == 0) 128 else combined
            }
        }
    }

    private data class WrittenDuration(
        val duration: NoteDuration,
        val dotted: Boolean,
        val beats: Float,
    )

    private val writtenDurations = listOf(
        WrittenDuration(NoteDuration.SIXTEENTH, false, 0.25f),
        WrittenDuration(NoteDuration.SIXTEENTH, true, 0.375f),
        WrittenDuration(NoteDuration.EIGHTH, false, 0.5f),
        WrittenDuration(NoteDuration.EIGHTH, true, 0.75f),
        WrittenDuration(NoteDuration.QUARTER, false, 1f),
        WrittenDuration(NoteDuration.QUARTER, true, 1.5f),
        WrittenDuration(NoteDuration.HALF, false, 2f),
        WrittenDuration(NoteDuration.HALF, true, 3f),
        WrittenDuration(NoteDuration.WHOLE, false, 4f),
        WrittenDuration(NoteDuration.WHOLE, true, 6f),
    )

    private fun nearestWrittenDuration(beats: Float): WrittenDuration =
        writtenDurations.minByOrNull { abs(it.beats - beats.coerceAtLeast(0.01f)) }
            ?: WrittenDuration(NoteDuration.SIXTEENTH, false, 0.25f)

    private fun resolveTempos(
        events: List<TempoEvent>,
        ticksPerQuarter: Int,
        warnings: MutableList<String>,
    ): List<ScoreTempoChange> {
        if (events.isEmpty()) return listOf(ScoreTempoChange(0f, DEFAULT_BPM))

        val resolved = mutableListOf<ScoreTempoChange>()
        events.sortedBy { it.tick }
            .groupBy { it.tick }
            .forEach { (tick, atTick) ->
                val distinct = atTick.map { it.microsecondsPerQuarter }.distinct()
                val chosen = atTick.last().microsecondsPerQuarter.coerceAtLeast(1)
                val bpm = (60_000_000.0 / chosen.toDouble())
                    .roundToInt()
                    .coerceIn(ScoreTempos.MIN_BPM, ScoreTempos.MAX_BPM)
                if (distinct.size > 1) {
                    val beat = tick.toFloat() / ticksPerQuarter.toFloat()
                    warnings += "Conflicting MIDI tempos at beat ${formatBeat(beat)}; $bpm BPM was used."
                }
                resolved += ScoreTempoChange(
                    startBeat = tick.toFloat() / ticksPerQuarter.toFloat(),
                    bpm = bpm,
                )
            }
        return ScoreTempos.normalize(resolved)
    }

    private fun resolveTimeSignatures(
        events: List<TimeSignatureEvent>,
        ticksPerQuarter: Int,
        warnings: MutableList<String>,
    ): List<ScoreTimeSignature> {
        if (events.isEmpty()) return listOf(ScoreTimeSignatures.DEFAULT)

        val resolved = mutableListOf<ScoreTimeSignature>()
        events.sortedBy { it.tick }
            .groupBy { it.tick }
            .forEach { (tick, atTick) ->
                val distinct = atTick.map { it.numerator to it.denominator }.distinct()
                val chosen = atTick.last()
                if (distinct.size > 1) {
                    val beat = tick.toFloat() / ticksPerQuarter.toFloat()
                    warnings += "Conflicting MIDI time signatures at beat ${formatBeat(beat)}; ${chosen.numerator}/${chosen.denominator} was used."
                }
                resolved += ScoreTimeSignature(
                    startBeat = tick.toFloat() / ticksPerQuarter.toFloat(),
                    numerator = chosen.numerator,
                    denominator = chosen.denominator,
                )
            }

        return ScoreTimeSignatures.normalize(resolved)
    }

    private fun resolveKeySignatures(
        events: List<KeySignatureEvent>,
        ticksPerQuarter: Int,
        warnings: MutableList<String>,
    ): List<ScoreKeySignature> {
        if (events.isEmpty()) return listOf(ScoreKeySignatures.DEFAULT)

        val resolved = mutableListOf<ScoreKeySignature>()
        events.sortedBy { it.tick }
            .groupBy { it.tick }
            .forEach { (tick, atTick) ->
                val distinct = atTick.map { it.fifths to it.minor }.distinct()
                val chosen = atTick.last()
                if (distinct.size > 1) {
                    val beat = tick.toFloat() / ticksPerQuarter.toFloat()
                    warnings += "Conflicting MIDI key signatures at beat ${formatBeat(beat)}; ${ScoreKeySignature(0f, chosen.fifths, chosen.minor).displayName} was used."
                }
                resolved += ScoreKeySignature(
                    startBeat = tick.toFloat() / ticksPerQuarter.toFloat(),
                    fifths = chosen.fifths,
                    minor = chosen.minor,
                )
            }

        return ScoreKeySignatures.normalize(resolved)
    }

    private fun formatBeat(beat: Float): String {
        val rounded = (beat * 1000f).roundToInt() / 1000f
        return if (abs(rounded - rounded.toInt()) < 0.001f) {
            rounded.toInt().toString()
        } else {
            rounded.toString()
        }
    }

    private fun parse(bytes: ByteArray): ParsedMidi {
        val cursor = Cursor(bytes)
        require(cursor.readAscii(4) == HEADER) { "This file does not have a Standard MIDI header." }
        val headerLength = cursor.readUInt32().toInt()
        require(headerLength >= 6) { "The MIDI header is invalid." }
        val format = cursor.readUInt16()
        val trackCount = cursor.readUInt16()
        val division = cursor.readUInt16()
        require(format in 0..2) { "Unsupported MIDI format $format." }
        require(trackCount > 0) { "This MIDI file contains no tracks." }
        require((division and 0x8000) == 0) { "SMPTE-timed MIDI files are not supported yet." }
        val ticksPerQuarter = division and 0x7FFF
        require(ticksPerQuarter > 0) { "The MIDI timing division is invalid." }
        if (headerLength > 6) cursor.skip(headerLength - 6)

        val notes = mutableListOf<RawNote>()
        val states = mutableMapOf<Pair<Int, Int>, TrackChannelState>()
        val tempoEvents = mutableListOf<TempoEvent>()
        val timeSignatureEvents = mutableListOf<TimeSignatureEvent>()
        val keySignatureEvents = mutableListOf<KeySignatureEvent>()
        val sourceTrackNames = mutableMapOf<Int, String>()
        val systemExclusiveMessages = mutableListOf<ByteArray>()
        val warnings = mutableListOf<String>()
        var parsedTracks = 0

        while (cursor.remaining >= 8 && parsedTracks < trackCount) {
            val chunkType = cursor.readAscii(4)
            val chunkLength = cursor.readUInt32().toInt()
            require(chunkLength >= 0 && chunkLength <= cursor.remaining) { "A MIDI track chunk is truncated." }
            if (chunkType != TRACK) {
                cursor.skip(chunkLength)
                continue
            }
            val trackBytes = cursor.readBytes(chunkLength)
            parseTrack(
                bytes = trackBytes,
                sourceTrack = parsedTracks,
                notes = notes,
                states = states,
                tempoEvents = tempoEvents,
                timeSignatureEvents = timeSignatureEvents,
                keySignatureEvents = keySignatureEvents,
                sourceTrackNames = sourceTrackNames,
                systemExclusiveMessages = systemExclusiveMessages,
            )
            parsedTracks += 1
        }

        require(parsedTracks > 0) { "No MIDI track chunks were found." }
        if (format == 2) warnings += "Format-2 MIDI sequences were imported as parallel Score Forge tracks."
        return ParsedMidi(
            ticksPerQuarter = ticksPerQuarter,
            notes = notes,
            states = states,
            tempoEvents = tempoEvents,
            timeSignatureEvents = timeSignatureEvents,
            keySignatureEvents = keySignatureEvents,
            sourceTrackNames = sourceTrackNames,
            bankSelectMode = detectBankSelectMode(systemExclusiveMessages),
            warnings = warnings,
        )
    }

    private fun detectBankSelectMode(messages: List<ByteArray>): BankSelectMode {
        var mode = BankSelectMode.MMA
        messages.forEach { payload ->
            fun byte(index: Int): Int = payload[index].toInt() and 0xFF
            when {
                payload.size >= 7 &&
                    byte(0) == 0x43 &&
                    (byte(1) and 0xF0) == 0x10 &&
                    byte(2) == 0x4C &&
                    byte(3) == 0x00 &&
                    byte(4) == 0x00 &&
                    byte(5) == 0x7E &&
                    byte(6) == 0x00 -> mode = BankSelectMode.XG

                payload.size >= 8 &&
                    byte(0) == 0x41 &&
                    byte(2) == 0x42 &&
                    byte(3) == 0x12 &&
                    byte(4) == 0x40 &&
                    byte(5) == 0x00 &&
                    byte(6) == 0x7F &&
                    byte(7) == 0x00 -> mode = BankSelectMode.GS

                payload.size >= 4 &&
                    byte(0) == 0x7E &&
                    byte(2) == 0x09 &&
                    byte(3) == 0x03 -> mode = BankSelectMode.MMA

                payload.size >= 4 &&
                    byte(0) == 0x7E &&
                    byte(2) == 0x09 &&
                    byte(3) == 0x01 -> mode = BankSelectMode.GM
            }
        }
        return mode
    }

    private fun parseTrack(
        bytes: ByteArray,
        sourceTrack: Int,
        notes: MutableList<RawNote>,
        states: MutableMap<Pair<Int, Int>, TrackChannelState>,
        tempoEvents: MutableList<TempoEvent>,
        timeSignatureEvents: MutableList<TimeSignatureEvent>,
        keySignatureEvents: MutableList<KeySignatureEvent>,
        sourceTrackNames: MutableMap<Int, String>,
        systemExclusiveMessages: MutableList<ByteArray>,
    ) {
        val cursor = Cursor(bytes)
        val active = mutableMapOf<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>()
        var tick = 0L
        var runningStatus = -1
        var trackName = ""

        fun state(channel: Int): TrackChannelState =
            states.getOrPut(sourceTrack to channel) { TrackChannelState(name = trackName) }

        while (cursor.remaining > 0) {
            tick += cursor.readVarLen()
            if (cursor.remaining <= 0) break
            var status = cursor.peekU8()
            var firstData: Int? = null
            if (status < 0x80) {
                require(runningStatus >= 0x80) { "Invalid MIDI running status." }
                status = runningStatus
                firstData = cursor.readU8()
            } else {
                status = cursor.readU8()
                if (status < 0xF0) runningStatus = status
            }

            when {
                status == 0xFF -> {
                    runningStatus = -1
                    val type = cursor.readU8()
                    val length = cursor.readVarLen().toInt()
                    require(length <= cursor.remaining) { "A MIDI meta event is truncated." }
                    val payload = cursor.readBytes(length)
                    when (type) {
                        0x03 -> {
                            trackName = payload.toString(Charsets.UTF_8).replace('\u0000', ' ').trim().take(80)
                            if (trackName.isNotBlank()) sourceTrackNames[sourceTrack] = trackName
                        }
                        0x2F -> break
                        0x51 -> if (payload.size == 3) {
                            val us = ((payload[0].toInt() and 0xFF) shl 16) or
                                ((payload[1].toInt() and 0xFF) shl 8) or
                                (payload[2].toInt() and 0xFF)
                            if (us > 0) tempoEvents += TempoEvent(tick, us)
                        }
                        0x58 -> if (payload.size >= 2) {
                            val numerator = payload[0].toInt() and 0xFF
                            val denominatorPower = payload[1].toInt() and 0xFF
                            if (numerator in 1..32 && denominatorPower in 0..7) {
                                timeSignatureEvents += TimeSignatureEvent(
                                    tick = tick,
                                    numerator = numerator,
                                    denominator = 1 shl denominatorPower,
                                )
                            }
                        }
                        0x59 -> if (payload.size >= 2) {
                            val fifths = payload[0].toInt()
                            val mode = payload[1].toInt() and 0xFF
                            if (fifths in -7..7 && mode in 0..1) {
                                keySignatureEvents += KeySignatureEvent(
                                    tick = tick,
                                    fifths = fifths,
                                    minor = mode == 1,
                                )
                            }
                        }
                    }
                }
                status == 0xF0 || status == 0xF7 -> {
                    runningStatus = -1
                    val length = cursor.readVarLen().toInt()
                    require(length <= cursor.remaining) { "A MIDI SysEx event is truncated." }
                    val payload = cursor.readBytes(length)
                    if (status == 0xF0) systemExclusiveMessages += payload
                }
                status in 0x80..0xEF -> {
                    val command = status and 0xF0
                    val channel = status and 0x0F
                    val data1 = firstData ?: cursor.readU8()
                    when (command) {
                        0x80 -> {
                            val velocity = cursor.readU8()
                            finishNote(active, notes, sourceTrack, channel, data1, tick)
                            @Suppress("UNUSED_VARIABLE") val ignored = velocity
                        }
                        0x90 -> {
                            val velocity = cursor.readU8()
                            if (velocity == 0) {
                                finishNote(active, notes, sourceTrack, channel, data1, tick)
                            } else {
                                active.getOrPut(channel to data1) { ArrayDeque() }
                                    .addLast(tick to velocity)
                            }
                        }
                        0xA0, 0xE0 -> cursor.readU8()
                        0xB0 -> {
                            val value = cursor.readU8()
                            when (data1) {
                                0 -> state(channel).bankMsb = value
                                32 -> state(channel).bankLsb = value
                                7 -> if (state(channel).volume == null) state(channel).volume = value
                                10 -> if (state(channel).pan == null) state(channel).pan = value
                            }
                        }
                        0xC0 -> state(channel).program = data1.coerceIn(0, 127)
                        0xD0 -> Unit
                    }
                }
                else -> throw IllegalArgumentException("Unsupported MIDI status 0x${status.toString(16)}.")
            }
        }

        // Malformed files occasionally omit final note-off messages. Keep the notes editable instead of
        // throwing the whole import away, using one quarter note as a conservative fallback length.
        active.forEach { (key, starts) ->
            val channel = key.first
            val pitch = key.second
            while (starts.isNotEmpty()) {
                val (start, velocity) = starts.removeFirst()
                notes += RawNote(sourceTrack, channel, pitch, velocity, start, start + 1L)
            }
        }
    }

    private fun finishNote(
        active: MutableMap<Pair<Int, Int>, ArrayDeque<Pair<Long, Int>>>,
        notes: MutableList<RawNote>,
        sourceTrack: Int,
        channel: Int,
        pitch: Int,
        tick: Long,
    ) {
        val queue = active[channel to pitch] ?: return
        val start = if (queue.isEmpty()) null else queue.removeFirst()
        if (queue.isEmpty()) active.remove(channel to pitch)
        if (start != null) {
            notes += RawNote(
                sourceTrack = sourceTrack,
                channel = channel,
                pitch = pitch,
                velocity = start.second,
                startTick = start.first,
                endTick = tick.coerceAtLeast(start.first + 1L),
            )
        }
    }

    private class Cursor(private val bytes: ByteArray) {
        private var position = 0
        val remaining: Int get() = bytes.size - position

        fun readU8(): Int {
            require(remaining >= 1) { "Unexpected end of MIDI file." }
            return bytes[position++].toInt() and 0xFF
        }

        fun peekU8(): Int {
            require(remaining >= 1) { "Unexpected end of MIDI file." }
            return bytes[position].toInt() and 0xFF
        }

        fun readUInt16(): Int = (readU8() shl 8) or readU8()

        fun readUInt32(): Long =
            (readU8().toLong() shl 24) or
                (readU8().toLong() shl 16) or
                (readU8().toLong() shl 8) or
                readU8().toLong()

        fun readAscii(length: Int): String = readBytes(length).toString(Charsets.US_ASCII)

        fun readBytes(length: Int): ByteArray {
            require(length >= 0 && length <= remaining) { "Unexpected end of MIDI file." }
            val result = bytes.copyOfRange(position, position + length)
            position += length
            return result
        }

        fun skip(length: Int) {
            require(length >= 0 && length <= remaining) { "Unexpected end of MIDI file." }
            position += length
        }

        fun readVarLen(): Long {
            var value = 0L
            repeat(4) {
                val next = readU8()
                value = (value shl 7) or (next and 0x7F).toLong()
                if ((next and 0x80) == 0) return value
            }
            throw IllegalArgumentException("Invalid MIDI variable-length value.")
        }
    }
}
