from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f"Pattern not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# --- Score model + playback semantics ---
path = "app/src/main/java/com/scoreforge/app/music/ScoreModels.kt"
replace_once(
    path,
    '''enum class NoteDuration(val beats: Float, val displayName: String) {
    WHOLE(4f, "Whole"),
    HALF(2f, "Half"),
    QUARTER(1f, "Quarter"),
    EIGHTH(0.5f, "Eighth"),
    SIXTEENTH(0.25f, "16th");

    fun effectiveBeats(dotted: Boolean): Float = beats * if (dotted) 1.5f else 1f
}
''',
    '''enum class NoteDuration(val beats: Float, val displayName: String) {
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
''',
)
replace_once(
    path,
    '''data class ScoreNote(
    val midiPitch: Int,
    override val duration: NoteDuration,
    override val startBeat: Float = 0f,
    val velocity: Int = 96,
    override val dotted: Boolean = false,
    val tieToNext: Boolean = false,
) : ScoreEvent
''',
    '''data class ScoreNote(
    val midiPitch: Int,
    override val duration: NoteDuration,
    override val startBeat: Float = 0f,
    val velocity: Int = 96,
    override val dotted: Boolean = false,
    val tieToNext: Boolean = false,
    val articulation: NoteArticulation = NoteArticulation.NORMAL,
) : ScoreEvent
''',
)
replace_once(
    path,
    '''data class ScoreRest(
    override val duration: NoteDuration,
    override val startBeat: Float = 0f,
    override val dotted: Boolean = false,
) : ScoreEvent

object ScoreTimeline {
''',
    '''data class ScoreRest(
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
''',
)

# --- Project persistence: optional v2 fields preserve old files ---
path = "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt"
replace_once(
    path,
    '''    val selectedDuration: NoteDuration = NoteDuration.QUARTER,
    val selectedDotted: Boolean = false,
    val pianoOctaveShift: Int = 0,
''',
    '''    val selectedDuration: NoteDuration = NoteDuration.QUARTER,
    val selectedDotted: Boolean = false,
    val selectedArticulation: NoteArticulation = NoteArticulation.NORMAL,
    val pianoOctaveShift: Int = 0,
''',
)
replace_once(
    path,
    '''        append("DURATION\\t").append(snapshot.selectedDuration.name).append('\\n')
        append("DOTTED_INPUT\\t").append(if (snapshot.selectedDotted) 1 else 0).append('\\n')
        append("PIANO_OCTAVE\\t").append(snapshot.pianoOctaveShift.coerceIn(-4, 3)).append('\\n')
''',
    '''        append("DURATION\\t").append(snapshot.selectedDuration.name).append('\\n')
        append("DOTTED_INPUT\\t").append(if (snapshot.selectedDotted) 1 else 0).append('\\n')
        append("ARTICULATION\\t").append(snapshot.selectedArticulation.name).append('\\n')
        append("PIANO_OCTAVE\\t").append(snapshot.pianoOctaveShift.coerceIn(-4, 3)).append('\\n')
''',
)
replace_once(
    path,
    '''                    .append(event.velocity.coerceIn(1, 127)).append('\\t')
                    .append(if (event.dotted) 1 else 0).append('\\t')
                    .append(if (event.tieToNext) 1 else 0).append('\\n')
''',
    '''                    .append(event.velocity.coerceIn(1, 127)).append('\\t')
                    .append(if (event.dotted) 1 else 0).append('\\t')
                    .append(if (event.tieToNext) 1 else 0).append('\\t')
                    .append(event.articulation.name).append('\\n')
''',
)
replace_once(
    path,
    '''        var selectedDuration = NoteDuration.QUARTER
        var selectedDotted = false
        var pianoOctaveShift = 0
''',
    '''        var selectedDuration = NoteDuration.QUARTER
        var selectedDotted = false
        var selectedArticulation = NoteArticulation.NORMAL
        var pianoOctaveShift = 0
''',
)
replace_once(
    path,
    '''                "DURATION" -> parseDuration(parts.getOrNull(1))?.let { selectedDuration = it }
                "DOTTED_INPUT" -> selectedDotted = parts.getOrNull(1) == "1"
                "PIANO_OCTAVE" -> parts.getOrNull(1)?.toIntOrNull()?.let {
''',
    '''                "DURATION" -> parseDuration(parts.getOrNull(1))?.let { selectedDuration = it }
                "DOTTED_INPUT" -> selectedDotted = parts.getOrNull(1) == "1"
                "ARTICULATION" -> parseArticulation(parts.getOrNull(1))?.let {
                    selectedArticulation = it
                }
                "PIANO_OCTAVE" -> parts.getOrNull(1)?.toIntOrNull()?.let {
''',
)
replace_once(
    path,
    '''            selectedDuration = selectedDuration,
            selectedDotted = selectedDotted,
            pianoOctaveShift = pianoOctaveShift,
''',
    '''            selectedDuration = selectedDuration,
            selectedDotted = selectedDotted,
            selectedArticulation = selectedArticulation,
            pianoOctaveShift = pianoOctaveShift,
''',
)
replace_once(
    path,
    '''        val dotted = parts.getOrNull(5) == "1"
        val tieToNext = parts.getOrNull(6) == "1"
        return ScoreNote(pitch, duration, startBeat, velocity, dotted, tieToNext)
''',
    '''        val dotted = parts.getOrNull(5) == "1"
        val tieToNext = parts.getOrNull(6) == "1"
        val articulation = parseArticulation(parts.getOrNull(7)) ?: NoteArticulation.NORMAL
        return ScoreNote(pitch, duration, startBeat, velocity, dotted, tieToNext, articulation)
''',
)
replace_once(
    path,
    '''    private fun parseDuration(value: String?): NoteDuration? =
        NoteDuration.entries.firstOrNull { it.name == value }

    private fun sanitizeTrackName(name: String): String =
''',
    '''    private fun parseDuration(value: String?): NoteDuration? =
        NoteDuration.entries.firstOrNull { it.name == value }

    private fun parseArticulation(value: String?): NoteArticulation? =
        NoteArticulation.entries.firstOrNull { it.name == value }

    private fun sanitizeTrackName(name: String): String =
''',
)

