package com.scoreforge.app.audio

internal object NativeFluidSynth {
    init {
        System.loadLibrary("scoreforge_native")
    }

    external fun create(sampleRate: Int): Long
    external fun destroy(handle: Long)
    external fun loadSoundFont(handle: Long, path: String): Int
    external fun listPresets(handle: Long, soundFontId: Int): Array<String>
    external fun selectPreset(
        handle: Long,
        soundFontId: Int,
        channel: Int,
        bank: Int,
        program: Int,
    ): Int
    external fun programChange(handle: Long, channel: Int, program: Int): Int
    external fun controlChange(handle: Long, channel: Int, controller: Int, value: Int): Int
    external fun noteOn(handle: Long, channel: Int, key: Int, velocity: Int): Int
    external fun noteOff(handle: Long, channel: Int, key: Int): Int
    external fun allNotesOff(handle: Long, channel: Int)
    external fun renderStereo(handle: Long, frames: Int): ShortArray
}
