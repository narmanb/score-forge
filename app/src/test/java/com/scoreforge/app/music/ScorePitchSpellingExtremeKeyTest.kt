package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScorePitchSpellingExtremeKeyTest {
    @Test
    fun sevenFlatKeyCanSpellCbWithoutPrintedAccidental() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 59, // Cb4 / B3
            keySignature = ScoreKeySignature(fifths = -7),
        )

        assertEquals(4 * 7, spelling.diatonicPosition) // C4 staff position
        assertEquals(ScoreAccidental.NONE, spelling.accidental)
    }

    @Test
    fun sevenSharpKeyCanSpellBsharpWithoutPrintedAccidental() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 60, // B#3 / C4
            keySignature = ScoreKeySignature(fifths = 7),
        )

        assertEquals(3 * 7 + 6, spelling.diatonicPosition) // B3 staff position
        assertEquals(ScoreAccidental.NONE, spelling.accidental)
    }
}
