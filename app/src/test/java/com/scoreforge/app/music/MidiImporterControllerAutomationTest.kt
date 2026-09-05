package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream

class MidiImporterControllerAutomationTest {
    @Test
    fun endOfSongFadeDoesNotMuteWholeImportedTrack() {
        val out = ByteArrayOutputStream()
        fun bytes(vararg values: Int) = values.forEach(out::write)
        fun ascii(value: String) = value.forEach { out.write(it.code) }
        fun int32(value: Int) = bytes(value ushr 24, value ushr 16, value ushr 8, value)

        ascii("MThd"); int32(6); bytes(0, 0, 0, 1, 1, 0)
        val track = ByteArrayOutputStream()
        fun t(vararg values: Int) = values.forEach(track::write)
        t(0x00, 0xB0, 0x07, 127)
        t(0x00, 0x90, 60, 100)
        t(0x81, 0x00, 0x80, 60, 0)
        t(0x00, 0xB0, 0x07, 0)
        t(0x00, 0xFF, 0x2F, 0x00)
        ascii("MTrk"); int32(track.size()); out.write(track.toByteArray())

        val result = MidiImporter.import(out.toByteArray(), "Fade Test")
        assertEquals(127, result.snapshot.effectiveTracks().single().volume)
    }
}
