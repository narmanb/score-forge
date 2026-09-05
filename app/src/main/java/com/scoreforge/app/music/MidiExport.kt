package com.scoreforge.app.music

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import kotlin.math.roundToLong

/** Result of exporting a Score Forge project as a Standard MIDI File. */
data class MidiExportResult(
    val bytes: ByteArray,
    val exportedTrackCount: Int,
    val exportedNoteCount: Int,
    val warnings: List<String> = emptyList(),
)

/**
 * Dependency-free Standard MIDI File (SMF) exporter.
 *
 * The exporter writes format 1 MIDI with a conductor track for project-wide tempo, meter and key
 * metadata plus one MIDI track per Score Forge track. Mixer/instrument state is emitted at tick 0.
 * MIDI has no native notation tie or articulation objects, so ties are exported as sustained notes
 * and articulations are rendered through their playback velocity/gate interpretation.
 */
object MidiExporter {
    private const val TICKS_PER_QUARTER = 480
    private const val FORMAT_1 = 1
    private const val PERCUSSION_CHANNEL = 9

    private data class TimedEvent(
        val tick: Long,
        val priority: Int,
        val order: Int,
        val data: ByteArray,
    )

    fun export(snapshot: ScoreProjectSnapshot): MidiExportResult {
        val tracks = snapshot.effectiveTracks()
        val warnings = mutableListOf<String>()
        val channels = assignChannels(tracks, warnings)

        val chunks = mutableListOf<ByteArray>()
        chunks += encodeConductorTrack(snapshot)

        var noteCount = 0
        tracks.forEachIndexed { index, track ->
            noteCount += track.notes.count { note ->
                val noteIndex = track.notes.indexOf(note)
                !ScoreTies.isContinuation(track.notes, noteIndex)
            }
            chunks += encodeMusicTrack(track.normalized(), channels[index])
        }

        val output = ByteArrayOutputStream()
        DataOutputStream(output).use { data ->
            data.writeBytes("MThd")
            data.writeInt(6)
            data.writeShort(FORMAT_1)
            data.writeShort(chunks.size)
            data.writeShort(TICKS_PER_QUARTER)
            chunks.forEach { chunk ->
                data.writeBytes("MTrk")
                data.writeInt(chunk.size)
                data.write(chunk)
            }
        }

        return MidiExportResult(
            bytes = output.toByteArray(),
            exportedTrackCount = tracks.size,
            exportedNoteCount = noteCount,
            warnings = warnings.distinct(),
        )
    }

    private fun assignChannels(
        tracks: List<ScoreTrack>,
        warnings: MutableList<String>,
    ): List<Int> {
        val melodicChannels = (0..15).filter { it != PERCUSSION_CHANNEL }
        var melodicIndex = 0
        var percussionCount = 0

        return tracks.map { track ->
            if (track.presetBank == 128) {
                percussionCount += 1
                PERCUSSION_CHANNEL
            } else if (melodicIndex < melodicChannels.size) {
                melodicChannels[melodicIndex++]
            } else {
                warnings +=
                    "This project has 16 melodic tracks, so the last track must use MIDI channel 10; " +
                        "General MIDI players may interpret that channel as percussion."
                PERCUSSION_CHANNEL
            }
        }.also {
            if (percussionCount > 1) {
                warnings +=
                    "Multiple percussion tracks share MIDI channel 10, so per-track program, volume or pan " +
                        "differences may not remain independent in every MIDI player."
            }
        }
    }

    private fun encodeConductorTrack(snapshot: ScoreProjectSnapshot): ByteArray {
        val events = mutableListOf<TimedEvent>()
        var order = 0

        fun add(beat: Float, priority: Int, data: ByteArray) {
            events += TimedEvent(beatToTick(beat), priority, order++, data)
        }

        add(0f, 0, metaText(0x03, snapshot.safeProjectName()))

        snapshot.effectiveTempoChanges().forEach { tempo ->
            val microsPerQuarter = (60_000_000.0 / tempo.bpm.coerceAtLeast(1).toDouble())
                .roundToLong()
                .coerceIn(1L, 0xFFFFFFL)
                .toInt()
            add(
                tempo.startBeat,
                10,
                meta(
                    0x51,
                    byteArrayOf(
                        ((microsPerQuarter ushr 16) and 0xFF).toByte(),
                        ((microsPerQuarter ushr 8) and 0xFF).toByte(),
                        (microsPerQuarter and 0xFF).toByte(),
                    ),
                ),
            )
        }

        snapshot.effectiveTimeSignatures().forEach { signature ->
            val denominatorPower = Integer.numberOfTrailingZeros(signature.denominator.coerceAtLeast(1))
            add(
                signature.startBeat,
                20,
                meta(
                    0x58,
                    byteArrayOf(
                        signature.numerator.coerceIn(1, 255).toByte(),
                        denominatorPower.coerceIn(0, 7).toByte(),
                        24,
                        8,
                    ),
                ),
            )
        }

        snapshot.effectiveKeySignatures().forEach { signature ->
            add(
                signature.startBeat,
                30,
                meta(
                    0x59,
                    byteArrayOf(
                        signature.fifths.coerceIn(-7, 7).toByte(),
                        if (signature.minor) 1 else 0,
                    ),
                ),
            )
        }

        return encodeTrack(events)
    }

