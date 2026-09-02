package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiImporterKeySignatureTest {
    @Test
    fun importsMajorFlatAndMinorKeyChanges() {
        val payload = bytes(
            0x00, 0xFF, 0x59, 0x02, 0x01, 0x00, // G major at beat 0
            0x00, 0x90, 60, 96,
        ) + varLen(480) + bytes(
            0x80, 60, 0,
        ) + varLen(480) + bytes(
            0xFF, 0x59, 0x02, 0xFD, 0x00, // Eb major (-3) at beat 2
            0x00, 0x90, 63, 96,
        ) + varLen(480) + bytes(
            0x80, 63, 0,
        ) + varLen(480) + bytes(
            0xFF, 0x59, 0x02, 0xFF, 0x01, // D minor (-1) at beat 4
            0x00, 0x90, 62, 96,
        ) + varLen(480) + bytes(
            0x80, 62, 0,
            0x00, 0xFF, 0x2F, 0x00,
        )

        val result = MidiImporter.import(midiFile(480, track(payload)))

        assertEquals(
            listOf(
                ScoreKeySignature(startBeat = 0f, fifths = 1, minor = false),
                ScoreKeySignature(startBeat = 2f, fifths = -3, minor = false),
                ScoreKeySignature(startBeat = 4f, fifths = -1, minor = true),
            ),
            result.snapshot.effectiveKeySignatures(),
        )
        assertEquals(3, result.importedNoteCount)
        assertFalse(result.warnings.any { it.contains("key signature", ignoreCase = true) })
    }

    @Test
    fun insertsDefaultCmajorBeforeLateMidiKeyEvent() {
        val payload = bytes(
            0x00, 0x90, 60, 96,
        ) + varLen(480) + bytes(
            0x80, 60, 0,
        ) + varLen(480) + bytes(
            0xFF, 0x59, 0x02, 0x02, 0x00, // D major at beat 2
            0x00, 0xFF, 0x2F, 0x00,
        )

        val keys = MidiImporter.import(midiFile(480, track(payload)))
            .snapshot
            .effectiveKeySignatures()

        assertEquals(ScoreKeySignatures.DEFAULT, keys[0])
        assertEquals(ScoreKeySignature(startBeat = 2f, fifths = 2), keys[1])
    }

    private fun midiFile(ticksPerQuarter: Int, track: ByteArray): ByteArray {
        val out = ByteArrayOutputStream()
        out.write("MThd".toByteArray(Charsets.US_ASCII))
        writeInt(out, 6)
        writeShort(out, 0)
        writeShort(out, 1)
        writeShort(out, ticksPerQuarter)
        out.write(track)
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
