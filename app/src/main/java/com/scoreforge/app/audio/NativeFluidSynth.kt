package com.scoreforge.app.audio

internal object NativeFluidSynth {
    private var nativeLoadFailure: Throwable? = null

    val isAvailable: Boolean

    val loadErrorMessage: String?
        get() = nativeLoadFailure?.let { error ->
            val detail = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
            "${error::class.java.simpleName}: $detail"
        }

    init {
        isAvailable = try {
            // Load the native dependency chain explicitly. Some Android linker/device combinations
            // are less forgiving about resolving transitive JNI dependencies from an APK.
            System.loadLibrary("c++_shared")
            System.loadLibrary("fluidsynth")
            System.loadLibrary("scoreforge_native")
            true
        } catch (error: Throwable) {
            nativeLoadFailure = error
            false
        }
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