    private fun encodeMusicTrack(track: ScoreTrack, channel: Int): ByteArray {
        val events = mutableListOf<TimedEvent>()
        var order = 0

        fun add(tick: Long, priority: Int, data: ByteArray) {
            events += TimedEvent(tick.coerceAtLeast(0L), priority, order++, data)
        }

        add(0L, 0, metaText(0x03, track.name))

        val bank = track.presetBank
        if (bank != null && !(channel == PERCUSSION_CHANNEL && bank == 128)) {
            add(0L, 1, channelMessage(0xB0 or channel, 0, (bank ushr 7) and 0x7F))
            add(0L, 2, channelMessage(0xB0 or channel, 32, bank and 0x7F))
        }
        track.presetProgram?.let { program ->
            add(0L, 3, channelMessage(0xC0 or channel, program.coerceIn(0, 127)))
        }
        add(0L, 4, channelMessage(0xB0 or channel, 7, track.volume.coerceIn(0, 127)))
        add(0L, 5, channelMessage(0xB0 or channel, 10, (track.pan + 64).coerceIn(0, 127)))

        val notes = track.notes
        notes.forEachIndexed { noteIndex, note ->
            if (ScoreTies.isContinuation(notes, noteIndex)) return@forEachIndexed

            val startTick = beatToTick(note.startBeat)
            val endBeat = if (ScoreTies.hasValidTie(notes, noteIndex)) {
                ScoreTies.chainEndBeat(notes, noteIndex)
            } else {
                ScoreArticulations.playbackEndBeat(notes, noteIndex)
            }
            val endTick = maxOf(startTick + 1L, beatToTick(endBeat))
            val velocity = ScoreArticulations.playbackVelocity(note)
            val pitch = note.midiPitch.coerceIn(0, 127)

            add(startTick, 20, channelMessage(0x90 or channel, pitch, velocity))
            add(endTick, 10, channelMessage(0x80 or channel, pitch, 0))
        }

        return encodeTrack(events)
    }

    private fun encodeTrack(sourceEvents: List<TimedEvent>): ByteArray {
        val events = sourceEvents.toMutableList()
        val endTick = events.maxOfOrNull { it.tick } ?: 0L
        events += TimedEvent(endTick, 100, Int.MAX_VALUE, meta(0x2F, byteArrayOf()))

        val sorted = events.sortedWith(
            compareBy<TimedEvent> { it.tick }
                .thenBy { it.priority }
                .thenBy { it.order }
        )

        val output = ByteArrayOutputStream()
        var previousTick = 0L
        sorted.forEach { event ->
            val delta = (event.tick - previousTick).coerceAtLeast(0L)
            output.write(variableLengthQuantity(delta))
            output.write(event.data)
            previousTick = event.tick
        }
        return output.toByteArray()
    }

    private fun beatToTick(beat: Float): Long =
        (beat.coerceAtLeast(0f).toDouble() * TICKS_PER_QUARTER.toDouble()).roundToLong()

    private fun channelMessage(status: Int, data1: Int): ByteArray =
        byteArrayOf(status.toByte(), data1.coerceIn(0, 127).toByte())

    private fun channelMessage(status: Int, data1: Int, data2: Int): ByteArray =
        byteArrayOf(
            status.toByte(),
            data1.coerceIn(0, 127).toByte(),
            data2.coerceIn(0, 127).toByte(),
        )

    private fun metaText(type: Int, text: String): ByteArray =
        meta(type, text.toByteArray(Charsets.UTF_8))

    private fun meta(type: Int, payload: ByteArray): ByteArray = buildList<Byte> {
        add(0xFF.toByte())
        add(type.toByte())
        addAll(variableLengthQuantity(payload.size.toLong()).toList())
        addAll(payload.toList())
    }.toByteArray()

    private fun variableLengthQuantity(value: Long): ByteArray {
        var remaining = value.coerceIn(0L, 0x0FFFFFFFL)
        var buffer = remaining and 0x7F
        val bytes = mutableListOf<Byte>()
        while (remaining ushr 7 > 0) {
            remaining = remaining ushr 7
            buffer = (buffer shl 8) or ((remaining and 0x7F) or 0x80)
        }
        while (true) {
            bytes += (buffer and 0xFF).toByte()
            if (buffer and 0x80 != 0L) {
                buffer = buffer ushr 8
            } else {
                break
            }
        }
        return bytes.toByteArray()
    }
}