# --- Fallback playback ---
path = "app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt"
replace_once(
    path,
    '''import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreNote
''',
    '''import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreArticulations
import com.scoreforge.app.music.ScoreNote
''',
)
replace_once(
    path,
    '''                val end = ScoreTies.chainEndBeat(notes, index).takeIf { it > note.startBeat }
                    ?: (note.startBeat + note.effectiveBeats)
                renderFallbackNote(
''',
    '''                val end = if (ScoreTies.hasValidTie(notes, index)) {
                    ScoreTies.chainEndBeat(notes, index)
                } else {
                    ScoreArticulations.playbackEndBeat(notes, index)
                }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)
                renderFallbackNote(
''',
)
replace_once(
    path,
    '''        val velocityGain = note.velocity.coerceIn(1, 127) / 127f
''',
    '''        val velocityGain = ScoreArticulations.playbackVelocity(note) / 127f
''',
)
replace_once(
    path,
    '''            val chainEnd = ScoreTies.chainEndBeat(notes, index).takeIf { it > note.startBeat }
                ?: (note.startBeat + note.effectiveBeats)
            val startSample = (note.startBeat * secondsPerBeat * sampleRate).toInt()
''',
    '''            val chainEnd = if (ScoreTies.hasValidTie(notes, index)) {
                ScoreTies.chainEndBeat(notes, index)
            } else {
                ScoreArticulations.playbackEndBeat(notes, index)
            }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)
            val startSample = (note.startBeat * secondsPerBeat * sampleRate).toInt()
''',
)
replace_once(
    path,
    '''            val velocityGain = note.velocity.coerceIn(1, 127) / 127f
''',
    '''            val velocityGain = ScoreArticulations.playbackVelocity(note) / 127f
''',
)

# --- SoundFont playback ---
path = "app/src/main/java/com/scoreforge/app/audio/SoundFontEngine.kt"
replace_once(
    path,
    '''import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTies
''',
    '''import com.scoreforge.app.music.ScoreArticulations
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTies
''',
)
replace_once(
    path,
    '''                    val offFrame = (
                        (note.startBeat + note.effectiveBeats) * secondsPerBeat * sampleRate
                        ).toInt().coerceAtLeast(onFrame + 1)
                    if (!suppressOn) add(MidiEvent(onFrame, true, note.midiPitch, note.velocity, channel))
''',
    '''                    val playbackEndBeat = if (suppressOn || suppressOff) {
                        note.startBeat + note.effectiveBeats
                    } else {
                        ScoreArticulations.playbackEndBeat(notes, index)
                    }
                    val offFrame = (
                        playbackEndBeat * secondsPerBeat * sampleRate
                        ).toInt().coerceAtLeast(onFrame + 1)
                    if (!suppressOn) {
                        add(
                            MidiEvent(
                                onFrame,
                                true,
                                note.midiPitch,
                                ScoreArticulations.playbackVelocity(note),
                                channel,
                            )
                        )
                    }
''',
)

