package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreKeySignatureAlterationTest {
    @Test
    fun threeSharpsAlterFcg() {
        val key = ScoreKeySignature(fifths = 3)
        assertEquals(1, ScoreKeySignatures.alterationForLetter(key, 3)) // F
        assertEquals(1, ScoreKeySignatures.alterationForLetter(key, 0)) // C
        assertEquals(1, ScoreKeySignatures.alterationForLetter(key, 4)) // G
        assertEquals(0, ScoreKeySignatures.alterationForLetter(key, 1)) // D
    }

    @Test
    fun threeFlatsAlterBea() {
        val key = ScoreKeySignature(fifths = -3)
        assertEquals(-1, ScoreKeySignatures.alterationForLetter(key, 6)) // B
        assertEquals(-1, ScoreKeySignatures.alterationForLetter(key, 2)) // E
        assertEquals(-1, ScoreKeySignatures.alterationForLetter(key, 5)) // A
        assertEquals(0, ScoreKeySignatures.alterationForLetter(key, 1)) // D
    }
}
