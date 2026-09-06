package com.scoreforge.app.ui

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.content.Context
import kotlin.math.PI
import kotlin.math.sin

/** Tiny process-lifetime UI chirps for tactile command feedback. */
internal object ScoreForgeUiFeedback {
    private const val SAMPLE_RATE = 24_000
    private const val DURATION_MS = 34
    private const val VOLUME = 0.16f

    private val increaseTrack by lazy { buildTrack(startHz = 700.0, endHz = 980.0) }
    private val decreaseTrack by lazy { buildTrack(startHz = 620.0, endHz = 420.0) }
    private val neutralTrack by lazy { buildTrack(startHz = 610.0, endHz = 660.0) }

    fun play(feedback: UiCommandFeedback, context: Context) {
        if (!ScoreForgeSettingsRepository.commandSoundsEnabled(context)) return
        val track = when (feedback) {
            UiCommandFeedback.NONE -> return
            UiCommandFeedback.NEUTRAL -> neutralTrack
            UiCommandFeedback.INCREASE -> increaseTrack
            UiCommandFeedback.DECREASE -> decreaseTrack
        } ?: return

        synchronized(track) {
            try {
                if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
                track.setPlaybackHeadPosition(0)
                track.setVolume(VOLUME)
                track.play()
            } catch (_: IllegalStateException) {
                // Sonification is optional; never let a device audio quirk break a command.
            }
        }
    }

    private fun buildTrack(startHz: Double, endHz: Double): AudioTrack? = try {
        val sampleCount = (SAMPLE_RATE * DURATION_MS / 1000.0).toInt().coerceAtLeast(1)
        val pcm = ShortArray(sampleCount)
        var phase = 0.0
        for (i in pcm.indices) {
            val progress = if (pcm.lastIndex == 0) 0.0 else i.toDouble() / pcm.lastIndex.toDouble()
            val frequency = startHz + (endHz - startHz) * progress
            phase += 2.0 * PI * frequency / SAMPLE_RATE.toDouble()
            val envelope = sin(PI * progress).coerceAtLeast(0.0)
            pcm[i] = (sin(phase) * envelope * Short.MAX_VALUE * 0.55).toInt().toShort()
        }

        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size * 2)
            .build()
            .also { track -> track.write(pcm, 0, pcm.size, AudioTrack.WRITE_BLOCKING) }
    } catch (_: Exception) {
        null
    }
}
