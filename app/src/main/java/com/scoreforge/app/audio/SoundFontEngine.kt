package com.scoreforge.app.audio

import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeline
import java.io.Closeable

data class SoundFontPreset(
    val bank: Int,
    val program: Int,
    val name: String,
) {
    val displayName: String
        get() = if (bank == 0) name else "$name (bank $bank)"
}

/**
 * Thin managed wrapper around the native FluidSynth instance.
 *
 * A caller provides a local filesystem path to an .sf2/.sf3 file. Score Forge then enumerates
 * the presets actually present in that file rather than assuming a General MIDI-only layout.
 */
class SoundFontEngine private constructor(
    val sampleRate: Int,
    private var handle: Long,
) : Closeable {
    private val lock = Any()
    private var soundFontId: Int = -1
    private var loadedPresets: List<SoundFontPreset> = emptyList()
    private var activePreset: SoundFontPreset? = null

    val hasSoundFont: Boolean
        get() = synchronized(lock) { soundFontId >= 0 }

    val presets: List<SoundFontPreset>
        get() = synchronized(lock) { loadedPresets.toList() }

    val selectedPreset: SoundFontPreset?
        get() = synchronized(lock) { activePreset }

    fun loadSoundFont(path: String): Boolean = synchronized(lock) {
        if (handle == 0L) return@synchronized false

        soundFontId = NativeFluidSynth.loadSoundFont(handle, path)
        if (soundFontId < 0) {
            loadedPresets = emptyList()
            activePreset = null
            return@synchronized false
        }

        loadedPresets = NativeFluidSynth.listPresets(handle, soundFontId)
            .mapNotNull(::parsePresetRow)
            .sortedWith(compareBy<SoundFontPreset> { it.bank }.thenBy { it.program }.thenBy { it.name })

        val first = loadedPresets.firstOrNull()
        if (first != null) {
            val selected = selectPresetLocked(first, channel = 0)
            if (!selected) {
                activePreset = null
                return@synchronized false
            }
        } else {
            // Some minimal SoundFonts do not expose iterable preset metadata. Keep bank/program 0
            // as a compatibility fallback rather than rejecting a file FluidSynth loaded correctly.
            NativeFluidSynth.programChange(handle, 0, 0)
            activePreset = SoundFontPreset(0, 0, "Program 1")
        }
        true
    }

    fun selectPreset(preset: SoundFontPreset, channel: Int = 0): Boolean = synchronized(lock) {
        selectPresetLocked(preset, channel)
    }

    fun selectPresetAt(index: Int, channel: Int = 0): Boolean = synchronized(lock) {
        val preset = loadedPresets.getOrNull(index) ?: return@synchronized false
        selectPresetLocked(preset, channel)
    }

    fun selectedPresetIndex(): Int = synchronized(lock) {
        val selected = activePreset ?: return@synchronized -1
        loadedPresets.indexOf(selected)
    }

    private fun selectPresetLocked(preset: SoundFontPreset, channel: Int): Boolean {
        if (handle == 0L || soundFontId < 0) return false
        val result = NativeFluidSynth.selectPreset(
            handle = handle,
            soundFontId = soundFontId,
            channel = channel.coerceIn(0, 15),
            bank = preset.bank.coerceAtLeast(0),
            program = preset.program.coerceIn(0, 127),
        )
        if (result == 0) activePreset = preset
        return result == 0
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
     * Offline-renders the current SoundFont/preset for a score. Long-term playback can become
     * streamed; this deterministic renderer is sufficient for the first real-instrument layer.
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
        activePreset?.let { selectPresetLocked(it, channel = 0) }

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
            loadedPresets = emptyList()
            activePreset = null
        }
    }

    private fun parsePresetRow(row: String): SoundFontPreset? {
        val parts = row.split('\t', limit = 3)
        if (parts.size != 3) return null
        val bank = parts[0].toIntOrNull() ?: return null
        val program = parts[1].toIntOrNull() ?: return null
        return SoundFontPreset(
            bank = bank,
            program = program,
            name = parts[2].ifBlank { "Bank $bank Program ${program + 1}" },
        )
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
