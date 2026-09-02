package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiImporterTest {
    @Test
    fun importsTempoNotesVelocityAndProgram() {
        val midi = midiFile(
            ticksPerQuarter = 480,
            tracks = listOf(
                track(
                    bytes(
                        0x00, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20, // 120 BPM
                        0x00, 0xFF, 0x2F, 0x00,
                    )
                ),
                track(
                    bytes(
                        0x00, 0xFF, 0x03, 0x05, 'P'.code, 'i'.code, 'a'.code, 'n'.code, 'o'.code,
                        0x00, 0xC0, 0x05,
                        0x00, 0x90, 60, 100,
                    ) + varLen(480) + bytes(
                        0x80, 60, 0,
                        0x00, 0x90, 64, 80,
                    ) + varLen(960) + bytes(
                        0x80, 64, 0,
                        0x00, 0xFF, 0x2F, 0x00,
                    )
                ),
            ),
        )

        val result = MidiImporter.import(midi, "Test Song")

        assertEquals(120, result.bpm)
        assertEquals(1, result.importedTrackCount)
        assertEquals(2, result.importedNoteCount)
        assertEquals("Test Song", result.snapshot.projectName)
        assertEquals(listOf(ScoreTimeSignature()), result.snapshot.timeSignatures)
        val importedTrack = result.snapshot.tracks.single()
        assertEquals("Piano", importedTrack.name)
        assertEquals(5, importedTrack.presetProgram)
        val first = importedTrack.notes[0]
        val second = importedTrack.notes[1]
        assertEquals(60, first.midiPitch)
        assertEquals(100, first.velocity)
        assertEquals(NoteDuration.QUARTER, first.duration)
        assertFalse(first.dotted)
        assertEquals(NoteDuration.HALF, second.duration)
        assertEquals(1f, second.startBeat, 0.0001f)
    }

    @Test
    fun splitsChannelsAndQuantizesNotation() {
        val midi = midiFile(
            ticksPerQuarter = 480,
            tracks = listOf(
                track(
                    bytes(
                        0x00, 0x90, 60, 90,
                    ) + varLen(350) + bytes(0x80, 60, 0) +
                        bytes(0x00, 0x91, 67, 90) + varLen(240) +
                        bytes(0x81, 67, 0, 0x00, 0xFF, 0x2F, 0x00)
                )
            ),
        )

        val result = MidiImporter.import(midi)

        assertEquals(2, result.importedTrackCount)
        assertEquals(2, result.importedNoteCount)
        assertTrue(result.warnings.any { it.contains("quantized") })
        assertEquals(NoteDuration.EIGHTH, result.snapshot.tracks[1].notes.single().duration)
        assertTrue(result.snapshot.tracks.all { it.name.contains("Ch") })
    }

    @Test
    fun reportsTempoChangesAndUsesFirstTempo() {
        val midi = midiFile(
            ticksPerQuarter = 480,
            tracks = listOf(
                track(
                    bytes(
                        0x00, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20,
                    ) + varLen(480) + bytes(
                        0xFF, 0x51, 0x03, 0x0F, 0x42, 0x40,
                        0x00, 0x90, 60, 96,
                    ) + varLen(480) + bytes(
                        0x80, 60, 0,
                        0x00, 0xFF, 0x2F, 0x00,
                    )
                )
            ),
        )

        val result = MidiImporter.import(midi)
        assertEquals(120, result.bpm)
        assertTrue(result.warnings.any { it.contains("Tempo changes") })
    }

    @Test
    fun importsTimeSignatureAndMidSongMeterChange() {
        val midi = midiFile(
            ticksPerQuarter = 480,
            tracks = listOf(
                track(
                    bytes(
                        0x00, 0xFF, 0x58, 0x04, 0x03, 0x02, 0x18, 0x08, // 3/4 at beat 0
                        0x00, 0x90, 60, 96,
                    ) + varLen(480) + bytes(
                        0x80, 60, 0,
                    ) + varLen(960) + bytes(
                        0xFF, 0x58, 0x04, 0x06, 0x03, 0x18, 0x08, // 6/8 at beat 3
                        0x00, 0xFF, 0x2F, 0x00,
                    )
                )
            ),
        )

        val result = MidiImporter.import(midi)

        assertEquals(
            listOf(
                ScoreTimeSignature(startBeat = 0f, numerator = 3, denominator = 4),
                ScoreTimeSignature(startBeat = 3f, numerator = 6, denominator = 8),
            ),
            result.snapshot.timeSignatures,
        )
        assertFalse(result.warnings.any { it.contains("time signature", ignoreCase = true) })
    }

    @Test
    fun insertsDefaultFourFourBeforeLateMidiMeterEvent() {
        val midi = midiFile(
            ticksPerQuarter = 480,
            tracks = listOf(
                track(
                    bytes(
                        0x00, 0x90, 60, 96,
                    ) + varLen(480) + bytes(
                        0x80, 60, 0,
                    ) + varLen(480) + bytes(
                        0xFF, 0x58, 0x04, 0x05, 0x02, 0x18, 0x08,
                        0x00, 0xFF, 0x2F, 0x00,
                    )
                )
            ),
        )

        val result = MidiImporter.import(midi)

        assertEquals(ScoreTimeSignature(), result.snapshot.timeSignatures[0])
        assertEquals(ScoreTimeSignature(2f, 5, 4), result.snapshot.timeSignatures[1])
    }

    private fun midiFile(ticksPerQuarter: Int, tracks: List<ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt(out, 6)
        writeShort(out, if (tracks.size > 1) 1 else 0)
        writeShort(out, tracks.size)
        writeShort(out, ticksPerQuarter)
        tracks.forEach { out.write(it) }
        return out.toByteArray()
    }

    private fun track(payload: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MTrk".toByteArray(Charsets.US_ASCII))
        writeInt(out, payload.size)
        out.write(payload)
        return out.toByteArray()
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun varLen(value: Int): ByteArray {
        var buffer = value and 0x7F
        var remaining = value ushr 7
        while (remaining > 0) {
            buffer = (buffer shl 8) or ((remaining and 0x7F) or 0x80)
            remaining = remaining ushr 7
        }
        val out = ByteArrayOutputStream()
        while (true) {
            out.write(buffer and 0xFF)
            if ((buffer and 0x80) != 0) buffer = buffer ushr 8 else break
        }
        return out.toByteArray()
    }

    private fun writeShort(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }

    private fun writeInt(out: ByteArrayOutputStream, value: Int) {
        out.write((value ushr 24) and 0xFF)
        out.write((value ushr 16) and 0xFF)
        out.write((value ushr 8) and 0xFF)
        out.write(value and 0xFF)
    }
}
