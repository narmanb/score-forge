package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSignatureEditingTest {
    @Test
    fun measureStartFollowsThreeFourBarlines() {
        val signatures = listOf(ScoreTimeSignature(0f, 3, 4))
        assertEquals(0f, ScoreTimeSignatures.measureStartAt(signatures, 2.99f), 0.0001f)
        assertEquals(3f, ScoreTimeSignatures.measureStartAt(signatures, 3f), 0.0001f)
        assertEquals(6f, ScoreTimeSignatures.measureStartAt(signatures, 8.5f), 0.0001f)
    }

    @Test
    fun editingAtMeasureStartAddsAndReplacesChange() {
        var signatures = listOf(ScoreTimeSignature(0f, 4, 4))
        signatures = ScoreTimeSignatures.withChange(signatures, 8f, 6, 8)
        assertEquals("6/8", ScoreTimeSignatures.atBeat(signatures, 8f).displayName)
        signatures = ScoreTimeSignatures.withChange(signatures, 8f, 5, 8)
        assertEquals(2, signatures.size)
        assertEquals("5/8", ScoreTimeSignatures.atBeat(signatures, 9f).displayName)
    }

    @Test
    fun removingChangeRestoresPreviousMeterButCannotRemoveInitialMeter() {
        val withChange = ScoreTimeSignatures.withChange(
            listOf(ScoreTimeSignature(0f, 3, 4)),
            6f,
            7,
            8,
        )
        val removed = ScoreTimeSignatures.withoutChange(withChange, 6f)
        assertEquals(1, removed.size)
        assertEquals("3/4", ScoreTimeSignatures.atBeat(removed, 20f).displayName)
        val attemptedInitialRemoval = ScoreTimeSignatures.withoutChange(removed, 0f)
        assertFalse(attemptedInitialRemoval.isEmpty())
        assertTrue(attemptedInitialRemoval.first().startBeat == 0f)
    }
}
