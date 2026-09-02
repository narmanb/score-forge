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
 * start, written duration, velocity, track/channel grouping, tempo, program, bank, volume and pan.
 * Continuous controllers, automation and mid-song tempo changes are retained only as warnings until
 * Score Forge has a model that can represent them without silently pretending they were preserved.
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

    private data class TempoEvent(val tick: Long, val microsecondsPerQuarter: Int)

    private data class ParsedMidi(
        val ticksPerQuarter: Int,
        val notes: List<RawNote>,
        val states: Map<Pair<Int, Int>, TrackChannelState>,
        val tempoEvents: List<TempoEvent>,
        val sourceTrackNames: Map<Int, String>,
        val warnings: MutableList<String>,
    )

    fun import(bytes: ByteArray, projectName: String = "Imported MIDI"): MidiImportResult {
        require(bytes.isNotEmpty()) { "The selected MIDI file is empty." }
        require(bytes.size <= MAX_BYTES) { "This MIDI file is larger than the current 16 MB import limit." }
        val parsed = parse(bytes)
        require(parsed.notes.isNotEmpty()) { "No playable note events were found in this MIDI file." }

        val warnings = parsed.warnings.toMutableList()
        val bpm = resolveBpm(parsed.tempoEvents, warnings)
        val grouped = parsed.notes.groupBy { it.sourceTrack to it.channel }
            .entries
            .sortedWith(compareBy({ it.key.first }, { it.key.second }))

        if (grouped.size > ScoreTracks.MAX_TRACKS) {
            warnings += "Only the first ${ScoreTracks.MAX_TRACKS} MIDI track/channel groups were imported."
        }

        var quantizedCount = 0
        val tracks = grouped.take(ScoreTracks.MAX_TRACKS).mapIndexedNotNull { index, (key, rawNotes) ->
            val sourceTrack = key.first
            val channel = key.second
            val state = parsed.states[key] ?: TrackChannelState()
            val events = rawNotes
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
            val sourceName = parsed.sourceTrackNames[sourceTrack].orEmpty().trim()
            val channelSuffix = if (grouped.count { it.key.first == sourceTrack } > 1) " Ch ${channel + 1}" else ""
            val trackName = (sourceName.ifBlank { "MIDI Track ${sourceTrack + 1}" } + channelSuffix)
                .replace('\t', ' ')
                .replace('\n', ' ')
                .take(80)
            val bank = ((state.bankMsb and 0x7F) shl 7) or (state.bankLsb and 0x7F)
            ScoreTrack(
                id = index + 1,
                name = trackName,
                events = events,
                cursorBeat = ScoreTimeline.endBeat(events),
                presetBank = if (state.program != null || bank != 0) bank else null,
                presetProgram = state.program,
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
            cursorBeat = first.cursorBeat,
            selectedDuration = NoteDuration.QUARTER,
            selectedDotted = false,
            selectedArticulation = NoteArticulation.NORMAL,
            tracks = tracks,
            activeTrackIndex = 0,
            projectName = safeName,
        )
        return MidiImportResult(
            snapshot = snapshot,
            importedTrackCount = tracks.size,
            importedNoteCount = tracks.sumOf { it.notes.size },
            bpm = bpm,
            warnings = warnings.distinct(),
        )
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

    private fun resolveBpm(tempoEvents: List<TempoEvent>, warnings: MutableList<String>): Int {
        if (tempoEvents.isEmpty()) return DEFAULT_BPM
        val ordered = tempoEvents.sortedBy { it.tick }
        val first = ordered.first().microsecondsPerQuarter.coerceAtLeast(1)
        val bpm = (60_000_000.0 / first.toDouble()).roundToInt().coerceIn(30, 300)
        if (ordered.map { it.microsecondsPerQuarter }.distinct().size > 1) {
            warnings += "Tempo changes are not editable yet; the first MIDI tempo ($bpm BPM) was used for the project."
        }
        return bpm
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
        val sourceTrackNames = mutableMapOf<Int, String>()
        val warnings = mutableListOf<String>()
        var foundTimeSignatureChange = false
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
                sourceTrackNames = sourceTrackNames,
                onNonFourFour = { foundTimeSignatureChange = true },
            )
            parsedTracks += 1
        }

        require(parsedTracks > 0) { "No MIDI track chunks were found." }
        if (format == 2) warnings += "Format-2 MIDI sequences were imported as parallel Score Forge tracks."
        if (foundTimeSignatureChange) {
            warnings += "Score Forge currently displays 4/4; non-4/4 MIDI time signatures were not preserved."
        }
        return ParsedMidi(
            ticksPerQuarter = ticksPerQuarter,
            notes = notes,
            states = states,
            tempoEvents = tempoEvents,
            sourceTrackNames = sourceTrackNames,
            warnings = warnings,
        )
    }

    private fun parseTrack(
        bytes: ByteArray,
        sourceTrack: Int,
        notes: MutableList<RawNote>,
        states: MutableMap<Pair<Int, Int>, TrackChannelState>,
        tempoEvents: MutableList<TempoEvent>,
        sourceTrackNames: MutableMap<Int, String>,
        onNonFourFour: () -> Unit,
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
                            val denominator = 1 shl (payload[1].toInt() and 0xFF).coerceIn(0, 7)
                            if (numerator != 4 || denominator != 4) onNonFourFour()
                        }
                    }
                }
                status == 0xF0 || status == 0xF7 -> {
                    runningStatus = -1
                    val length = cursor.readVarLen().toInt()
                    cursor.skip(length)
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
                                7 -> state(channel).volume = value
                                10 -> state(channel).pan = value
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
