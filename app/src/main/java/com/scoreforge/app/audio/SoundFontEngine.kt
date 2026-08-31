package com.scoreforge.app.audio

import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeline
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

    /**
     * Offline-renders the current SoundFont/program for a score. Keeping the entire scheduling
     * pass under one lock prevents program changes or preview notes from corrupting the render.
     * Long-term playback can become streamed; this is intentionally simple and deterministic
     * for the first SoundFont milestone.
     */
    fun renderScore(
        notes: List<ScoreNote>,
        bpm: Int,
        tailSeconds: Float = 0.45f,
    ): ShortArray = synchronized(lock) {
        if (handle == 0L || soundFontId < 0 || notes.isEmpty()) return@synchronized ShortArray(0)

        data class MidiEvent(
            val frame: Int,
            val noteOn: Boolean,
            val key: Int,
            val velocity: Int,
        )

        val safeBpm = bpm.coerceIn(30, 300)
        val secondsPerBeat = 60f / safeBpm
        val totalFrames = (
            (ScoreTimeline.endBeat(notes) * secondsPerBeat + tailSeconds.coerceAtLeast(0f)) * sampleRate
            ).toInt().coerceAtLeast(1)

        val events = buildList {
            notes.forEach { note ->
                val onFrame = (note.startBeat * secondsPerBeat * sampleRate).toInt().coerceAtLeast(0)
                val offFrame = (
                    (note.startBeat + note.duration.beats) * secondsPerBeat * sampleRate
                    ).toInt().coerceAtLeast(onFrame + 1)
                add(MidiEvent(onFrame, true, note.midiPitch, note.velocity))
                add(MidiEvent(offFrame, false, note.midiPitch, 0))
            }
        }.sortedWith(
            compareBy<MidiEvent> { it.frame }
                .thenBy { if (it.noteOn) 1 else 0 }
                .thenBy { it.key }
        )

        NativeFluidSynth.allNotesOff(handle, 0)
        val output = ShortArray(totalFrames * 2)
        var frameCursor = 0
        var eventIndex = 0

        fun renderIntoOutput(frameCount: Int) {
            if (frameCount <= 0) return
            val rendered = NativeFluidSynth.renderStereo(handle, frameCount)
            val targetOffset = frameCursor * 2
            val copyCount = minOf(rendered.size, output.size - targetOffset)
            if (copyCount > 0) rendered.copyInto(output, targetOffset, 0, copyCount)
            frameCursor += frameCount
        }

        while (eventIndex < events.size && frameCursor < totalFrames) {
            val eventFrame = events[eventIndex].frame.coerceIn(frameCursor, totalFrames)
            renderIntoOutput(eventFrame - frameCursor)

            while (eventIndex < events.size && events[eventIndex].frame <= frameCursor) {
                val event = events[eventIndex]
                if (event.noteOn) {
                    NativeFluidSynth.noteOn(
                        handle,
                        0,
                        event.key.coerceIn(0, 127),
                        event.velocity.coerceIn(1, 127),
                    )
                } else {
                    NativeFluidSynth.noteOff(handle, 0, event.key.coerceIn(0, 127))
                }
                eventIndex++
            }
        }

        renderIntoOutput(totalFrames - frameCursor)
        NativeFluidSynth.allNotesOff(handle, 0)
        output
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
