package com.scoreforge.app.music

import kotlin.math.abs

/** Optional Hold-mode duration policies. These affect written length only, not performed onset spacing. */
enum class HoldDurationMode(val displayName: String) {
    STANDARD("Standard"),
    FAVOR_LONGER("Favor Longer"),
    NO_DOTTED("No Dots"),
}

object HoldDurationTiming {
    private val allDurations = listOf(
        NaturalEntryTiming.WrittenDuration(NoteDuration.SIXTEENTH, false),
        NaturalEntryTiming.WrittenDuration(NoteDuration.SIXTEENTH, true),
        NaturalEntryTiming.WrittenDuration(NoteDuration.EIGHTH, false),
        NaturalEntryTiming.WrittenDuration(NoteDuration.EIGHTH, true),
        NaturalEntryTiming.WrittenDuration(NoteDuration.QUARTER, false),
        NaturalEntryTiming.WrittenDuration(NoteDuration.QUARTER, true),
        NaturalEntryTiming.WrittenDuration(NoteDuration.HALF, false),
        NaturalEntryTiming.WrittenDuration(NoteDuration.HALF, true),
        NaturalEntryTiming.WrittenDuration(NoteDuration.WHOLE, false),
        NaturalEntryTiming.WrittenDuration(NoteDuration.WHOLE, true),
    )

    private val undottedDurations = allDurations.filterNot { it.dotted }

    fun writtenForHoldMs(
        holdMs: Long,
        bpm: Int,
        mode: HoldDurationMode,
    ): NaturalEntryTiming.WrittenDuration {
        if (mode == HoldDurationMode.STANDARD) {
            return NaturalEntryTiming.writtenForHoldMs(holdMs, bpm)
        }

        val safeBpm = bpm.coerceIn(30, 300)
        val safeHoldMs = holdMs.coerceAtLeast(0L)
        val beats = safeHoldMs.toDouble() / (60_000.0 / safeBpm.toDouble())

        return when (mode) {
            HoldDurationMode.STANDARD -> NaturalEntryTiming.writtenForHoldMs(holdMs, bpm)
            HoldDurationMode.NO_DOTTED -> nearest(beats, undottedDurations)
            HoldDurationMode.FAVOR_LONGER -> favorLonger(beats)
        }
    }

    /**
     * Standard midpoint rounding stays intact except after a dotted value. Once a player passes a
     * dotted value, the following longer undotted value takes over after only 20% of the gap. This
     * makes an intended half note easier to reach: dotted-quarter (1.5) -> half (2.0) switches at
     * 1.6 beats instead of the standard 1.75-beat midpoint.
     */
    private fun favorLonger(beats: Double): NaturalEntryTiming.WrittenDuration {
        if (beats <= allDurations.first().beats) return allDurations.first()

        for (index in 0 until allDurations.lastIndex) {
            val lower = allDurations[index]
            val upper = allDurations[index + 1]
            val lowerBeats = lower.beats.toDouble()
            val upperBeats = upper.beats.toDouble()
            val boundary = if (lower.dotted && !upper.dotted) {
                lowerBeats + (upperBeats - lowerBeats) * 0.20
            } else {
                (lowerBeats + upperBeats) / 2.0
            }
            if (beats < boundary) return lower
        }
        return allDurations.last()
    }

    private fun nearest(
        beats: Double,
        candidates: List<NaturalEntryTiming.WrittenDuration>,
    ): NaturalEntryTiming.WrittenDuration =
        candidates.minByOrNull { candidate -> abs(candidate.beats.toDouble() - beats) }
            ?: candidates.first()
}
