package com.scoreforge.app.music

import android.content.Context
import java.io.File

/**
 * Small versioned snapshot used for the current automatic draft.
 *
 * This is intentionally separate from future interchange formats such as MIDI and MusicXML.
 * Keeping the codec pure Kotlin also makes migrations and corruption tests cheap.
 */
data class ScoreProjectSnapshot(
    val events: List<ScoreEvent>,
    val bpm: Int = 120,
    val cursorBeat: Float = ScoreTimeline.endBeat(events),
    val selectedDuration: NoteDuration = NoteDuration.QUARTER,
)

object ScoreProjectCodec {
    private const val MAGIC = "SCOREFORGE"
    private const val VERSION = 1

    fun encode(snapshot: ScoreProjectSnapshot): String = buildString {
        append(MAGIC).append('\t').append(VERSION).append('\n')
        append("BPM\t").append(snapshot.bpm.coerceIn(30, 300)).append('\n')
        append("CURSOR\t").append(snapshot.cursorBeat.coerceAtLeast(0f)).append('\n')
        append("DURATION\t").append(snapshot.selectedDuration.name).append('\n')

        snapshot.events.forEach { event ->
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
        if (header.size != 2 || header[0] != MAGIC || header[1].toIntOrNull() != VERSION) return null

        var bpm = 120
        var cursorBeat = 0f
        var selectedDuration = NoteDuration.QUARTER
        val events = mutableListOf<ScoreEvent>()

        lines.drop(1).forEach { line ->
            val parts = line.split('\t')
            when (parts.firstOrNull()) {
                "BPM" -> parts.getOrNull(1)?.toIntOrNull()?.let { bpm = it.coerceIn(30, 300) }
                "CURSOR" -> parts.getOrNull(1)?.toFloatOrNull()?.let {
                    cursorBeat = it.coerceAtLeast(0f)
                }
                "DURATION" -> parseDuration(parts.getOrNull(1))?.let { selectedDuration = it }
                "N" -> decodeNote(parts)?.let(events::add)
                "R" -> decodeRest(parts)?.let(events::add)
            }
        }

        return ScoreProjectSnapshot(
            events = events,
            bpm = bpm,
            cursorBeat = maxOf(cursorBeat, ScoreTimeline.endBeat(events)),
            selectedDuration = selectedDuration,
        )
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
