package com.scoreforge.app.music

/**
 * One editable musical track/staff.
 *
 * Preset bank/program refer to the currently loaded SoundFont. Keeping the reference as numbers
 * avoids coupling the music model to the Android/native audio classes and lets the project format
 * survive even when the SoundFont itself is temporarily unavailable.
 *
 * Mixer values deliberately mirror MIDI conventions: volume is 0..127 and pan is -64..63.
 * [timeSignatures] mirrors project meter metadata so the current Compose editor can carry it through
 * track copies without silently dropping imported meter changes before the dedicated meter UI lands.
 */
data class ScoreTrack(
    val id: Int,
    val name: String,
    val events: List<ScoreEvent> = emptyList(),
    val cursorBeat: Float = ScoreTimeline.endBeat(events),
    val presetBank: Int? = null,
    val presetProgram: Int? = null,
    val muted: Boolean = false,
    val solo: Boolean = false,
    val volume: Int = DEFAULT_VOLUME,
    val pan: Int = CENTER_PAN,
    val timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),
) {
    val notes: List<ScoreNote>
        get() = events.filterIsInstance<ScoreNote>()

    val endBeat: Float
        get() = maxOf(cursorBeat, ScoreTimeline.endBeat(events))

    fun normalized(): ScoreTrack {
        val safeEvents = sanitizeScoreTies(events.toList())
        return copy(
            id = id.coerceAtLeast(1),
            name = name.replace('\t', ' ').replace('\n', ' ').trim().ifBlank { "Track $id" }.take(80),
            events = safeEvents,
            cursorBeat = cursorBeat.coerceAtLeast(0f),
            presetBank = presetBank?.coerceAtLeast(0),
            presetProgram = presetProgram?.coerceIn(0, 127),
            volume = volume.coerceIn(MIN_VOLUME, MAX_VOLUME),
            pan = pan.coerceIn(MIN_PAN, MAX_PAN),
            timeSignatures = ScoreTimeSignatures.normalize(timeSignatures),
        )
    }

    companion object {
        const val MIN_VOLUME = 0
        const val MAX_VOLUME = 127
        const val DEFAULT_VOLUME = 100
        const val MIN_PAN = -64
        const val MAX_PAN = 63
        const val CENTER_PAN = 0
    }
}

object ScoreTracks {
    const val MAX_TRACKS = 16

    fun defaultTrack(): ScoreTrack = ScoreTrack(id = 1, name = "Track 1")

    fun nextId(tracks: List<ScoreTrack>): Int =
        ((tracks.maxOfOrNull { it.id } ?: 0) + 1).coerceAtLeast(1)

    fun newTrack(tracks: List<ScoreTrack>): ScoreTrack {
        val id = nextId(tracks)
        val inheritedTimeSignatures = tracks.firstOrNull()?.timeSignatures
            ?: listOf(ScoreTimeSignatures.DEFAULT)
        return ScoreTrack(
            id = id,
            name = "Track $id",
            timeSignatures = inheritedTimeSignatures,
        )
    }

    fun audibleTracks(tracks: List<ScoreTrack>): List<ScoreTrack> {
        val unmuted = tracks.filterNot { it.muted }.take(MAX_TRACKS)
        return if (unmuted.any { it.solo }) unmuted.filter { it.solo } else unmuted
    }

    fun endBeat(tracks: List<ScoreTrack>): Float =
        audibleTracks(tracks).maxOfOrNull { ScoreTimeline.endBeat(it.events) } ?: 0f

    fun allNotes(tracks: List<ScoreTrack>): List<ScoreNote> =
        audibleTracks(tracks).flatMap { it.notes }
}
