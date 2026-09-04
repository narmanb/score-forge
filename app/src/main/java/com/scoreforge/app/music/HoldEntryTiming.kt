package com.scoreforge.app.music

/** Timing helpers for Hold entry that preserve performed attack spacing separately from note length. */
object HoldEntryTiming {
    fun nextStartBeat(
        previousStartBeat: Float,
        previousOnsetMs: Long,
        currentOnsetMs: Long,
        bpm: Int,
    ): Float {
        val intervalMs = (currentOnsetMs - previousOnsetMs).coerceAtLeast(0L)
        return previousStartBeat + NaturalEntryTiming.quantizedOnsetSpacingBeats(intervalMs, bpm)
    }
}
