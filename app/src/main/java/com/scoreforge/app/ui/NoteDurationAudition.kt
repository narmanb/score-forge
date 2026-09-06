package com.scoreforge.app.ui

import com.scoreforge.app.music.NoteDuration
import kotlin.math.roundToInt

internal object NoteDurationAudition {
    const val MIDI_PITCH = 60 // Middle C (C4)
    private const val KEYBOARD_REFERENCE_VELOCITY = 96

    fun durationMs(duration: NoteDuration, dotted: Boolean, bpm: Int): Long {
        val safeBpm = bpm.coerceIn(30, 300)
        val quarterMs = 60_000f / safeBpm.toFloat()
        return (duration.effectiveBeats(dotted) * quarterMs).roundToInt().coerceAtLeast(1).toLong()
    }

    /** Maps the independent audition-volume preference against the keyboard's normal velocity. */
    fun velocity(volume: Float): Int =
        (KEYBOARD_REFERENCE_VELOCITY * volume.coerceIn(0.05f, 0.70f))
            .roundToInt()
            .coerceIn(1, 127)
}
