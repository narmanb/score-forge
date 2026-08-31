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
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

@Composable
fun ScoreForgeComposerScreen() {
    val context = LocalContext.current
    val tracks = remember { mutableStateListOf(ScoreTracks.defaultTrack()) }
    val playback = remember { ScorePlaybackEngine() }
    val soundFontEngine = remember { SoundFontEngine.createOrNull() }
    val editHistory = remember { ScoreEditHistory() }
    var activeTrackIndex by remember { mutableIntStateOf(0) }
    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    var chordMode by remember { mutableStateOf(false) }
    var pianoOctaveShift by remember { mutableIntStateOf(0) }
    var staffSharpInput by remember { mutableStateOf(false) }
    var draftLoaded by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }

    val safeActiveTrackIndex = activeTrackIndex.coerceIn(0, tracks.lastIndex)
    val activeTrack = tracks[safeActiveTrackIndex]
    val activeEvents = activeTrack.events
    val activeCursorBeat = activeTrack.cursorBeat
    val activeNoteCount = activeEvents.count { it is ScoreNote }
    val activeRestCount = activeEvents.count { it is ScoreRest }
    val draftTracks = tracks.map { it.copy(events = it.events.toList()) }
    val arrangementEndBeat = tracks.maxOfOrNull { ScoreTimeline.endBeat(it.events) } ?: 0f
    val playableNoteCount = tracks.filterNot { it.muted }.sumOf { track ->
        track.events.count { it is ScoreNote }
    }

    fun syncHistoryButtons() {
        canUndo = editHistory.canUndo
        canRedo = editHistory.canRedo
    }

    fun activeIndex(): Int = activeTrackIndex.coerceIn(0, tracks.lastIndex)

    fun currentTrack(): ScoreTrack = tracks[activeIndex()]

    fun replaceTrack(index: Int, updated: ScoreTrack) {
        if (index in tracks.indices) tracks[index] = updated.normalized()
    }

    fun replaceActiveTrack(transform: (ScoreTrack) -> ScoreTrack) {
        val index = activeIndex()
        replaceTrack(index, transform(tracks[index]))
    }

    fun currentEditState(): ScoreEditState {
        val index = activeIndex()
        val current = tracks[index]
        return ScoreEditState(
            events = current.events,
            cursorBeat = current.cursorBeat,
            tracks = tracks.map { it.copy(events = it.events.toList()) },
            activeTrackIndex = index,
        )
    }

    fun recordBeforeScoreEdit() {
        editHistory.recordBeforeChange(currentEditState())
        syncHistoryButtons()
    }

    fun restoreEditState(state: ScoreEditState) {
        val restoredTracks = state.tracks.ifEmpty { listOf(ScoreTracks.defaultTrack()) }
            .take(ScoreTracks.MAX_TRACKS)
            .map { it.normalized() }
        tracks.clear()
        tracks.addAll(restoredTracks)
        activeTrackIndex = state.activeTrackIndex.coerceIn(0, tracks.lastIndex)
    }

    LaunchedEffect(Unit) {
        val restored = withContext(Dispatchers.IO) {
            ScoreProjectRepository.loadDraft(context)
        }
        if (restored != null) {
            val restoredTracks = restored.effectiveTracks()
            tracks.clear()
            tracks.addAll(restoredTracks)
            activeTrackIndex = restored.activeTrackIndex.coerceIn(0, tracks.lastIndex)
            bpm = restored.bpm
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
        draftTracks,
        activeTrackIndex,
        bpm,
        selectedDuration,
        pianoOctaveShift,
        staffSharpInput,
    ) {
        if (!draftLoaded || draftTracks.isEmpty()) return@LaunchedEffect
        delay(250L)
        val safeIndex = activeTrackIndex.coerceIn(0, draftTracks.lastIndex)
        val active = draftTracks[safeIndex]
        val snapshot = ScoreProjectSnapshot(
            events = active.events,
            bpm = bpm,
            cursorBeat = active.cursorBeat,
            selectedDuration = selectedDuration,
            pianoOctaveShift = pianoOctaveShift,
            staffSharpInput = staffSharpInput,
            tracks = draftTracks,
            activeTrackIndex = safeIndex,
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
        val track = currentTrack()
        val quantizedStart = ScoreTimeline.quantizeBeat(startBeat)
        val note = ScoreNote(
            midiPitch = pitch,
            duration = selectedDuration,
            startBeat = quantizedStart,
        )
        val nextCursor = when {
            advanceCursor && chordMode -> track.cursorBeat
            advanceCursor -> quantizedStart + selectedDuration.beats
            else -> maxOf(track.cursorBeat, quantizedStart + selectedDuration.beats)
        }
        replaceActiveTrack {
            it.copy(
                events = it.events + note,
                cursorBeat = nextCursor,
            )
        }
        if (preview) playback.previewPitch(pitch)
    }

    fun insertStepNote(pitch: Int, preview: Boolean) {
        val track = currentTrack()
        insertNoteAt(
            pitch = pitch,
            startBeat = track.cursorBeat,
            preview = preview,
            advanceCursor = true,
        )
    }

    fun insertRest() {
        recordBeforeScoreEdit()
        LiveInstrumentBus.allNotesOff()
        val track = currentTrack()
        val rest = ScoreRest(
            duration = selectedDuration,
            startBeat = track.cursorBeat,
        )
        replaceActiveTrack {
            it.copy(
                events = it.events + rest,
                cursorBeat = track.cursorBeat + selectedDuration.beats,
            )
        }
    }

    fun changePianoOctave(delta: Int) {
        LiveInstrumentBus.allNotesOff()
        pianoOctaveShift = (pianoOctaveShift + delta).coerceIn(-4, 3)
    }

    fun deleteEvent(eventIndex: Int) {
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        val track = currentTrack()
        if (eventIndex in track.events.indices) {
            recordBeforeScoreEdit()
            val updatedEvents = track.events.toMutableList().apply { removeAt(eventIndex) }
            replaceActiveTrack {
                it.copy(
                    events = updatedEvents,
                    cursorBeat = if (chordMode) {
                        it.cursorBeat
                    } else {
                        ScoreTimeline.endBeat(updatedEvents)
                    },
                )
            }
        }
    }

    fun selectTrack(index: Int) {
        if (index !in tracks.indices || index == activeIndex()) return
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        activeTrackIndex = index
    }

    fun addTrack() {
        if (tracks.size >= ScoreTracks.MAX_TRACKS) return
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        val preset = soundFontEngine?.selectedPreset
        val newTrack = ScoreTracks.newTrack(tracks).copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
        )
        tracks.add(newTrack)
        activeTrackIndex = tracks.lastIndex
        syncHistoryButtons()
    }

    fun toggleActiveTrackMute() {
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(muted = !it.muted) }
    }

    fun deleteActiveTrack() {
        if (tracks.size <= 1) return
        stopPlayback()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        val index = activeIndex()
        tracks.removeAt(index)
        activeTrackIndex = index.coerceAtMost(tracks.lastIndex)
        syncHistoryButtons()
    }

    fun setActiveTrackPreset(bank: Int, program: Int, recordHistory: Boolean) {
        val track = currentTrack()
        if (track.presetBank == bank && track.presetProgram == program) return
        if (recordHistory) recordBeforeScoreEdit()
        replaceActiveTrack {
            it.copy(
                presetBank = bank,
                presetProgram = program,
            )
        }
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderBar(
                    activeTrackName = activeTrack.name,
                    trackCount = tracks.size,
                    noteCount = activeNoteCount,
                    restCount = activeRestCount,
                    measureCount = ScoreTimeline.measureCount(emptyList(), arrangementEndBeat),
                    bpm = bpm,
                    cursorBeat = activeCursorBeat,
                    isPlaying = isPlaying,
                    canUndo = canUndo,
                    canRedo = canRedo,
                    canPlay = playableNoteCount > 0,
                    onTempoDown = { bpm = (bpm - 5).coerceAtLeast(30) },
                    onTempoUp = { bpm = (bpm + 5).coerceAtMost(300) },
                    onPlay = {
                        if (playableNoteCount > 0) {
                            isPlaying = true
                            playback.playTracks(
                                tracks = tracks,
                                bpm = bpm,
                                throughBeat = ScoreTracks.endBeat(tracks),
                            ) { isPlaying = false }
                        }
                    },
                    onStop = ::stopPlayback,
                    onUndo = ::undoScore,
                    onRedo = ::redoScore,
                    onClearTrack = {
                        val track = currentTrack()
                        if (track.events.isNotEmpty()) {
                            stopPlayback()
                            LiveInstrumentBus.allNotesOff()
                            recordBeforeScoreEdit()
                            replaceActiveTrack {
                                it.copy(events = emptyList(), cursorBeat = 0f)
                            }
                        }
                    },
                )

                TrackControls(
                    tracks = tracks,
                    activeTrackIndex = safeActiveTrackIndex,
                    onSelectTrack = ::selectTrack,
                    onAddTrack = ::addTrack,
                    onToggleMute = ::toggleActiveTrackMute,
                    onDeleteTrack = ::deleteActiveTrack,
                )

                SoundFontControls(
                    engine = soundFontEngine,
                    requestedPresetBank = activeTrack.presetBank,
                    requestedPresetProgram = activeTrack.presetProgram,
                    onSoundFontLoaded = { _, preset ->
                        if (preset != null) {
                            setActiveTrackPreset(
                                bank = preset.bank,
                                program = preset.program,
                                recordHistory = false,
                            )
                        }
                    },
                    onPresetSelected = { preset ->
                        setActiveTrackPreset(
                            bank = preset.bank,
                            program = preset.program,
                            recordHistory = true,
                        )
                    },
                )

                DurationSelector(
                    selected = selectedDuration,
                    sharpInput = staffSharpInput,
                    onSelected = { selectedDuration = it },
                    onInsertRest = ::insertRest,
                    onToggleSharpInput = { staffSharpInput = !staffSharpInput },
                )

                ScoreStaffEditor(
                    events = activeEvents,
                    selectedDuration = selectedDuration,
                    cursorBeat = activeCursorBeat,
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
                        val track = currentTrack()
                        val note = track.events.getOrNull(eventIndex) as? ScoreNote
                        if (note != null) {
                            val updatedEvents = track.events.toMutableList().apply {
                                this[eventIndex] = note.copy(
                                    midiPitch = pitch,
                                    startBeat = startBeat,
                                )
                            }
                            replaceActiveTrack {
                                it.copy(
                                    events = updatedEvents,
                                    cursorBeat = if (chordMode) {
                                        it.cursorBeat
                                    } else {
                                        ScoreTimeline.endBeat(updatedEvents)
                                    },
                                )
                            }
                        }
                    },
                    onMoveRest = { eventIndex, startBeat ->
                        val track = currentTrack()
                        val rest = track.events.getOrNull(eventIndex) as? ScoreRest
                        if (rest != null) {
                            val updatedEvents = track.events.toMutableList().apply {
                                this[eventIndex] = rest.copy(startBeat = startBeat)
                            }
                            replaceActiveTrack {
                                it.copy(
                                    events = updatedEvents,
                                    cursorBeat = if (chordMode) {
                                        it.cursorBeat
                                    } else {
                                        ScoreTimeline.endBeat(updatedEvents)
                                    },
                                )
                            }
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
                        val track = currentTrack()
                        if (chordMode) {
                            chordMode = false
                            replaceActiveTrack {
                                it.copy(cursorBeat = maxOf(it.cursorBeat, ScoreTimeline.endBeat(it.events)))
                            }
                        } else {
                            chordMode = true
                            replaceActiveTrack {
                                it.copy(cursorBeat = ScoreTimeline.endBeat(track.events))
                            }
                        }
                    },
                    onAdvanceChord = {
                        LiveInstrumentBus.allNotesOff()
                        replaceActiveTrack {
                            it.copy(
                                cursorBeat = maxOf(
                                    it.cursorBeat + selectedDuration.beats,
                                    ScoreTimeline.endBeat(it.events),
                                )
                            )
                        }
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
    activeTrackName: String,
    trackCount: Int,
    noteCount: Int,
    restCount: Int,
    measureCount: Int,
    bpm: Int,
    cursorBeat: Float,
    isPlaying: Boolean,
    canUndo: Boolean,
    canRedo: Boolean,
    canPlay: Boolean,
    onTempoDown: () -> Unit,
    onTempoUp: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onClearTrack: () -> Unit,
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
                "Untitled • $activeTrackName • $trackCount tracks • 4/4 • $bpm BPM • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",
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
            Button(onClick = onPlay, enabled = canPlay) { Text("Play") }
        }

        OutlinedButton(onClick = onUndo, enabled = canUndo) { Text("Undo") }
        OutlinedButton(onClick = onRedo, enabled = canRedo) { Text("Redo") }
        OutlinedButton(onClick = onClearTrack, enabled = eventCount > 0) { Text("Clear Track") }
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
