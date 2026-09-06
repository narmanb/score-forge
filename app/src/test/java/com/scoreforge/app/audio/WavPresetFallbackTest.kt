package com.scoreforge.app.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class WavPresetFallbackTest {
    private val bankZeroBass = SoundFontPreset(bank = 0, program = 39, name = "Synth Bass 2")
    private val exactVariation = SoundFontPreset(bank = 6, program = 39, name = "Mello Synth Bass 2")
    private val activeFallback = SoundFontPreset(bank = 0, program = 0, name = "Piano")

    @Test
    fun exactPresetWinsWhenAvailable() {
        val resolved = WavAudioExporter.resolvePresetFallback(
            presets = listOf(activeFallback, bankZeroBass, exactVariation),
            requestedBank = 6,
            requestedProgram = 39,
            fallbackPreset = activeFallback,
        )

        assertSame(exactVariation, resolved)
    }

    @Test
    fun missingVariationFallsBackToSameProgramInBankZero() {
        val resolved = WavAudioExporter.resolvePresetFallback(
            presets = listOf(activeFallback, bankZeroBass),
            requestedBank = 6,
            requestedProgram = 39,
            fallbackPreset = activeFallback,
        )

        assertEquals(0, resolved?.bank)
        assertEquals(39, resolved?.program)
        assertEquals("Synth Bass 2", resolved?.name)
    }

    @Test
    fun percussionDoesNotFallBackToMelodicBankZeroProgram() {
        val melodicProgram = SoundFontPreset(bank = 0, program = 25, name = "Steel Guitar")
        val resolved = WavAudioExporter.resolvePresetFallback(
            presets = listOf(activeFallback, melodicProgram),
            requestedBank = 128,
            requestedProgram = 25,
            fallbackPreset = activeFallback,
        )

        assertSame(activeFallback, resolved)
    }
}
