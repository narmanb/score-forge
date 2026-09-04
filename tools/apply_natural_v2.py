from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
PIANO = ROOT / "app/src/main/java/com/scoreforge/app/ui/PianoRollEditor.kt"
BUILD = ROOT / "app/build.gradle.kts"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"{label}: expected one match, found {text.count(old)}")
    return text.replace(old, new, 1)


composer = COMPOSER.read_text()
composer = replace_once(
    composer,
    '''private data class NaturalHeldInput(
    val startedAtMs: Long,
    val bpmAtPress: Int,
)
''',
    '''private data class NaturalHeldInput(
    val startedAtMs: Long,
    val bpmAtPress: Int,
    val eventIndex: Int,
    val groupOnsetMs: Long,
)

private data class NaturalOnsetGroup(
    val onsetMs: Long,
    val startBeat: Float,
    val bpm: Int,
    val eventIndices: List<Int>,
    val maxReleasedHoldMs: Long = 0L,
)
''',
    "natural held model",
)

composer = replace_once(
    composer,
    '''    var pianoEntryMode by rememberSaveable { mutableStateOf(PianoEntryMode.STEP) }
    var naturalGroupStartBeat by remember { mutableStateOf<Float?>(null) }
    var naturalGroupMaxBeats by remember { mutableStateOf(0f) }
    var liveRecordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
''',
    '''    var pianoEntryMode by rememberSaveable { mutableStateOf(PianoEntryMode.STEP) }
    var naturalCurrentGroup by remember { mutableStateOf<NaturalOnsetGroup?>(null) }
    var liveRecordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
''',
    "natural state",
)

composer = replace_once(
    composer,
    '''    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalGroupStartBeat = null
        naturalGroupMaxBeats = 0f
    }
''',
    '''    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalCurrentGroup = null
    }
''',
    "cancel natural",
)

composer = replace_once(
    composer,
    '''    fun insertRest() {
        recordBeforeScoreEdit()
        LiveInstrumentBus.allNotesOff()
''',
    '''    fun insertRest() {
        cancelNaturalEntryGroup()
        recordBeforeScoreEdit()
        LiveInstrumentBus.allNotesOff()
''',
    "rest ends natural phrase",
)

old_natural = '''    fun beginNaturalPitch(pitch: Int) {
        if (naturalHeldInputs.containsKey(pitch)) return
        if (naturalHeldInputs.isEmpty()) {
            naturalGroupStartBeat = currentTrack().cursorBeat
            naturalGroupMaxBeats = 0f
            recordBeforeScoreEdit()
        }
        naturalHeldInputs[pitch] = NaturalHeldInput(
            startedAtMs = SystemClock.elapsedRealtime(),
            bpmAtPress = bpm,
        )
        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) playback.previewPitch(pitch)
    }

    fun finishNaturalPitch(pitch: Int) {
        LiveInstrumentBus.noteOff(pitch)
        val held = naturalHeldInputs.remove(pitch) ?: return
        val groupStart = naturalGroupStartBeat ?: currentTrack().cursorBeat
        val duration = NaturalEntryTiming.durationForHoldMs(
            holdMs = SystemClock.elapsedRealtime() - held.startedAtMs,
            bpm = held.bpmAtPress,
        )
        naturalGroupMaxBeats = maxOf(naturalGroupMaxBeats, duration.beats)
        val newEventIndex = currentTrack().events.size
        val finalizingGroup = naturalHeldInputs.isEmpty()
        val finalCursor = groupStart + naturalGroupMaxBeats
        replaceActiveTrack {
            it.copy(
                events = it.events + ScoreNote(
                    midiPitch = pitch,
                    duration = duration,
                    startBeat = groupStart,
                    dotted = false,
                    articulation = selectedArticulation,
                ),
                cursorBeat = if (finalizingGroup) finalCursor else groupStart,
            )
        }
        selectedEventIndex = newEventIndex
        if (finalizingGroup) {
            naturalGroupStartBeat = null
            naturalGroupMaxBeats = 0f
            syncHistoryButtons()
        }
    }
'''