# --- Staff notation marks + legato slurs ---
path = "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
replace_once(
    path,
    '''import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
''',
    '''import com.scoreforge.app.music.NoteArticulation
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
''',
)
replace_once(
    path,
    '''                    events.forEachIndexed { sourceIndex, event ->
                        if (event !is ScoreNote || !ScoreTies.hasValidTie(events, sourceIndex)) {
                            return@forEachIndexed
                        }
                        val targetIndex = ScoreTies.targetIndex(events, sourceIndex)
                            ?: return@forEachIndexed
                        val target = events.getOrNull(targetIndex) as? ScoreNote
                            ?: return@forEachIndexed
                        drawTieCurve(event, target, timelineLeftPx, beatWidthPx, geometry)
                    }

                    val playheadX = StaffTimelineLayout.xAtBeat(
''',
    '''                    events.forEachIndexed { sourceIndex, event ->
                        if (event !is ScoreNote || !ScoreTies.hasValidTie(events, sourceIndex)) {
                            return@forEachIndexed
                        }
                        val targetIndex = ScoreTies.targetIndex(events, sourceIndex)
                            ?: return@forEachIndexed
                        val target = events.getOrNull(targetIndex) as? ScoreNote
                            ?: return@forEachIndexed
                        drawTieCurve(event, target, timelineLeftPx, beatWidthPx, geometry)
                    }

                    events.forEachIndexed { sourceIndex, event ->
                        val source = event as? ScoreNote ?: return@forEachIndexed
                        if (
                            source.articulation != NoteArticulation.LEGATO ||
                            ScoreTies.hasValidTie(events, sourceIndex)
                        ) return@forEachIndexed
                        val target = events
                            .filterIsInstance<ScoreNote>()
                            .asSequence()
                            .filter { it.startBeat > source.startBeat + 0.001f }
                            .minByOrNull { it.startBeat }
                            ?: return@forEachIndexed
                        val writtenEnd = source.startBeat + source.effectiveBeats
                        if (target.startBeat <= writtenEnd + 0.25f) {
                            drawLegatoCurve(source, target, timelineLeftPx, beatWidthPx, geometry)
                        }
                    }

                    val playheadX = StaffTimelineLayout.xAtBeat(
''',
)
replace_once(
    path,
    '''    if (note.dotted) {
        drawCircle(
            Color(0xFF111111),
            maxOf(2.2f, geometry.lineSpacing * 0.07f),
            Offset(x + noteWidth * 0.95f, y),
        )
    }
}

private fun DrawScope.drawTieCurve(
''',
    '''    if (note.dotted) {
        drawCircle(
            Color(0xFF111111),
            maxOf(2.2f, geometry.lineSpacing * 0.07f),
            Offset(x + noteWidth * 0.95f, y),
        )
    }

    drawArticulationMark(note, x, y, noteWidth, geometry)
}

private fun DrawScope.drawArticulationMark(
    note: ScoreNote,
    x: Float,
    y: Float,
    noteWidth: Float,
    geometry: StaffGeometry,
) {
    if (note.articulation == NoteArticulation.NORMAL || note.articulation == NoteArticulation.LEGATO) {
        return
    }
    val below = y >= geometry.middleLine
    val direction = if (below) 1f else -1f
    val markY = y + geometry.lineSpacing * 0.72f * direction
    val ink = Color(0xFF111111)

    when (note.articulation) {
        NoteArticulation.STACCATO -> drawCircle(
            ink,
            maxOf(2.1f, geometry.lineSpacing * 0.065f),
            Offset(x, markY),
        )
        NoteArticulation.TENUTO -> drawLine(
            ink,
            Offset(x - noteWidth * 0.45f, markY),
            Offset(x + noteWidth * 0.45f, markY),
            maxOf(1.5f, geometry.lineSpacing * 0.05f),
        )
        NoteArticulation.ACCENT -> {
            val halfWidth = noteWidth * 0.58f
            val halfHeight = geometry.lineSpacing * 0.14f
            drawLine(
                ink,
                Offset(x - halfWidth, markY - halfHeight),
                Offset(x + halfWidth, markY),
                maxOf(1.5f, geometry.lineSpacing * 0.05f),
            )
            drawLine(
                ink,
                Offset(x - halfWidth, markY + halfHeight),
                Offset(x + halfWidth, markY),
                maxOf(1.5f, geometry.lineSpacing * 0.05f),
            )
        }
        NoteArticulation.NORMAL,
        NoteArticulation.LEGATO -> Unit
    }
}

private fun DrawScope.drawLegatoCurve(
    source: ScoreNote,
    target: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
) {
    val sourceX = StaffTimelineLayout.xAtBeat(
        source.startBeat + 0.16f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val targetX = StaffTimelineLayout.xAtBeat(
        target.startBeat + 0.04f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val sourceY = noteY(source.midiPitch, geometry)
    val targetY = noteY(target.midiPitch, geometry)
    val below = (sourceY + targetY) / 2f >= geometry.middleLine
    val baseline = if (below) {
        maxOf(sourceY, targetY) + geometry.lineSpacing * 0.62f
    } else {
        minOf(sourceY, targetY) - geometry.lineSpacing * 0.62f
    }
    val controlY = baseline + geometry.lineSpacing * if (below) 0.68f else -0.68f
    val path = Path().apply {
        moveTo(sourceX, baseline)
        quadraticBezierTo((sourceX + targetX) / 2f, controlY, targetX, baseline)
    }
    drawPath(
        path,
        Color(0xFF111111),
        style = Stroke(width = maxOf(1.5f, geometry.lineSpacing * 0.05f)),
    )
}

private fun DrawScope.drawTieCurve(
''',
)

