from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise RuntimeError(f"Expected pattern not found in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# 1) Chord mode: the edit cursor is an insertion point, not necessarily the arrangement end.
replace_once(
    "app/src/main/java/com/scoreforge/app/music/ScoreTracks.kt",
    "            cursorBeat = maxOf(cursorBeat.coerceAtLeast(0f), ScoreTimeline.endBeat(safeEvents)),\n",
    "            cursorBeat = cursorBeat.coerceAtLeast(0f),\n",
)

# 2) Live recording gets its own nearest-written-duration quantizer, including dotted values.
Path("app/src/main/java/com/scoreforge/app/music/LiveEntryTiming.kt").write_text('''package com.scoreforge.app.music

import kotlin.math.abs

/** Timing helpers for real-time piano recording. */
object LiveEntryTiming {
    data class WrittenDuration(
        val duration: NoteDuration,
        val dotted: Boolean,
    ) {
        val beats: Float get() = duration.effectiveBeats(dotted)
    }

    private val writtenDurations = listOf(
        WrittenDuration(NoteDuration.SIXTEENTH, false),
        WrittenDuration(NoteDuration.SIXTEENTH, true),
        WrittenDuration(NoteDuration.EIGHTH, false),
        WrittenDuration(NoteDuration.EIGHTH, true),
        WrittenDuration(NoteDuration.QUARTER, false),
        WrittenDuration(NoteDuration.QUARTER, true),
        WrittenDuration(NoteDuration.HALF, false),
        WrittenDuration(NoteDuration.HALF, true),
        WrittenDuration(NoteDuration.WHOLE, false),
        WrittenDuration(NoteDuration.WHOLE, true),
    )

    fun beatAtElapsedMs(startBeat: Float, elapsedMs: Long, bpm: Int): Float {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeElapsedMs = elapsedMs.coerceAtLeast(0L)
        val beatsElapsed = safeElapsedMs.toDouble() / (60_000.0 / safeBpm.toDouble())
        return startBeat.coerceAtLeast(0f) + beatsElapsed.toFloat()
    }

    fun quantizedBeatAtElapsedMs(startBeat: Float, elapsedMs: Long, bpm: Int): Float =
        ScoreTimeline.quantizeBeat(beatAtElapsedMs(startBeat, elapsedMs, bpm))

    /**
     * Live mode preserves actual press/release timing, then chooses the nearest conventional
     * written value. Unlike Natural Entry, this includes dotted values so a performed 3/4 beat
     * note can become a dotted eighth instead of being forced into a quarter-note bucket.
     */
    fun quantizedDurationForHoldMs(holdMs: Long, bpm: Int): WrittenDuration {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeHoldMs = holdMs.coerceAtLeast(0L)
        val heldBeats = safeHoldMs.toDouble() / (60_000.0 / safeBpm.toDouble())
        return writtenDurations.minByOrNull { candidate ->
            abs(candidate.beats.toDouble() - heldBeats)
        } ?: writtenDurations.first()
    }
}
''')

# 3) Composer: use live quantization, reset playhead, move Clear Track, bottom undo/redo,
# and let vertical staff drags drive the page scroll.
composer = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    composer,
    '''        val duration = NaturalEntryTiming.durationForHoldMs(
            holdMs = finishedAtMs - held.startedAtMs,
            bpm = held.bpmAtPress,
        )
        val updatedEvents = track.events.toMutableList().apply {
            this[held.eventIndex] = note.copy(duration = duration, dotted = false)
        }
''',
    '''        val written = LiveEntryTiming.quantizedDurationForHoldMs(
            holdMs = finishedAtMs - held.startedAtMs,
            bpm = held.bpmAtPress,
        )
        val updatedEvents = track.events.toMutableList().apply {
            this[held.eventIndex] = note.copy(
                duration = written.duration,
                dotted = written.dotted,
            )
        }
''',
)
replace_once(
    composer,
    '''                    val duration = NaturalEntryTiming.durationForHoldMs(
                        holdMs = now - held.startedAtMs,
                        bpm = held.bpmAtPress,
                    )
                    if (note.duration != duration || note.dotted) {
                        updatedEvents[held.eventIndex] = note.copy(
                            duration = duration,
                            dotted = false,
                        )
                        changed = true
                    }
''',
    '''                    val written = LiveEntryTiming.quantizedDurationForHoldMs(
                        holdMs = now - held.startedAtMs,
                        bpm = held.bpmAtPress,
                    )
                    if (note.duration != written.duration || note.dotted != written.dotted) {
                        updatedEvents[held.eventIndex] = note.copy(
                            duration = written.duration,
                            dotted = written.dotted,
                        )
                        changed = true
                    }
''',
)
replace_once(
    composer,
    '''        applyProjectSnapshot(
            ScoreProjectSnapshot(
                events = emptyList(),
                bpm = 120,
                cursorBeat = 0f,
                selectedDuration = NoteDuration.QUARTER,
                selectedDotted = false,
                pianoOctaveShift = 0,
                staffSharpInput = false,
                tracks = listOf(blankTrack),
                activeTrackIndex = 0,
                projectName = "Untitled",
            ),
            clearHistory = true,
        )
    }
''',
    '''        applyProjectSnapshot(
            ScoreProjectSnapshot(
                events = emptyList(),
                bpm = 120,
                cursorBeat = 0f,
                selectedDuration = NoteDuration.QUARTER,
                selectedDotted = false,
                pianoOctaveShift = 0,
                staffSharpInput = false,
                tracks = listOf(blankTrack),
                activeTrackIndex = 0,
                projectName = "Untitled",
            ),
            clearHistory = true,
        )
        ScoreTransportBus.seek(0f)
    }
''',
)
replace_once(
    composer,
    '''    fun setActiveTrackPreset(bank: Int, program: Int, recordHistory: Boolean) {
        val track = currentTrack()
        if (track.presetBank == bank && track.presetProgram == program) return
        if (recordHistory) recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(presetBank = bank, presetProgram = program) }
    }

    MaterialTheme''',
    '''    fun setActiveTrackPreset(bank: Int, program: Int, recordHistory: Boolean) {
        val track = currentTrack()
        if (track.presetBank == bank && track.presetProgram == program) return
        if (recordHistory) recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(presetBank = bank, presetProgram = program) }
    }

    fun clearActiveTrack() {
        val track = currentTrack()
        if (track.events.isEmpty()) return
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(events = emptyList(), cursorBeat = 0f) }
        selectedEventIndex = -1
        syncHistoryButtons()
    }

    MaterialTheme''',
)
replace_once(
    composer,
    '''                    canUndo = canUndo,
                    canRedo = canRedo,
                    canPlay = playableNoteCount > 0 && !liveRecordingActive,
''',
    '''                    canPlay = playableNoteCount > 0 && !liveRecordingActive,
''',
)
replace_once(
    composer,
    '''                    onStop = ::stopPlayback,
                    onUndo = ::undoScore,
                    onRedo = ::redoScore,
                    onClearTrack = {
                        val track = currentTrack()
                        if (track.events.isNotEmpty()) {
                            stopPlayback()
                            stopLiveRecording()
                            cancelNaturalEntryGroup()
                            LiveInstrumentBus.allNotesOff()
                            recordBeforeScoreEdit()
                            replaceActiveTrack { it.copy(events = emptyList(), cursorBeat = 0f) }
                            selectedEventIndex = -1
                        }
                    },
''',
    '''                    onStop = ::stopPlayback,
''',
)
replace_once(
    composer,
    '''                ProjectFileControls(
                    projectName = projectName,
                    snapshotProvider = ::currentProjectSnapshot,
                    onNewProject = ::newProject,
                    onRenameProject = ::renameProject,
                    onOpenProject = ::openProject,
                )
''',
    '''                ProjectFileControls(
                    projectName = projectName,
                    activeTrackName = activeTrack.name,
                    canClearTrack = activeEvents.isNotEmpty(),
                    snapshotProvider = ::currentProjectSnapshot,
                    onNewProject = ::newProject,
                    onClearTrack = ::clearActiveTrack,
                    onRenameProject = ::renameProject,
                    onOpenProject = ::openProject,
                )
''',
)
replace_once(
    composer,
    '''                        onMoveRest = ::moveActiveRest,
                        onDeleteEvent = ::deleteEvent,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
''',
    '''                        onMoveRest = ::moveActiveRest,
                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
''',
)
replace_once(
    composer,
    '''                        liveRecordingActive = liveRecordingActive,
                        onEntryModeChanged = { mode ->
''',
    '''                        liveRecordingActive = liveRecordingActive,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = ::undoScore,
                        onRedo = ::redoScore,
                        onEntryModeChanged = { mode ->
''',
)
replace_once(
    composer,
    '''    canUndo: Boolean,
    canRedo: Boolean,
    canPlay: Boolean,
''',
    '''    canPlay: Boolean,
''',
)
replace_once(
    composer,
    '''    onStop: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearTrack: () -> Unit,
) {
    val eventCount = noteCount + restCount
''',
    '''    onStop: () -> Unit,
) {
''',
)
replace_once(
    composer,
    '''        OutlinedButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
        OutlinedButton(onClick = onRedo, enabled = canRedo) { Text("Redo") }
        OutlinedButton(onClick = onClearTrack, enabled = eventCount > 0) { Text("Clear Track") }
''',
    '''''',
)

