package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class KeySignatureUiAlignmentTest {
    @Test
    fun manualKeyChangeUsesContainingMeasureStartAcrossMeterChanges() {
        val meters = listOf(
            ScoreTimeSignature(startBeat = 0f, numerator = 3, denominator = 4),
            ScoreTimeSignature(startBeat = 6f, numerator = 5, denominator = 8),
        )

        assertEquals(3f, ScoreTimeSignatures.measureStartAt(meters, 5.5f), 0.0001f)
        assertEquals(8.5f, ScoreTimeSignatures.measureStartAt(meters, 9f), 0.0001f)
    }
}