# --- Piano bottom strip articulation palette ---
path = "app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt"
replace_once(
    path,
    '''import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
''',
    '''import com.scoreforge.app.music.NoteArticulation
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
''',
)
replace_once(
    path,
    '''    selectedDuration: NoteDuration,
    selectedDotted: Boolean,
    tieEnabled: Boolean,
''',
    '''    selectedDuration: NoteDuration,
    selectedDotted: Boolean,
    selectedArticulation: NoteArticulation,
    tieEnabled: Boolean,
''',
)
replace_once(
    path,
    '''    onDurationSelected: (NoteDuration) -> Unit,
    onToggleDotted: () -> Unit,
    onToggleTie: () -> Unit,
''',
    '''    onDurationSelected: (NoteDuration) -> Unit,
    onToggleDotted: () -> Unit,
    onArticulationSelected: (NoteArticulation) -> Unit,
    onToggleTie: () -> Unit,
''',
)
replace_once(
    path,
    '''    var durationPaletteOpen by remember { mutableStateOf(false) }
''',
    '''    var durationPaletteOpen by remember { mutableStateOf(false) }
    var articulationPaletteOpen by remember { mutableStateOf(false) }
''',
)
replace_once(
    path,
    '''            if (durationPaletteOpen) {
''',
    '''            if (durationPaletteOpen) {
''',
)
replace_once(
    path,
    '''                ChamferedControlButton(
                    label = if (tieActive) "Tie On" else "Tie",
                    onClick = {
                        releaseAllPitches()
                        onToggleTie()
                    },
                    selected = tieActive,
                    enabled = tieEnabled,
                )
            } else {
''',
    '''                ChamferedControlButton(
                    label = if (tieActive) "Tie On" else "Tie",
                    onClick = {
                        releaseAllPitches()
                        onToggleTie()
                    },
                    selected = tieActive,
                    enabled = tieEnabled,
                )
            } else if (articulationPaletteOpen) {
                ChamferedControlButton(
                    label = "← Back",
                    onClick = {
                        releaseAllPitches()
                        articulationPaletteOpen = false
                    },
                )
                NoteArticulation.entries.forEach { articulation ->
                    ChamferedControlButton(
                        label = articulationControlLabel(articulation, includePrefix = false),
                        onClick = {
                            releaseAllPitches()
                            onArticulationSelected(articulation)
                        },
                        selected = selectedArticulation == articulation,
                    )
                }
            } else {
''',
)
replace_once(
    path,
    '''                ChamferedControlButton(
                    label = durationControlLabel(selectedDuration, selectedDotted),
                    onClick = {
                        releaseAllPitches()
                        durationPaletteOpen = true
                    },
                )

                ChamferedControlButton(
                    label = "Rest",
''',
    '''                ChamferedControlButton(
                    label = durationControlLabel(selectedDuration, selectedDotted),
                    onClick = {
                        releaseAllPitches()
                        articulationPaletteOpen = false
                        durationPaletteOpen = true
                    },
                )

                ChamferedControlButton(
                    label = articulationControlLabel(selectedArticulation, includePrefix = true),
                    onClick = {
                        releaseAllPitches()
                        durationPaletteOpen = false
                        articulationPaletteOpen = true
                    },
                )

                ChamferedControlButton(
                    label = "Rest",
''',
)
replace_once(
    path,
    '''private fun durationControlLabel(duration: NoteDuration, dotted: Boolean): String {
''',
    '''private fun articulationControlLabel(
    articulation: NoteArticulation,
    includePrefix: Boolean,
): String {
    val glyph = when (articulation) {
        NoteArticulation.NORMAL -> ""
        NoteArticulation.STACCATO -> "• "
        NoteArticulation.TENUTO -> "— "
        NoteArticulation.ACCENT -> "> "
        NoteArticulation.LEGATO -> "⌒ "
    }
    val core = "$glyph${articulation.displayName}"
    return if (includePrefix) "Art $core" else core
}

private fun durationControlLabel(duration: NoteDuration, dotted: Boolean): String {
''',
)