# 4) Project controls: Clear Track is upper-level and only affects the selected track.
project_controls = "app/src/main/java/com/scoreforge/app/ui/ProjectFileControls.kt"
replace_once(
    project_controls,
    '''fun ProjectFileControls(
    projectName: String,
    snapshotProvider: () -> ScoreProjectSnapshot,
    onNewProject: () -> Unit,
    onRenameProject: (String) -> Unit,
''',
    '''fun ProjectFileControls(
    projectName: String,
    activeTrackName: String,
    canClearTrack: Boolean,
    snapshotProvider: () -> ScoreProjectSnapshot,
    onNewProject: () -> Unit,
    onClearTrack: () -> Unit,
    onRenameProject: (String) -> Unit,
''',
)
replace_once(
    project_controls,
    '''        OutlinedButton(onClick = { newProjectDialogOpen = true }) {
            Text("New")
        }

        OutlinedButton(
''',
    '''        OutlinedButton(onClick = { newProjectDialogOpen = true }) {
            Text("New")
        }

        OutlinedButton(
            onClick = {
                onClearTrack()
                status = "Cleared $activeTrackName"
            },
            enabled = canClearTrack,
        ) {
            Text("Clear Track")
        }

        OutlinedButton(
''',
)

# 5) Piano control strip: handy undo/redo and a clearer Next Chord label.
keyboard = "app/src/main/java/com/scoreforge/app/ui/MultitouchPianoKeyboard.kt"
replace_once(
    keyboard,
    '''    entryMode: PianoEntryMode,
    liveRecordingActive: Boolean,
    onEntryModeChanged: (PianoEntryMode) -> Unit,
''',
    '''    entryMode: PianoEntryMode,
    liveRecordingActive: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onEntryModeChanged: (PianoEntryMode) -> Unit,
''',
)
replace_once(
    keyboard,
    '''            Text("Piano", style = MaterialTheme.typography.labelMedium, color = Color.White)

            ChamferedControlButton(
                label = "Oct −",
''',
    '''            Text("Piano", style = MaterialTheme.typography.labelMedium, color = Color.White)

            ChamferedControlButton(
                label = "↶",
                onClick = {
                    releaseAllPitches()
                    onUndo()
                },
                enabled = canUndo,
            )
            ChamferedControlButton(
                label = "↷",
                onClick = {
                    releaseAllPitches()
                    onRedo()
                },
                enabled = canRedo,
            )

            ChamferedControlButton(
                label = "Oct −",
''',
)
replace_once(keyboard, '                    label = "Next",\n', '                    label = "Next Chord",\n')

