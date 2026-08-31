package com.scoreforge.app.ui

import kotlin.math.floor

/** Pure coordinate mapping used by the piano-roll canvas and unit tests. */
object PianoRollMapping {
    const val LEFT_GUTTER_PX = 48f
    const val RIGHT_MARGIN_PX = 12f
    const val VISIBLE_PITCHES = 36

    fun lowPitch(octaveShift: Int): Int =
        (48 + octaveShift.coerceIn(-4, 3) * 12)
            .coerceIn(0, 127 - (VISIBLE_PITCHES - 1))

    fun highPitch(octaveShift: Int): Int = lowPitch(octaveShift) + VISIBLE_PITCHES - 1

    fun xAtBeat(beat: Float, visibleBeats: Float, width: Float): Float {
        val usable = (width - LEFT_GUTTER_PX - RIGHT_MARGIN_PX).coerceAtLeast(1f)
        return LEFT_GUTTER_PX +
            usable * (beat.coerceIn(0f, visibleBeats) / visibleBeats.coerceAtLeast(1f))
    }

    fun beatAtX(x: Float, visibleBeats: Float, width: Float): Float {
        val usable = (width - LEFT_GUTTER_PX - RIGHT_MARGIN_PX).coerceAtLeast(1f)
        val fraction = ((x - LEFT_GUTTER_PX) / usable).coerceIn(0f, 1f)
        return fraction * visibleBeats.coerceAtLeast(0f)
    }

    fun rowHeight(height: Float, lowPitch: Int, highPitch: Int): Float =
        height.coerceAtLeast(1f) / (highPitch - lowPitch + 1).coerceAtLeast(1)

    fun yCenterForPitch(pitch: Int, lowPitch: Int, highPitch: Int, height: Float): Float {
        val safePitch = pitch.coerceIn(lowPitch, highPitch)
        val row = highPitch - safePitch
        val rowHeight = rowHeight(height, lowPitch, highPitch)
        return row * rowHeight + rowHeight / 2f
    }

    fun pitchAtY(y: Float, lowPitch: Int, highPitch: Int, height: Float): Int {
        val rowHeight = rowHeight(height, lowPitch, highPitch)
        val row = floor((y.coerceIn(0f, height.coerceAtLeast(1f) - 0.001f)) / rowHeight).toInt()
        return (highPitch - row).coerceIn(lowPitch, highPitch)
    }
}
