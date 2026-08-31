package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class PitchNamesTest {
    @Test
    fun conventionalStaffPositionsSharpenByOneSemitone() {
        assertEquals(61, PitchNames.sharpenIfAvailable(60)) // C4 -> C#4
        assertEquals(63, PitchNames.sharpenIfAvailable(62)) // D4 -> D#4
        assertEquals(66, PitchNames.sharpenIfAvailable(65)) // F4 -> F#4
        assertEquals(68, PitchNames.sharpenIfAvailable(67)) // G4 -> G#4
        assertEquals(70, PitchNames.sharpenIfAvailable(69)) // A4 -> A#4
    }

    @Test
    fun eAndBRemainNaturalInSimpleSharpEntryMode() {
        assertEquals(64, PitchNames.sharpenIfAvailable(64))
        assertEquals(71, PitchNames.sharpenIfAvailable(71))
    }

    @Test
    fun existingSharpsAreNotSharpenedAgain() {
        assertEquals(61, PitchNames.sharpenIfAvailable(61))
        assertEquals(70, PitchNames.sharpenIfAvailable(70))
    }
}
