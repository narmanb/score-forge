package com.scoreforge.app.audio

import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeline
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
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
            NativeFluidSynth.programChange(handle, 0, 0)
            activePreset = SoundFontPreset(0, 0, "Program 1")
        }
        setChannelMixerLocked(channel = 0, volume = ScoreTrack.DEFAULT_VOLUME, pan = ScoreTrack.CENTER_PAN)
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
        val result = selectPresetOnChannelLocked(preset, channel)
        if (result) activePreset = preset
        return result
    }

    private fun selectPresetOnChannelLocked(preset: SoundFontPreset, channel: Int): Boolean {
        if (handle == 0L || soundFontId < 0) return false
        return NativeFluidSynth.selectPreset(
            handle = handle,
            soundFontId = soundFontId,
            channel = channel.coerceIn(0, 15),
            bank = preset.bank.coerceAtLeast(0),
            program = preset.program.coerceIn(0, 127),
        ) == 0
    }

    fun setChannelMixer(
        volume: Int,
        pan: Int,
        channel: Int = 0,
    ): Boolean = synchronized(lock) {
        setChannelMixerLocked(channel, volume, pan)
    }

    private fun setChannelMixerLocked(channel: Int, volume: Int, pan: Int): Boolean {
        if (handle == 0L || soundFontId < 0) return false
        val safeChannel = channel.coerceIn(0, 15)
        val volumeResult = NativeFluidSynth.controlChange(
            handle,
            safeChannel,
            MIDI_CC_VOLUME,
            volume.coerceIn(ScoreTrack.MIN_VOLUME, ScoreTrack.MAX_VOLUME),
        )
        val panResult = NativeFluidSynth.controlChange(
            handle,
            safeChannel,
            MIDI_CC_PAN,
            (pan.coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN) + 64).coerceIn(0, 127),
        )
        return volumeResult == 0 && panResult == 0
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
     * Offline-renders the current SoundFont/preset for a score. [throughBeat] can extend the
     * rendered transport beyond the final sounding note, which preserves explicit trailing rests.
     */
    fun renderScore(
        notes: List<ScoreNote>,
        bpm: Int,
        tailSeconds: Float = 0.45f,
        throughBeat: Float = ScoreTimeline.endBeat(notes),
    ): ShortArray = synchronized(lock) {
        if (handle == 0L || soundFontId < 0 || notes.isEmpty()) return@synchronized ShortArray(0)
        renderMidiEventsLocked(
            channelNotes = listOf(
                ChannelNotes(
                    channel = 0,
                    notes = notes,
                    preset = activePreset,
                    volume = ScoreTrack.DEFAULT_VOLUME,
                    pan = ScoreTrack.CENTER_PAN,
                )
            ),
            bpm = bpm,
            tailSeconds = tailSeconds,
            throughBeat = throughBeat,
        )
    }

    /**
     * Renders up to 16 audible tracks at once, one MIDI channel per track. Each track may request
     * its own bank/program and carries independent volume/pan mixer controls.
     */
    fun renderTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        tailSeconds: Float = 0.45f,
        throughBeat: Float = ScoreTracks.endBeat(tracks),
    ): ShortArray = synchronized(lock) {
        if (handle == 0L || soundFontId < 0) return@synchronized ShortArray(0)

        val originalPreset = activePreset
        val channelNotes = ScoreTracks.audibleTracks(tracks)
            .take(ScoreTracks.MAX_TRACKS)
            .mapIndexedNotNull { channel, track ->
                if (track.notes.isEmpty()) return@mapIndexedNotNull null
                val requestedPreset = if (track.presetBank != null && track.presetProgram != null) {
                    loadedPresets.firstOrNull {
                        it.bank == track.presetBank && it.program == track.presetProgram
                    }
                } else {
                    null
                }
                ChannelNotes(
                    channel = channel,
                    notes = track.notes,
                    preset = requestedPreset ?: originalPreset ?: loadedPresets.firstOrNull(),
                    volume = track.volume,
                    pan = track.pan,
                )
            }

        if (channelNotes.isEmpty()) return@synchronized ShortArray(0)
        val pcm = renderMidiEventsLocked(
            channelNotes = channelNotes,
            bpm = bpm,
            tailSeconds = tailSeconds,
            throughBeat = maxOf(throughBeat, ScoreTracks.endBeat(tracks)),
        )

        originalPreset?.let {
            selectPresetOnChannelLocked(it, channel = 0)
            activePreset = it
        }
        setChannelMixerLocked(channel = 0, volume = ScoreTrack.DEFAULT_VOLUME, pan = ScoreTrack.CENTER_PAN)
        pcm
    }

    private data class ChannelNotes(
        val channel: Int,
        val notes: List<ScoreNote>,
        val preset: SoundFontPreset?,
        val volume: Int,
        val pan: Int,
    )

    private data class MidiEvent(
        val frame: Int,
        val noteOn: Boolean,
        val key: Int,
        val velocity: Int,
        val channel: Int,
    )

    private fun renderMidiEventsLocked(
        channelNotes: List<ChannelNotes>,
        bpm: Int,
        tailSeconds: Float,
        throughBeat: Float,
    ): ShortArray {
        val safeBpm = bpm.coerceIn(30, 300)
        val secondsPerBeat = 60f / safeBpm
        val notesEndBeat = channelNotes.maxOfOrNull { ScoreTimeline.endBeat(it.notes) } ?: 0f
        val scoreEndBeat = maxOf(notesEndBeat, throughBeat.coerceAtLeast(0f))
        val totalFrames = (
            (scoreEndBeat * secondsPerBeat + tailSeconds.coerceAtLeast(0f)) * sampleRate
            ).toInt().coerceAtLeast(1)

        val events = buildList {
            channelNotes.forEach { channelTrack ->
                val channel = channelTrack.channel.coerceIn(0, 15)
                channelTrack.notes.forEach { note ->
                    val onFrame = (note.startBeat * secondsPerBeat * sampleRate).toInt().coerceAtLeast(0)
                    val offFrame = (
                        (note.startBeat + note.effectiveBeats) * secondsPerBeat * sampleRate
                        ).toInt().coerceAtLeast(onFrame + 1)
                    add(MidiEvent(onFrame, true, note.midiPitch, note.velocity, channel))
                    add(MidiEvent(offFrame, false, note.midiPitch, 0, channel))
                }
            }
        }.sortedWith(
            compareBy<MidiEvent> { it.frame }
                .thenBy { if (it.noteOn) 1 else 0 }
                .thenBy { it.channel }
                .thenBy { it.key }
        )

        repeat(16) { NativeFluidSynth.allNotesOff(handle, it) }
        channelNotes.forEach { track ->
            track.preset?.let { selectPresetOnChannelLocked(it, track.channel) }
            setChannelMixerLocked(track.channel, track.volume, track.pan)
        }

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
                        event.channel,
                        event.key.coerceIn(0, 127),
                        event.velocity.coerceIn(1, 127),
                    )
                } else {
                    NativeFluidSynth.noteOff(
                        handle,
                        event.channel,
                        event.key.coerceIn(0, 127),
                    )
                }
                eventIndex++
            }
        }

        renderIntoOutput(totalFrames - frameCursor)
        repeat(16) { NativeFluidSynth.allNotesOff(handle, it) }
        return output
    }

    override fun close() = synchronized(lock) {
        if (handle != 0L) {
            repeat(16) { NativeFluidSynth.allNotesOff(handle, it) }
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
        private const val MIDI_CC_VOLUME = 7
        private const val MIDI_CC_PAN = 10

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
