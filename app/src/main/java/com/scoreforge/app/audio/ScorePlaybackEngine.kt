package com.scoreforge.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeline
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class ScorePlaybackEngine {
    private val sampleRate = 44_100
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var generation = 0

    @Volatile
    private var soundFontEngine: SoundFontEngine? = null

    private data class RenderedAudio(
        val pcm: ShortArray,
        val channels: Int,
    )

    fun setSoundFontEngine(engine: SoundFontEngine?) {
        soundFontEngine = engine
    }

    fun playScore(
        notes: List<ScoreNote>,
        bpm: Int,
        throughBeat: Float = ScoreTimeline.endBeat(notes),
        onFinished: () -> Unit = {},
    ) {
        val compatibilityTrack = ScoreTrack(
            id = 1,
            name = "Track 1",
            events = notes,
            cursorBeat = throughBeat,
        )
        playTracks(
            tracks = listOf(compatibilityTrack),
            bpm = bpm,
            throughBeat = throughBeat,
            onFinished = onFinished,
        )
    }

    fun playTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        throughBeat: Float = ScoreTracks.endBeat(tracks),
        onFinished: () -> Unit = {},
    ) {
        val playableTracks = ScoreTracks.audibleTracks(tracks)
            .take(ScoreTracks.MAX_TRACKS)
            .map { it.copy(events = it.events.toList()) }
        val notes = playableTracks.flatMap { it.notes }
        if (notes.isEmpty()) {
            onFinished()
            return
        }

        stop()
        val myGeneration = ++generation
        val safeBpm = bpm.coerceIn(30, 300)
        val safeThroughBeat = maxOf(
            playableTracks.maxOfOrNull { ScoreTimeline.endBeat(it.events) } ?: 0f,
            throughBeat.coerceAtLeast(0f),
        )

        thread(name = "ScoreForgePlayback", isDaemon = true) {
            val rendered = renderBestAvailableTracks(playableTracks, safeBpm, safeThroughBeat)
            if (myGeneration != generation || rendered.pcm.isEmpty()) return@thread

            val track = createStaticTrack(rendered.pcm, rendered.channels)
            if (myGeneration != generation) {
                track.release()
                return@thread
            }

            activeTrack = track
            track.write(rendered.pcm, 0, rendered.pcm.size)
            track.play()

            val frames = rendered.pcm.size.toDouble() / rendered.channels.coerceAtLeast(1)
            val durationMs = (frames / sampleRate * 1000.0).toLong() + 80L
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
        if (LiveInstrumentBus.previewPitch(midiPitch, velocity = 82)) return

        val preview = listOf(
            ScoreNote(
                midiPitch = midiPitch,
                duration = NoteDuration.QUARTER,
                startBeat = 0f,
                velocity = 82,
            )
        )

        thread(name = "ScoreForgePreview", isDaemon = true) {
            val pcm = renderFallbackScore(preview, bpm = 240, tailSeconds = 0.08f)
            if (pcm.isEmpty()) return@thread
            val track = createStaticTrack(pcm, channels = 1)
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

    private fun renderBestAvailableTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        throughBeat: Float,
    ): RenderedAudio {
        val soundFont = soundFontEngine
        if (soundFont != null && soundFont.hasSoundFont) {
            val pcm = soundFont.renderTracks(tracks, bpm, throughBeat = throughBeat)
            if (pcm.isNotEmpty()) return RenderedAudio(pcm, channels = 2)
        }

        return RenderedAudio(
            pcm = renderFallbackTracks(
                tracks = tracks,
                bpm = bpm,
                throughBeat = throughBeat,
            ),
            channels = 2,
        )
    }

    private fun createStaticTrack(pcm: ShortArray, channels: Int): AudioTrack =
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
                    .setChannelMask(
                        if (channels == 2) {
                            AudioFormat.CHANNEL_OUT_STEREO
                        } else {
                            AudioFormat.CHANNEL_OUT_MONO
                        }
                    )
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

    private fun renderFallbackTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        tailSeconds: Float = 0.35f,
        throughBeat: Float = ScoreTracks.endBeat(tracks),
    ): ShortArray {
        val audible = ScoreTracks.audibleTracks(tracks).filter { it.notes.isNotEmpty() }
        if (audible.isEmpty()) return ShortArray(0)

        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)
        val notesEndBeat = audible.maxOfOrNull { ScoreTimeline.endBeat(it.notes) } ?: 0f
        val endBeat = maxOf(notesEndBeat, throughBeat.coerceAtLeast(0f))
        val totalSeconds = endBeat * secondsPerBeat + tailSeconds
        val totalFrames = (totalSeconds * sampleRate).toInt().coerceAtLeast(1)
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        audible.forEach { track ->
            val volumeGain = track.volume.coerceIn(ScoreTrack.MIN_VOLUME, ScoreTrack.MAX_VOLUME) / 127f
            val panNorm = track.pan.coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN) / 64f
            val panAngle = ((panNorm + 1f) * (PI.toFloat() / 4f)).coerceIn(0f, PI.toFloat() / 2f)
            val leftGain = cos(panAngle) * volumeGain
            val rightGain = sin(panAngle) * volumeGain

            track.notes.forEach { note ->
                renderFallbackNote(
                    note = note,
                    secondsPerBeat = secondsPerBeat,
                    left = left,
                    right = right,
                    leftGain = leftGain,
                    rightGain = rightGain,
                )
            }
        }

        var peak = 0f
        for (i in left.indices) {
            peak = maxOf(peak, kotlin.math.abs(left[i]), kotlin.math.abs(right[i]))
        }
        val normalization = if (peak > 0.92f) 0.92f / peak else 1f

        return ShortArray(totalFrames * 2) { sampleIndex ->
            val frame = sampleIndex / 2
            val source = if (sampleIndex % 2 == 0) left[frame] else right[frame]
            val sample = (source * normalization).coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun renderFallbackNote(
        note: ScoreNote,
        secondsPerBeat: Float,
        left: FloatArray,
        right: FloatArray,
        leftGain: Float,
        rightGain: Float,
    ) {
        val startSample = (note.startBeat * secondsPerBeat * sampleRate).toInt()
        val noteSeconds = note.duration.beats * secondsPerBeat
        val noteSamples = (noteSeconds * sampleRate).toInt().coerceAtLeast(1)
        val frequency = 440.0 * Math.pow(2.0, (note.midiPitch - 69) / 12.0)
        val velocityGain = note.velocity.coerceIn(1, 127) / 127f

        for (i in 0 until noteSamples) {
            val target = startSample + i
            if (target !in left.indices) break

            val t = i.toFloat() / sampleRate
            val remaining = (noteSamples - i).toFloat() / sampleRate
            val attack = (t / 0.012f).coerceIn(0f, 1f)
            val release = (remaining / 0.07f).coerceIn(0f, 1f)
            val decay = exp((-1.8f * t).toDouble()).toFloat()
            val phase = 2.0 * PI * frequency * t

            val timbre =
                sin(phase).toFloat() * 0.72f +
                    sin(phase * 2.0).toFloat() * 0.20f +
                    sin(phase * 3.0).toFloat() * 0.08f
            val voice = timbre * attack * release * decay * velocityGain * 0.55f
            left[target] += voice * leftGain
            right[target] += voice * rightGain
        }
    }

    private fun renderFallbackScore(
        notes: List<ScoreNote>,
        bpm: Int,
        tailSeconds: Float = 0.35f,
        throughBeat: Float = ScoreTimeline.endBeat(notes),
    ): ShortArray {
        if (notes.isEmpty()) return ShortArray(0)

        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)
        val endBeat = maxOf(ScoreTimeline.endBeat(notes), throughBeat.coerceAtLeast(0f))
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
