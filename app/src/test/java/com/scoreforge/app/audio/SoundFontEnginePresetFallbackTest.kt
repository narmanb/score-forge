package com.scoreforge.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class SoundFontEnginePresetFallbackTest {
    private val piano = SoundFontPreset(bank = 0, program = 0, name = "Piano")
    private val synthBass2 = SoundFontPreset(bank = 0, program = 39, name = "Synth Bass 2")

    @Test
    fun missingVariationUsesSameProgramFromBankZero() {
        val resolved = SoundFontEngine.resolvePresetFallback(
            presets = listOf(piano, synthBass2),
            requestedBank = 6,
            requestedProgram = 39,
            fallbackPreset = piano,
        )

        assertEquals(0, resolved?.bank)
        assertEquals(39, resolved?.program)
    }

    @Test
    fun missingDrumKitDoesNotUseMelodicProgramFallback() {
        val melodicProgram = SoundFontPreset(bank = 0, program = 25, name = "Steel Guitar")
        val resolved = SoundFontEngine.resolvePresetFallback(
            presets = listOf(piano, melodicProgram),
            requestedBank = 128,
            requestedProgram = 25,
            fallbackPreset = piano,
        )

        assertSame(piano, resolved)
    }
}
