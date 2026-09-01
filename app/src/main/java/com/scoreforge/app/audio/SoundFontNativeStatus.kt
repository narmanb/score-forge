package com.scoreforge.app.audio

object SoundFontNativeStatus {
    fun unavailableMessage(): String {
        if (!NativeFluidSynth.isAvailable) {
            return NativeFluidSynth.loadErrorMessage
                ?.let { "Native audio load failed • $it" }
                ?: "Native audio load failed"
        }
        return "Native libraries loaded • FluidSynth synth creation failed"
    }
}
