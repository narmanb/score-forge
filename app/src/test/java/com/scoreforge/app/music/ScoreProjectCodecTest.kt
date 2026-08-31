package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
            N\t60\tQUARTER\t0.0\t96
            N\t999\tQUARTER\t1.0\t96
            R\tNOT_A_DURATION\t1.0
            FUTURE_EVENT\tignored
            """.trimIndent().replace("\\t", "\t")
        )

        requireNotNull(decoded)
        assertEquals(90, decoded.bpm)
        assertEquals(NoteDuration.EIGHTH, decoded.selectedDuration)
        assertEquals(1, decoded.events.size)
        assertEquals(2f, decoded.cursorBeat, 0.0001f)
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
