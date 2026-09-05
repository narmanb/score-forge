package com.scoreforge.app.music

enum class ScoreClefMode(val displayName: String) {
    AUTO("Auto"),
    TREBLE("Treble"),
    BASS("Bass"),
}

enum class ScoreClef(val displayName: String) {
    TREBLE("Treble"),
    BASS("Bass"),
}

/** Staff-clef selection. Auto deliberately chooses one clef for the whole active track. */
object ScoreClefs {
    private const val AUTO_BASS_MAX_MEDIAN_PITCH = 59 // B3 and below favors bass; C4+ favors treble.

    fun effective(mode: ScoreClefMode, events: List<ScoreEvent>): ScoreClef = when (mode) {
        ScoreClefMode.TREBLE -> ScoreClef.TREBLE
        ScoreClefMode.BASS -> ScoreClef.BASS
        ScoreClefMode.AUTO -> autoFor(events)
    }

    fun autoFor(events: List<ScoreEvent>): ScoreClef {
        val pitches = events.filterIsInstance<ScoreNote>().map { it.midiPitch }.sorted()
        if (pitches.isEmpty()) return ScoreClef.TREBLE
        val middle = pitches.size / 2
        val median = if (pitches.size % 2 == 1) {
            pitches[middle]
        } else {
            (pitches[middle - 1] + pitches[middle]) / 2
        }
        return if (median <= AUTO_BASS_MAX_MEDIAN_PITCH) ScoreClef.BASS else ScoreClef.TREBLE
    }

    /** Diatonic position of the bottom staff line: E4 for treble, G2 for bass. */
    fun bottomLineDiatonic(clef: ScoreClef): Int = when (clef) {
        ScoreClef.TREBLE -> 4 * 7 + 2 // E4
        ScoreClef.BASS -> 2 * 7 + 4 // G2
    }
}
