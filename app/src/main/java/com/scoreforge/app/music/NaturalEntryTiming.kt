package com.scoreforge.app.music

import kotlin.math.abs
import kotlin.math.round

/**
 * Timing helpers for Natural piano entry.
 *
 * Natural mode treats the spacing between note attacks as the strongest clue for written rhythm.
 * Release duration is retained as a fallback for the newest note/chord until a following attack
 * arrives, which lets crisp quarter-note playing remain quarter notes instead of being shortened
 * purely because the finger lifted early.
 */
object NaturalEntryTiming {
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

    private const val MIN_CHORD_WINDOW_MS = 55L
    private const val MAX_CHORD_WINDOW_MS = 120L
    private const val CHORD_WINDOW_BEAT_FRACTION = 0.18
    private const val LONG_GAP_BEATS = 4.5

    fun chordWindowMs(bpm: Int): Long {
        val safeBpm = bpm.coerceIn(30, 300)
        val msPerBeat = 60_000.0 / safeBpm.toDouble()
        return (msPerBeat * CHORD_WINDOW_BEAT_FRACTION)
            .toLong()
            .coerceIn(MIN_CHORD_WINDOW_MS, MAX_CHORD_WINDOW_MS)
    }

    fun isSameOnsetGroup(previousOnsetMs: Long, currentOnsetMs: Long, bpm: Int): Boolean {
        if (currentOnsetMs < previousOnsetMs) return false
        return currentOnsetMs - previousOnsetMs <= chordWindowMs(bpm)
    }

    fun writtenForOnsetIntervalMs(intervalMs: Long, bpm: Int): WrittenDuration =
        nearestWrittenDuration(beatsForMs(intervalMs, bpm))

    fun writtenForHoldMs(holdMs: Long, bpm: Int): WrittenDuration =
        nearestWrittenDuration(beatsForMs(holdMs, bpm))

    /**
     * Start-to-start spacing for the next Natural group. Quarter-beat quantization preserves
     * syncopated positions while keeping the score on Score Forge's existing edit grid.
     */
    fun quantizedOnsetSpacingBeats(intervalMs: Long, bpm: Int): Float {
        val rawBeats = beatsForMs(intervalMs, bpm).coerceAtLeast(0.25)
        return (round(rawBeats * 4.0) / 4.0).toFloat().coerceAtLeast(0.25f)
    }

    /** A very long pause should not turn the previous note into one giant sustained note. */
    fun shouldUseOnsetAsWrittenDuration(intervalMs: Long, bpm: Int): Boolean =
        beatsForMs(intervalMs, bpm) <= LONG_GAP_BEATS

    /** Legacy convenience retained for callers/tests that only need an undotted hold bucket. */
    fun durationForHoldMs(holdMs: Long, bpm: Int): NoteDuration =
        writtenForHoldMs(holdMs, bpm).duration

    private fun beatsForMs(durationMs: Long, bpm: Int): Double {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeDurationMs = durationMs.coerceAtLeast(0L)
        return safeDurationMs.toDouble() / (60_000.0 / safeBpm.toDouble())
    }

    private fun nearestWrittenDuration(beats: Double): WrittenDuration =
        writtenDurations.minByOrNull { candidate ->
            abs(candidate.beats.toDouble() - beats)
        } ?: writtenDurations.first()
}
