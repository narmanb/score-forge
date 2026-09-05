package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiImporterTempoMapTest {
    @Test
    fun importsAllTempoEventsAsEditableMap() {
        val out = ByteArrayOutputStream()
        fun bytes(vararg values: Int) = values.forEach(out::write)
        fun ascii(value: String) = value.forEach { out.write(it.code) }
        fun int32(value: Int) = bytes(value ushr 24, value ushr 16, value ushr 8, value)
        ascii("MThd"); int32(6); bytes(0, 0, 0, 1, 1, 0)
        val track = ByteArrayOutputStream()
        fun t(vararg values: Int) = values.forEach(track::write)
        t(0x00, 0xFF, 0x51, 0x03, 0x07, 0xA1, 0x20)
        t(0x00, 0x90, 60, 100)
        t(0x82, 0x00, 0x80, 60, 0)
        t(0x82, 0x00, 0xFF, 0x51, 0x03, 0x0F, 0x42, 0x40)
        t(0x00, 0xFF, 0x2F, 0x00)
        ascii("MTrk"); int32(track.size()); out.write(track.toByteArray())
        val result = MidiImporter.import(out.toByteArray(), "Tempo Test")
        val tempos = result.snapshot.effectiveTempoChanges()
        assertEquals(2, tempos.size)
        assertEquals(120, tempos[0].bpm)
        assertEquals(0f, tempos[0].startBeat, 0.001f)
        assertEquals(60, tempos[1].bpm)
        assertEquals(2f, tempos[1].startBeat, 0.001f)
        assertFalse(result.warnings.any { it.contains("not editable yet") })
    }
}
