package com.scoreforge.app.music

data class ScoreMeasureBounds(
    val startBeat: Float,
    val endBeat: Float,
) {
    val lengthBeats: Float
        get() = (endBeat - startBeat).coerceAtLeast(0f)
}

data class ScoreMeasureClipboard(
    val sourceLengthBeats: Float,
    val events: List<ScoreEvent>,
)

/** Meter-aware single-track copy, replace-paste, and duplicate operations. */
object ScoreMeasureEdits {
    private const val EPSILON = 0.001f

    fun boundsAt(
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
    ): ScoreMeasureBounds {
        val normalized = ScoreTimeSignatures.normalize(timeSignatures)
        val start = ScoreTimeSignatures.measureStartAt(normalized, beat)
        val active = ScoreTimeSignatures.atBeat(normalized, start)
        val searchThrough = start + active.beatsPerMeasure.coerceAtLeast(0.125f) + 0.01f
        val end = ScoreTimeSignatures.measureBoundaries(normalized, searchThrough)
            .firstOrNull { it > start + EPSILON }
            ?: (start + active.beatsPerMeasure.coerceAtLeast(0.125f))
        return ScoreMeasureBounds(start, end)
    }

    fun copyMeasure(
        events: List<ScoreEvent>,
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
    ): ScoreMeasureClipboard {
        val bounds = boundsAt(timeSignatures, beat)
        val relativeEvents = events
            .filter { it.startBeat >= bounds.startBeat - EPSILON && it.startBeat < bounds.endBeat - EPSILON }
            .map { event -> event.withStartBeat((event.startBeat - bounds.startBeat).coerceAtLeast(0f)) }
        return ScoreMeasureClipboard(
            sourceLengthBeats = bounds.lengthBeats,
            events = sanitizeScoreTies(relativeEvents),
        )
    }

    /**
     * Paste is safe when every copied onset still begins inside the destination measure. Written
     * duration may extend across the barline; only event starts are constrained.
     */
    fun canPasteAt(
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreMeasureClipboard,
    ): Boolean {
        val destination = boundsAt(timeSignatures, destinationBeat)
        return clipboard.events.all { event ->
            event.startBeat >= -EPSILON && event.startBeat < destination.lengthBeats - EPSILON
        }
    }

    /**
     * Replaces events whose start lies in the destination measure. Copied events retain their
     * relative onset and full written duration; events are never silently clipped at a barline.
     */
    fun pasteReplace(
        events: List<ScoreEvent>,
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreMeasureClipboard,
    ): List<ScoreEvent> {
        if (!canPasteAt(timeSignatures, destinationBeat, clipboard)) return events
        val destination = boundsAt(timeSignatures, destinationBeat)
        val retained = events.filterNot {
            it.startBeat >= destination.startBeat - EPSILON &&
                it.startBeat < destination.endBeat - EPSILON
        }
        val pasted = clipboard.events.map { event ->
            event.withStartBeat(destination.startBeat + event.startBeat)
        }
        return sanitizeScoreTies(retained + pasted)
    }

    /**
     * Inserts [copies] additional copies directly after the active measure and shifts later events
     * on this track forward by the inserted beat length.
     */
    fun duplicateMeasure(
        events: List<ScoreEvent>,
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
        copies: Int = 1,
    ): List<ScoreEvent> {
        val safeCopies = copies.coerceAtLeast(1)
        val bounds = boundsAt(timeSignatures, beat)
        val length = bounds.lengthBeats
        if (length <= EPSILON) return events

        val clipboard = copyMeasure(events, timeSignatures, beat)
        val insertedLength = length * safeCopies
        val beforeInsertion = events.filter { it.startBeat < bounds.endBeat - EPSILON }
        val afterInsertion = events
            .filter { it.startBeat >= bounds.endBeat - EPSILON }
            .map { event -> event.withStartBeat(event.startBeat + insertedLength) }
        val inserted = buildList {
            repeat(safeCopies) { copyIndex ->
                val copyStart = bounds.endBeat + length * copyIndex
                clipboard.events.forEach { event ->
                    add(event.withStartBeat(copyStart + event.startBeat))
                }
            }
        }

        return sanitizeScoreTies(beforeInsertion + inserted + afterInsertion)
    }

    fun duplicateCursorBeat(
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
        copies: Int,
    ): Float {
        val bounds = boundsAt(timeSignatures, beat)
        return bounds.startBeat + bounds.lengthBeats * copies.coerceAtLeast(1)
    }

    private fun ScoreEvent.withStartBeat(newStartBeat: Float): ScoreEvent = when (this) {
        is ScoreNote -> copy(startBeat = newStartBeat)
        is ScoreRest -> copy(startBeat = newStartBeat)
    }
}
