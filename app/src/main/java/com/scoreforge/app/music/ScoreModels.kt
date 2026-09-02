package com.scoreforge.app.music

import kotlin.math.abs
import kotlin.math.round

enum class NoteDuration(val beats: Float, val displayName: String) {
    WHOLE(4f, "Whole"),
    HALF(2f, "Half"),
    QUARTER(1f, "Quarter"),
    EIGHTH(0.5f, "Eighth"),
    SIXTEENTH(0.25f, "16th");

    fun effectiveBeats(dotted: Boolean): Float = beats * if (dotted) 1.5f else 1f
}

enum class NoteArticulation(val displayName: String) {
    NORMAL("Normal"),
    STACCATO("Staccato"),
    TENUTO("Tenuto"),
    ACCENT("Accent"),
    LEGATO("Legato"),
}

/** A musical meter beginning at [startBeat], measured in quarter-note beats. */
data class ScoreTimeSignature(
    val startBeat: Float = 0f,
    val numerator: Int = 4,
    val denominator: Int = 4,
) {
    val beatsPerMeasure: Float
        get() = numerator.coerceAtLeast(1) * (4f / denominator.coerceAtLeast(1).toFloat())

    val displayName: String
        get() = "$numerator/$denominator"

    fun normalized(): ScoreTimeSignature = ScoreTimeSignatures.normalizeOne(this)
}

/** Helpers for a project-wide time-signature map, including mid-song meter changes. */
object ScoreTimeSignatures {
    private const val EPSILON = 0.001f
    val DEFAULT = ScoreTimeSignature()
    val SUPPORTED_DENOMINATORS = listOf(1, 2, 4, 8, 16, 32, 64, 128)

    fun normalizeOne(signature: ScoreTimeSignature): ScoreTimeSignature {
        val denominator = signature.denominator.takeIf { it in SUPPORTED_DENOMINATORS } ?: 4
        return signature.copy(
            startBeat = signature.startBeat.coerceAtLeast(0f),
            numerator = signature.numerator.coerceIn(1, 32),
            denominator = denominator,
        )
    }

    fun normalize(signatures: List<ScoreTimeSignature>): List<ScoreTimeSignature> {
        val sorted = signatures
            .map(::normalizeOne)
            .sortedBy { it.startBeat }
        val merged = mutableListOf<ScoreTimeSignature>()
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

    fun atBeat(signatures: List<ScoreTimeSignature>, beat: Float): ScoreTimeSignature {
        val safeBeat = beat.coerceAtLeast(0f)
        return normalize(signatures)
            .lastOrNull { it.startBeat <= safeBeat + EPSILON }
            ?: DEFAULT
    }

    /**
     * Returns barline beats beginning with 0 and continuing through the first barline at or after
     * [throughBeat]. A meter change itself starts a new measure, even for unusual MIDI files that
     * place the change before the previous measure would naturally end.
     */
    fun measureBoundaries(
        signatures: List<ScoreTimeSignature>,
        throughBeat: Float,
    ): List<Float> {
        val normalized = normalize(signatures)
        val target = throughBeat.coerceAtLeast(0f)
        val boundaries = mutableListOf(0f)
        if (target <= EPSILON) return boundaries

        var signatureIndex = 0
        var position = 0f
        var guard = 0
        while (position < target - EPSILON && guard++ < 100_000) {
            while (
                signatureIndex + 1 < normalized.size &&
                normalized[signatureIndex + 1].startBeat <= position + EPSILON
            ) {
                signatureIndex += 1
            }

            val signature = normalized[signatureIndex]
            val naturalEnd = position + signature.beatsPerMeasure.coerceAtLeast(0.125f)
            val nextChange = normalized.getOrNull(signatureIndex + 1)?.startBeat
            val nextBoundary = if (
                nextChange != null &&
                nextChange > position + EPSILON &&
                nextChange < naturalEnd - EPSILON
            ) {
                nextChange
            } else {
                naturalEnd
            }

            if (nextBoundary <= position + EPSILON) break
            position = nextBoundary
            boundaries += position
        }
        return boundaries
    }

    fun measureCount(signatures: List<ScoreTimeSignature>, throughBeat: Float): Int =
        maxOf(1, measureBoundaries(signatures, throughBeat).size - 1)

    fun endBeatAfterMeasures(signatures: List<ScoreTimeSignature>, measureCount: Int): Float {
        val normalized = normalize(signatures)
        var signatureIndex = 0
        var position = 0f
        repeat(measureCount.coerceAtLeast(1)) {
            while (
                signatureIndex + 1 < normalized.size &&
                normalized[signatureIndex + 1].startBeat <= position + EPSILON
            ) {
                signatureIndex += 1
            }

            val signature = normalized[signatureIndex]
            val naturalEnd = position + signature.beatsPerMeasure.coerceAtLeast(0.125f)
            val nextChange = normalized.getOrNull(signatureIndex + 1)?.startBeat
            position = if (
                nextChange != null &&
                nextChange > position + EPSILON &&
                nextChange < naturalEnd - EPSILON
            ) {
                nextChange
            } else {
                naturalEnd
            }
        }
        return position
    }
}

sealed interface ScoreEvent {
    val duration: NoteDuration
    val startBeat: Float
    val dotted: Boolean