# 6) Staff gestures: horizontal empty-space drags pan music; vertical drags scroll the page.
staff = "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
replace_once(
    staff,
    '''    onMoveRest: (eventIndex: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
''',
    '''    onMoveRest: (eventIndex: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    onVerticalPan: (dragY: Float) -> Unit = {},
    modifier: Modifier = Modifier,
''',
)
replace_once(
    staff,
    '''                                if (event == null) {
                                    scrollState.dispatchRawDelta(-dragAmount.x)
                                    change.consume()
                                    return@detectDragGestures
                                }
''',
    '''                                if (event == null) {
                                    if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                                        scrollState.dispatchRawDelta(-dragAmount.x)
                                    } else {
                                        onVerticalPan(dragAmount.y)
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }
''',
)

# 7) Regression tests for chord cursor semantics and Live written-duration quantization.
score_tracks_test = Path("app/src/test/java/com/scoreforge/app/music/ScoreTracksTest.kt")
text = score_tracks_test.read_text()
text = text.replace('assertEquals(6f, track.cursorBeat, 0.0001f)', 'assertEquals(0f, track.cursorBeat, 0.0001f)')
insert = '''
    @Test
    fun normalizationPreservesChordAnchorBeforeEventEnd() {
        val anchor = 2f
        val first = ScoreTrack(
            id = 1,
            name = "Chord",
            cursorBeat = anchor,
            events = listOf(ScoreNote(60, NoteDuration.QUARTER, startBeat = anchor)),
        ).normalized()
        val second = first.copy(
            events = first.events + ScoreNote(64, NoteDuration.QUARTER, startBeat = first.cursorBeat),
        ).normalized()

        assertEquals(anchor, first.cursorBeat, 0.0001f)
        assertEquals(anchor, second.cursorBeat, 0.0001f)
        assertEquals(listOf(anchor, anchor), second.notes.map { it.startBeat })
    }
'''
if insert.strip() not in text:
    pos = text.rfind("}\n")
    text = text[:pos] + insert + text[pos:]
