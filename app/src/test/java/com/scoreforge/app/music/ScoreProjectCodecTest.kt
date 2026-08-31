package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreProjectCodecTest {
    @Test
    fun roundTripPreservesScoreAndEditorState() {
        val original = ScoreProjectSnapshot(
            events = listOf(
                ScoreNote(60, NoteDuration.QUARTER, startBeat = 0f, velocity = 88),
                ScoreRest(NoteDuration.EIGHTH, startBeat = 1f),
                ScoreNote(64, NoteDuration.HALF, startBeat = 1.5f, velocity = 101),
            ),
            bpm = 146,
            cursorBeat = 3.5f,
            selectedDuration = NoteDuration.SIXTEENTH,
            pianoOctaveShift = -2,
            staffSharpInput = true,
        )

        assertEquals(original, ScoreProjectCodec.decode(ScoreProjectCodec.encode(original)))
    }

    @Test
    fun versionTwoRoundTripPreservesMultipleTracksAndPresets() {
        val tracks = listOf(
            ScoreTrack(
                id = 1,
                name = "Piano",
                events = listOf(ScoreNote(60, NoteDuration.QUARTER, 0f)),
                cursorBeat = 1f,
                presetBank = 0,
                presetProgram = 0,
            ),
            ScoreTrack(
                id = 2,
                name = "Lead Synth",
                events = listOf(
                    ScoreRest(NoteDuration.HALF, 0f),
                    ScoreNote(76, NoteDuration.HALF, 2f, velocity = 110),
                ),
                cursorBeat = 4f,
                presetBank = 2,
                presetProgram = 41,
                muted = true,
            ),
        )
        val original = ScoreProjectSnapshot(
            events = tracks[1].events,
            cursorBeat = tracks[1].cursorBeat,
            tracks = tracks,
            activeTrackIndex = 1,
            bpm = 132,
        )

        val decoded = requireNotNull(ScoreProjectCodec.decode(ScoreProjectCodec.encode(original)))
        assertEquals(2, decoded.tracks.size)
        assertEquals(1, decoded.activeTrackIndex)
        assertEquals("Lead Synth", decoded.tracks[1].name)
        assertEquals(2, decoded.tracks[1].presetBank)
        assertEquals(41, decoded.tracks[1].presetProgram)
        assertTrue(decoded.tracks[1].muted)
        assertEquals(original, decoded)
    }

    @Test
    fun versionOneDraftMigratesIntoTrackOne() {
        val decoded = ScoreProjectCodec.decode(
            """
            SCOREFORGE\t1
            BPM\t118
            CURSOR\t3.0
            N\t60\tQUARTER\t0.0\t96
            R\tHALF\t1.0
            """.trimIndent().replace("\\t", "\t")
        )

        requireNotNull(decoded)
        assertEquals(1, decoded.tracks.size)
        assertEquals("Track 1", decoded.tracks.single().name)
        assertEquals(decoded.events, decoded.tracks.single().events)
        assertEquals(3f, decoded.tracks.single().cursorBeat, 0.0001f)
        assertEquals(118, decoded.bpm)
    }

    @Test
    fun unknownOrMalformedEventsAreSkippedWithoutDestroyingDraft() {
        val decoded = ScoreProjectCodec.decode(
            """
            SCOREFORGE\t1
            BPM\t90
            CURSOR\t2.0
            DURATION\tEIGHTH
            PIANO_OCTAVE\t99
            STAFF_SHARP\t1
            N\t60\tQUARTER\t0.0\t96
            N\t999\tQUARTER\t1.0\t96
            R\tNOT_A_DURATION\t1.0
            FUTURE_EVENT\tignored
            """.trimIndent().replace("\\t", "\t")
        )

        requireNotNull(decoded)
        assertEquals(90, decoded.bpm)
        assertEquals(NoteDuration.EIGHTH, decoded.selectedDuration)
        assertEquals(3, decoded.pianoOctaveShift)
        assertTrue(decoded.staffSharpInput)
        assertEquals(1, decoded.events.size)
        assertEquals(2f, decoded.cursorBeat, 0.0001f)
    }

    @Test
    fun olderVersionOneDraftWithoutNewEditorFieldsUsesSafeDefaults() {
        val decoded = ScoreProjectCodec.decode(
            """
            SCOREFORGE\t1
            BPM\t120
            N\t60\tQUARTER\t0.0\t96
            """.trimIndent().replace("\\t", "\t")
        )

        requireNotNull(decoded)
        assertEquals(0, decoded.pianoOctaveShift)
        assertFalse(decoded.staffSharpInput)
        assertEquals(1, decoded.tracks.size)
    }

    @Test
    fun cursorNeverRestoresBeforeEndOfWrittenMusic() {
        val decoded = ScoreProjectCodec.decode(
            """
            SCOREFORGE\t1
            CURSOR\t0.0
            N\t67\tHALF\t4.0\t96
            """.trimIndent().replace("\\t", "\t")
        )

        requireNotNull(decoded)
        assertEquals(6f, decoded.cursorBeat, 0.0001f)
        assertEquals(6f, decoded.tracks.single().cursorBeat, 0.0001f)
    }

    @Test
    fun incompatibleHeaderIsRejected() {
        assertNull(ScoreProjectCodec.decode("SCOREFORGE\t99\nBPM\t120"))
        assertNull(ScoreProjectCodec.decode("NOT_SCORE_FORGE\t1"))
    }
}
