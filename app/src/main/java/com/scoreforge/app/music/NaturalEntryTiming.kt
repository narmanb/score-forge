package com.scoreforge.app.music

/**
 * Converts a relaxed press-and-hold gesture into a conventional written note value.
 * Natural Entry intentionally ignores the time between gestures; only hold length matters.
 */
object NaturalEntryTiming {
    fun durationForHoldMs(holdMs: Long, bpm: Int): NoteDuration {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeHoldMs = holdMs.coerceAtLeast(0L)
        val msPerBeat = 60_000.0 / safeBpm.toDouble()
        val heldBeats = safeHoldMs.toDouble() / msPerBeat

        return when {
            heldBeats < 0.375 -> NoteDuration.SIXTEENTH
            heldBeats < 0.75 -> NoteDuration.EIGHTH
            heldBeats < 1.5 -> NoteDuration.QUARTER
            heldBeats < 3.0 -> NoteDuration.HALF
            else -> NoteDuration.WHOLE
        }
    }
}
