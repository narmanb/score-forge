from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
STAFF = ROOT / "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
NATURAL = ROOT / "app/src/main/java/com/scoreforge/app/music/NaturalEntryTiming.kt"
TEST = ROOT / "app/src/test/java/com/scoreforge/app/music/NaturalBrowseResumeTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


composer = COMPOSER.read_text()
composer = composer.replace("import androidx.compose.ui.window.Dialog\n", "")
composer = composer.replace("import androidx.compose.ui.window.DialogProperties\n", "")
composer = composer.replace(
    "    var pianoRollDialogOpen by rememberSaveable { mutableStateOf(false) }\n",
    "",
)
composer = replace_once(
    composer,
    """    fun applyNaturalGroupDuration(
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
""",
    """    fun applyNaturalGroupDuration(
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

    fun finishNaturalPhraseForStaffBrowse() {
        val group = naturalCurrentGroup ?: return
        val written = NaturalEntryTiming.writtenForUiBreak(
            recentIntervalsMs = naturalRecentIntervalsMs,
            bpm = group.bpm,
            holdFallbackMs = group.maxReleasedHoldMs,
        )
        applyNaturalGroupDuration(
            group = group,
            written = written,
            cursorBeat = group.startBeat + written.beats,
        )
        cancelNaturalEntryGroup()
    }
""",
    "natural browse finalizer",
)
composer = replace_once(
    composer,
    """                    onModeChanged = { mode ->
                        editorMode = mode
                        if (mode == ScoreEditorMode.PIANO_ROLL) pianoRollDialogOpen = true
                    },
""",
    "                    onModeChanged = { editorMode = it },\n",
    "editor mode switch",
)
composer = replace_once(
    composer,
    """                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )

                    ScoreEditorMode.PIANO_ROLL -> Column(
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
                }

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
""",
    """                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        onManualBrowse = {
                            if (pianoEntryMode == PianoEntryMode.NATURAL) {
                                finishNaturalPhraseForStaffBrowse()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )

                    ScoreEditorMode.PIANO_ROLL -> PianoRollEditor(
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
                }
""",
    "editor blocks",
)
COMPOSER.write_text(composer)

staff = STAFF.read_text()
staff = replace_once(
    staff,
    """    onDeleteEvent: (eventIndex: Int) -> Unit,
    onVerticalPan: (dragY: Float) -> Unit = {},
    modifier: Modifier = Modifier,
""",
    """    onDeleteEvent: (eventIndex: Int) -> Unit,
    onVerticalPan: (dragY: Float) -> Unit = {},
    onManualBrowse: () -> Unit = {},
    modifier: Modifier = Modifier,
""",
    "staff callback signature",
)
staff = replace_once(
    staff,
    """    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
""",
    """    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var manualBrowseNotified by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
""",
    "staff browse state",
)
staff = replace_once(
    staff,
    """                                onDragStart = { position ->
                                    val geometry = staffGeometry(events, keySignatures, size.height.toFloat())
""",
    """                                onDragStart = { position ->
                                    manualBrowseNotified = false
                                    val geometry = staffGeometry(events, keySignatures, size.height.toFloat())
""",
    "staff drag start",
)
staff = replace_once(
    staff,
    """                                onDragEnd = { draggingEventIndex = -1 },
                                onDragCancel = { draggingEventIndex = -1 },
""",
    """                                onDragEnd = {
                                    draggingEventIndex = -1
                                    manualBrowseNotified = false
                                },
                                onDragCancel = {
                                    draggingEventIndex = -1
                                    manualBrowseNotified = false
                                },
""",
    "staff drag finish",
)
staff = replace_once(
    staff,
    """                                if (event == null) {
                                    if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                                        scrollState.dispatchRawDelta(-dragAmount.x)
                                    } else {
                                        onVerticalPan(dragAmount.y)
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }
""",
    """                                if (event == null) {
                                    if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                                        if (!manualBrowseNotified && abs(dragAmount.x) >= 1f) {
                                            manualBrowseNotified = true
                                            onManualBrowse()
                                        }
                                        scrollState.dispatchRawDelta(-dragAmount.x)
                                    } else {
                                        onVerticalPan(dragAmount.y)
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }
""",
    "staff empty drag",
)
STAFF.write_text(staff)

natural = NATURAL.read_text()
natural = replace_once(
    natural,
    """    /** Keep a small rolling pulse history while excluding phrase-break gaps. */
    fun rememberInterval(
""",
    """    /**
     * Resolve the final Natural note before a non-musical UI interaction such as browsing the
     * staff. UI time must never be interpreted as performed silence. Prefer the learned pulse;
     * fall back to the released hold only when there is not enough attack history yet.
     */
    fun writtenForUiBreak(
        recentIntervalsMs: List<Long>,
        bpm: Int,
        holdFallbackMs: Long,
    ): WrittenDuration {
        val expectedMs = expectedPulseMs(recentIntervalsMs)
        return when {
            expectedMs != null -> writtenForOnsetIntervalMs(expectedMs, bpm)
            holdFallbackMs > 0L -> writtenForHoldMs(holdFallbackMs, bpm)
            else -> WrittenDuration(NoteDuration.QUARTER, false)
        }
    }

    /** Keep a small rolling pulse history while excluding phrase-break gaps. */
    fun rememberInterval(
""",
    "natural ui break helper",
)
NATURAL.write_text(natural)

TEST.write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NaturalBrowseResumeTest {
    @Test
    fun `staff browsing seals the phrase at learned pulse instead of browsing time`() {
        val written = NaturalEntryTiming.writtenForUiBreak(
            recentIntervalsMs = listOf(498L, 505L, 502L),
            bpm = 120,
            holdFallbackMs = 180L,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertFalse(written.dotted)
    }

    @Test
    fun `staff browsing falls back to released hold before pulse is learned`() {
        val written = NaturalEntryTiming.writtenForUiBreak(
            recentIntervalsMs = emptyList(),
            bpm = 120,
            holdFallbackMs = 510L,
        )

        assertEquals(NoteDuration.QUARTER, written.duration)
        assertFalse(written.dotted)
    }
}
''')
