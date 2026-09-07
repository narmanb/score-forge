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
    val sourceMeasureLengths: List<Float> = listOf(sourceLengthBeats),
) {
    val sourceMeasureCount: Int
        get() = sourceMeasureLengths.size.coerceAtLeast(1)
}

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

    fun boundsRange(
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
        measureCount: Int,
    ): List<ScoreMeasureBounds> {
        val safeCount = measureCount.coerceAtLeast(1)
        return buildList {
            var bounds = boundsAt(timeSignatures, beat)
            add(bounds)
            repeat(safeCount - 1) {
                bounds = boundsAt(timeSignatures, bounds.endBeat + EPSILON)
                add(bounds)
            }
        }
    }

    fun copyMeasure(
        events: List<ScoreEvent>,
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
    ): ScoreMeasureClipboard = copyMeasures(events, timeSignatures, beat, 1)

    fun copyMeasures(
        events: List<ScoreEvent>,
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
        measureCount: Int,
    ): ScoreMeasureClipboard {
        val bounds = boundsRange(timeSignatures, beat, measureCount)
        val rangeStart = bounds.first().startBeat
        val rangeEnd = bounds.last().endBeat
        val relativeEvents = events
            .filter { it.startBeat >= rangeStart - EPSILON && it.startBeat < rangeEnd - EPSILON }
            .map { event -> event.withStartBeat((event.startBeat - rangeStart).coerceAtLeast(0f)) }
        return ScoreMeasureClipboard(
            sourceLengthBeats = rangeEnd - rangeStart,
            events = sanitizeScoreTies(relativeEvents),
            sourceMeasureLengths = bounds.map { it.lengthBeats },
        )
    }

    /**
     * Paste is safe when every copied onset still begins inside its corresponding destination
     * measure. Written duration may extend across a barline; only event starts are constrained.
     */
    fun canPasteAt(
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreMeasureClipboard,
    ): Boolean {
        val sourceLengths = effectiveSourceMeasureLengths(clipboard)
        val destinations = boundsRange(timeSignatures, destinationBeat, sourceLengths.size)
        return clipboard.events.all { event ->
            val mapped = sourceMeasurePosition(event.startBeat, sourceLengths) ?: return@all false
            val destination = destinations[mapped.first]
            mapped.second >= -EPSILON && mapped.second < destination.lengthBeats - EPSILON
        }
    }

    /**
     * Replaces events whose starts lie in the destination measure range. Copied events retain
     * their within-measure onset and full written duration; events are never silently clipped.
     */
    fun pasteReplace(
        events: List<ScoreEvent>,
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreMeasureClipboard,
    ): List<ScoreEvent> {
        if (!canPasteAt(timeSignatures, destinationBeat, clipboard)) return events
        val sourceLengths = effectiveSourceMeasureLengths(clipboard)
        val destinations = boundsRange(timeSignatures, destinationBeat, sourceLengths.size)
        val rangeStart = destinations.first().startBeat
        val rangeEnd = destinations.last().endBeat
        val retained = events.filterNot {
            it.startBeat >= rangeStart - EPSILON && it.startBeat < rangeEnd - EPSILON
        }
        val pasted = clipboard.events.mapNotNull { event ->
            val mapped = sourceMeasurePosition(event.startBeat, sourceLengths) ?: return@mapNotNull null
            val destination = destinations[mapped.first]
            event.withStartBeat(destination.startBeat + mapped.second)
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

    private fun effectiveSourceMeasureLengths(clipboard: ScoreMeasureClipboard): List<Float> {
        val usable = clipboard.sourceMeasureLengths.filter { it > EPSILON }
        return if (usable.isNotEmpty()) usable else listOf(clipboard.sourceLengthBeats.coerceAtLeast(0.125f))
    }

    private fun sourceMeasurePosition(
        relativeBeat: Float,
        sourceLengths: List<Float>,
    ): Pair<Int, Float>? {
        if (relativeBeat < -EPSILON) return null
        var sourceStart = 0f
        sourceLengths.forEachIndexed { index, length ->
            val sourceEnd = sourceStart + length
            val isLast = index == sourceLengths.lastIndex
            if (relativeBeat < sourceEnd - EPSILON || isLast) {
                val withinMeasure = relativeBeat - sourceStart
                if (withinMeasure < -EPSILON || withinMeasure >= length - EPSILON) return null
                return index to withinMeasure.coerceAtLeast(0f)
            }
            sourceStart = sourceEnd
        }
        return null
    }

    private fun ScoreEvent.withStartBeat(newStartBeat: Float): ScoreEvent = when (this) {
        is ScoreNote -> copy(startBeat = newStartBeat)
        is ScoreRest -> copy(startBeat = newStartBeat)
    }
}
