package com.scoreforge.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.LiveInstrumentBus
import com.scoreforge.app.audio.ScorePlaybackEngine
import com.scoreforge.app.audio.SoundFontEngine
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreEditHistory
import com.scoreforge.app.music.ScoreEditState
import com.scoreforge.app.music.ScoreEvent
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreProjectRepository
import com.scoreforge.app.music.ScoreProjectSnapshot
import com.scoreforge.app.music.ScoreRest
import com.scoreforge.app.music.ScoreTimeline
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ScoreForgeComposerScreen() {
    val context = LocalContext.current
    val events = remember { mutableStateListOf<ScoreEvent>() }
    val playback = remember { ScorePlaybackEngine() }
    val soundFontEngine = remember { SoundFontEngine.createOrNull() }
    val editHistory = remember { ScoreEditHistory() }
    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    var cursorBeat by remember { mutableStateOf(0f) }
    var chordMode by remember { mutableStateOf(false) }
    var pianoOctaveShift by remember { mutableIntStateOf(0) }
    var staffSharpInput by remember { mutableStateOf(false) }
    var draftLoaded by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    val noteCount = events.count { it is ScoreNote }
    val restCount = events.count { it is ScoreRest }
    val draftEvents = events.toList()

    fun syncHistoryButtons() {
        canUndo = editHistory.canUndo
        canRedo = editHistory.canRedo
    }

    fun currentEditState(): ScoreEditState = ScoreEditState(
        events = events.toList(),
        cursorBeat = cursorBeat,
    )

    fun recordBeforeScoreEdit() {
        editHistory.recordBeforeChange(currentEditState())
        syncHistoryButtons()
    }

    fun restoreEditState(state: ScoreEditState) {
        events.clear()
        events.addAll(state.events)
        cursorBeat = state.cursorBeat
    }

    LaunchedEffect(Unit) {
        val restored = withContext(Dispatchers.IO) {
            ScoreProjectRepository.loadDraft(context)
        }
        if (restored != null) {
            events.clear()
            events.addAll(restored.events)
            bpm = restored.bpm
            cursorBeat = restored.cursorBeat
            selectedDuration = restored.selectedDuration
            pianoOctaveShift = restored.pianoOctaveShift
            staffSharpInput = restored.staffSharpInput
        }
        editHistory.clear()
        syncHistoryButtons()
        draftLoaded = true
    }

    LaunchedEffect(
        draftLoaded,
        draftEvents,
        bpm,
        cursorBeat,
        selectedDuration,
        pianoOctaveShift,
        staffSharpInput,
    ) {
        if (!draftLoaded) return@LaunchedEffect
        delay(250L)
        val snapshot = ScoreProjectSnapshot(
            events = draftEvents,
            bpm = bpm,
            cursorBeat = cursorBeat,
            selectedDuration = selectedDuration,
            pianoOctaveShift = pianoOctaveShift,
            staffSharpInput = staffSharpInput,
        )
        withContext(Dispatchers.IO) {
            ScoreProjectRepository.saveDraft(context, snapshot)
        }
    }

    DisposableEffect(playback, soundFontEngine) {
        playback.setSoundFontEngine(soundFontEngine)
        onDispose {
            LiveInstrumentBus.allNotesOff()
            playback.setSoundFontEngine(null)
            playback.release()
            soundFontEngine?.close()
        }
    }

    fun stopPlayback() {
        playback.stop()
        isPlaying = false
    }

    fun undoScore() {
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        val restored = editHistory.undo(currentEditState()) ?: return
        restoreEditState(restored)
        syncHistoryButtons()
    }

    fun redoScore() {
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        val restored = editHistory.redo(currentEditState()) ?: return
        restoreEditState(restored)
        syncHistoryButtons()
    }

    fun insertNoteAt(pitch: Int, startBeat: Float, preview: Boolean, advanceCursor: Boolean) {
        recordBeforeScoreEdit()
        val quantizedStart = ScoreTimeline.quantizeBeat(startBeat)
        events.add(
            ScoreNote(
                midiPitch = pitch,
                duration = selectedDuration,
                startBeat = quantizedStart,
            )
        )
        if (preview) playback.previewPitch(pitch)
        if (advanceCursor && !chordMode) {
            cursorBeat = quantizedStart + selectedDuration.beats
        } else {
            cursorBeat = maxOf(cursorBeat, quantizedStart + selectedDuration.beats)
        }
    }

    fun insertStepNote(pitch: Int, preview: Boolean) {
        insertNoteAt(
            pitch = pitch,
            startBeat = cursorBeat,
            preview = preview,
            advanceCursor = true,
        )
    }

    fun insertRest() {
        recordBeforeScoreEdit()
        LiveInstrumentBus.allNotesOff()
        events.add(
            ScoreRest(
                duration = selectedDuration,
                startBeat = cursorBeat,
            )
        )
        cursorBeat += selectedDuration.beats
    }

    fun changePianoOctave(delta: Int) {
        LiveInstrumentBus.allNotesOff()
        pianoOctaveShift = (pianoOctaveShift + delta).coerceIn(-4, 3)
    }

    fun deleteEvent(eventIndex: Int) {
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        if (eventIndex in events.indices) {
            recordBeforeScoreEdit()
            events.removeAt(eventIndex)
            if (!chordMode) cursorBeat = ScoreTimeline.endBeat(events)
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderBar(
                    noteCount = noteCount,
                    restCount = restCount,
                    measureCount = ScoreTimeline.measureCount(events, cursorBeat),
                    bpm = bpm,
                    cursorBeat = cursorBeat,
                    isPlaying = isPlaying,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    onTempoDown = { bpm = (bpm - 5).coerceAtLeast(30) },
                    onTempoUp = { bpm = (bpm + 5).coerceAtMost(300) },
                    onPlay = {
                        val notes = events.filterIsInstance<ScoreNote>()
                        if (notes.isNotEmpty()) {
                            isPlaying = true
                            playback.playScore(
                                notes = notes,
                                bpm = bpm,
                                throughBeat = ScoreTimeline.endBeat(events),
                            ) { isPlaying = false }
                        }
                    },
                    onStop = ::stopPlayback,
                    onUndo = ::undoScore,
                    onRedo = ::redoScore,
                    onClear = {
                        if (events.isNotEmpty()) {
                            stopPlayback()
                            LiveInstrumentBus.allNotesOff()
                            recordBeforeScoreEdit()
                            events.clear()
                            cursorBeat = 0f
                        }
                    },
                )

                SoundFontControls(engine = soundFontEngine)

                DurationSelector(
                    selected = selectedDuration,
                    sharpInput = staffSharpInput,
                    onSelected = { selectedDuration = it },
                    onInsertRest = ::insertRest,
                    onToggleSharpInput = { staffSharpInput = !staffSharpInput },
                )

                ScoreStaffEditor(
                    events = events,
                    selectedDuration = selectedDuration,
                    cursorBeat = cursorBeat,
                    onAddPitch = { naturalPitch, tappedBeat ->
                        val pitch = if (staffSharpInput) {
                            PitchNames.sharpenIfAvailable(naturalPitch)
                        } else {
                            naturalPitch
                        }
                        insertNoteAt(
                            pitch = pitch,
                            startBeat = tappedBeat,
                            preview = true,
                            advanceCursor = false,
                        )
                    },
                    onBeginMove = { recordBeforeScoreEdit() },
                    onMoveNote = { eventIndex, pitch, startBeat ->
                        val note = events.getOrNull(eventIndex) as? ScoreNote
                        if (note != null) {
                            events[eventIndex] = note.copy(
                                midiPitch = pitch,
                                startBeat = startBeat,
                            )
                            if (!chordMode) cursorBeat = ScoreTimeline.endBeat(events)
                        }
                    },
                    onMoveRest = { eventIndex, startBeat ->
                        val rest = events.getOrNull(eventIndex) as? ScoreRest
                        if (rest != null) {
                            events[eventIndex] = rest.copy(startBeat = startBeat)
                            if (!chordMode) cursorBeat = ScoreTimeline.endBeat(events)
                        }
                    },
                    onDeleteEvent = ::deleteEvent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                MultitouchPianoKeyboard(
                    chordMode = chordMode,
                    octaveShift = pianoOctaveShift,
                    onToggleChordMode = {
                        LiveInstrumentBus.allNotesOff()
                        if (chordMode) {
                            chordMode = false
                            cursorBeat = maxOf(cursorBeat, ScoreTimeline.endBeat(events))
                        } else {
                            chordMode = true
                            cursorBeat = ScoreTimeline.endBeat(events)
                        }
                    },
                    onAdvanceChord = {
                        LiveInstrumentBus.allNotesOff()
                        cursorBeat = maxOf(
                            cursorBeat + selectedDuration.beats,
                            ScoreTimeline.endBeat(events),
                        )
                    },
                    onOctaveDown = { changePianoOctave(-1) },
                    onOctaveUp = { changePianoOctave(1) },
                    onPitchDown = { pitch ->
                        insertStepNote(pitch, preview = false)
                        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) {
                            playback.previewPitch(pitch)
                        }
                    },
                    onPitchUp = { pitch -> LiveInstrumentBus.noteOff(pitch) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    noteCount: Int,
    restCount: Int,
    measureCount: Int,
    bpm: Int,
    cursorBeat: Float,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    onTempoDown: () -> Unit,
    onTempoUp: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClear: () -> Unit,
) {
    val eventCount = noteCount + restCount

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge)
            Text(
                "Untitled • Piano • 4/4 • $bpm BPM • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = onTempoDown) { Text("−5") }
        Text("$bpm", style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = onTempoUp) { Text("+5") }

        if (isPlaying) {
            Button(onClick = onStop) { Text("Stop") }
        } else {
            Button(onClick = onPlay, enabled = noteCount > 0) { Text("Play") }
        }

        OutlinedButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
        OutlinedButton(onClick = onRedo, enabled = canRedo) { Text("Redo") }
        OutlinedButton(onClick = onClear, enabled = eventCount > 0) { Text("Clear") }
    }
}

private fun formatBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)

@Composable
private fun DurationSelector(
    selected: NoteDuration,
    sharpInput: Boolean,
    onSelected: (NoteDuration) -> Unit,
    onInsertRest: () -> Unit,
    onToggleSharpInput: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Duration:", style = MaterialTheme.typography.labelLarge)
        NoteDuration.entries.forEach { duration ->
            if (duration == selected) {
                Button(onClick = { onSelected(duration) }) { Text(duration.displayName) }
            } else {
                OutlinedButton(onClick = { onSelected(duration) }) { Text(duration.displayName) }
            }
        }

        Button(onClick = onInsertRest) { Text("Rest") }

        if (sharpInput) {
            Button(onClick = onToggleSharpInput) { Text("Staff ♯") }
        } else {
            OutlinedButton(onClick = onToggleSharpInput) { Text("Staff ♯") }
        }

        Spacer(modifier = Modifier.weight(1f))
        Text(
            if (sharpInput) {
                "Staff tap enters sharps at tapped beat • drag to move • long-press delete"
            } else {
                "Staff tap enters naturals at tapped beat • drag to move • long-press delete"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
