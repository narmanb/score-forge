package com.scoreforge.app.music

/**
 * One editable musical track/staff.
 *
 * Preset bank/program refer to the currently loaded SoundFont. Keeping the reference as numbers
 * avoids coupling the music model to the Android/native audio classes and lets the project format
 * survive even when the SoundFont itself is temporarily unavailable.
 */
data class ScoreTrack(
    val id: Int,
    val name: String,
    val events: List<ScoreEvent> = emptyList(),
    val cursorBeat: Float = ScoreTimeline.endBeat(events),
    val presetBank: Int? = null,
    val presetProgram: Int? = null,
    val muted: Boolean = false,
) {
    val notes: List<ScoreNote>
        get() = events.filterIsInstance<ScoreNote>()

    val endBeat: Float
        get() = maxOf(cursorBeat, ScoreTimeline.endBeat(events))

    fun normalized(): ScoreTrack = copy(
        id = id.coerceAtLeast(1),
        name = name.replace('\t', ' ').replace('\n', ' ').trim().ifBlank { "Track $id" }.take(80),
        events = events.toList(),
        cursorBeat = maxOf(cursorBeat.coerceAtLeast(0f), ScoreTimeline.endBeat(events)),
        presetBank = presetBank?.coerceAtLeast(0),
        presetProgram = presetProgram?.coerceIn(0, 127),
    )
}

object ScoreTracks {
    const val MAX_TRACKS = 16

    fun defaultTrack(): ScoreTrack = ScoreTrack(id = 1, name = "Track 1")

    fun nextId(tracks: List<ScoreTrack>): Int =
        ((tracks.maxOfOrNull { it.id } ?: 0) + 1).coerceAtLeast(1)

    fun newTrack(tracks: List<ScoreTrack>): ScoreTrack {
        val id = nextId(tracks)
        return ScoreTrack(id = id, name = "Track $id")
    }

    fun endBeat(tracks: List<ScoreTrack>): Float =
        tracks.filterNot { it.muted }.maxOfOrNull { ScoreTimeline.endBeat(it.events) } ?: 0f

    fun allNotes(tracks: List<ScoreTrack>): List<ScoreNote> =
        tracks.filterNot { it.muted }.flatMap { it.notes }
}
