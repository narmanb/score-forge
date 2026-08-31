package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreTiesTest {
    @Test
    fun tieRequiresContiguousSamePitchTarget() {
        val events: List<ScoreEvent> = listOf(
            ScoreNote(60, NoteDuration.QUARTER, startBeat = 0f),
            ScoreNote(60, NoteDuration.QUARTER, startBeat = 1f),
            ScoreNote(62, NoteDuration.QUARTER, startBeat = 1f),
        )

        assertEquals(1, ScoreTies.targetIndex(events, 0))
        assertTrue(ScoreTies.canToggle(events, 0))
        assertNull(ScoreTies.targetIndex(events, 1))
        assertFalse(ScoreTies.canToggle(events, 1))
    }

    @Test
    fun dottedDurationDeterminesTieBoundary() {
        val events: List<ScoreEvent> = listOf(
            ScoreNote(64, NoteDuration.QUARTER, startBeat = 0f, dotted = true),
            ScoreNote(64, NoteDuration.EIGHTH, startBeat = 1.5f),
        )

        assertEquals(1, ScoreTies.targetIndex(events, 0))
    }

    @Test
    fun toggleAddsAndRemovesValidTie() {
        val events: List<ScoreEvent> = listOf(
            ScoreNote(67, NoteDuration.HALF, startBeat = 0f),
            ScoreNote(67, NoteDuration.QUARTER, startBeat = 2f),
        )

        val tied = requireNotNull(ScoreTies.toggle(events, 0))
        assertTrue((tied[0] as ScoreNote).tieToNext)
        assertTrue(ScoreTies.hasValidTie(tied, 0))
        assertEquals(0, ScoreTies.incomingTieSourceIndex(tied, 1))

        val untied = requireNotNull(ScoreTies.toggle(tied, 0))
        assertFalse((untied[0] as ScoreNote).tieToNext)
        assertFalse(ScoreTies.hasValidTie(untied, 0))
    }

    @Test
    fun invalidStoredTieDoesNotCountAsActiveTie() {
        val events: List<ScoreEvent> = listOf(
            ScoreNote(60, NoteDuration.QUARTER, startBeat = 0f, tieToNext = true),
            ScoreNote(62, NoteDuration.QUARTER, startBeat = 1f),
        )

        assertFalse(ScoreTies.hasValidTie(events, 0))
        assertNull(ScoreTies.incomingTieSourceIndex(events, 1))
    }
}
