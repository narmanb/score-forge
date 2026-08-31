package com.scoreforge.app.music

import kotlin.math.ceil
import kotlin.math.round

enum class NoteDuration(val beats: Float, val displayName: String) {
    WHOLE(4f, "Whole"),
    HALF(2f, "Half"),
    QUARTER(1f, "Quarter"),
    EIGHTH(0.5f, "Eighth"),
    SIXTEENTH(0.25f, "16th");

    fun effectiveBeats(dotted: Boolean): Float = beats * if (dotted) 1.5f else 1f
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
) : ScoreEvent

data class ScoreRest(
    override val duration: NoteDuration,
    override val startBeat: Float = 0f,
    override val dotted: Boolean = false,
) : ScoreEvent

object ScoreTimeline {
    const val BEATS_PER_MEASURE = 4f
    const val EDIT_GRID_BEATS = 0.25f

    fun endBeat(events: List<ScoreEvent>): Float =
        events.maxOfOrNull { it.startBeat + it.effectiveBeats } ?: 0f

    fun nextBeat(events: List<ScoreEvent>): Float = endBeat(events)

    fun measureCount(events: List<ScoreEvent>, throughBeat: Float = endBeat(events)): Int {
        val furthestBeat = maxOf(endBeat(events), throughBeat, BEATS_PER_MEASURE)
        return ceil(furthestBeat / BEATS_PER_MEASURE).toInt()
    }

    fun visibleBeats(
        events: List<ScoreEvent>,
        minimumMeasures: Int = 4,
        throughBeat: Float = endBeat(events),
    ): Float {
        val measures = maxOf(measureCount(events, throughBeat), minimumMeasures)
        return measures * BEATS_PER_MEASURE
    }

    fun quantizeBeat(beat: Float, gridBeats: Float = EDIT_GRID_BEATS): Float {
        if (gridBeats <= 0f) return beat.coerceAtLeast(0f)
        return (round(beat.coerceAtLeast(0f) / gridBeats) * gridBeats).coerceAtLeast(0f)
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
