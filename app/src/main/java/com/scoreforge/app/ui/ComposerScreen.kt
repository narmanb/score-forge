package com.scoreforge.app.ui

import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.LiveInstrumentBus
import com.scoreforge.app.audio.ScorePlaybackEngine
import com.scoreforge.app.audio.ScoreTransportBus
import com.scoreforge.app.audio.SoundFontEngine
import com.scoreforge.app.music.LiveEntryTiming
import com.scoreforge.app.music.NaturalEntryTiming
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreEditHistory
import com.scoreforge.app.music.ScoreEditState
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreProjectRepository
import com.scoreforge.app.music.ScoreProjectSnapshot
import com.scoreforge.app.music.ScoreRest
import com.scoreforge.app.music.ScoreTies
import com.scoreforge.app.music.ScoreTimeline
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class NaturalHeldInput(
    val startedAtMs: Long,
    val bpmAtPress: Int,
)

private data class LiveHeldInput(
    val eventIndex: Int,
    val startedAtMs: Long,
    val bpmAtPress: Int,
)

@Composable
fun ScoreForgeComposerScreen() {
    val context = LocalContext.current
    val pageScrollState = rememberScrollState()
    val tracks = remember { mutableStateListOf(ScoreTracks.defaultTrack()) }
    val playback = remember { ScorePlaybackEngine() }
    val soundFontEngine = remember { SoundFontEngine.createOrNull() }
    val editHistory = remember { ScoreEditHistory() }
    val naturalHeldInputs = remember { mutableMapOf<Int, NaturalHeldInput>() }
    val liveHeldInputs = remember { mutableMapOf<Int, LiveHeldInput>() }

    var activeTrackIndex by remember { mutableIntStateOf(0) }
    var selectedEventIndex by remember { mutableIntStateOf(-1) }
    var projectName by remember { mutableStateOf("Untitled") }
    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var selectedDotted by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    var chordMode by remember { mutableStateOf(false) }
    var pianoEntryMode by remember { mutableStateOf(PianoEntryMode.STEP) }
    var naturalGroupStartBeat by remember { mutableStateOf<Float?>(null) }
    var naturalGroupMaxBeats by remember { mutableStateOf(0f) }
    var liveRecordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var liveRecordingStartBeat by remember { mutableFloatStateOf(0f) }
    var liveRecordingBpm by remember { mutableIntStateOf(120) }
    var pianoOctaveShift by remember { mutableIntStateOf(0) }
    var staffSharpInput by remember { mutableStateOf(false) }
    var editorMode by remember { mutableStateOf(ScoreEditorMode.STAFF) }
    var showPianoKeyboard by remember { mutableStateOf(true) }
    var draftLoaded by remember { mutableStateOf(false) }
    var canUndo by remember { mutableStateOf(false) }
    var canRedo by remember { mutableStateOf(false) }
    var mixerGestureHistoryRecorded by remember { mutableStateOf(false) }

    val safeActiveTrackIndex = activeTrackIndex.coerceIn(0, tracks.lastIndex)
    val activeTrack = tracks[safeActiveTrackIndex]
    val activeEvents = activeTrack.events
    val activeCursorBeat = activeTrack.cursorBeat
    val activeNoteCount = activeEvents.count { it is ScoreNote }
    val activeRestCount = activeEvents.count { it is ScoreRest }
    val draftTracks = tracks.map { it.copy(events = it.events.toList()) }
    val arrangementEndBeat = tracks.maxOfOrNull { ScoreTimeline.endBeat(it.events) } ?: 0f
    val audibleTracks = ScoreTracks.audibleTracks(tracks)
    val playableNoteCount = audibleTracks.sumOf { track -> track.events.count { it is ScoreNote } }
    val selectedEvent = activeEvents.getOrNull(selectedEventIndex)
    val selectedTieActive =
        (selectedEvent as? ScoreNote)?.tieToNext == true &&
            ScoreTies.hasValidTie(activeEvents, selectedEventIndex)
    val canTieSelected = ScoreTies.canToggle(activeEvents, selectedEventIndex)
    val liveRecordingActive = liveRecordingStartedAtMs != null

    fun syncHistoryButtons() {
        canUndo = editHistory.canUndo
        canRedo = editHistory.canRedo
    }

    fun activeIndex(): Int = activeTrackIndex.coerceIn(0, tracks.lastIndex)
    fun currentTrack(): ScoreTrack = tracks[activeIndex()]
    fun selectedInputBeats(): Float = selectedDuration.effectiveBeats(selectedDotted)

    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalGroupStartBeat = null
        naturalGroupMaxBeats = 0f
    }

    fun replaceTrack(index: Int, updated: ScoreTrack) {
        if (index in tracks.indices) tracks[index] = updated.normalized()
    }

    fun replaceActiveTrack(transform: (ScoreTrack) -> ScoreTrack) {
        val index = activeIndex()
        replaceTrack(index, transform(tracks[index]))
    }

    fun currentProjectSnapshot(): ScoreProjectSnapshot {
        val frozenTracks = tracks.map { it.copy(events = it.events.toList()) }
        val index = activeTrackIndex.coerceIn(0, frozenTracks.lastIndex)
        val current = frozenTracks[index]
        return ScoreProjectSnapshot(
            events = current.events,
            bpm = bpm,
            cursorBeat = current.cursorBeat,
            selectedDuration = selectedDuration,
            selectedDotted = selectedDotted,
            pianoOctaveShift = pianoOctaveShift,
            staffSharpInput = staffSharpInput,
            tracks = frozenTracks,
            activeTrackIndex = index,
            projectName = projectName,
        )
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

    fun finishLivePitch(pitch: Int, finishedAtMs: Long = SystemClock.elapsedRealtime()) {
        LiveInstrumentBus.noteOff(pitch)
        val held = liveHeldInputs.remove(pitch) ?: return
        val track = currentTrack()
        val note = track.events.getOrNull(held.eventIndex) as? ScoreNote ?: return
        val written = LiveEntryTiming.quantizedDurationForHoldMs(
            holdMs = finishedAtMs - held.startedAtMs,
            bpm = held.bpmAtPress,
        )
        val updatedEvents = track.events.toMutableList().apply {
            this[held.eventIndex] = note.copy(
                duration = written.duration,
                dotted = written.dotted,
            )
        }
        replaceActiveTrack {
            it.copy(
                events = updatedEvents,
                cursorBeat = maxOf(it.cursorBeat, ScoreTimeline.endBeat(updatedEvents)),
            )
        }
        selectedEventIndex = held.eventIndex
    }

    fun stopLiveRecording() {
        val startedAt = liveRecordingStartedAtMs ?: run {
            liveHeldInputs.clear()
            return
        }
        val now = SystemClock.elapsedRealtime()
        liveHeldInputs.keys.toList().forEach { pitch -> finishLivePitch(pitch, now) }
        LiveInstrumentBus.allNotesOff()
        val finalBeat = LiveEntryTiming.beatAtElapsedMs(
            startBeat = liveRecordingStartBeat,
            elapsedMs = now - startedAt,
            bpm = liveRecordingBpm,
        )
        replaceActiveTrack { it.copy(cursorBeat = maxOf(it.cursorBeat, finalBeat)) }
        ScoreTransportBus.finish(finalBeat)
        liveRecordingStartedAtMs = null
        liveHeldInputs.clear()
        syncHistoryButtons()
    }

    fun cancelLiveRecording() {
        if (liveRecordingStartedAtMs == null && liveHeldInputs.isEmpty()) return
        liveHeldInputs.keys.toList().forEach { LiveInstrumentBus.noteOff(it) }
        liveHeldInputs.clear()
        LiveInstrumentBus.allNotesOff()
        ScoreTransportBus.stop()
        liveRecordingStartedAtMs = null
    }

    fun beginLivePitch(pitch: Int) {
        if (liveHeldInputs.containsKey(pitch)) return
        val now = SystemClock.elapsedRealtime()
        if (liveRecordingStartedAtMs == null) {
            recordBeforeScoreEdit()
            liveRecordingStartBeat = ScoreTransportBus.state.value.beat.coerceAtLeast(0f)
            liveRecordingBpm = bpm
            liveRecordingStartedAtMs = now
            ScoreTransportBus.begin(liveRecordingStartBeat, Float.MAX_VALUE)
        }
        val startedAt = liveRecordingStartedAtMs ?: now
        val noteStartBeat = LiveEntryTiming.quantizedBeatAtElapsedMs(
            startBeat = liveRecordingStartBeat,
            elapsedMs = now - startedAt,
            bpm = liveRecordingBpm,
        )
        val eventIndex = currentTrack().events.size
        replaceActiveTrack {
            it.copy(
                events = it.events + ScoreNote(
                    midiPitch = pitch,
                    duration = NoteDuration.SIXTEENTH,
                    startBeat = noteStartBeat,
                    dotted = false,
                ),
                cursorBeat = maxOf(it.cursorBeat, noteStartBeat + NoteDuration.SIXTEENTH.beats),
            )
        }
        liveHeldInputs[pitch] = LiveHeldInput(
            eventIndex = eventIndex,
            startedAtMs = now,
            bpmAtPress = liveRecordingBpm,
        )
        selectedEventIndex = eventIndex
        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) playback.previewPitch(pitch)
    }

    fun restoreEditState(state: ScoreEditState) {
        val restoredTracks = state.tracks.ifEmpty { listOf(ScoreTracks.defaultTrack()) }
            .take(ScoreTracks.MAX_TRACKS)
            .map { it.normalized() }
        tracks.clear()
        tracks.addAll(restoredTracks)
        activeTrackIndex = state.activeTrackIndex.coerceIn(0, tracks.lastIndex)
        selectedEventIndex = -1
        mixerGestureHistoryRecorded = false
    }

    fun applyProjectSnapshot(snapshot: ScoreProjectSnapshot, clearHistory: Boolean) {
        cancelNaturalEntryGroup()
        cancelLiveRecording()
        val restoredTracks = snapshot.effectiveTracks()
        tracks.clear()
        tracks.addAll(restoredTracks)
        activeTrackIndex = snapshot.effectiveActiveTrackIndex()
        selectedEventIndex = -1
        projectName = snapshot.safeProjectName()
        bpm = snapshot.bpm.coerceIn(30, 300)
        selectedDuration = snapshot.selectedDuration
        selectedDotted = snapshot.selectedDotted
        pianoOctaveShift = snapshot.pianoOctaveShift.coerceIn(-4, 3)
        staffSharpInput = snapshot.staffSharpInput
        chordMode = false
        pianoEntryMode = PianoEntryMode.STEP
        mixerGestureHistoryRecorded = false
        if (clearHistory) editHistory.clear()
        syncHistoryButtons()
    }

    LaunchedEffect(Unit) {
        val restored = withContext(Dispatchers.IO) { ScoreProjectRepository.loadDraft(context) }
        if (restored != null) {
            applyProjectSnapshot(restored, clearHistory = true)
        } else {
            editHistory.clear()
            syncHistoryButtons()
        }
        draftLoaded = true
    }

    LaunchedEffect(
        draftLoaded,
        draftTracks,
        activeTrackIndex,
        projectName,
        bpm,
        selectedDuration,
        selectedDotted,
        pianoOctaveShift,
        staffSharpInput,
    ) {
        if (!draftLoaded || draftTracks.isEmpty()) return@LaunchedEffect
        delay(250L)
        val snapshot = currentProjectSnapshot()
        withContext(Dispatchers.IO) { ScoreProjectRepository.saveDraft(context, snapshot) }
    }

    LaunchedEffect(pianoEntryMode, liveRecordingStartedAtMs) {
        val startedAt = liveRecordingStartedAtMs ?: return@LaunchedEffect
        while (
            pianoEntryMode == PianoEntryMode.LIVE &&
            liveRecordingStartedAtMs == startedAt
        ) {
            val now = SystemClock.elapsedRealtime()
            val playheadBeat = LiveEntryTiming.beatAtElapsedMs(
                startBeat = liveRecordingStartBeat,
                elapsedMs = now - startedAt,
                bpm = liveRecordingBpm,
            )
            ScoreTransportBus.progress(playheadBeat)

            if (liveHeldInputs.isNotEmpty()) {
                val track = currentTrack()
                val updatedEvents = track.events.toMutableList()
                var changed = false
                liveHeldInputs.values.forEach { held ->
                    val note = updatedEvents.getOrNull(held.eventIndex) as? ScoreNote
                        ?: return@forEach
                    val written = LiveEntryTiming.quantizedDurationForHoldMs(
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
                }
                if (changed) {
                    replaceActiveTrack {
                        it.copy(
                            events = updatedEvents,
                            cursorBeat = maxOf(it.cursorBeat, ScoreTimeline.endBeat(updatedEvents)),
                        )
                    }
                }
            }
            delay(33L)
        }
    }

    LaunchedEffect(activeTrack.id, activeTrack.volume, activeTrack.pan) {
        LiveInstrumentBus.setMixer(activeTrack.volume, activeTrack.pan)
    }

    DisposableEffect(playback, soundFontEngine) {
        playback.setSoundFontEngine(soundFontEngine)
        onDispose {
            cancelNaturalEntryGroup()
            cancelLiveRecording()
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

    fun openProject(snapshot: ScoreProjectSnapshot) {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        applyProjectSnapshot(snapshot, clearHistory = true)
    }

    fun newProject() {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        val preset = soundFontEngine?.selectedPreset
        val blankTrack = ScoreTracks.defaultTrack().copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
        )
        applyProjectSnapshot(
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

    fun renameProject(name: String) {
        projectName = ScoreProjectSnapshot.sanitizeProjectName(name)
    }

    fun undoScore() {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        val restored = editHistory.undo(currentEditState()) ?: return
        restoreEditState(restored)
        syncHistoryButtons()
    }

    fun redoScore() {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        val restored = editHistory.redo(currentEditState()) ?: return
        restoreEditState(restored)
        syncHistoryButtons()
    }

    fun selectEvent(eventIndex: Int) {
        selectedEventIndex = if (eventIndex in currentTrack().events.indices) eventIndex else -1
    }

    fun toggleSelectedTie() {
        val index = selectedEventIndex
        val updatedEvents = ScoreTies.toggle(currentTrack().events, index) ?: return
        stopPlayback()
        stopLiveRecording()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(events = updatedEvents) }
        selectedEventIndex = index.coerceAtMost(currentTrack().events.lastIndex)
    }

    fun insertNoteAt(pitch: Int, startBeat: Float, preview: Boolean, advanceCursor: Boolean) {
        recordBeforeScoreEdit()
        val track = currentTrack()
        val newEventIndex = track.events.size
        val quantizedStart = ScoreTimeline.quantizeBeat(startBeat)
        val inputBeats = selectedInputBeats()
        val note = ScoreNote(
            midiPitch = pitch,
            duration = selectedDuration,
            startBeat = quantizedStart,
            dotted = selectedDotted,
        )
        val nextCursor = when {
            advanceCursor && chordMode -> track.cursorBeat
            advanceCursor -> quantizedStart + inputBeats
            else -> maxOf(track.cursorBeat, quantizedStart + inputBeats)
        }
        replaceActiveTrack { it.copy(events = it.events + note, cursorBeat = nextCursor) }
        selectedEventIndex = newEventIndex
        if (preview) playback.previewPitch(pitch)
    }

    fun insertStepNote(pitch: Int, preview: Boolean) {
        val track = currentTrack()
        insertNoteAt(pitch, track.cursorBeat, preview, advanceCursor = true)
    }

    fun insertRest() {
        recordBeforeScoreEdit()
        LiveInstrumentBus.allNotesOff()
        val track = currentTrack()
        val newEventIndex = track.events.size
        val inputBeats = selectedInputBeats()
        val rest = ScoreRest(
            duration = selectedDuration,
            startBeat = track.cursorBeat,
            dotted = selectedDotted,
        )
        replaceActiveTrack {
            it.copy(events = it.events + rest, cursorBeat = track.cursorBeat + inputBeats)
        }
        selectedEventIndex = newEventIndex
    }

    fun beginNaturalPitch(pitch: Int) {
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

    fun changePianoOctave(delta: Int) {
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        pianoOctaveShift = (pianoOctaveShift + delta).coerceIn(-4, 3)
    }

    fun deleteEvent(eventIndex: Int) {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        val track = currentTrack()
        if (eventIndex in track.events.indices) {
            recordBeforeScoreEdit()
            val updatedEvents = track.events.toMutableList().apply { removeAt(eventIndex) }
            replaceActiveTrack {
                it.copy(
                    events = updatedEvents,
                    cursorBeat = if (chordMode) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents),
                )
            }
            selectedEventIndex = -1
        }
    }

    fun moveActiveNote(eventIndex: Int, pitch: Int, startBeat: Float) {
        val track = currentTrack()
        val note = track.events.getOrNull(eventIndex) as? ScoreNote ?: return
        val updatedEvents = track.events.toMutableList().apply {
            this[eventIndex] = note.copy(midiPitch = pitch, startBeat = startBeat)
        }
        replaceActiveTrack {
            it.copy(
                events = updatedEvents,
                cursorBeat = if (chordMode) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents),
            )
        }
        selectedEventIndex = eventIndex.coerceAtMost(currentTrack().events.lastIndex)
    }

    fun moveActiveRest(eventIndex: Int, startBeat: Float) {
        val track = currentTrack()
        val rest = track.events.getOrNull(eventIndex) as? ScoreRest ?: return
        val updatedEvents = track.events.toMutableList().apply {
            this[eventIndex] = rest.copy(startBeat = startBeat)
        }
        replaceActiveTrack {
            it.copy(
                events = updatedEvents,
                cursorBeat = if (chordMode) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents),
            )
        }
        selectedEventIndex = eventIndex.coerceAtMost(currentTrack().events.lastIndex)
    }

    fun selectTrack(index: Int) {
        if (index !in tracks.indices || index == activeIndex()) return
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        mixerGestureHistoryRecorded = false
        selectedEventIndex = -1
        activeTrackIndex = index
    }

    fun addTrack() {
        if (tracks.size >= ScoreTracks.MAX_TRACKS) return
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        val preset = soundFontEngine?.selectedPreset
        val newTrack = ScoreTracks.newTrack(tracks).copy(
            presetBank = preset?.bank,
            presetProgram = preset?.program,
        )
        tracks.add(newTrack)
        activeTrackIndex = tracks.lastIndex
        selectedEventIndex = -1
        mixerGestureHistoryRecorded = false
        syncHistoryButtons()
    }

    fun renameActiveTrack(name: String) {
        val safeName = name.replace('\t', ' ').replace('\n', ' ').trim().take(80)
        if (safeName.isBlank() || safeName == currentTrack().name) return
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(name = safeName) }
    }

    fun toggleActiveTrackMute() {
        stopPlayback()
        stopLiveRecording()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(muted = !it.muted) }
    }

    fun toggleActiveTrackSolo() {
        stopPlayback()
        stopLiveRecording()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(solo = !it.solo) }
    }

    fun beginMixerGestureIfNeeded() {
        if (!mixerGestureHistoryRecorded) {
            recordBeforeScoreEdit()
            mixerGestureHistoryRecorded = true
        }
    }

    fun changeActiveTrackVolume(volume: Int) {
        val safeVolume = volume.coerceIn(ScoreTrack.MIN_VOLUME, ScoreTrack.MAX_VOLUME)
        if (safeVolume == currentTrack().volume) return
        beginMixerGestureIfNeeded()
        replaceActiveTrack { it.copy(volume = safeVolume) }
    }

    fun changeActiveTrackPan(pan: Int) {
        val safePan = pan.coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN)
        if (safePan == currentTrack().pan) return
        beginMixerGestureIfNeeded()
        replaceActiveTrack { it.copy(pan = safePan) }
    }

    fun finishMixerGesture() {
        mixerGestureHistoryRecorded = false
        syncHistoryButtons()
    }

    fun deleteActiveTrack() {
        if (tracks.size <= 1) return
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        val index = activeIndex()
        tracks.removeAt(index)
        activeTrackIndex = index.coerceAtMost(tracks.lastIndex)
        selectedEventIndex = -1
        mixerGestureHistoryRecorded = false
        syncHistoryButtons()
    }

    fun setActiveTrackPreset(bank: Int, program: Int, recordHistory: Boolean) {
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

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .safeDrawingPadding()
                    .verticalScroll(pageScrollState),
            ) {
                HeaderBar(
                    projectName = projectName,
                    activeTrackName = activeTrack.name,
                    trackCount = tracks.size,
                    noteCount = activeNoteCount,
                    restCount = activeRestCount,
                    measureCount = ScoreTimeline.measureCount(emptyList(), arrangementEndBeat),
                    bpm = bpm,
                    cursorBeat = activeCursorBeat,
                    isPlaying = isPlaying,
                    canPlay = playableNoteCount > 0 && !liveRecordingActive,
                    onTempoDown = { bpm = (bpm - 5).coerceAtLeast(30) },
                    onTempoUp = { bpm = (bpm + 5).coerceAtMost(300) },
                    onPlay = {
                        if (playableNoteCount > 0 && !liveRecordingActive) {
                            isPlaying = true
                            playback.playTracks(
                                tracks = tracks,
                                bpm = bpm,
                                throughBeat = ScoreTracks.endBeat(tracks),
                            ) { isPlaying = false }
                        }
                    },
                    onStop = ::stopPlayback,
                )

                ProjectFileControls(
                    projectName = projectName,
                    activeTrackName = activeTrack.name,
                    canClearTrack = activeEvents.isNotEmpty(),
                    snapshotProvider = ::currentProjectSnapshot,
                    onNewProject = ::newProject,
                    onClearTrack = ::clearActiveTrack,
                    onRenameProject = ::renameProject,
                    onOpenProject = ::openProject,
                )

                TrackControls(
                    tracks = tracks,
                    activeTrackIndex = safeActiveTrackIndex,
                    onSelectTrack = ::selectTrack,
                    onAddTrack = ::addTrack,
                    onRenameTrack = ::renameActiveTrack,
                    onToggleMute = ::toggleActiveTrackMute,
                    onToggleSolo = ::toggleActiveTrackSolo,
                    onVolumeChange = ::changeActiveTrackVolume,
                    onVolumeChangeFinished = ::finishMixerGesture,
                    onPanChange = ::changeActiveTrackPan,
                    onPanChangeFinished = ::finishMixerGesture,
                    onDeleteTrack = ::deleteActiveTrack,
                )

                SoundFontControls(
                    engine = soundFontEngine,
                    requestedPresetBank = activeTrack.presetBank,
                    requestedPresetProgram = activeTrack.presetProgram,
                    onSoundFontLoaded = { _, preset ->
                        if (preset != null) {
                            setActiveTrackPreset(preset.bank, preset.program, recordHistory = false)
                        }
                    },
                    onPresetSelected = { preset ->
                        setActiveTrackPreset(preset.bank, preset.program, recordHistory = true)
                    },
                )

                DurationSelector(
                    selected = selectedDuration,
                    dotted = selectedDotted,
                    sharpInput = staffSharpInput,
                    tieEnabled = canTieSelected,
                    tieActive = selectedTieActive,
                    onSelected = { selectedDuration = it },
                    onToggleDotted = { selectedDotted = !selectedDotted },
                    onInsertRest = ::insertRest,
                    onToggleSharpInput = { staffSharpInput = !staffSharpInput },
                    onToggleTie = ::toggleSelectedTie,
                )

                EditorModeControls(
                    mode = editorMode,
                    showPianoKeyboard = showPianoKeyboard,
                    onModeChanged = { editorMode = it },
                    onTogglePianoKeyboard = {
                        stopLiveRecording()
                        cancelNaturalEntryGroup()
                        LiveInstrumentBus.allNotesOff()
                        showPianoKeyboard = !showPianoKeyboard
                    },
                )

                when (editorMode) {
                    ScoreEditorMode.STAFF -> ScoreStaffEditor(
                        events = activeEvents,
                        selectedDuration = selectedDuration,
                        cursorBeat = activeCursorBeat,
                        selectedEventIndex = selectedEventIndex,
                        onAddPitch = { naturalPitch, tappedBeat ->
                            val pitch = if (staffSharpInput) {
                                PitchNames.sharpenIfAvailable(naturalPitch)
                            } else {
                                naturalPitch
                            }
                            insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                        },
                        onSelectEvent = ::selectEvent,
                        onBeginMove = { recordBeforeScoreEdit() },
                        onMoveNote = ::moveActiveNote,
                        onMoveRest = ::moveActiveRest,
                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )

                    ScoreEditorMode.PIANO_ROLL -> PianoRollEditor(
                        events = activeEvents,
                        selectedDuration = selectedDuration,
                        cursorBeat = activeCursorBeat,
                        octaveShift = pianoOctaveShift,
                        selectedEventIndex = selectedEventIndex,
                        onAddPitch = { pitch, tappedBeat ->
                            insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                        },
                        onSelectEvent = ::selectEvent,
                        onBeginMove = { recordBeforeScoreEdit() },
                        onMoveNote = ::moveActiveNote,
                        onDeleteEvent = ::deleteEvent,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )
                }

                if (showPianoKeyboard) {
                    MultitouchPianoKeyboard(
                        chordMode = chordMode,
                        octaveShift = pianoOctaveShift,
                        entryMode = pianoEntryMode,
                        liveRecordingActive = liveRecordingActive,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = ::undoScore,
                        onRedo = ::redoScore,
                        onEntryModeChanged = { mode ->
                            if (pianoEntryMode == PianoEntryMode.LIVE) stopLiveRecording()
                            cancelNaturalEntryGroup()
                            LiveInstrumentBus.allNotesOff()
                            chordMode = false
                            if (mode == PianoEntryMode.LIVE) stopPlayback()
                            pianoEntryMode = mode
                        },
                        onStopLive = ::stopLiveRecording,
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
                                replaceActiveTrack { it.copy(cursorBeat = ScoreTimeline.endBeat(track.events)) }
                            }
                        },
                        onAdvanceChord = {
                            LiveInstrumentBus.allNotesOff()
                            replaceActiveTrack {
                                it.copy(
                                    cursorBeat = maxOf(
                                        it.cursorBeat + selectedInputBeats(),
                                        ScoreTimeline.endBeat(it.events),
                                    )
                                )
                            }
                        },
                        onInsertRest = {
                            if (pianoEntryMode != PianoEntryMode.LIVE) insertRest()
                        },
                        onOctaveDown = { changePianoOctave(-1) },
                        onOctaveUp = { changePianoOctave(1) },
                        onPitchDown = { pitch ->
                            when (pianoEntryMode) {
                                PianoEntryMode.STEP -> {
                                    insertStepNote(pitch, preview = false)
                                    if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) {
                                        playback.previewPitch(pitch)
                                    }
                                }
                                PianoEntryMode.NATURAL -> beginNaturalPitch(pitch)
                                PianoEntryMode.LIVE -> beginLivePitch(pitch)
                            }
                        },
                        onPitchUp = { pitch ->
                            when (pianoEntryMode) {
                                PianoEntryMode.STEP -> LiveInstrumentBus.noteOff(pitch)
                                PianoEntryMode.NATURAL -> finishNaturalPitch(pitch)
                                PianoEntryMode.LIVE -> finishLivePitch(pitch)
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(120.dp),
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun HeaderBar(
    projectName: String,
    activeTrackName: String,
    trackCount: Int,
    noteCount: Int,
    restCount: Int,
    measureCount: Int,
    bpm: Int,
    cursorBeat: Float,
    isPlaying: Boolean,
    canPlay: Boolean,
    onTempoDown: () -> Unit,
    onTempoUp: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
) {

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .background(ComposerControlStripColor)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Column {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge, color = Color.White)
            Text(
                "$projectName • $activeTrackName • $trackCount tracks • 4/4 • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE0DCE5),
            )
        }

        ChamferedControlButton(
            label = "−5",
            onClick = onTempoDown,
            enabled = bpm > 30,
            compact = false,
        )

        Surface(
            shape = ChamferedControlShape,
            color = Color(0xFF35323B),
            border = BorderStroke(1.dp, Color(0xFFE1DAE9)),
        ) {
            Text(
                "Tempo: $bpm BPM",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 11.dp),
                style = MaterialTheme.typography.labelLarge,
                color = Color.White,
            )
        }

        ChamferedControlButton(
            label = "+5",
            onClick = onTempoUp,
            enabled = bpm < 300,
            compact = false,
        )

        if (isPlaying) Button(onClick = onStop) { Text("Stop") }
        else Button(onClick = onPlay, enabled = canPlay) { Text("Play") }

    }
}

