package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScorePitchSpellingTest {
    @Test
    fun inKeySharpUsesSignatureWithoutPrintedAccidental() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 66, // F#4
            keySignature = ScoreKeySignature(fifths = 1), // G major
        )

        assertEquals(4 * 7 + 3, spelling.diatonicPosition)
        assertEquals(ScoreAccidental.NONE, spelling.accidental)
    }

    @Test
    fun naturalCancelsSharpSignatureInsteadOfBecomingExoticEnharmonic() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 65, // F4
            keySignature = ScoreKeySignature(fifths = 1), // F is normally sharp
        )

        assertEquals(4 * 7 + 3, spelling.diatonicPosition)
        assertEquals(ScoreAccidental.NATURAL, spelling.accidental)
    }

    @Test
    fun flatKeySpellsBlackPitchAsFlatAndOmitsInKeyAccidental() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 70, // Bb4 / A#4
            keySignature = ScoreKeySignature(fifths = -1), // F major: Bb
        )

        assertEquals(4 * 7 + 6, spelling.diatonicPosition)
        assertEquals(ScoreAccidental.NONE, spelling.accidental)
    }

    @Test
    fun naturalCancelsFlatSignature() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 71, // B4
            keySignature = ScoreKeySignature(fifths = -1),
        )

        assertEquals(4 * 7 + 6, spelling.diatonicPosition)
        assertEquals(ScoreAccidental.NATURAL, spelling.accidental)
    }

    @Test
    fun neutralKeyDefaultsChromaticPitchToSharpSpelling() {
        val spelling = ScorePitchSpelling.spell(
            midiPitch = 61, // C#4 / Db4
            keySignature = ScoreKeySignature(),
        )

        assertEquals(4 * 7, spelling.diatonicPosition)
        assertEquals(ScoreAccidental.SHARP, spelling.accidental)
    }
}
