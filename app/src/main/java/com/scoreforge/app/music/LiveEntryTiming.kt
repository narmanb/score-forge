package com.scoreforge.app.music

import kotlin.math.abs

/** Timing helpers for real-time piano recording. */
object LiveEntryTiming {
    data class WrittenDuration(
        val duration: NoteDuration,
        val dotted: Boolean,
    ) {
        val beats: Float get() = duration.effectiveBeats(dotted)
    }

    private val writtenDurations = listOf(
        WrittenDuration(NoteDuration.SIXTEENTH, false),
        WrittenDuration(NoteDuration.SIXTEENTH, true),
        WrittenDuration(NoteDuration.EIGHTH, false),
        WrittenDuration(NoteDuration.EIGHTH, true),
        WrittenDuration(NoteDuration.QUARTER, false),
        WrittenDuration(NoteDuration.QUARTER, true),
        WrittenDuration(NoteDuration.HALF, false),
        WrittenDuration(NoteDuration.HALF, true),
        WrittenDuration(NoteDuration.WHOLE, false),
        WrittenDuration(NoteDuration.WHOLE, true),
    )

    fun beatAtElapsedMs(startBeat: Float, elapsedMs: Long, bpm: Int): Float {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeElapsedMs = elapsedMs.coerceAtLeast(0L)
        val beatsElapsed = safeElapsedMs.toDouble() / (60_000.0 / safeBpm.toDouble())
        return startBeat.coerceAtLeast(0f) + beatsElapsed.toFloat()
    }

    fun quantizedBeatAtElapsedMs(startBeat: Float, elapsedMs: Long, bpm: Int): Float =
        ScoreTimeline.quantizeBeat(beatAtElapsedMs(startBeat, elapsedMs, bpm))

    /**
     * Live mode preserves actual press/release timing, then chooses the nearest conventional
     * written value. Unlike Natural Entry, this includes dotted values so a performed 3/4 beat
     * note can become a dotted eighth instead of being forced into a quarter-note bucket.
     */
    fun quantizedDurationForHoldMs(holdMs: Long, bpm: Int): WrittenDuration {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeHoldMs = holdMs.coerceAtLeast(0L)
        val heldBeats = safeHoldMs.toDouble() / (60_000.0 / safeBpm.toDouble())
        return writtenDurations.minByOrNull { candidate ->
            abs(candidate.beats.toDouble() - heldBeats)
        } ?: writtenDurations.first()
    }
}
