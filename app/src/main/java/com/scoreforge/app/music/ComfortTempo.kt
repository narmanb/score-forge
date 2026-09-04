package com.scoreforge.app.music

import kotlin.math.roundToInt

/** Estimates a comfortable quarter-note tempo from eight evenly intended attacks. */
object ComfortTempo {
    const val REQUIRED_ATTACKS = 8
    const val MIN_INTERVAL_MS = 120L
    const val MIN_BPM = 30
    const val MAX_BPM = 300

    fun addAttack(existing: List<Long>, timestampMs: Long): List<Long> {
        if (existing.size >= REQUIRED_ATTACKS) return existing
        val last = existing.lastOrNull()
        if (last != null && timestampMs - last < MIN_INTERVAL_MS) return existing
        return existing + timestampMs
    }

    fun estimateBpm(attacksMs: List<Long>): Int? {
        if (attacksMs.size < REQUIRED_ATTACKS) return null
        val sample = attacksMs.takeLast(REQUIRED_ATTACKS)
        val intervals = sample.zipWithNext { first, second -> second - first }
        if (intervals.size != REQUIRED_ATTACKS - 1 || intervals.any { it <= 0L }) return null

        // Seven intervals gives us an exact middle value and makes the estimate resistant to
        // one or two early/late taps.
        val medianIntervalMs = intervals.sorted()[intervals.size / 2]
        if (medianIntervalMs <= 0L) return null
        return (60_000.0 / medianIntervalMs.toDouble())
            .roundToInt()
            .coerceIn(MIN_BPM, MAX_BPM)
    }
}
