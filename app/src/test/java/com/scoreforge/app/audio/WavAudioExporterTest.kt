package com.scoreforge.app.audio

import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreProjectSnapshot
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WavAudioExporterTest {
    @Test
    fun wavHeader_describes44100Stereo16BitPcm() {
        val header = WavAudioExporter.wavHeader(sampleRate = 44_100, totalFrames = 44_100)

        assertEquals(44, header.size)
        assertEquals("RIFF", header.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals("WAVE", header.copyOfRange(8, 12).toString(Charsets.US_ASCII))
        assertEquals("fmt ", header.copyOfRange(12, 16).toString(Charsets.US_ASCII))
        assertEquals("data", header.copyOfRange(36, 40).toString(Charsets.US_ASCII))
        assertEquals(2, le16(header, 22))
        assertEquals(44_100L, le32(header, 24))
        assertEquals(176_400L, le32(header, 28))
        assertEquals(4, le16(header, 32))
        assertEquals(16, le16(header, 34))
        assertEquals(176_400L, le32(header, 40))
        assertEquals(176_436L, le32(header, 4))
    }

    @Test
    fun renderPlan_usesTempoMapForEventFrames() {
        val first = ScoreNote(
            midiPitch = 60,
            duration = NoteDuration.QUARTER,
            startBeat = 0f,
        )
        val second = ScoreNote(
            midiPitch = 64,
            duration = NoteDuration.QUARTER,
            startBeat = 2f,
        )
        val track = ScoreTrack(
            id = 1,
            name = "Piano",
            events = listOf(first, second),
        )
        val snapshot = ScoreProjectSnapshot(
            events = track.events,
            bpm = 120,
            tempoChanges = listOf(
                ScoreTempoChange(0f, 120),
                ScoreTempoChange(1f, 60),
            ),
            tracks = listOf(track),
        )

        val plan = WavAudioExporter.buildRenderPlan(
            snapshot = snapshot,
            sampleRate = 44_100,
            tailSeconds = 0f,
        )

        val secondOn = plan.events.first { it.noteOn && it.key == 64 }
        assertEquals(66_150, secondOn.frame)
        assertEquals(2, plan.noteCount)
        assertEquals(110_250, plan.scoreFrames)
    }

    @Test
    fun renderPlan_suppressesContinuationAttackForTieChain() {
        val track = ScoreTrack(
            id = 1,
            name = "Tie",
            events = listOf(
                ScoreNote(
                    midiPitch = 60,
                    duration = NoteDuration.QUARTER,
                    startBeat = 0f,
                    tieToNext = true,
                ),
                ScoreNote(
                    midiPitch = 60,
                    duration = NoteDuration.QUARTER,
                    startBeat = 1f,
                ),
            ),
        )
        val snapshot = ScoreProjectSnapshot(
            events = track.events,
            tracks = listOf(track),
        )

        val plan = WavAudioExporter.buildRenderPlan(snapshot, sampleRate = 44_100, tailSeconds = 0f)
        val noteOns = plan.events.filter { it.noteOn && it.key == 60 }
        val noteOffs = plan.events.filter { !it.noteOn && it.key == 60 }

        assertEquals(1, noteOns.size)
        assertEquals(1, noteOffs.size)
        assertTrue(noteOffs.single().frame > noteOns.single().frame)
    }

    @Test
    fun renderPlan_honorsCursorBeyondLastNote() {
        val note = ScoreNote(
            midiPitch = 60,
            duration = NoteDuration.QUARTER,
            startBeat = 0f,
        )
        val track = ScoreTrack(
            id = 1,
            name = "Trailing space",
            events = listOf(note),
            cursorBeat = 8f,
        )
        val snapshot = ScoreProjectSnapshot(
            events = track.events,
            tracks = listOf(track),
        )

        val plan = WavAudioExporter.buildRenderPlan(
            snapshot = snapshot,
            sampleRate = 44_100,
            tailSeconds = 0f,
        )

        assertEquals(176_400, plan.scoreFrames)
    }

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Long =
        (bytes[offset].toLong() and 0xff) or
            ((bytes[offset + 1].toLong() and 0xff) shl 8) or
            ((bytes[offset + 2].toLong() and 0xff) shl 16) or
            ((bytes[offset + 3].toLong() and 0xff) shl 24)
}
