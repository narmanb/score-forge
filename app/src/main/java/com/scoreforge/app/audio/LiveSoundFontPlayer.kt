package com.scoreforge.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import com.scoreforge.app.music.ScoreTrack
import java.io.Closeable
import kotlin.concurrent.thread

/**
 * Low-latency-ish live preview path for the touchscreen keyboard.
 *
 * This intentionally owns a second FluidSynth instance. Offline score rendering can therefore
 * happen on ScorePlaybackEngine's synth without stealing notes or changing presets underneath
 * a finger that is currently holding a piano key.
 */
class LiveSoundFontPlayer(
    private val sampleRate: Int = 44_100,
) : Closeable {
    private val stateLock = Any()

    @Volatile
    private var engine: SoundFontEngine? = null

    @Volatile
    private var audioTrack: AudioTrack? = null

    @Volatile
    private var renderThread: Thread? = null

    @Volatile
    private var running = false

    @Volatile
    private var loadGeneration = 0

    @Volatile
    private var desiredPreset: SoundFontPreset? = null

    @Volatile
    private var desiredVolume: Int = ScoreTrack.DEFAULT_VOLUME

    @Volatile
    private var desiredPan: Int = ScoreTrack.CENTER_PAN

    val isReady: Boolean
        get() = engine?.hasSoundFont == true && running

    /**
     * Loads/reloads the live synth off the UI thread. Only the newest request is installed.
     */
    fun loadSoundFont(path: String, preset: SoundFontPreset? = null) {
        val generation = ++loadGeneration
        desiredPreset = preset

        thread(name = "ScoreForgeLiveSoundFontLoad", isDaemon = true) {
            val candidate = SoundFontEngine.createOrNull(sampleRate) ?: return@thread
            if (!candidate.loadSoundFont(path)) {
                candidate.close()
                return@thread
            }

            val wanted = desiredPreset ?: preset
            if (wanted != null) {
                candidate.presets
                    .firstOrNull { it.bank == wanted.bank && it.program == wanted.program }
                    ?.let(candidate::selectPreset)
            }
            candidate.setChannelMixer(desiredVolume, desiredPan)

            synchronized(stateLock) {
                if (generation != loadGeneration) {
                    candidate.close()
                    return@synchronized
                }
                stopRendererLocked()
                engine?.close()
                engine = candidate
                startRendererLocked(candidate)
            }
        }
    }

    fun selectPreset(preset: SoundFontPreset) {
        desiredPreset = preset
        engine?.let { current ->
            current.presets
                .firstOrNull { it.bank == preset.bank && it.program == preset.program }
                ?.let(current::selectPreset)
        }
    }

    fun setMixer(volume: Int, pan: Int) {
        desiredVolume = volume.coerceIn(ScoreTrack.MIN_VOLUME, ScoreTrack.MAX_VOLUME)
        desiredPan = pan.coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN)
        engine?.setChannelMixer(desiredVolume, desiredPan)
    }

    fun noteOn(midiPitch: Int, velocity: Int = 96): Boolean {
        val current = engine ?: return false
        if (!isReady || renderThread?.isAlive != true) return false
        return current.noteOn(midiPitch, velocity)
    }

    fun noteOff(midiPitch: Int): Boolean =
        engine?.noteOff(midiPitch) == true

    /** Used by staff tapping until the staff gets a press/hold gesture of its own. */
    fun playOneShot(midiPitch: Int, velocity: Int = 92, durationMs: Long = 320L): Boolean {
        val current = engine ?: return false
        if (!isReady || renderThread?.isAlive != true) return false
        if (!current.noteOn(midiPitch, velocity)) return false

        thread(name = "ScoreForgeSoundFontOneShot", isDaemon = true) {
            try {
                Thread.sleep(durationMs.coerceAtLeast(20L))
            } catch (_: InterruptedException) {
                // Release the note below.
            }
            current.noteOff(midiPitch)
        }
        return true
    }

    fun allNotesOff() {
        engine?.allNotesOff()
    }

    private fun startRendererLocked(soundFont: SoundFontEngine) {
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)

        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()

        running = true
        audioTrack = track
        track.play()

        val framesPerBlock = 256
        val worker = thread(name = "ScoreForgeLiveSoundFontAudio", isDaemon = true) {
            try {
                while (running && engine === soundFont) {
                    val pcm = soundFont.renderStereo(framesPerBlock)
                    if (pcm.isEmpty()) break
                    val result = track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING)
                    if (result < 0) break
                }
            } finally {
                if (engine === soundFont) running = false
            }
        }
        renderThread = worker
    }

    private fun stopRendererLocked() {
        running = false
        engine?.allNotesOff()

        audioTrack?.let { track ->
            try {
                track.pause()
                track.flush()
                track.stop()
            } catch (_: IllegalStateException) {
                // Renderer may already have stopped.
            }
            try {
                track.release()
            } catch (_: Exception) {
                // Best-effort teardown.
            }
        }
        audioTrack = null

        renderThread?.interrupt()
        renderThread = null
    }

    override fun close() {
        ++loadGeneration
        synchronized(stateLock) {
            stopRendererLocked()
            engine?.close()
            engine = null
        }
    }
}
