package com.scoreforge.app.ui

/** Shared horizontal mapping between staff pixels and musical beats. */
object StaffTimeMapping {
    // Leave room for the clef/time-signature header before beat zero.
    const val LEFT_MARGIN_PX = 120f
    const val RIGHT_MARGIN_PX = 20f

    fun xAtBeat(beat: Float, visibleBeats: Float, width: Float): Float {
        val usable = (width - LEFT_MARGIN_PX - RIGHT_MARGIN_PX).coerceAtLeast(1f)
        return LEFT_MARGIN_PX +
            usable * (beat.coerceIn(0f, visibleBeats) / visibleBeats.coerceAtLeast(1f))
    }

    fun beatAtX(x: Float, visibleBeats: Float, width: Float): Float {
        val usable = (width - LEFT_MARGIN_PX - RIGHT_MARGIN_PX).coerceAtLeast(1f)
        val fraction = ((x - LEFT_MARGIN_PX) / usable).coerceIn(0f, 1f)
        return fraction * visibleBeats.coerceAtLeast(0f)
    }
}
