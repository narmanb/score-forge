package com.scoreforge.app.ui

import kotlin.math.abs
import kotlin.math.floor

enum class PianoRollEmptyDragTarget {
    TIMELINE,
    PITCH,
    PAGE,
}

/** Pure coordinate mapping used by the piano-roll canvas and unit tests. */
object PianoRollMapping {
    const val LEFT_GUTTER_PX = 48f
    const val RIGHT_MARGIN_PX = 12f
    const val VISIBLE_PITCHES = 36
    const val FULL_LOW_PITCH = 0
    const val FULL_HIGH_PITCH = 127

    fun lowPitch(octaveShift: Int): Int =
        (48 + octaveShift.coerceIn(-4, 3) * 12)
            .coerceIn(0, 127 - (VISIBLE_PITCHES - 1))

    fun highPitch(octaveShift: Int): Int = lowPitch(octaveShift) + VISIBLE_PITCHES - 1

    /**
     * With notes present, the roll should open around the score rather than around the keyboard's
     * current octave. Median pitch is resistant to one unusually high/low note.
     */
    fun focusPitch(notePitches: List<Int>, octaveShift: Int): Int {
        val safe = notePitches.map { it.coerceIn(FULL_LOW_PITCH, FULL_HIGH_PITCH) }.sorted()
        if (safe.isNotEmpty()) return safe[safe.size / 2]
        return (65 + octaveShift.coerceIn(-4, 3) * 12)
            .coerceIn(FULL_LOW_PITCH, FULL_HIGH_PITCH)
    }

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

    /** Retained for regression coverage of the previous inline-editor gesture routing. */
    fun emptyDragTarget(startX: Float, dragX: Float, dragY: Float): PianoRollEmptyDragTarget {
        if (startX < LEFT_GUTTER_PX) return PianoRollEmptyDragTarget.PITCH
        return if (abs(dragX) >= abs(dragY)) {
            PianoRollEmptyDragTarget.TIMELINE
        } else {
            PianoRollEmptyDragTarget.PAGE
        }
    }
}
