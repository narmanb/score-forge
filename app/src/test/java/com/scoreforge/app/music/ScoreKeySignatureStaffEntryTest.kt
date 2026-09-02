package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreKeySignatureStaffEntryTest {
    @Test
    fun gMajorRaisesTappedFToFsharp() {
        assertEquals(
            66,
            ScoreKeySignatures.applyToNaturalPitch(65, ScoreKeySignature(fifths = 1)),
        )
    }

    @Test
    fun fMajorLowersTappedBToBflat() {
        assertEquals(
            70,
            ScoreKeySignatures.applyToNaturalPitch(71, ScoreKeySignature(fifths = -1)),
        )
    }

    @Test
    fun extremeSignaturesCanCrossOctaveEnharmonically() {
        assertEquals(
            59,
            ScoreKeySignatures.applyToNaturalPitch(60, ScoreKeySignature(fifths = -7)),
        )
        assertEquals(
            60,
            ScoreKeySignatures.applyToNaturalPitch(59, ScoreKeySignature(fifths = 7)),
        )
    }
}
