package com.scoreforge.app.music

import android.content.Context
import java.io.File

/**
 * Small versioned snapshot used for the current automatic draft and user-saved .sfp files.
 *
 * [events] and [cursorBeat] remain as compatibility fields for older editor code and tests. New
 * code should use [tracks] and [activeTrackIndex]. The encoder writes the multi-track v2 format,
 * while the decoder still migrates v1 drafts into Track 1. New optional v2 fields are deliberately
 * backward-compatible so existing v2 files continue to open.
 */
data class ScoreProjectSnapshot(
    val events: List<ScoreEvent>,
    val bpm: Int = 120,
    val cursorBeat: Float = ScoreTimeline.endBeat(events),
    val selectedDuration: NoteDuration = NoteDuration.QUARTER,
    val pianoOctaveShift: Int = 0,
    val staffSharpInput: Boolean = false,
    val tracks: List<ScoreTrack> = listOf(
        ScoreTrack(
            id = 1,
            name = "Track 1",
            events = events,
            cursorBeat = cursorBeat,
        )
    ),
    val activeTrackIndex: Int = 0,
    val projectName: String = "Untitled",
) {
    fun effectiveTracks(): List<ScoreTrack> =
        tracks.takeIf { it.isNotEmpty() }
            ?.take(ScoreTracks.MAX_TRACKS)
            ?.map { it.normalized() }
            ?: listOf(
                ScoreTrack(
                    id = 1,
                    name = "Track 1",
                    events = events,
                    cursorBeat = cursorBeat,
                ).normalized()
            )

    fun effectiveActiveTrackIndex(): Int =
        activeTrackIndex.coerceIn(0, effectiveTracks().lastIndex)

    fun safeProjectName(): String = cleanProjectName(projectName)

    companion object {
        fun sanitizeProjectName(name: String): String = cleanProjectName(name)
    }
}

private fun cleanProjectName(name: String): String =
    name.replace('\t', ' ')
        .replace('\n', ' ')
        .replace('\r', ' ')
        .trim()
        .ifBlank { "Untitled" }
        .take(120)

object ScoreProjectCodec {
    private const val MAGIC = "SCOREFORGE"
    private const val VERSION = 2
    private const val LEGACY_VERSION = 1
    private const val NO_PRESET = -1

    fun encode(snapshot: ScoreProjectSnapshot): String = buildString {
        val tracks = snapshot.effectiveTracks()
        val activeTrackIndex = snapshot.activeTrackIndex.coerceIn(0, tracks.lastIndex)

        append(MAGIC).append('\t').append(VERSION).append('\n')
        append("PROJECT_NAME\t").append(snapshot.safeProjectName()).append('\n')
        append("BPM\t").append(snapshot.bpm.coerceIn(30, 300)).append('\n')
        append("DURATION\t").append(snapshot.selectedDuration.name).append('\n')
        append("PIANO_OCTAVE\t").append(snapshot.pianoOctaveShift.coerceIn(-4, 3)).append('\n')
        append("STAFF_SHARP\t").append(if (snapshot.staffSharpInput) 1 else 0).append('\n')
        append("ACTIVE_TRACK\t").append(activeTrackIndex).append('\n')

        tracks.forEach { track ->
            val safeTrack = track.normalized()
            append("TRACK\t")
                .append(safeTrack.id).append('\t')
                .append(sanitizeTrackName(safeTrack.name)).append('\t')
                .append(safeTrack.cursorBeat).append('\t')
                .append(if (safeTrack.muted) 1 else 0).append('\t')
                .append(safeTrack.presetBank ?: NO_PRESET).append('\t')
                .append(safeTrack.presetProgram ?: NO_PRESET).append('\t')
                .append(if (safeTrack.solo) 1 else 0).append('\t')
                .append(safeTrack.volume).append('\t')
                .append(safeTrack.pan).append('\n')

            appendEvents(safeTrack.events)
            append("END_TRACK\n")
        }
    }

    private fun StringBuilder.appendEvents(events: List<ScoreEvent>) {
        events.forEach { event ->
            when (event) {
                is ScoreNote -> append("N\t")
                    .append(event.midiPitch.coerceIn(0, 127)).append('\t')
                    .append(event.duration.name).append('\t')
                    .append(event.startBeat.coerceAtLeast(0f)).append('\t')
                    .append(event.velocity.coerceIn(1, 127)).append('\n')

                is ScoreRest -> append("R\t")
                    .append(event.duration.name).append('\t')
                    .append(event.startBeat.coerceAtLeast(0f)).append('\n')
            }
        }
    }