new_natural = '''    fun applyNaturalGroupDuration(
        group: NaturalOnsetGroup,
        written: NaturalEntryTiming.WrittenDuration,
        cursorBeat: Float,
    ) {
        val track = currentTrack()
        val updatedEvents = track.events.toMutableList()
        group.eventIndices.forEach { index ->
            val note = updatedEvents.getOrNull(index) as? ScoreNote ?: return@forEach
            updatedEvents[index] = note.copy(
                duration = written.duration,
                dotted = written.dotted,
            )
        }
        replaceActiveTrack { it.copy(events = updatedEvents, cursorBeat = cursorBeat) }
    }

    fun finalizeNaturalGroupForNextAttack(group: NaturalOnsetGroup, nextOnsetMs: Long): Float {
        val intervalMs = (nextOnsetMs - group.onsetMs).coerceAtLeast(0L)
        val onsetWritten = NaturalEntryTiming.writtenForOnsetIntervalMs(intervalMs, group.bpm)
        val regularRhythm = NaturalEntryTiming.shouldUseOnsetAsWrittenDuration(intervalMs, group.bpm)
        val written = if (regularRhythm) {
            onsetWritten
        } else {
            val fallbackMs = group.maxReleasedHoldMs.takeIf { it > 0L } ?: intervalMs
            NaturalEntryTiming.writtenForHoldMs(fallbackMs, group.bpm)
        }
        val stepBeats = if (regularRhythm) {
            onsetWritten.beats
        } else {
            NaturalEntryTiming.quantizedOnsetSpacingBeats(intervalMs, group.bpm)
        }
        val nextStartBeat = group.startBeat + stepBeats
        applyNaturalGroupDuration(group, written, nextStartBeat)
        return nextStartBeat
    }

    fun beginNaturalPitch(pitch: Int) {
        if (naturalHeldInputs.containsKey(pitch)) return
        val now = SystemClock.elapsedRealtime()
        val previousGroup = naturalCurrentGroup
        val joinsCurrentChord = previousGroup != null &&
            NaturalEntryTiming.isSameOnsetGroup(previousGroup.onsetMs, now, previousGroup.bpm)

        val groupStartBeat = when {
            joinsCurrentChord -> previousGroup!!.startBeat
            previousGroup != null -> finalizeNaturalGroupForNextAttack(previousGroup, now)
            else -> currentTrack().cursorBeat
        }

        if (!joinsCurrentChord) recordBeforeScoreEdit()

        val provisional = if (joinsCurrentChord) {
            val firstIndex = previousGroup!!.eventIndices.firstOrNull()
            val firstNote = firstIndex?.let { currentTrack().events.getOrNull(it) as? ScoreNote }
            if (firstNote != null) {
                NaturalEntryTiming.WrittenDuration(firstNote.duration, firstNote.dotted)
            } else {
                NaturalEntryTiming.WrittenDuration(NoteDuration.QUARTER, false)
            }
        } else {
            NaturalEntryTiming.WrittenDuration(NoteDuration.QUARTER, false)
        }

        val newEventIndex = currentTrack().events.size
        replaceActiveTrack {
            it.copy(
                events = it.events + ScoreNote(
                    midiPitch = pitch,
                    duration = provisional.duration,
                    startBeat = groupStartBeat,
                    dotted = provisional.dotted,
                    articulation = selectedArticulation,
                ),
                cursorBeat = groupStartBeat + provisional.beats,
            )
        }

        val group = if (joinsCurrentChord) {
            previousGroup!!.copy(eventIndices = previousGroup.eventIndices + newEventIndex)
        } else {
            NaturalOnsetGroup(
                onsetMs = now,
                startBeat = groupStartBeat,
                bpm = bpm,
                eventIndices = listOf(newEventIndex),
            )
        }
        naturalCurrentGroup = group
        naturalHeldInputs[pitch] = NaturalHeldInput(
            startedAtMs = now,
            bpmAtPress = bpm,
            eventIndex = newEventIndex,
            groupOnsetMs = group.onsetMs,
        )
        selectedEventIndex = newEventIndex
        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) playback.previewPitch(pitch)
    }

    fun finishNaturalPitch(pitch: Int) {
        LiveInstrumentBus.noteOff(pitch)
        val held = naturalHeldInputs.remove(pitch) ?: return
        val group = naturalCurrentGroup ?: return
        if (group.onsetMs != held.groupOnsetMs) return

        val holdMs = (SystemClock.elapsedRealtime() - held.startedAtMs).coerceAtLeast(0L)
        val updatedGroup = group.copy(
            maxReleasedHoldMs = maxOf(group.maxReleasedHoldMs, holdMs),
        )
        naturalCurrentGroup = updatedGroup
        val fallback = NaturalEntryTiming.writtenForHoldMs(
            updatedGroup.maxReleasedHoldMs,
            held.bpmAtPress,
        )
        applyNaturalGroupDuration(
            updatedGroup,
            fallback,
            updatedGroup.startBeat + fallback.beats,
        )
        if (naturalHeldInputs.values.none { it.groupOnsetMs == updatedGroup.onsetMs }) {
            syncHistoryButtons()
        }
    }
'''
composer = replace_once(composer, old_natural, new_natural, "natural entry functions")

