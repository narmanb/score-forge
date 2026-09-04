from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
PIANO = ROOT / "app/src/main/java/com/scoreforge/app/ui/PianoRollEditor.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


composer = COMPOSER.read_text()
composer = replace_once(
    composer,
    "import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.Column\n",
    "import androidx.compose.foundation.layout.Arrangement\nimport androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.Column\n",
    "composer Box import",
)
composer = replace_once(
    composer,
    "import androidx.compose.ui.unit.dp\n",
    "import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.window.Dialog\nimport androidx.compose.ui.window.DialogProperties\n",
    "composer dialog imports",
)
composer = replace_once(
    composer,
    "    var naturalCurrentGroup by remember { mutableStateOf<NaturalOnsetGroup?>(null) }\n",
    "    var naturalCurrentGroup by remember { mutableStateOf<NaturalOnsetGroup?>(null) }\n    var naturalRecentIntervalsMs by remember { mutableStateOf(emptyList<Long>()) }\n",
    "natural pulse state",
)
composer = replace_once(
    composer,
    "    var editorMode by rememberSaveable { mutableStateOf(ScoreEditorMode.STAFF) }\n",
    "    var editorMode by rememberSaveable { mutableStateOf(ScoreEditorMode.STAFF) }\n    var pianoRollDialogOpen by rememberSaveable { mutableStateOf(false) }\n",
    "piano dialog state",
)
composer = replace_once(
    composer,
    """    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalCurrentGroup = null
    }
""",
    """    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalCurrentGroup = null
        naturalRecentIntervalsMs = emptyList()
    }
""",
    "cancel natural pulse history",
)
composer = replace_once(
    composer,
    """    fun finalizeNaturalGroupForNextAttack(group: NaturalOnsetGroup, nextOnsetMs: Long): Float {
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
""",
    """    fun finalizeNaturalGroupForNextAttack(group: NaturalOnsetGroup, nextOnsetMs: Long): Float {
        val intervalMs = (nextOnsetMs - group.onsetMs).coerceAtLeast(0L)
        val nextBarline = ScoreTimeSignatures.measureBoundaries(
            timeSignatures,
            group.startBeat + 16f,
        ).firstOrNull { it > group.startBeat + 0.001f }
        val beatsToBarline = nextBarline?.let { it - group.startBeat }
        val inference = NaturalEntryTiming.inferInterval(
            intervalMs = intervalMs,
            bpm = group.bpm,
            recentIntervalsMs = naturalRecentIntervalsMs,
            beatsToNextBarline = beatsToBarline,
            holdFallbackMs = group.maxReleasedHoldMs,
        )
        naturalRecentIntervalsMs = NaturalEntryTiming.rememberInterval(
            recentIntervalsMs = naturalRecentIntervalsMs,
            intervalMs = intervalMs,
            phraseBreak = inference.phraseBreak,
        )

        // Preserve the performed start-to-start spacing even when the previous written duration is
        // shortened to the learned pulse at a phrase ending. The leftover space is musical silence,
        // not a giant sustained note.
        val stepBeats = NaturalEntryTiming.quantizedOnsetSpacingBeats(intervalMs, group.bpm)
        val nextStartBeat = group.startBeat + stepBeats
        applyNaturalGroupDuration(group, inference.written, nextStartBeat)
        return nextStartBeat
    }
""",
    "natural v3 finalization",
)
composer = replace_once(
    composer,
    "                    onModeChanged = { editorMode = it },\n",
    """                    onModeChanged = { mode ->
                        editorMode = mode
                        if (mode == ScoreEditorMode.PIANO_ROLL) pianoRollDialogOpen = true
                    },
""",
    "editor mode modal open",
)
composer = replace_once(
    composer,
    """                    ScoreEditorMode.PIANO_ROLL -> PianoRollEditor(
                        events = activeEvents,
                        selectedDuration = selectedDuration,
                        cursorBeat = activeCursorBeat,
                        timeSignatures = timeSignatures,
                        octaveShift = pianoOctaveShift,
                        selectedEventIndex = selectedEventIndex,
                        onAddPitch = { pitch, tappedBeat ->
                            insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                        },
                        onSelectEvent = ::selectEvent,
                        onBeginMove = { recordBeforeScoreEdit() },
                        onMoveNote = ::moveActiveNote,
                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
""",
    """                    ScoreEditorMode.PIANO_ROLL -> Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Text(
                            "Piano Roll opens in its own large editor so its scrolling does not fight the main page.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                        OutlinedButton(onClick = { pianoRollDialogOpen = true }) {
                            Text("Open Piano Roll")
                        }
                    }
""",
    "inline piano roll replacement",
)
composer = replace_once(
    composer,
    """                }

                if (showPianoKeyboard) {
""",
    """                }

                if (pianoRollDialogOpen) {
                    Dialog(
                        onDismissRequest = { pianoRollDialogOpen = false },
                        properties = DialogProperties(usePlatformDefaultWidth = false),
                    ) {
                        Surface(
                            modifier = Modifier.fillMaxSize().padding(10.dp),
                            color = MaterialTheme.colorScheme.surface,
                        ) {
                            Box(modifier = Modifier.fillMaxSize()) {
                                PianoRollEditor(
                                    events = activeEvents,
                                    selectedDuration = selectedDuration,
                                    cursorBeat = activeCursorBeat,
                                    timeSignatures = timeSignatures,
                                    octaveShift = pianoOctaveShift,
                                    selectedEventIndex = selectedEventIndex,
                                    onAddPitch = { pitch, tappedBeat ->
                                        insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                                    },
                                    onSelectEvent = ::selectEvent,
                                    onBeginMove = { recordBeforeScoreEdit() },
                                    onMoveNote = ::moveActiveNote,
                                    onDeleteEvent = ::deleteEvent,
                                    modifier = Modifier.fillMaxSize().padding(top = 48.dp),
                                )
                                Row(
                                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth().padding(6.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("Piano Roll", style = MaterialTheme.typography.titleMedium)
                                    OutlinedButton(onClick = { pianoRollDialogOpen = false }) {
                                        Text("Close")
                                    }
                                }
                            }
                        }
                    }
                }

                if (showPianoKeyboard) {
""",
    "piano dialog",
)
COMPOSER.write_text(composer)