    fun decode(raw: String): ScoreProjectSnapshot? {
        val lines = raw.lineSequence().filter { it.isNotBlank() }.toList()
        val header = lines.firstOrNull()?.split('\t') ?: return null
        if (header.size != 2 || header[0] != MAGIC) return null

        return when (header[1].toIntOrNull()) {
            VERSION -> decodeV2(lines.drop(1))
            LEGACY_VERSION -> decodeV1(lines.drop(1))
            else -> null
        }
    }

    private fun decodeV2(lines: List<String>): ScoreProjectSnapshot {
        var projectName = "Untitled"
        var bpm = 120
        var selectedDuration = NoteDuration.QUARTER
        var pianoOctaveShift = 0
        var staffSharpInput = false
        var activeTrackIndex = 0
        val tracks = mutableListOf<ScoreTrack>()
        var trackBuilder: TrackBuilder? = null

        fun finishTrack() {
            val builder = trackBuilder ?: return
            if (tracks.size < ScoreTracks.MAX_TRACKS) tracks.add(builder.build().normalized())
            trackBuilder = null
        }

        lines.forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "PROJECT_NAME" -> projectName = cleanProjectName(parts.getOrNull(1).orEmpty())
                "BPM" -> parts.getOrNull(1)?.toIntOrNull()?.let { bpm = it.coerceIn(30, 300) }
                "DURATION" -> parseDuration(parts.getOrNull(1))?.let { selectedDuration = it }
                "PIANO_OCTAVE" -> parts.getOrNull(1)?.toIntOrNull()?.let {
                    pianoOctaveShift = it.coerceIn(-4, 3)
                }
                "STAFF_SHARP" -> staffSharpInput = parts.getOrNull(1) == "1"
                "ACTIVE_TRACK" -> parts.getOrNull(1)?.toIntOrNull()?.let {
                    activeTrackIndex = it.coerceAtLeast(0)
                }
                "TRACK" -> {
                    finishTrack()
                    trackBuilder = decodeTrackHeader(parts)
                }
                "END_TRACK" -> finishTrack()
                "N" -> decodeNote(parts)?.let { trackBuilder?.events?.add(it) }
                "R" -> decodeRest(parts)?.let { trackBuilder?.events?.add(it) }
            }
        }
        finishTrack()

        val safeTracks = tracks.ifEmpty { mutableListOf(ScoreTracks.defaultTrack()) }
        val safeActiveIndex = activeTrackIndex.coerceIn(0, safeTracks.lastIndex)
        val active = safeTracks[safeActiveIndex]

        return ScoreProjectSnapshot(
            events = active.events,
            bpm = bpm,
            cursorBeat = active.cursorBeat,
            selectedDuration = selectedDuration,
            pianoOctaveShift = pianoOctaveShift,
            staffSharpInput = staffSharpInput,
            tracks = safeTracks,
            activeTrackIndex = safeActiveIndex,
            projectName = projectName,
        )
    }

    /** Migrates the original one-staff project format into a single track. */
    private fun decodeV1(lines: List<String>): ScoreProjectSnapshot {
        var bpm = 120
        var cursorBeat = 0f
        var selectedDuration = NoteDuration.QUARTER
        var pianoOctaveShift = 0
        var staffSharpInput = false
        val events = mutableListOf<ScoreEvent>()

        lines.forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "BPM" -> parts.getOrNull(1)?.toIntOrNull()?.let { bpm = it.coerceIn(30, 300) }
                "CURSOR" -> parts.getOrNull(1)?.toFloatOrNull()?.let {
                    cursorBeat = it.coerceAtLeast(0f)
                }
                "DURATION" -> parseDuration(parts.getOrNull(1))?.let { selectedDuration = it }
                "PIANO_OCTAVE" -> parts.getOrNull(1)?.toIntOrNull()?.let {
                    pianoOctaveShift = it.coerceIn(-4, 3)
                }
                "STAFF_SHARP" -> staffSharpInput = parts.getOrNull(1) == "1"
                "N" -> decodeNote(parts)?.let(events::add)
                "R" -> decodeRest(parts)?.let(events::add)
            }
        }

        cursorBeat = maxOf(cursorBeat, ScoreTimeline.endBeat(events))
        val track = ScoreTrack(
            id = 1,
            name = "Track 1",
            events = events,
            cursorBeat = cursorBeat,
        )

        return ScoreProjectSnapshot(
            events = events,
            bpm = bpm,
            cursorBeat = cursorBeat,
            selectedDuration = selectedDuration,
            pianoOctaveShift = pianoOctaveShift,
            staffSharpInput = staffSharpInput,
            tracks = listOf(track),
            activeTrackIndex = 0,
            projectName = "Untitled",
        )
    }

    private fun decodeTrackHeader(parts: List<String>): TrackBuilder? {
        if (parts.size < 7) return null
        val id = parts[1].toIntOrNull()?.coerceAtLeast(1) ?: return null
        val name = parts[2].ifBlank { "Track $id" }
        val cursorBeat = parts[3].toFloatOrNull()?.coerceAtLeast(0f) ?: 0f
        val muted = parts[4] == "1"
        val bank = parts[5].toIntOrNull()?.takeIf { it >= 0 }
        val program = parts[6].toIntOrNull()?.takeIf { it in 0..127 }
        val solo = parts.getOrNull(7) == "1"
        val volume = parts.getOrNull(8)?.toIntOrNull() ?: ScoreTrack.DEFAULT_VOLUME
        val pan = parts.getOrNull(9)?.toIntOrNull() ?: ScoreTrack.CENTER_PAN
        return TrackBuilder(id, name, cursorBeat, bank, program, muted, solo, volume, pan)
    }

    private fun decodeNote(parts: List<String>): ScoreNote? {
        if (parts.size < 5) return null
        val pitch = parts[1].toIntOrNull()?.takeIf { it in 0..127 } ?: return null
        val duration = parseDuration(parts[2]) ?: return null
        val startBeat = parts[3].toFloatOrNull()?.takeIf { it >= 0f } ?: return null
        val velocity = parts[4].toIntOrNull()?.takeIf { it in 1..127 } ?: return null
        return ScoreNote(pitch, duration, startBeat, velocity)
    }

    private fun decodeRest(parts: List<String>): ScoreRest? {
        if (parts.size < 3) return null
        val duration = parseDuration(parts[1]) ?: return null
        val startBeat = parts[2].toFloatOrNull()?.takeIf { it >= 0f } ?: return null
        return ScoreRest(duration, startBeat)
    }

    private fun parseDuration(value: String?): NoteDuration? =
        NoteDuration.entries.firstOrNull { it.name == value }

    private fun sanitizeTrackName(name: String): String =
        name.replace('\t', ' ').replace('\n', ' ').trim().ifBlank { "Track" }.take(80)

    private data class TrackBuilder(
        val id: Int,
        val name: String,
        val cursorBeat: Float,
        val presetBank: Int?,
        val presetProgram: Int?,
        val muted: Boolean,
        val solo: Boolean,
        val volume: Int,
        val pan: Int,
        val events: MutableList<ScoreEvent> = mutableListOf(),
    ) {
        fun build(): ScoreTrack = ScoreTrack(
            id = id,
            name = name,
            events = events.toList(),
            cursorBeat = maxOf(cursorBeat, ScoreTimeline.endBeat(events)),
            presetBank = presetBank,
            presetProgram = presetProgram,
            muted = muted,
            solo = solo,
            volume = volume,
            pan = pan,
        )
    }
}

object ScoreProjectRepository {
    private const val PROJECTS_FOLDER = "projects"
    private const val DRAFT_FILE = "autosave.sfp"

    fun saveDraft(context: Context, snapshot: ScoreProjectSnapshot): Result<Unit> = runCatching {
        val folder = File(context.filesDir, PROJECTS_FOLDER).apply { mkdirs() }
        val destination = File(folder, DRAFT_FILE)
        val temporary = File(folder, "$DRAFT_FILE.tmp")
        temporary.writeText(ScoreProjectCodec.encode(snapshot))
        temporary.copyTo(destination, overwrite = true)
        temporary.delete()
    }

    fun loadDraft(context: Context): ScoreProjectSnapshot? {
        val file = File(File(context.filesDir, PROJECTS_FOLDER), DRAFT_FILE)
        if (!file.isFile) return null
        return runCatching { ScoreProjectCodec.decode(file.readText()) }.getOrNull()
    }
}