composer = replace_once(
    composer,
    '''                        onDeleteEvent = ::deleteEvent,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
''',
    '''                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
''',
    "piano roll page pan callback",
)
COMPOSER.write_text(composer)

piano = PIANO.read_text()
piano = replace_once(
    piano,
    '''import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
''',
    '''import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
''',
    "piano button import",
)
piano = replace_once(
    piano,
    '''import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredHeight
''',
    '''import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.requiredHeight
''',
    "piano row import",
)
piano = replace_once(
    piano,
    '''import androidx.compose.ui.unit.dp
''',
    '''import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
''',
    "piano arrangement import",
)
piano = replace_once(
    piano,
    '''    onMoveNote: (eventIndex: Int, pitch: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
''',
    '''    onMoveNote: (eventIndex: Int, pitch: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    onVerticalPan: (dragY: Float) -> Unit = {},
    modifier: Modifier = Modifier,
''',
    "piano callback",
)
piano = replace_once(
    piano,
    '''    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var horizontalOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }
''',
    '''    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var dragStartX by remember { mutableFloatStateOf(-1f) }
    var horizontalOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }
''',
    "piano drag state",
)
piano = replace_once(
    piano,
    '''                            onDragStart = { position ->
                                draggingEventIndex = noteIndexAtPoint(
''',
    '''                            onDragStart = { position ->
                                dragStartX = position.x
                                draggingEventIndex = noteIndexAtPoint(
''',
    "piano drag start",
)
piano = replace_once(
    piano,
    '''                            onDragEnd = { draggingEventIndex = -1 },
                            onDragCancel = { draggingEventIndex = -1 },
''',
    '''                            onDragEnd = {
                                draggingEventIndex = -1
                                dragStartX = -1f
                            },
                            onDragCancel = {
                                draggingEventIndex = -1
                                dragStartX = -1f
                            },
''',
    "piano drag end",
)
piano = replace_once(
    piano,
    '''                            if (note == null) {
                                horizontalOffsetPx =
                                    (horizontalOffsetPx - dragAmount.x)
                                        .coerceIn(0f, maxHorizontalOffset)
                                verticalOffsetPx =
                                    (verticalOffsetPx - dragAmount.y)
                                        .coerceIn(0f, maxVerticalOffset)
                                change.consume()
                                return@detectDragGestures
                            }
''',
    '''                            if (note == null) {
                                when (
                                    PianoRollMapping.emptyDragTarget(
                                        dragStartX,
                                        dragAmount.x,
                                        dragAmount.y,
                                    )
                                ) {
                                    PianoRollEmptyDragTarget.TIMELINE -> {
                                        horizontalOffsetPx =
                                            (horizontalOffsetPx - dragAmount.x)
                                                .coerceIn(0f, maxHorizontalOffset)
                                    }
                                    PianoRollEmptyDragTarget.PITCH -> {
                                        verticalOffsetPx =
                                            (verticalOffsetPx - dragAmount.y)
                                                .coerceIn(0f, maxVerticalOffset)
                                    }
                                    PianoRollEmptyDragTarget.PAGE -> onVerticalPan(dragAmount.y)
                                }
                                change.consume()
                                return@detectDragGestures
                            }
''',
    "piano empty drag routing",
)
piano = replace_once(
    piano,
    '''            if (visibleNotes.isEmpty()) {
''',
    '''            Row(
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        verticalOffsetPx =
                            (verticalOffsetPx - rowHeightPx * 12f).coerceIn(0f, maxVerticalOffset)
                    },
                    enabled = verticalOffsetPx > 0f,
                ) { Text("Pitch ↑") }
                OutlinedButton(
                    onClick = {
                        verticalOffsetPx =
                            (verticalOffsetPx + rowHeightPx * 12f).coerceIn(0f, maxVerticalOffset)
                    },
                    enabled = verticalOffsetPx < maxVerticalOffset,
                ) { Text("Pitch ↓") }
            }

            if (visibleNotes.isEmpty()) {
''',
    "piano pitch buttons",
)
piano = piano.replace(
    '"Tap to place ${selectedDuration.displayName.lowercase()} notes • drag empty grid to pan"',
    '"Tap to place ${selectedDuration.displayName.lowercase()} notes • drag ↔ for timeline"',
)
piano = piano.replace(
    '"Drag empty grid ↔ ↕ • purple = playhead"',
    '"Grid ↔ timeline • ↑↓ page • Pitch buttons move range • purple = playhead"',
)
PIANO.write_text(piano)

build = BUILD.read_text()
build = replace_once(build, 'versionCode = 24', 'versionCode = 25', 'version code')
build = replace_once(build, 'versionName = "0.2.21"', 'versionName = "0.2.22"', 'version name')
BUILD.write_text(build)