    val effectiveBeats: Float
        get() = duration.effectiveBeats(dotted)
}

data class ScoreNote(
    val midiPitch: Int,
    override val duration: NoteDuration,
    override val startBeat: Float = 0f,
    val velocity: Int = 96,
    override val dotted: Boolean = false,
    val tieToNext: Boolean = false,
    val articulation: NoteArticulation = NoteArticulation.NORMAL,
) : ScoreEvent

data class ScoreRest(
    override val duration: NoteDuration,
    override val startBeat: Float = 0f,
    override val dotted: Boolean = false,
) : ScoreEvent

/** Playback interpretation for written articulation without changing score timing. */
object ScoreArticulations {
    private const val EPSILON = 0.001f

    fun playbackVelocity(note: ScoreNote): Int = when (note.articulation) {
        NoteArticulation.ACCENT -> (note.velocity + 22).coerceIn(1, 127)
        NoteArticulation.TENUTO -> (note.velocity + 4).coerceIn(1, 127)
        else -> note.velocity.coerceIn(1, 127)
    }

    fun playbackEndBeat(notes: List<ScoreNote>, noteIndex: Int): Float {
        val note = notes.getOrNull(noteIndex) ?: return 0f
        val writtenEnd = note.startBeat + note.effectiveBeats

        // Ties already define a continuous written chain and take precedence over articulation gates.
        if (ScoreTies.hasValidTie(notes, noteIndex) || ScoreTies.isContinuation(notes, noteIndex)) {
            return writtenEnd
        }

        return when (note.articulation) {
            NoteArticulation.NORMAL,
            NoteArticulation.TENUTO -> writtenEnd

            NoteArticulation.STACCATO ->
                note.startBeat + (note.effectiveBeats * 0.50f).coerceAtLeast(0.08f)

            NoteArticulation.ACCENT ->
                note.startBeat + (note.effectiveBeats * 0.90f).coerceAtLeast(0.08f)

            NoteArticulation.LEGATO -> {
                val next = notes
                    .asSequence()
                    .filter { it.startBeat > note.startBeat + EPSILON }
                    .minByOrNull { it.startBeat }
                if (next == null || next.startBeat > writtenEnd + 0.25f) {
                    writtenEnd
                } else if (next.midiPitch == note.midiPitch) {
                    // Avoid a late note-off cutting off a newly-started identical pitch.
                    maxOf(writtenEnd, next.startBeat)
                } else {
                    maxOf(
                        writtenEnd,
                        next.startBeat + minOf(0.08f, note.effectiveBeats * 0.08f),
                    )
                }
            }
        }
    }
}

object ScoreTimeline {
    /** Compatibility constant for older callers; new meter-aware code should use the signature map. */
    const val BEATS_PER_MEASURE = 4f
    const val EDIT_GRID_BEATS = 0.25f

    fun endBeat(events: List<ScoreEvent>): Float =
        events.maxOfOrNull { it.startBeat + it.effectiveBeats } ?: 0f

    fun nextBeat(events: List<ScoreEvent>): Float = endBeat(events)

    fun measureCount(
        events: List<ScoreEvent>,
        throughBeat: Float = endBeat(events),
        timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),
    ): Int {
        val furthestBeat = maxOf(endBeat(events), throughBeat, 0f)
        return ScoreTimeSignatures.measureCount(timeSignatures, furthestBeat)
    }

    fun visibleBeats(
        events: List<ScoreEvent>,
        minimumMeasures: Int = 4,
        throughBeat: Float = endBeat(events),
        timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),
    ): Float {
        val furthestBeat = maxOf(endBeat(events), throughBeat, 0f)
        val measures = maxOf(
            ScoreTimeSignatures.measureCount(timeSignatures, furthestBeat),
            minimumMeasures,
        )
        return maxOf(
            furthestBeat,
            ScoreTimeSignatures.endBeatAfterMeasures(timeSignatures, measures),
        )
    }

    fun quantizeBeat(beat: Float, gridBeats: Float = EDIT_GRID_BEATS): Float {
        if (gridBeats <= 0f) return beat.coerceAtLeast(0f)
        return (round(beat.coerceAtLeast(0f) / gridBeats) * gridBeats).coerceAtLeast(0f)
    }
}