# --- Composer state + all note entry paths ---
path = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    path,
    '''import com.scoreforge.app.music.NaturalEntryTiming
import com.scoreforge.app.music.NoteDuration
''',
    '''import com.scoreforge.app.music.NaturalEntryTiming
import com.scoreforge.app.music.NoteArticulation
import com.scoreforge.app.music.NoteDuration
''',
)
replace_once(
    path,
    '''    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var selectedDotted by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(120) }
''',
    '''    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var selectedDotted by remember { mutableStateOf(false) }
    var selectedArticulation by remember { mutableStateOf(NoteArticulation.NORMAL) }
    var bpm by remember { mutableIntStateOf(120) }
''',
)
replace_once(
    path,
    '''            selectedDuration = selectedDuration,
            selectedDotted = selectedDotted,
            pianoOctaveShift = pianoOctaveShift,
''',
    '''            selectedDuration = selectedDuration,
            selectedDotted = selectedDotted,
            selectedArticulation = selectedArticulation,
            pianoOctaveShift = pianoOctaveShift,
''',
)
replace_once(
    path,
    '''        selectedDuration = snapshot.selectedDuration
        selectedDotted = snapshot.selectedDotted
        pianoOctaveShift = snapshot.pianoOctaveShift.coerceIn(-4, 3)
''',
    '''        selectedDuration = snapshot.selectedDuration
        selectedDotted = snapshot.selectedDotted
        selectedArticulation = snapshot.selectedArticulation
        pianoOctaveShift = snapshot.pianoOctaveShift.coerceIn(-4, 3)
''',
)
replace_once(
    path,
    '''        selectedDuration,
        selectedDotted,
        pianoOctaveShift,
''',
    '''        selectedDuration,
        selectedDotted,
        selectedArticulation,
        pianoOctaveShift,
''',
)
replace_once(
    path,
    '''                selectedDuration = NoteDuration.QUARTER,
                selectedDotted = false,
                pianoOctaveShift = 0,
''',
    '''                selectedDuration = NoteDuration.QUARTER,
                selectedDotted = false,
                selectedArticulation = NoteArticulation.NORMAL,
                pianoOctaveShift = 0,
''',
)
# Live entry note
replace_once(
    path,
    '''                    duration = NoteDuration.SIXTEENTH,
                    startBeat = noteStartBeat,
                    dotted = false,
                ),
''',
    '''                    duration = NoteDuration.SIXTEENTH,
                    startBeat = noteStartBeat,
                    dotted = false,
                    articulation = selectedArticulation,
                ),
''',
)
# Step/staff/piano-roll entry note
replace_once(
    path,
    '''            duration = selectedDuration,
            startBeat = quantizedStart,
            dotted = selectedDotted,
        )
''',
    '''            duration = selectedDuration,
            startBeat = quantizedStart,
            dotted = selectedDotted,
            articulation = selectedArticulation,
        )
''',
)
# Natural entry note
replace_once(
    path,
    '''                    duration = duration,
                    startBeat = groupStart,
                    dotted = false,
                ),
''',
    '''                    duration = duration,
                    startBeat = groupStart,
                    dotted = false,
                    articulation = selectedArticulation,
                ),
''',
)
replace_once(
    path,
    '''                        selectedDuration = selectedDuration,
                        selectedDotted = selectedDotted,
                        tieEnabled = canTieSelected,
''',
    '''                        selectedDuration = selectedDuration,
                        selectedDotted = selectedDotted,
                        selectedArticulation = selectedArticulation,
                        tieEnabled = canTieSelected,
''',
)
replace_once(
    path,
    '''                        onDurationSelected = { selectedDuration = it },
                        onToggleDotted = { selectedDotted = !selectedDotted },
                        onToggleTie = ::toggleSelectedTie,
''',
    '''                        onDurationSelected = { selectedDuration = it },
                        onToggleDotted = { selectedDotted = !selectedDotted },
                        onArticulationSelected = { selectedArticulation = it },
                        onToggleTie = ::toggleSelectedTie,
''',
)

