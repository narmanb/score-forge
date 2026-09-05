package com.scoreforge.app.music

import kotlin.math.abs

/** A project tempo beginning at [startBeat], measured in quarter-note beats. */
data class ScoreTempoChange(
    val startBeat: Float = 0f,
    val bpm: Int = 120,
) {
    fun normalized(): ScoreTempoChange = ScoreTempos.normalizeOne(this)
}

/**
 * Project-wide tempo-map helpers, including beat/time conversion for variable-tempo playback.
 * Tempos are normalized to Score Forge's supported 30–300 BPM range.
 */
object ScoreTempos {
    private const val EPSILON = 0.001f
    const val MIN_BPM = 30
    const val MAX_BPM = 300
    val DEFAULT = ScoreTempoChange()

    fun normalizeOne(change: ScoreTempoChange): ScoreTempoChange = change.copy(
        startBeat = change.startBeat.coerceAtLeast(0f),
        bpm = change.bpm.coerceIn(MIN_BPM, MAX_BPM),
    )

    fun normalize(changes: List<ScoreTempoChange>): List<ScoreTempoChange> {
        val sorted = changes.map(::normalizeOne).sortedBy { it.startBeat }
        val merged = mutableListOf<ScoreTempoChange>()
        sorted.forEach { candidate ->
            val previous = merged.lastOrNull()
            if (previous != null && abs(previous.startBeat - candidate.startBeat) <= EPSILON) {
                merged[merged.lastIndex] = candidate.copy(startBeat = previous.startBeat)
            } else {
                merged += candidate
            }
        }

        if (merged.isEmpty()) return listOf(DEFAULT)
        if (merged.first().startBeat > EPSILON) {
            merged.add(0, DEFAULT)
        } else {
            merged[0] = merged[0].copy(startBeat = 0f)
        }
        return merged
    }

    fun atBeat(changes: List<ScoreTempoChange>, beat: Float): ScoreTempoChange {
        val safeBeat = beat.coerceAtLeast(0f)
        return normalize(changes)
            .lastOrNull { it.startBeat <= safeBeat + EPSILON }
            ?: DEFAULT
    }

    fun withChange(
        changes: List<ScoreTempoChange>,
        startBeat: Float,
        bpm: Int,
    ): List<ScoreTempoChange> {
        val safeStart = startBeat.coerceAtLeast(0f)
        val retained = normalize(changes)
            .filterNot { abs(it.startBeat - safeStart) <= EPSILON }
        return normalize(retained + ScoreTempoChange(safeStart, bpm).normalized())
    }

    fun withoutChange(
        changes: List<ScoreTempoChange>,
        startBeat: Float,
    ): List<ScoreTempoChange> {
        if (startBeat <= EPSILON) return normalize(changes)
        return normalize(
            normalize(changes).filterNot { abs(it.startBeat - startBeat) <= EPSILON }
        )
    }

    /** Absolute elapsed seconds from beat zero through [beat]. */
    fun secondsAtBeat(changes: List<ScoreTempoChange>, beat: Float): Double {
        val target = beat.coerceAtLeast(0f)
        if (target <= EPSILON) return 0.0
        val normalized = normalize(changes)
        var seconds = 0.0
        for (index in normalized.indices) {
            val current = normalized[index]
            if (target <= current.startBeat + EPSILON && index > 0) break
            val nextBeat = normalized.getOrNull(index + 1)?.startBeat ?: target
            val segmentEnd = minOf(target, nextBeat)
            val segmentBeats = (segmentEnd - current.startBeat).coerceAtLeast(0f)
            if (segmentBeats > 0f) {
                seconds += segmentBeats.toDouble() * 60.0 / current.bpm.toDouble()
            }
            if (target <= nextBeat + EPSILON) break
        }
        return seconds.coerceAtLeast(0.0)
    }

    fun durationSeconds(
        changes: List<ScoreTempoChange>,
        startBeat: Float,
        endBeat: Float,
    ): Double {
        val safeStart = startBeat.coerceAtLeast(0f)
        val safeEnd = endBeat.coerceAtLeast(safeStart)
        return (secondsAtBeat(changes, safeEnd) - secondsAtBeat(changes, safeStart)).coerceAtLeast(0.0)
    }

    /** Inverse of [secondsAtBeat] for transport/playhead updates. */
    fun beatAtSeconds(changes: List<ScoreTempoChange>, seconds: Double): Float {
        var remaining = seconds.coerceAtLeast(0.0)
        val normalized = normalize(changes)
        for (index in normalized.indices) {
            val current = normalized[index]
            val next = normalized.getOrNull(index + 1)
            if (next == null) {
                return (current.startBeat + remaining * current.bpm.toDouble() / 60.0)
                    .toFloat()
                    .coerceAtLeast(0f)
            }

            val segmentBeats = (next.startBeat - current.startBeat).coerceAtLeast(0f)
            val segmentSeconds = segmentBeats.toDouble() * 60.0 / current.bpm.toDouble()
            if (remaining <= segmentSeconds + 1e-9) {
                return (current.startBeat + remaining * current.bpm.toDouble() / 60.0)
                    .toFloat()
                    .coerceAtLeast(0f)
            }
            remaining -= segmentSeconds
        }
        return 0f
    }
}