score_tracks_test.write_text(text)

Path("app/src/test/java/com/scoreforge/app/music/LiveEntryTimingTest.kt").write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class LiveEntryTimingTest {
    @Test
    fun `120 bpm advances one beat every 500 ms`() {
        assertEquals(4f, LiveEntryTiming.beatAtElapsedMs(4f, 0, 120), 0.001f)
        assertEquals(5f, LiveEntryTiming.beatAtElapsedMs(4f, 500, 120), 0.001f)
        assertEquals(6f, LiveEntryTiming.beatAtElapsedMs(4f, 1000, 120), 0.001f)
    }

    @Test
    fun `live starts quantize to sixteenth grid`() {
        assertEquals(0.25f, LiveEntryTiming.quantizedBeatAtElapsedMs(0f, 130, 120), 0.001f)
        assertEquals(1f, LiveEntryTiming.quantizedBeatAtElapsedMs(0f, 510, 120), 0.001f)
    }

    @Test
    fun `live release timing can produce dotted written values`() {
        val dottedEighth = LiveEntryTiming.quantizedDurationForHoldMs(375, 120)
        assertEquals(NoteDuration.EIGHTH, dottedEighth.duration)
        assertEquals(true, dottedEighth.dotted)

        val dottedQuarter = LiveEntryTiming.quantizedDurationForHoldMs(750, 120)
        assertEquals(NoteDuration.QUARTER, dottedQuarter.duration)
        assertEquals(true, dottedQuarter.dotted)
    }

    @Test
    fun `live release timing chooses nearest conventional value`() {
        val quarter = LiveEntryTiming.quantizedDurationForHoldMs(520, 120)
        assertEquals(NoteDuration.QUARTER, quarter.duration)
        assertEquals(false, quarter.dotted)

        val half = LiveEntryTiming.quantizedDurationForHoldMs(980, 120)
        assertEquals(NoteDuration.HALF, half.duration)
        assertEquals(false, half.dotted)
    }
}
''')

print("Score Forge 0.2.10 source patch applied")