piano = PIANO.read_text()
piano = replace_once(
    piano,
    "    onDeleteEvent: (eventIndex: Int) -> Unit,\n    onVerticalPan: (dragY: Float) -> Unit = {},\n    modifier: Modifier = Modifier,\n",
    "    onDeleteEvent: (eventIndex: Int) -> Unit,\n    modifier: Modifier = Modifier,\n",
    "remove page pan callback",
)
piano = replace_once(
    piano,
    """    val lowPitch = PianoRollMapping.lowPitch(octaveShift)
    val highPitch = PianoRollMapping.highPitch(octaveShift)
    val visibleNotes = events.filterIsInstance<ScoreNote>()
""",
    """    val lowPitch = PianoRollMapping.FULL_LOW_PITCH
    val highPitch = PianoRollMapping.FULL_HIGH_PITCH
    val visibleNotes = events.filterIsInstance<ScoreNote>()
    val focusPitch = PianoRollMapping.focusPitch(visibleNotes.map { it.midiPitch }, octaveShift)
""",
    "full pitch range",
)
piano = replace_once(
    piano,
    """            LaunchedEffect(octaveShift, maxVerticalOffset, viewportHeightPx, contentHeightPx) {
                if (maxVerticalOffset <= 0f) return@LaunchedEffect
                val keyboardCenterPitch = (65 + octaveShift.coerceIn(-4, 3) * 12)
                    .coerceIn(lowPitch, highPitch)
                val centerY = PianoRollMapping.yCenterForPitch(
                    keyboardCenterPitch,
                    lowPitch,
                    highPitch,
                    contentHeightPx,
                )
                verticalOffsetPx =
                    (centerY - viewportHeightPx * 0.52f).coerceIn(0f, maxVerticalOffset)
            }
""",
    """            LaunchedEffect(focusPitch, maxVerticalOffset, viewportHeightPx, contentHeightPx) {
                if (maxVerticalOffset <= 0f) return@LaunchedEffect
                val centerY = PianoRollMapping.yCenterForPitch(
                    focusPitch,
                    lowPitch,
                    highPitch,
                    contentHeightPx,
                )
                verticalOffsetPx =
                    (centerY - viewportHeightPx * 0.52f).coerceIn(0f, maxVerticalOffset)
            }
""",
    "focus actual notes",
)
piano = replace_once(
    piano,
    """                            val note = events.getOrNull(draggingEventIndex) as? ScoreNote
                            if (note == null) {
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
""",
    """                            val note = events.getOrNull(draggingEventIndex) as? ScoreNote
                            if (note == null) {
                                horizontalOffsetPx =
                                    (horizontalOffsetPx - dragAmount.x)
                                        .coerceIn(0f, maxHorizontalOffset)
                                verticalOffsetPx =
                                    (verticalOffsetPx - dragAmount.y)
                                        .coerceIn(0f, maxVerticalOffset)
                                change.consume()
                                return@detectDragGestures
                            }
""",
    "modal internal panning",
)
start = piano.find("            Row(\n                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),")
end_marker = "\n\n            if (visibleNotes.isEmpty()) {"
if start == -1:
    raise SystemExit("pitch button row start not found")
end = piano.find(end_marker, start)
if end == -1:
    raise SystemExit("pitch button row end not found")
center_button = '''            OutlinedButton(
                onClick = {
                    val centerY = PianoRollMapping.yCenterForPitch(
                        focusPitch,
                        lowPitch,
                        highPitch,
                        contentHeightPx,
                    )
                    verticalOffsetPx =
                        (centerY - viewportHeightPx * 0.52f).coerceIn(0f, maxVerticalOffset)
                },
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            ) { Text("Center Notes") }
'''
piano = piano[:start] + center_button + piano[end:]
piano = piano.replace(
    '"Tap to place ${selectedDuration.displayName.lowercase()} notes • drag ↔ for timeline"',
    '"Tap to place ${selectedDuration.displayName.lowercase()} notes • drag the grid to pan"',
)
piano = piano.replace(
    '"Grid ↔ timeline • ↑↓ page • Pitch buttons move range • purple = playhead"',
    '"Drag grid ↔/↕ • Center Notes returns to the score • purple = playhead"',
)
PIANO.write_text(piano)