private fun formatBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)

@Composable
private fun DurationSelector(
    selected: NoteDuration,
    dotted: Boolean,
    sharpInput: Boolean,
    tieEnabled: Boolean,
    tieActive: Boolean,
    onSelected: (NoteDuration) -> Unit,
    onToggleDotted: () -> Unit,
    onInsertRest: () -> Unit,
    onToggleSharpInput: () -> Unit,
    onToggleTie: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Duration:", style = MaterialTheme.typography.labelLarge)
        NoteDuration.entries.forEach { duration ->
            if (duration == selected) Button(onClick = { onSelected(duration) }) { Text(duration.displayName) }
            else OutlinedButton(onClick = { onSelected(duration) }) { Text(duration.displayName) }
        }

        if (dotted) Button(onClick = onToggleDotted) { Text("Dot •") }
        else OutlinedButton(onClick = onToggleDotted) { Text("Dot") }

        OutlinedButton(onClick = onInsertRest) {
            Text(if (dotted) "Insert Dotted Rest" else "Insert Rest")
        }

        if (tieActive) Button(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }
        else OutlinedButton(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }

        if (sharpInput) Button(onClick = onToggleSharpInput) { Text("Staff ♯") }
        else OutlinedButton(onClick = onToggleSharpInput) { Text("Staff ♯") }

        Text(
            buildString {
                append(if (sharpInput) "Staff tap enters sharps" else "Staff tap enters naturals")
                if (dotted) append(" • dotted input on")
                append(" • tap note to select")
                if (tieEnabled) append(" • Tie → available")
                append(" • drag to move • long-press delete")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
