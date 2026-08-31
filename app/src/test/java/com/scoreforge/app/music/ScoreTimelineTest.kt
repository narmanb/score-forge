package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreTimelineTest {
    @Test
    fun endBeatUsesLatestNoteEnd() {
        val notes = listOf(
            ScoreNote(60, NoteDuration.QUARTER, startBeat = 0f),
            ScoreNote(64, NoteDuration.HALF, startBeat = 1f),
            ScoreNote(67, NoteDuration.EIGHTH, startBeat = 3f),
        )

        assertEquals(3.5f, ScoreTimeline.endBeat(notes), 0.0001f)
    }

    @Test
    fun measureCountRoundsUpToWholeMeasures() {
        val notes = listOf(
            ScoreNote(60, NoteDuration.QUARTER, startBeat = 4f),
        )

        assertEquals(2, ScoreTimeline.measureCount(notes))
    }

    @Test
    fun emptyScoreStillShowsOneMeasure() {
        assertEquals(1, ScoreTimeline.measureCount(emptyList()))
        assertEquals(16f, ScoreTimeline.visibleBeats(emptyList()), 0.0001f)
    }

    @Test
    fun visibleTimelineExpandsByWholeMeasures() {
        val notes = listOf(
            ScoreNote(60, NoteDuration.HALF, startBeat = 16f),
        )

        assertEquals(20f, ScoreTimeline.visibleBeats(notes), 0.0001f)
    }
}
