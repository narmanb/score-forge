package com.scoreforge.app.ui

import com.scoreforge.app.music.ScoreKeySignature
import com.scoreforge.app.music.ScoreKeySignatures
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTimeSignatures
import kotlin.math.abs

/**
 * Adds purely visual horizontal room for notation changes without changing musical beat timing.
 *
 * Bar lines stay anchored to the real beat. Notes/rests/cursors at the change beat are placed after
 * the reserved notation gap, and every later beat is shifted by the same amount. This keeps rhythmic
 * spacing stable while preventing cancellation naturals / new key symbols from colliding with notes.
 */
internal data class StaffNotationGap(
    val beat: Float,
    val widthBeats: Float,
)

internal object StaffNotationSpacing {
    private const val EPSILON = 0.001f
    private const val TIME_SIGNATURE_GAP_BEATS = 0.72f
    private const val KEY_SIGNATURE_MARGIN_BEATS = 0.32f
    private const val KEY_SYMBOL_BEATS = 0.22f
    private const val BETWEEN_CHANGES_BEATS = 0.14f

    fun gaps(
        timeSignatures: List<ScoreTimeSignature>,
        keySignatures: List<ScoreKeySignature>,
    ): List<StaffNotationGap> {
        val times = ScoreTimeSignatures.normalize(timeSignatures)
        val keys = ScoreKeySignatures.normalize(keySignatures)
        val beats = (times.drop(1).map { it.startBeat } + keys.drop(1).map { it.startBeat })
            .distinct()
            .sorted()

        return beats.mapNotNull { beat ->
            var width = 0f
            val hasMeterChange = times.drop(1).any { abs(it.startBeat - beat) <= EPSILON }
            if (hasMeterChange) width += TIME_SIGNATURE_GAP_BEATS

            val keyIndex = keys.indexOfFirst { abs(it.startBeat - beat) <= EPSILON }
            if (keyIndex > 0) {
                val previous = keys[keyIndex - 1]
                val current = keys[keyIndex]
                val cancellations = (0..6).count { letter ->
                    val oldAlteration = ScoreKeySignatures.alterationForLetter(previous, letter)
                    val newAlteration = ScoreKeySignatures.alterationForLetter(current, letter)
                    oldAlteration != 0 && oldAlteration != newAlteration
                }
                val newSymbols = abs(current.fifths)
                val glyphCount = cancellations + newSymbols
                if (width > 0f) width += BETWEEN_CHANGES_BEATS
                width += KEY_SIGNATURE_MARGIN_BEATS + glyphCount * KEY_SYMBOL_BEATS
            }

            width.takeIf { it > EPSILON }?.let { StaffNotationGap(beat, it) }
        }
    }

    fun totalGapBeatsThrough(
        beat: Float,
        gaps: List<StaffNotationGap>,
        includeGapAtBeat: Boolean,
    ): Float = gaps.sumOf { gap ->
        val before = gap.beat < beat - EPSILON
        val atBeat = includeGapAtBeat && abs(gap.beat - beat) <= EPSILON
        if (before || atBeat) gap.widthBeats.toDouble() else 0.0
    }.toFloat()

    fun xAtBeat(
        beat: Float,
        timelineLeftPx: Float,
        pixelsPerBeat: Float,
        gaps: List<StaffNotationGap>,
        includeGapAtBeat: Boolean,
    ): Float {
        val safeBeat = beat.coerceAtLeast(0f)
        val extra = totalGapBeatsThrough(safeBeat, gaps, includeGapAtBeat)
        return timelineLeftPx + (safeBeat + extra) * pixelsPerBeat
    }

    /**
     * Inverse of [xAtBeat]. Taps inside a reserved notation gap resolve to the change beat itself.
     */
    fun beatAtX(
        x: Float,
        timelineLeftPx: Float,
        pixelsPerBeat: Float,
        gaps: List<StaffNotationGap>,
    ): Float {
        if (pixelsPerBeat <= 0f) return 0f
        var accumulatedGapBeats = 0f
        for (gap in gaps.sortedBy { it.beat }) {
            val gapStartX = timelineLeftPx + (gap.beat + accumulatedGapBeats) * pixelsPerBeat
            if (x < gapStartX) {
                return ((x - timelineLeftPx) / pixelsPerBeat - accumulatedGapBeats)
                    .coerceAtLeast(0f)
            }
            val gapEndX = gapStartX + gap.widthBeats * pixelsPerBeat
            if (x <= gapEndX) return gap.beat
            accumulatedGapBeats += gap.widthBeats
        }
        return ((x - timelineLeftPx) / pixelsPerBeat - accumulatedGapBeats)
            .coerceAtLeast(0f)
    }
}