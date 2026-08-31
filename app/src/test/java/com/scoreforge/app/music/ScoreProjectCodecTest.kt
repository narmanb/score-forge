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
    }

    @Test
    fun incompatibleHeaderIsRejected() {
        assertNull(ScoreProjectCodec.decode("SCOREFORGE\t99\nBPM\t120"))
        assertNull(ScoreProjectCodec.decode("NOT_SCORE_FORGE\t1"))
    }
}
