package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreKeySignaturesTest {
    @Test
    fun lateFirstChangeGetsDefaultCmajorBeforeIt() {
        val normalized = ScoreKeySignatures.normalize(
            listOf(ScoreKeySignature(startBeat = 8f, fifths = -3, minor = false))
        )

        assertEquals(
            listOf(
                ScoreKeySignature(),
                ScoreKeySignature(startBeat = 8f, fifths = -3, minor = false),
            ),
            normalized,
        )
        assertEquals("C major", ScoreKeySignatures.atBeat(normalized, 4f).displayName)
        assertEquals("E♭ major", ScoreKeySignatures.atBeat(normalized, 8f).displayName)
    }

    @Test
    fun canAddReplaceAndRemoveLaterKeyChanges() {
        var signatures = listOf(ScoreKeySignatures.DEFAULT)
        signatures = ScoreKeySignatures.withChange(signatures, 4f, 1, false)
        signatures = ScoreKeySignatures.withChange(signatures, 8f, -1, true)
        signatures = ScoreKeySignatures.withChange(signatures, 8f, -2, true)

        assertEquals("G major", ScoreKeySignatures.atBeat(signatures, 4f).displayName)
        assertEquals("G minor", ScoreKeySignatures.atBeat(signatures, 8f).displayName)

        signatures = ScoreKeySignatures.withoutChange(signatures, 8f)
        assertEquals("G major", ScoreKeySignatures.atBeat(signatures, 9f).displayName)

        val cannotRemoveInitial = ScoreKeySignatures.withoutChange(signatures, 0f)
        assertEquals(ScoreKeySignatures.DEFAULT, cannotRemoveInitial.first())
    }

    @Test
    fun circleOfFifthsNamesCoverMajorAndMinor() {
        assertEquals("B♭ major", ScoreKeySignature(fifths = -2).displayName)
        assertEquals("A major", ScoreKeySignature(fifths = 3).displayName)
        assertEquals("D minor", ScoreKeySignature(fifths = -1, minor = true).displayName)
        assertEquals("F♯ minor", ScoreKeySignature(fifths = 3, minor = true).displayName)
    }
}
