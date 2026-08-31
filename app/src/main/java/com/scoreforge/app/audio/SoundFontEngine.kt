package com.scoreforge.app.audio

import java.io.Closeable

/**
 * Thin managed wrapper around the native FluidSynth instance.
 *
 * This class deliberately does not own Android UI or file-picker behavior. A caller provides
 * a local filesystem path to an .sf2/.sf3 file, then uses MIDI-style note/program operations
 * and pulls stereo PCM frames for playback through Android AudioTrack.
 */
class SoundFontEngine private constructor(
    val sampleRate: Int,
    private var handle: Long,
) : Closeable {
    private val lock = Any()
    private var soundFontId: Int = -1

    val hasSoundFont: Boolean
        get() = synchronized(lock) { soundFontId >= 0 }

    fun loadSoundFont(path: String): Boolean = synchronized(lock) {
        if (handle == 0L) return@synchronized false
        soundFontId = NativeFluidSynth.loadSoundFont(handle, path)
        if (soundFontId >= 0) {
            NativeFluidSynth.programChange(handle, 0, 0)
            true
        } else {
            false
        }
    }

    fun selectProgram(program: Int, channel: Int = 0): Boolean = synchronized(lock) {
        if (handle == 0L || soundFontId < 0) return@synchronized false
        NativeFluidSynth.programChange(
            handle,
            channel.coerceIn(0, 15),
            program.coerceIn(0, 127),
        ) == 0
    }

    fun noteOn(
        midiPitch: Int,
        velocity: Int = 96,
        channel: Int = 0,
    ): Boolean = synchronized(lock) {
        if (handle == 0L || soundFontId < 0) return@synchronized false
        NativeFluidSynth.noteOn(
            handle,
            channel.coerceIn(0, 15),
            midiPitch.coerceIn(0, 127),
            velocity.coerceIn(1, 127),
        ) == 0
    }

    fun noteOff(midiPitch: Int, channel: Int = 0): Boolean = synchronized(lock) {
        if (handle == 0L || soundFontId < 0) return@synchronized false
        NativeFluidSynth.noteOff(
            handle,
            channel.coerceIn(0, 15),
            midiPitch.coerceIn(0, 127),
        ) == 0
    }

    fun allNotesOff(channel: Int = 0) = synchronized(lock) {
        if (handle != 0L) NativeFluidSynth.allNotesOff(handle, channel.coerceIn(0, 15))
    }

    /** Returns interleaved stereo 16-bit PCM: L, R, L, R... */
    fun renderStereo(frames: Int): ShortArray = synchronized(lock) {
        if (handle == 0L || soundFontId < 0 || frames <= 0) {
            ShortArray(0)
        } else {
            NativeFluidSynth.renderStereo(handle, frames)
        }
    }

    override fun close() = synchronized(lock) {
        if (handle != 0L) {
            NativeFluidSynth.allNotesOff(handle, 0)
            NativeFluidSynth.destroy(handle)
            handle = 0L
            soundFontId = -1
        }
    }

    companion object {
        fun createOrNull(sampleRate: Int = 44_100): SoundFontEngine? = try {
            val handle = NativeFluidSynth.create(sampleRate)
            if (handle == 0L) null else SoundFontEngine(sampleRate, handle)
        } catch (_: UnsatisfiedLinkError) {
            null
        } catch (_: ExceptionInInitializerError) {
            null
        }
    }
}
