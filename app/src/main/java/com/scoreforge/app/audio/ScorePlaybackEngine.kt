package com.scoreforge.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeline
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

class ScorePlaybackEngine {
    private val sampleRate = 44_100
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var generation = 0

    fun playScore(
        notes: List<ScoreNote>,
        bpm: Int,
        onFinished: () -> Unit = {},
    ) {
        if (notes.isEmpty()) {
            onFinished()
            return
        }

        stop()
        val myGeneration = ++generation
        val snapshot = notes.toList()
        val safeBpm = bpm.coerceIn(30, 300)

        thread(name = "ScoreForgePlayback", isDaemon = true) {
            val pcm = renderScore(snapshot, safeBpm)
            if (myGeneration != generation || pcm.isEmpty()) return@thread

            val track = createStaticTrack(pcm)
            if (myGeneration != generation) {
                track.release()
                return@thread
            }

            activeTrack = track
            track.write(pcm, 0, pcm.size)
            track.play()

            val durationMs = ((pcm.size.toDouble() / sampleRate) * 1000.0).toLong() + 80L
            try {
                Thread.sleep(durationMs)
            } catch (_: InterruptedException) {
                // A stop/restart invalidates this generation below.
            }

            if (myGeneration == generation) {
                releaseTrack(track)
                activeTrack = null
                mainHandler.post(onFinished)
            } else {
                releaseTrack(track)
            }
        }
    }

    fun previewPitch(midiPitch: Int) {
        val preview = listOf(
            ScoreNote(
                midiPitch = midiPitch,
                duration = NoteDuration.QUARTER,
                startBeat = 0f,
                velocity = 82,
            )
        )

        thread(name = "ScoreForgePreview", isDaemon = true) {
            val pcm = renderScore(preview, bpm = 240, tailSeconds = 0.08f)
            if (pcm.isEmpty()) return@thread
            val track = createStaticTrack(pcm)
            track.write(pcm, 0, pcm.size)
            track.play()
            try {
                Thread.sleep(((pcm.size.toDouble() / sampleRate) * 1000.0).toLong() + 30L)
            } catch (_: InterruptedException) {
                // Nothing else to do for a short preview voice.
            }
            releaseTrack(track)
        }
    }

    fun stop() {
        generation++
        activeTrack?.let(::releaseTrack)
        activeTrack = null
    }

    fun release() = stop()

    private fun createStaticTrack(pcm: ShortArray): AudioTrack =
        AudioTrack.Builder()
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
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

    private fun releaseTrack(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        } catch (_: IllegalStateException) {
            // Already stopped/released.
        }
        try {
            track.release()
        } catch (_: Exception) {
            // AudioTrack.release() is best-effort during rapid transport changes.
        }
    }

    private fun renderScore(
        notes: List<ScoreNote>,
        bpm: Int,
        tailSeconds: Float = 0.35f,
    ): ShortArray {
        if (notes.isEmpty()) return ShortArray(0)

        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)
        val endBeat = ScoreTimeline.endBeat(notes)
        val totalSeconds = endBeat * secondsPerBeat + tailSeconds
        val totalSamples = (totalSeconds * sampleRate).toInt().coerceAtLeast(1)
        val mix = FloatArray(totalSamples)

        notes.forEach { note ->
            val startSample = (note.startBeat * secondsPerBeat * sampleRate).toInt()
            val noteSeconds = note.duration.beats * secondsPerBeat
            val noteSamples = (noteSeconds * sampleRate).toInt().coerceAtLeast(1)
            val frequency = 440.0 * Math.pow(2.0, (note.midiPitch - 69) / 12.0)
            val velocityGain = note.velocity.coerceIn(1, 127) / 127f

            for (i in 0 until noteSamples) {
                val target = startSample + i
                if (target !in mix.indices) break

                val t = i.toFloat() / sampleRate
                val remaining = (noteSamples - i).toFloat() / sampleRate
                val attack = (t / 0.012f).coerceIn(0f, 1f)
                val release = (remaining / 0.07f).coerceIn(0f, 1f)
                val decay = exp((-1.8f * t).toDouble()).toFloat()
                val phase = 2.0 * PI * frequency * t

                // A lightweight harmonic voice for the first playable milestone.
                // The engine boundary is intentionally separate so FluidSynth/SoundFonts
                // can replace this oscillator without changing score/timeline code.
                val timbre =
                    sin(phase).toFloat() * 0.72f +
                        sin(phase * 2.0).toFloat() * 0.20f +
                        sin(phase * 3.0).toFloat() * 0.08f

                mix[target] += timbre * attack * release * decay * velocityGain * 0.55f
            }
        }

        var peak = 0f
        for (sample in mix) peak = maxOf(peak, kotlin.math.abs(sample))
        val normalization = if (peak > 0.92f) 0.92f / peak else 1f

        return ShortArray(mix.size) { index ->
            val sample = (mix[index] * normalization).coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
