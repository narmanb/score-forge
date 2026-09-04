package com.scoreforge.app.music

import kotlin.math.abs
import kotlin.math.round

/**
 * Timing helpers for Natural piano entry.
 *
 * Natural v3 combines attack spacing with a short rolling pulse estimate. The rolling pulse is
 * especially important at phrase/measure endings: a player may leave extra air after the final
 * note, but that pause should not automatically lengthen the written note itself.
 */
object NaturalEntryTiming {
    data class WrittenDuration(
        val duration: NoteDuration,
        val dotted: Boolean,
    ) {
        val beats: Float get() = duration.effectiveBeats(dotted)
    }

    data class IntervalInference(
        val written: WrittenDuration,
        val phraseBreak: Boolean,
    )

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
    private const val GENERAL_PHRASE_BREAK_RATIO = 1.75
    private const val BARLINE_PHRASE_BREAK_RATIO = 1.30
    private const val MAX_RECENT_INTERVALS = 6

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

    /** Median attack spacing from the most recent non-break intervals. */
    fun expectedPulseMs(recentIntervalsMs: List<Long>): Long? {
        val sorted = recentIntervalsMs.filter { it > 0L }.sorted()
        if (sorted.isEmpty()) return null
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            ((sorted[middle - 1] + sorted[middle]) / 2L)
        }
    }

    /**
     * Decide how the previous note/chord should be written once the next attack arrives.
     *
     * [beatsToNextBarline] is optional meter context. If the previous note sits roughly one learned
     * pulse before a barline and the next attack arrives noticeably late, Natural treats the extra
     * time as phrase breathing space instead of stretching the note across the barline.
     */
    fun inferInterval(
        intervalMs: Long,
        bpm: Int,
        recentIntervalsMs: List<Long>,
        beatsToNextBarline: Float?,
        holdFallbackMs: Long,
    ): IntervalInference {
        val safeInterval = intervalMs.coerceAtLeast(0L)
        val expectedMs = expectedPulseMs(recentIntervalsMs)
        val expectedBeats = expectedMs?.let { beatsForMs(it, bpm) }
        val intervalBeats = beatsForMs(safeInterval, bpm)

        val expectedMatchesBarline = expectedBeats != null && beatsToNextBarline != null &&
            abs(beatsToNextBarline.toDouble() - expectedBeats) <= maxOf(0.35, expectedBeats * 0.40)
        val ratio = if (expectedMs != null && expectedMs > 0L) {
            safeInterval.toDouble() / expectedMs.toDouble()
        } else {
            1.0
        }
        val phraseBreak = when {
            expectedMs == null -> intervalBeats > LONG_GAP_BEATS
            expectedMatchesBarline && ratio >= BARLINE_PHRASE_BREAK_RATIO -> true
            ratio >= GENERAL_PHRASE_BREAK_RATIO -> true
            else -> false
        }

        val written = when {
            phraseBreak && expectedBeats != null -> nearestWrittenDuration(expectedBeats)
            intervalBeats <= LONG_GAP_BEATS -> nearestWrittenDuration(intervalBeats)
            holdFallbackMs > 0L -> writtenForHoldMs(holdFallbackMs, bpm)
            expectedBeats != null -> nearestWrittenDuration(expectedBeats)
            else -> writtenForHoldMs(safeInterval, bpm)
        }
        return IntervalInference(written = written, phraseBreak = phraseBreak)
    }

    /** Keep a small rolling pulse history while excluding phrase-break gaps. */
    fun rememberInterval(
        recentIntervalsMs: List<Long>,
        intervalMs: Long,
        phraseBreak: Boolean,
    ): List<Long> {
        if (phraseBreak || intervalMs <= 0L) return recentIntervalsMs.takeLast(MAX_RECENT_INTERVALS)
        return (recentIntervalsMs + intervalMs).takeLast(MAX_RECENT_INTERVALS)
    }

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
