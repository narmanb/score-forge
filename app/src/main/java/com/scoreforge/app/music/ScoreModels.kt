package com.scoreforge.app.music

enum class NoteDuration(val beats: Float, val displayName: String) {
    WHOLE(4f, "Whole"),
    HALF(2f, "Half"),
    QUARTER(1f, "Quarter"),
    EIGHTH(0.5f, "Eighth"),
    SIXTEENTH(0.25f, "16th")
}

data class ScoreNote(
    val midiPitch: Int,
    val duration: NoteDuration,
    val velocity: Int = 96,
)

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
}