/** Rules and helpers for ordinary notation ties between contiguous notes of the same pitch. */
object ScoreTies {
    private const val EPSILON = 0.001f

    fun targetIndex(events: List<ScoreEvent>, sourceIndex: Int): Int? {
        val source = events.getOrNull(sourceIndex) as? ScoreNote ?: return null
        val targetBeat = source.startBeat + source.effectiveBeats
        return events.indices
            .asSequence()
            .filter { it != sourceIndex }
            .mapNotNull { index -> (events[index] as? ScoreNote)?.let { index to it } }
            .filter { (_, note) ->
                note.midiPitch == source.midiPitch && abs(note.startBeat - targetBeat) <= EPSILON
            }
            .minByOrNull { (index, _) -> index }
            ?.first
    }

    fun hasValidTie(events: List<ScoreEvent>, sourceIndex: Int): Boolean {
        val source = events.getOrNull(sourceIndex) as? ScoreNote ?: return false
        return source.tieToNext && targetIndex(events, sourceIndex) != null
    }

    fun incomingTieSourceIndex(events: List<ScoreEvent>, targetIndex: Int): Int? {
        val target = events.getOrNull(targetIndex) as? ScoreNote ?: return null
        return events.indices.firstOrNull { sourceIndex ->
            sourceIndex != targetIndex &&
                (events[sourceIndex] as? ScoreNote)?.tieToNext == true &&
                targetIndex(events, sourceIndex) == targetIndex &&
                (events[sourceIndex] as ScoreNote).midiPitch == target.midiPitch
        }
    }

    fun isContinuation(events: List<ScoreEvent>, noteIndex: Int): Boolean =
        incomingTieSourceIndex(events, noteIndex) != null

    fun chainEndBeat(events: List<ScoreEvent>, rootIndex: Int): Float {
        val root = events.getOrNull(rootIndex) as? ScoreNote ?: return 0f
        var currentIndex = rootIndex
        var endBeat = root.startBeat + root.effectiveBeats
        val visited = mutableSetOf<Int>()
        while (visited.add(currentIndex) && hasValidTie(events, currentIndex)) {
            val nextIndex = targetIndex(events, currentIndex) ?: break
            val next = events.getOrNull(nextIndex) as? ScoreNote ?: break
            endBeat = maxOf(endBeat, next.startBeat + next.effectiveBeats)
            currentIndex = nextIndex
        }
        return endBeat
    }

    fun canToggle(events: List<ScoreEvent>, sourceIndex: Int): Boolean =
        events.getOrNull(sourceIndex) is ScoreNote &&
            ((events[sourceIndex] as ScoreNote).tieToNext || targetIndex(events, sourceIndex) != null)

    fun toggle(events: List<ScoreEvent>, sourceIndex: Int): List<ScoreEvent>? {
        val source = events.getOrNull(sourceIndex) as? ScoreNote ?: return null
        if (!source.tieToNext && targetIndex(events, sourceIndex) == null) return null
        return events.toMutableList().apply {
            this[sourceIndex] = source.copy(tieToNext = !source.tieToNext)
        }
    }
}

object PitchNames {
    private val names = arrayOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

    fun name(midiPitch: Int): String {
        val safePitch = midiPitch.coerceIn(0, 127)
        val octave = safePitch / 12 - 1
        return "${names[safePitch % 12]}$octave"
    }

    fun diatonicPosition(midiPitch: Int): Int {
        val safePitch = midiPitch.coerceIn(0, 127)
        val pitchClass = safePitch % 12
        val octave = safePitch / 12 - 1
        val step = when (pitchClass) {
            0, 1 -> 0 // C / C#
            2, 3 -> 1 // D / D#
            4 -> 2 // E
            5, 6 -> 3 // F / F#
            7, 8 -> 4 // G / G#
            9, 10 -> 5 // A / A#
            else -> 6 // B
        }
        return octave * 7 + step
    }

    fun hasSharp(midiPitch: Int): Boolean = when (midiPitch.coerceIn(0, 127) % 12) {
        1, 3, 6, 8, 10 -> true
        else -> false
    }

    /** Sharps the ordinary staff positions C, D, F, G and A; E and B remain natural. */
    fun sharpenIfAvailable(midiPitch: Int): Int {
        val safePitch = midiPitch.coerceIn(0, 127)
        return when (safePitch % 12) {
            0, 2, 5, 7, 9 -> (safePitch + 1).coerceAtMost(127)
            else -> safePitch
        }
    }
}