# --- Version bump ---
path = "app/build.gradle.kts"
replace_once(path, 'versionCode = 13\n        versionName = "0.2.11"', 'versionCode = 14\n        versionName = "0.2.12"')

# --- Regression tests ---
Path("app/src/test/java/com/scoreforge/app/music/ScoreArticulationTest.kt").write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreArticulationTest {
    @Test
    fun staccatoShortensPlaybackWithoutChangingWrittenDuration() {
        val note = ScoreNote(
            midiPitch = 60,
            duration = NoteDuration.QUARTER,
            articulation = NoteArticulation.STACCATO,
        )
        val notes = listOf(note)

        assertEquals(1f, note.effectiveBeats, 0.0001f)
        assertEquals(0.5f, ScoreArticulations.playbackEndBeat(notes, 0), 0.0001f)
    }

    @Test
    fun accentRaisesPlaybackVelocityAndUsesShorterGate() {
        val note = ScoreNote(
            midiPitch = 64,
            duration = NoteDuration.QUARTER,
            velocity = 96,
            articulation = NoteArticulation.ACCENT,
        )
        val notes = listOf(note)

        assertEquals(118, ScoreArticulations.playbackVelocity(note))
        assertEquals(0.9f, ScoreArticulations.playbackEndBeat(notes, 0), 0.0001f)
    }

    @Test
    fun legatoBridgesAnAdjacentDifferentPitch() {
        val notes = listOf(
            ScoreNote(
                midiPitch = 60,
                duration = NoteDuration.QUARTER,
                startBeat = 0f,
                articulation = NoteArticulation.LEGATO,
            ),
            ScoreNote(62, NoteDuration.QUARTER, startBeat = 1f),
        )

        assertEquals(1.08f, ScoreArticulations.playbackEndBeat(notes, 0), 0.0001f)
    }

    @Test
    fun codecPreservesInputAndPerNoteArticulation() {
        val note = ScoreNote(
            midiPitch = 67,
            duration = NoteDuration.HALF,
            articulation = NoteArticulation.STACCATO,
        )
        val original = ScoreProjectSnapshot(
            events = listOf(note),
            selectedArticulation = NoteArticulation.LEGATO,
        )

        val decoded = requireNotNull(ScoreProjectCodec.decode(ScoreProjectCodec.encode(original)))
        assertEquals(NoteArticulation.LEGATO, decoded.selectedArticulation)
        assertEquals(
            NoteArticulation.STACCATO,
            (decoded.tracks.single().events.single() as ScoreNote).articulation,
        )
    }

    @Test
    fun olderVersionTwoNoteDefaultsToNormalArticulation() {
        val decoded = requireNotNull(
            ScoreProjectCodec.decode(
                """
                SCOREFORGE\\t2
                BPM\\t120
                ACTIVE_TRACK\\t0
                TRACK\\t1\\tTrack 1\\t1.0\\t0\\t-1\\t-1
                N\\t60\\tQUARTER\\t0.0\\t96
                END_TRACK
                """.trimIndent().replace("\\\\t", "\\t")
            )
        )

        assertEquals(NoteArticulation.NORMAL, decoded.selectedArticulation)
        assertEquals(
            NoteArticulation.NORMAL,
            (decoded.tracks.single().events.single() as ScoreNote).articulation,
        )
    }
}
''')

print("Articulation patch applied")
