package com.scoreforge.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.SystemClock
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.scoreforge.app.ExternalOpenRequest
import com.scoreforge.app.audio.LiveInstrumentBus
import com.scoreforge.app.audio.ScoreForgeAudioSession
import com.scoreforge.app.audio.ScoreTransportBus
import com.scoreforge.app.music.ComfortTempo
import com.scoreforge.app.music.HoldDurationMode
import com.scoreforge.app.music.HoldDurationTiming
import com.scoreforge.app.music.HoldEntryTiming
import com.scoreforge.app.music.LiveEntryTiming
import com.scoreforge.app.music.NaturalEntryTiming
import com.scoreforge.app.music.NoteArticulation
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreClef
import com.scoreforge.app.music.ScoreClefMode
import com.scoreforge.app.music.ScoreClefs
import com.scoreforge.app.music.ScoreEditHistory
import com.scoreforge.app.music.ScoreEditState
import com.scoreforge.app.music.ScoreKeySignatures
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreProjectRepository
import com.scoreforge.app.music.ScoreProjectSnapshot
import com.scoreforge.app.music.ScoreRest
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTempos
import com.scoreforge.app.music.ScoreTimeSignatures
import com.scoreforge.app.music.ScoreTies
import com.scoreforge.app.music.ScoreTimeline
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.withContext

private data class NaturalHeldInput(
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

private data class HoldHeldInput(
    val eventIndex: Int,
    val groupOnsetMs: Long,
)

private data class HoldOnsetGroup(
    val onsetMs: Long,
    val startBeat: Float,
    val bpm: Int,
    val eventIndices: List<Int>,
    val currentWritten: NaturalEntryTiming.WrittenDuration,
)

private data class LiveHeldInput(
    val eventIndex: Int,
    val startedAtMs: Long,
    val bpmAtPress: Int,
)

@Composable
fun ScoreForgeComposerScreen(
    externalOpenRequest: ExternalOpenRequest? = null,
    onExternalOpenConsumed: (ExternalOpenRequest) -> Unit = {},
) {
    val context = LocalContext.current
    var appSettings by remember { mutableStateOf(ScoreForgeSettingsRepository.load(context)) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* Playback remains available even if notification permission is declined. */ }
    val pageScrollState = rememberScrollState()
    val tracks = remember { mutableStateListOf(ScoreTracks.defaultTrack().copy(clefMode = appSettings.defaultClefMode)) }
    val playback = remember { ScoreForgeAudioSession.playbackEngine }
    val soundFontEngine = remember { ScoreForgeAudioSession.soundFontEngine }
    val editHistory = remember { ScoreEditHistory() }
    val naturalHeldInputs = remember { mutableMapOf<Int, NaturalHeldInput>() }
    val holdHeldInputs = remember { mutableMapOf<Int, HoldHeldInput>() }
    val liveHeldInputs = remember { mutableMapOf<Int, LiveHeldInput>() }

    var activeTrackIndex by remember { mutableIntStateOf(0) }
    var selectedEventIndex by remember { mutableIntStateOf(-1) }
    var projectName by remember { mutableStateOf("Untitled") }
    var selectedDuration by rememberSaveable { mutableStateOf(NoteDuration.QUARTER) }
    var selectedDotted by rememberSaveable { mutableStateOf(false) }
    var selectedArticulation by rememberSaveable { mutableStateOf(NoteArticulation.NORMAL) }
    var bpm by rememberSaveable { mutableIntStateOf(120) }
    var tempoChanges by remember { mutableStateOf(listOf(ScoreTempos.DEFAULT)) }
    var comfortTempoCapturing by rememberSaveable { mutableStateOf(false) }
    var comfortTempoAttackTimes by remember { mutableStateOf(emptyList<Long>()) }
    var comfortTempoEstimate by rememberSaveable { mutableStateOf<Int?>(null) }
    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }
    var keySignatures by remember { mutableStateOf(listOf(ScoreKeySignatures.DEFAULT)) }
    var metronomeEnabled by rememberSaveable { mutableStateOf(false) }
    var isPlaying by remember { mutableStateOf(ScoreTransportBus.state.value.isPlaying) }
    var chordMode by rememberSaveable { mutableStateOf(StepChordMode.OFF) }
    var pianoEntryMode by rememberSaveable { mutableStateOf(appSettings.defaultEntryMode) }
    var holdDurationMode by rememberSaveable { mutableStateOf(HoldDurationMode.STANDARD) }
    var naturalCurrentGroup by remember { mutableStateOf<NaturalOnsetGroup?>(null) }
    var naturalRecentIntervalsMs by remember { mutableStateOf(emptyList<Long>()) }
    var holdCurrentGroup by remember { mutableStateOf<HoldOnsetGroup?>(null) }
    var holdPreviousGroup by remember { mutableStateOf<HoldOnsetGroup?>(null) }
    var holdPreviewWritten by remember { mutableStateOf<NaturalEntryTiming.WrittenDuration?>(null) }
    var liveRecordingStartedAtMs by remember { mutableStateOf<Long?>(null) }
    var liveRecordingStartBeat by remember { mutableFloatStateOf(0f) }
    var liveRecordingBpm by remember { mutableIntStateOf(120) }
    var pianoOctaveShift by rememberSaveable { mutableIntStateOf(if (appSettings.rememberKeyboardOctave) appSettings.rememberedKeyboardOctave else 0) }
    var staffSharpInput by rememberSaveable { mutableStateOf(false) }
    var editorMode by rememberSaveable { mutableStateOf(appSettings.defaultEditorMode) }
    var showPianoKeyboard by rememberSaveable { mutableStateOf(true) }
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
    val activeTempoBpm = ScoreTempos.atBeat(tempoChanges, activeCursorBeat).bpm

    LaunchedEffect(Unit) {
        ScoreTransportBus.state.collect { state ->
            isPlaying = state.isPlaying
        }
    }

    fun syncHistoryButtons() {
        canUndo = editHistory.canUndo
        canRedo = editHistory.canRedo
    }

    fun activeIndex(): Int = activeTrackIndex.coerceIn(0, tracks.lastIndex)
    fun currentTrack(): ScoreTrack = tracks[activeIndex()]
    fun selectedInputBeats(): Float = selectedDuration.effectiveBeats(selectedDotted)
    fun tempoBpmAt(beat: Float): Int = ScoreTempos.atBeat(tempoChanges, beat).bpm

    fun setTempoChange(startBeat: Float, newBpm: Int) {
        if (isPlaying) {
            ScoreForgeAudioSession.stopPlayback(context)
            isPlaying = false
        }
        tempoChanges = ScoreTempos.withChange(tempoChanges, startBeat, newBpm)
        bpm = tempoChanges.first().bpm
    }

    fun removeTempoChange(startBeat: Float) {
        if (isPlaying) {
            ScoreForgeAudioSession.stopPlayback(context)
            isPlaying = false
        }
        tempoChanges = ScoreTempos.withoutChange(tempoChanges, startBeat)
        bpm = tempoChanges.first().bpm
    }

    fun cancelNaturalEntryGroup() {
        naturalHeldInputs.clear()
        naturalCurrentGroup = null
        naturalRecentIntervalsMs = emptyList()
        holdHeldInputs.clear()
        holdCurrentGroup = null
        holdPreviousGroup = null
        holdPreviewWritten = null
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
            bpm = tempoChanges.first().bpm,
            tempoChanges = tempoChanges,
            cursorBeat = current.cursorBeat,
            selectedDuration = selectedDuration,
            selectedDotted = selectedDotted,
            selectedArticulation = selectedArticulation,
            pianoOctaveShift = pianoOctaveShift,
            staffSharpInput = staffSharpInput,
            tracks = frozenTracks,
            activeTrackIndex = index,
            projectName = projectName,
            timeSignatures = timeSignatures,
            keySignatures = keySignatures,
            metronomeEnabled = metronomeEnabled,
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
            LiveInstrumentBus.allNotesOff()
            if (!isPlaying) ScoreTransportBus.stop()
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
        liveHeldInputs.keys.toList().forEach { LiveInstrumentBus.noteOff(it) }
        liveHeldInputs.clear()
        LiveInstrumentBus.allNotesOff()
        ScoreTransportBus.stop()
        liveRecordingStartedAtMs = null
    }

    fun repairUnexpectedTransportForEntry() {
        val decision = TransportRepairPolicy.decide(
            isLiveMode = pianoEntryMode == PianoEntryMode.LIVE,
            liveRecordingActive = liveRecordingStartedAtMs != null,
            scorePlaybackActive = isPlaying,
            transportPlaying = ScoreTransportBus.state.value.isPlaying,
        )
        if (decision.cancelLiveRecording) cancelLiveRecording()
        if (decision.stopTransport) ScoreForgeAudioSession.stopPlayback(context)
    }

    fun beginLivePitch(pitch: Int) {
        if (liveHeldInputs.containsKey(pitch)) return
        val now = SystemClock.elapsedRealtime()
        if (liveRecordingStartedAtMs == null) {
            recordBeforeScoreEdit()
            liveRecordingStartBeat = ScoreTransportBus.state.value.beat.coerceAtLeast(0f)
            liveRecordingBpm = tempoBpmAt(liveRecordingStartBeat)
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
                    articulation = selectedArticulation,
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

    fun applyHoldGroupDuration(
        group: HoldOnsetGroup,
        written: NaturalEntryTiming.WrittenDuration,
    ) {
        val track = currentTrack()
        val updatedEvents = track.events.toMutableList()
        group.eventIndices.forEach { index ->
            val note = updatedEvents.getOrNull(index) as? ScoreNote ?: return@forEach
            updatedEvents[index] = note.copy(duration = written.duration, dotted = written.dotted)
        }
        replaceActiveTrack {
            it.copy(events = updatedEvents, cursorBeat = group.startBeat + written.beats)
        }
    }

    fun updateHoldGroupAt(nowMs: Long): NaturalEntryTiming.WrittenDuration? {
        val group = holdCurrentGroup ?: return null
        val elapsedMs = (nowMs - group.onsetMs).coerceAtLeast(0L)
        val candidate = HoldDurationTiming.writtenForHoldMs(
            holdMs = elapsedMs,
            bpm = group.bpm,
            mode = holdDurationMode,
        )
        val written = if (candidate.beats >= group.currentWritten.beats) candidate else group.currentWritten
        val updatedGroup = if (written != group.currentWritten) group.copy(currentWritten = written) else group
        holdCurrentGroup = updatedGroup
        holdPreviewWritten = written
        applyHoldGroupDuration(updatedGroup, written)
        return written
    }

    fun beginHoldPitch(pitch: Int) {
        repairUnexpectedTransportForEntry()
        if (holdHeldInputs.containsKey(pitch)) return
        val now = SystemClock.elapsedRealtime()
        val activeGroup = holdCurrentGroup
        val completedAnchor = holdPreviousGroup
        val joinsCurrentChord = activeGroup != null && holdHeldInputs.isNotEmpty()
        val startBeat = when {
            joinsCurrentChord -> activeGroup!!.startBeat
            completedAnchor != null -> HoldEntryTiming.nextStartBeat(
                previousStartBeat = completedAnchor.startBeat,
                previousOnsetMs = completedAnchor.onsetMs,
                currentOnsetMs = now,
                bpm = completedAnchor.bpm,
            )
            else -> currentTrack().cursorBeat
        }
        val groupBpm = if (joinsCurrentChord) activeGroup!!.bpm else tempoBpmAt(startBeat)
        val initialWritten = if (joinsCurrentChord) activeGroup!!.currentWritten
        else HoldDurationTiming.writtenForHoldMs(0L, groupBpm, holdDurationMode)

        if (!joinsCurrentChord) recordBeforeScoreEdit()

        val eventIndex = currentTrack().events.size
        replaceActiveTrack {
            it.copy(
                events = it.events + ScoreNote(
                    midiPitch = pitch,
                    duration = initialWritten.duration,
                    startBeat = startBeat,
                    dotted = initialWritten.dotted,
                    articulation = selectedArticulation,
                ),
                cursorBeat = startBeat + initialWritten.beats,
            )
        }

        val group = if (joinsCurrentChord) {
            activeGroup!!.copy(eventIndices = activeGroup.eventIndices + eventIndex)
        } else {
            HoldOnsetGroup(
                onsetMs = now,
                startBeat = startBeat,
                bpm = groupBpm,
                eventIndices = listOf(eventIndex),
                currentWritten = initialWritten,
            )
        }
        holdCurrentGroup = group
        holdHeldInputs[pitch] = HoldHeldInput(eventIndex = eventIndex, groupOnsetMs = group.onsetMs)
        holdPreviewWritten = initialWritten
        selectedEventIndex = eventIndex
        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) playback.previewPitch(pitch)
    }

    fun finishHoldPitch(pitch: Int) {
        LiveInstrumentBus.noteOff(pitch)
        val held = holdHeldInputs.remove(pitch) ?: return
        val group = holdCurrentGroup
        if (group != null && group.onsetMs == held.groupOnsetMs) {
            updateHoldGroupAt(SystemClock.elapsedRealtime())
        }
        if (holdHeldInputs.values.none { it.groupOnsetMs == held.groupOnsetMs }) {
            holdPreviousGroup = holdCurrentGroup
            holdCurrentGroup = null
            syncHistoryButtons()
        }
    }

    fun finishHoldGroupForUiBreak() {
        if (holdCurrentGroup != null) updateHoldGroupAt(SystemClock.elapsedRealtime())
        if (holdHeldInputs.isNotEmpty()) LiveInstrumentBus.allNotesOff()
        holdHeldInputs.clear()
        holdCurrentGroup = null
        holdPreviousGroup = null
    }

    fun restoreEditState(state: ScoreEditState) {
        val restoredTracks = state.tracks.ifEmpty { listOf(ScoreTracks.defaultTrack().copy(clefMode = appSettings.defaultClefMode)) }
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
        if (liveRecordingStartedAtMs != null) {
            cancelLiveRecording()
        } else {
            liveHeldInputs.clear()
            LiveInstrumentBus.allNotesOff()
        }
        val restoredTracks = snapshot.effectiveTracks()
        tracks.clear()
        tracks.addAll(restoredTracks)
        activeTrackIndex = snapshot.effectiveActiveTrackIndex()
        selectedEventIndex = -1
        projectName = snapshot.safeProjectName()
        tempoChanges = snapshot.effectiveTempoChanges()
        bpm = tempoChanges.first().bpm
        selectedDuration = snapshot.selectedDuration
        selectedDotted = snapshot.selectedDotted
        selectedArticulation = snapshot.selectedArticulation
        pianoOctaveShift = snapshot.pianoOctaveShift.coerceIn(-4, 3)
        staffSharpInput = snapshot.staffSharpInput
        timeSignatures = snapshot.effectiveTimeSignatures()
        keySignatures = snapshot.effectiveKeySignatures()
        metronomeEnabled = snapshot.metronomeEnabled
        comfortTempoCapturing = false
        comfortTempoAttackTimes = emptyList()
        comfortTempoEstimate = null
        mixerGestureHistoryRecorded = false
        if (clearHistory) editHistory.clear()
        syncHistoryButtons()
    }

    LaunchedEffect(Unit) {
        val restored = if (appSettings.restoreLastProject) {
            withContext(Dispatchers.IO) { ScoreProjectRepository.loadDraft(context) }
        } else null
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
        tempoChanges,
        timeSignatures,
        keySignatures,
        metronomeEnabled,
        selectedDuration,
        selectedDotted,
        selectedArticulation,
        pianoOctaveShift,
        staffSharpInput,
    ) {
        if (!draftLoaded || draftTracks.isEmpty() || !appSettings.autosaveRecovery) return@LaunchedEffect
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
                    val note = updatedEvents.getOrNull(held.eventIndex) as? ScoreNote ?: return@forEach
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

    LaunchedEffect(pianoEntryMode, holdCurrentGroup?.onsetMs) {
        val onset = holdCurrentGroup?.onsetMs ?: return@LaunchedEffect
        if (pianoEntryMode != PianoEntryMode.HOLD) return@LaunchedEffect
        while (
            pianoEntryMode == PianoEntryMode.HOLD &&
            holdCurrentGroup?.onsetMs == onset &&
            holdHeldInputs.values.any { it.groupOnsetMs == onset }
        ) {
            updateHoldGroupAt(SystemClock.elapsedRealtime())
            delay(33L)
        }
    }

    LaunchedEffect(pianoEntryMode, liveRecordingStartedAtMs, isPlaying) {
        if (pianoEntryMode == PianoEntryMode.LIVE) return@LaunchedEffect
        val decision = TransportRepairPolicy.decide(
            isLiveMode = false,
            liveRecordingActive = liveRecordingStartedAtMs != null,
            scorePlaybackActive = isPlaying,
            transportPlaying = ScoreTransportBus.state.value.isPlaying,
        )
        if (decision.cancelLiveRecording) cancelLiveRecording()
        if (decision.stopTransport) ScoreForgeAudioSession.stopPlayback(context)
    }

    LaunchedEffect(activeTrack.id, activeTrack.volume, activeTrack.pan) {
        LiveInstrumentBus.setMixer(activeTrack.volume, activeTrack.pan)
    }

    DisposableEffect(Unit) {
        onDispose {
            if (ScoreForgeSettingsRepository.load(context).autosaveRecovery) {
                ScoreProjectRepository.saveDraft(context, currentProjectSnapshot())
            }
            cancelNaturalEntryGroup()
            if (liveRecordingStartedAtMs != null) cancelLiveRecording()
            LiveInstrumentBus.allNotesOff()
        }
    }

    fun stopPlayback() {
        ScoreForgeAudioSession.stopPlayback(context)
        isPlaying = false
    }

    fun startComfortTempoMeasurement() {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        comfortTempoAttackTimes = emptyList()
        comfortTempoEstimate = null
        comfortTempoCapturing = true
        showPianoKeyboard = true
    }

    fun cancelComfortTempoMeasurement() {
        comfortTempoCapturing = false
        comfortTempoAttackTimes = emptyList()
        comfortTempoEstimate = null
        LiveInstrumentBus.allNotesOff()
    }

    fun recordComfortTempoAttack(pitch: Int) {
        if (!comfortTempoCapturing) return
        val updated = ComfortTempo.addAttack(
            comfortTempoAttackTimes,
            SystemClock.elapsedRealtime(),
        )
        if (updated.size == comfortTempoAttackTimes.size) return
        comfortTempoAttackTimes = updated
        playback.previewPitch(pitch)
        if (updated.size >= ComfortTempo.REQUIRED_ATTACKS) {
            comfortTempoEstimate = ComfortTempo.estimateBpm(updated)
            comfortTempoCapturing = false
        }
    }

    fun applyComfortTempoEstimate() {
        val estimate = comfortTempoEstimate ?: return
        setTempoChange(
            ScoreTimeline.quantizeBeat(currentTrack().cursorBeat),
            estimate.coerceIn(ComfortTempo.MIN_BPM, ComfortTempo.MAX_BPM),
        )
        comfortTempoEstimate = null
        comfortTempoAttackTimes = emptyList()
    }

    fun openProject(snapshot: ScoreProjectSnapshot) {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        applyProjectSnapshot(snapshot, clearHistory = true)
        ScoreTransportBus.seek(0f)
        chordMode = StepChordMode.OFF
        pianoEntryMode = appSettings.defaultEntryMode
        editorMode = appSettings.defaultEditorMode
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
            clefMode = appSettings.defaultClefMode,
        )
        applyProjectSnapshot(
            ScoreProjectSnapshot(
                events = emptyList(),
                bpm = 120,
                cursorBeat = 0f,
                selectedDuration = NoteDuration.QUARTER,
                selectedDotted = false,
                selectedArticulation = NoteArticulation.NORMAL,
                pianoOctaveShift = if (appSettings.rememberKeyboardOctave) pianoOctaveShift else 0,
                staffSharpInput = false,
                tracks = listOf(blankTrack),
                activeTrackIndex = 0,
                projectName = "Untitled",
            ),
            clearHistory = true,
        )
        chordMode = StepChordMode.OFF
        pianoEntryMode = appSettings.defaultEntryMode
        editorMode = appSettings.defaultEditorMode
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
        repairUnexpectedTransportForEntry()
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
            articulation = selectedArticulation,
        )
        val nextCursor = when {
            advanceCursor && chordMode.holdsStepCursor -> track.cursorBeat
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
        cancelNaturalEntryGroup()
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

    fun applyNaturalGroupDuration(
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

    fun startPlayback() {
        if (playableNoteCount <= 0 || liveRecordingActive || isPlaying) return
        when (pianoEntryMode) {
            PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()
            PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()
            else -> Unit
        }
        LiveInstrumentBus.allNotesOff()
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        isPlaying = true
        ScoreForgeAudioSession.startPlayback(
            context = context,
            request = ScoreForgeAudioSession.PlaybackRequest(
                tracks = tracks.toList(),
                bpm = tempoChanges.first().bpm,
                tempoChanges = tempoChanges,
                throughBeat = ScoreTracks.endBeat(tracks),
                metronomeEnabled = metronomeEnabled,
                timeSignatures = timeSignatures,
                projectName = projectName,
            ),
        )
    }

    fun moveEntryCursor(beat: Float) {
        repairUnexpectedTransportForEntry()
        if (pianoEntryMode == PianoEntryMode.NATURAL) {
            finishNaturalPhraseForStaffBrowse()
            LiveInstrumentBus.allNotesOff()
        } else if (pianoEntryMode == PianoEntryMode.HOLD) {
            finishHoldGroupForUiBreak()
        } else if (pianoEntryMode == PianoEntryMode.LIVE && liveRecordingActive) {
            stopLiveRecording()
        }
        val targetBeat = ScoreTimeline.quantizeBeat(beat).coerceAtLeast(0f)
        replaceActiveTrack { it.copy(cursorBeat = targetBeat) }
        selectedEventIndex = -1
    }

    fun finalizeNaturalGroupForNextAttack(group: NaturalOnsetGroup, nextOnsetMs: Long): Float {
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
        val stepBeats = NaturalEntryTiming.quantizedOnsetSpacingBeats(intervalMs, group.bpm)
        val nextStartBeat = group.startBeat + stepBeats
        applyNaturalGroupDuration(group, inference.written, nextStartBeat)
        return nextStartBeat
    }

    fun beginNaturalPitch(pitch: Int) {
        repairUnexpectedTransportForEntry()
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
                bpm = tempoBpmAt(groupStartBeat),
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

    fun changePianoOctave(delta: Int) {
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        pianoOctaveShift = (pianoOctaveShift + delta).coerceIn(-4, 3)
        ScoreForgeSettingsRepository.rememberKeyboardOctave(context, pianoOctaveShift)
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
                    cursorBeat = if (chordMode.holdsStepCursor) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents),
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
                cursorBeat = if (chordMode.holdsStepCursor) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents),
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
                cursorBeat = if (chordMode.holdsStepCursor) it.cursorBeat else ScoreTimeline.endBeat(updatedEvents),
            )
        }
        selectedEventIndex = eventIndex.coerceAtMost(currentTrack().events.lastIndex)
    }

    fun selectTrack(index: Int) {
        if (index !in tracks.indices || index == activeIndex()) return
        if (liveRecordingActive) stopLiveRecording()
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
            clefMode = appSettings.defaultClefMode,
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

    fun setActiveTrackClefMode(mode: ScoreClefMode) {
        if (currentTrack().clefMode == mode) return
        when (pianoEntryMode) {
            PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()
            PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()
            else -> Unit
        }
        recordBeforeScoreEdit()
        replaceActiveTrack { it.copy(clefMode = mode) }
        selectedEventIndex = -1
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
        if (settingsOpen) {
            ScoreForgeSettingsScreen(
                onBack = { settingsOpen = false },
                onSettingsChanged = { updated -> appSettings = updated },
            )
        } else {
        ExternalOpenHandler(
            request = externalOpenRequest.takeIf { draftLoaded },
            onOpenProject = ::openProject,
            onConsumed = onExternalOpenConsumed,
        )

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
                    measureCount = ScoreTimeline.measureCount(
                        emptyList(),
                        arrangementEndBeat,
                        timeSignatures = timeSignatures,
                    ),
                    timeSignatureLabel = ScoreTimeSignatures.atBeat(
                        timeSignatures,
                        activeCursorBeat,
                    ).displayName,
                    keySignatureLabel = ScoreKeySignatures.atBeat(
                        keySignatures,
                        activeCursorBeat,
                    ).displayName,
                    bpm = activeTempoBpm,
                    cursorBeat = activeCursorBeat,
                    isPlaying = isPlaying,
                    canPlay = playableNoteCount > 0 && !liveRecordingActive && !comfortTempoCapturing,
                    metronomeEnabled = metronomeEnabled,
                    onToggleMetronome = {
                        if (isPlaying) stopPlayback()
                        metronomeEnabled = !metronomeEnabled
                    },
                    onTempoDown = {
                        setTempoChange(
                            ScoreTimeline.quantizeBeat(activeCursorBeat),
                            (activeTempoBpm - 5).coerceAtLeast(ScoreTempos.MIN_BPM),
                        )
                    },
                    onTempoUp = {
                        setTempoChange(
                            ScoreTimeline.quantizeBeat(activeCursorBeat),
                            (activeTempoBpm + 5).coerceAtMost(ScoreTempos.MAX_BPM),
                        )
                    },
                    onPlay = ::startPlayback,
                    onStop = ::stopPlayback,
                    onOpenSettings = { settingsOpen = true },
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
                    playbackActive = isPlaying,
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

                ComposerTransformToolbar(
                    tempoChanges = tempoChanges,
                    timeSignatures = timeSignatures,
                    keySignatures = keySignatures,
                    cursorBeat = activeCursorBeat,
                    clefMode = activeTrack.clefMode,
                    effectiveClef = ScoreClefs.effective(activeTrack.clefMode, activeEvents),
                    selectedDuration = selectedDuration,
                    durationOrder = appSettings.noteDurationOrder,
                    dotted = selectedDotted,
                    sharpInput = staffSharpInput,
                    tieEnabled = canTieSelected,
                    tieActive = selectedTieActive,
                    editorMode = editorMode,
                    showPianoKeyboard = showPianoKeyboard,
                    measureNumber = ScoreTimeline.measureCount(
                        emptyList(),
                        activeCursorBeat + 0.001f,
                        timeSignatures = timeSignatures,
                    ).coerceAtLeast(1),
                    comfortTempoCapturing = comfortTempoCapturing,
                    comfortTempoAttackCount = comfortTempoAttackTimes.size,
                    comfortTempoEstimate = comfortTempoEstimate,
                    onSetTempo = ::setTempoChange,
                    onRemoveTempo = ::removeTempoChange,
                    onStartComfortTempo = ::startComfortTempoMeasurement,
                    onCancelComfortTempo = ::cancelComfortTempoMeasurement,
                    onApplyComfortTempo = ::applyComfortTempoEstimate,
                    onTryComfortTempoAgain = ::startComfortTempoMeasurement,
                    onSetTimeSignature = { startBeat, numerator, denominator ->
                        timeSignatures = ScoreTimeSignatures.withChange(
                            timeSignatures,
                            startBeat,
                            numerator,
                            denominator,
                        )
                    },
                    onRemoveTimeSignature = { startBeat ->
                        timeSignatures = ScoreTimeSignatures.withoutChange(timeSignatures, startBeat)
                    },
                    onSetKeySignature = { startBeat, fifths, minor ->
                        keySignatures = ScoreKeySignatures.withChange(
                            keySignatures,
                            startBeat,
                            fifths,
                            minor,
                        )
                    },
                    onRemoveKeySignature = { startBeat ->
                        keySignatures = ScoreKeySignatures.withoutChange(keySignatures, startBeat)
                    },
                    onClefModeChanged = ::setActiveTrackClefMode,
                    onDurationSelected = { selectedDuration = it },
                    onToggleDotted = { selectedDotted = !selectedDotted },
                    onInsertRest = ::insertRest,
                    onToggleSharpInput = { staffSharpInput = !staffSharpInput },
                    onToggleTie = ::toggleSelectedTie,
                    onEditorModeChanged = { editorMode = it },
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
                        tempoChanges = tempoChanges,
                        timeSignatures = timeSignatures,
                        keySignatures = keySignatures,
                        clefMode = activeTrack.clefMode,
                        selectedEventIndex = selectedEventIndex,
                        isPlaying = isPlaying,
                        canPlay = playableNoteCount > 0 && !liveRecordingActive && !comfortTempoCapturing,
                        initialInputEnabled = appSettings.staffInputDefault,
                        followPlayback = appSettings.followPlayback,
                        onPlay = ::startPlayback,
                        onStop = ::stopPlayback,
                        onAddPitch = { naturalPitch, tappedBeat ->
                            val keyedPitch = ScoreKeySignatures.applyToNaturalPitch(
                                naturalPitch,
                                ScoreKeySignatures.atBeat(keySignatures, tappedBeat),
                            )
                            val pitch = if (staffSharpInput) {
                                PitchNames.sharpenIfAvailable(naturalPitch)
                            } else {
                                keyedPitch
                            }
                            insertNoteAt(pitch, tappedBeat, preview = true, advanceCursor = false)
                        },
                        onSelectEvent = ::selectEvent,
                        onBeginMove = { recordBeforeScoreEdit() },
                        onMoveNote = ::moveActiveNote,
                        onMoveRest = ::moveActiveRest,
                        onDeleteEvent = ::deleteEvent,
                        onVerticalPan = { dragY -> pageScrollState.dispatchRawDelta(-dragY) },
                        onManualBrowse = {
                            when (pianoEntryMode) {
                                PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()
                                PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()
                                else -> Unit
                            }
                        },
                        onMoveEntryCursor = ::moveEntryCursor,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                    )

                    ScoreEditorMode.PIANO_ROLL -> PianoRollEditor(
                        events = activeEvents,
                        selectedDuration = selectedDuration,
                        cursorBeat = activeCursorBeat,
                        timeSignatures = timeSignatures,
                        octaveShift = pianoOctaveShift,
                        selectedEventIndex = selectedEventIndex,
                        followPlayback = appSettings.followPlayback,
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

                if (showPianoKeyboard) {
                    MultitouchPianoKeyboard(
                        chordMode = chordMode,
                        octaveShift = pianoOctaveShift,
                        entryMode = pianoEntryMode,
                        holdDurationMode = holdDurationMode,
                        liveRecordingActive = liveRecordingActive,
                        holdPreviewDuration = holdPreviewWritten?.duration,
                        holdPreviewDotted = holdPreviewWritten?.dotted ?: false,
                        selectedDuration = selectedDuration,
                        durationOrder = appSettings.noteDurationOrder,
                        noteLabelSetting = appSettings.keyboardNoteLabels,
                        selectedDotted = selectedDotted,
                        selectedArticulation = selectedArticulation,
                        tieEnabled = canTieSelected,
                        tieActive = selectedTieActive,
                        canUndo = canUndo,
                        canRedo = canRedo,
                        onUndo = ::undoScore,
                        onRedo = ::redoScore,
                        onDurationSelected = { selectedDuration = it },
                        onToggleDotted = { selectedDotted = !selectedDotted },
                        onArticulationSelected = { selectedArticulation = it },
                        onToggleTie = ::toggleSelectedTie,
                        onEntryModeChanged = { mode ->
                            if (pianoEntryMode == PianoEntryMode.LIVE) stopLiveRecording()
                            cancelNaturalEntryGroup()
                            LiveInstrumentBus.allNotesOff()
                            chordMode = StepChordMode.OFF
                            if (mode == PianoEntryMode.LIVE) stopPlayback()
                            pianoEntryMode = mode
                        },
                        onHoldDurationModeChanged = { mode ->
                            if (holdDurationMode != mode) {
                                finishHoldGroupForUiBreak()
                                LiveInstrumentBus.allNotesOff()
                                holdPreviewWritten = null
                                holdDurationMode = mode
                            }
                        },
                        onStopLive = ::stopLiveRecording,
                        onCycleChordMode = {
                            LiveInstrumentBus.allNotesOff()
                            val previous = chordMode
                            val next = previous.next()
                            chordMode = next
                            when {
                                next == StepChordMode.OFF -> replaceActiveTrack {
                                    it.copy(cursorBeat = maxOf(it.cursorBeat, ScoreTimeline.endBeat(it.events)))
                                }
                                previous == StepChordMode.OFF -> {
                                    val track = currentTrack()
                                    replaceActiveTrack { it.copy(cursorBeat = ScoreTimeline.endBeat(track.events)) }
                                }
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
                            if (comfortTempoCapturing) {
                                recordComfortTempoAttack(pitch)
                            } else {
                                when (pianoEntryMode) {
                                    PianoEntryMode.STEP -> {
                                        insertStepNote(pitch, preview = false)
                                        if (!LiveInstrumentBus.noteOn(pitch, velocity = 96)) {
                                            playback.previewPitch(pitch)
                                        }
                                    }
                                    PianoEntryMode.NATURAL -> beginNaturalPitch(pitch)
                                    PianoEntryMode.HOLD -> beginHoldPitch(pitch)
                                    PianoEntryMode.LIVE -> beginLivePitch(pitch)
                                }
                            }
                        },
                        onPitchUp = { pitch ->
                            when (pianoEntryMode) {
                                PianoEntryMode.STEP -> LiveInstrumentBus.noteOff(pitch)
                                PianoEntryMode.NATURAL -> finishNaturalPitch(pitch)
                                PianoEntryMode.HOLD -> finishHoldPitch(pitch)
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
}

@Composable
private fun ClefControls(
    mode: ScoreClefMode,
    effectiveClef: ScoreClef,
    onModeChanged: (ScoreClefMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Text("Clef:", style = MaterialTheme.typography.titleSmall)
        ScoreClefMode.entries.forEach { option ->
            if (option == mode) {
                ScoreForgeButton(onClick = { onModeChanged(option) }) { Text(option.displayName) }
            } else {
                ScoreForgeOutlinedButton(onClick = { onModeChanged(option) }) { Text(option.displayName) }
            }
        }
        if (mode == ScoreClefMode.AUTO) {
            Text(
                "Using ${effectiveClef.displayName}",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFD8D2DF),
            )
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
    timeSignatureLabel: String,
    keySignatureLabel: String,
    bpm: Int,
    cursorBeat: Float,
    isPlaying: Boolean,
    canPlay: Boolean,
    metronomeEnabled: Boolean,
    onToggleMetronome: () -> Unit,
    onTempoDown: () -> Unit,
    onTempoUp: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
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
                "$projectName • $activeTrackName • $trackCount tracks • $timeSignatureLabel • $keySignatureLabel • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFE0DCE5),
            )
        }

        ComposerToolbarButton(
            label = "⚙ Settings",
            onClick = onOpenSettings,
        )

        ChamferedControlButton(
            label = "−5",
            feedback = UiCommandFeedback.DECREASE,
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
            feedback = UiCommandFeedback.INCREASE,
            onClick = onTempoUp,
            enabled = bpm < 300,
            compact = false,
        )

        if (metronomeEnabled) {
            ScoreForgeButton(onClick = onToggleMetronome) { Text("Metronome On") }
        } else {
            ScoreForgeOutlinedButton(onClick = onToggleMetronome) { Text("Metronome Off") }
        }

        if (isPlaying) ScoreForgeButton(onClick = onStop) { Text("Stop") }
        else ScoreForgeButton(onClick = onPlay, enabled = canPlay) { Text("Play") }
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
            if (duration == selected) ScoreForgeButton(onClick = { onSelected(duration) }) { Text(duration.displayName) }
            else ScoreForgeOutlinedButton(onClick = { onSelected(duration) }) { Text(duration.displayName) }
        }

        if (dotted) ScoreForgeButton(onClick = onToggleDotted) { Text("Dot •") }
        else ScoreForgeOutlinedButton(onClick = onToggleDotted) { Text("Dot") }

        ScoreForgeOutlinedButton(onClick = onInsertRest) {
            Text(if (dotted) "Insert Dotted Rest" else "Insert Rest")
        }

        if (tieActive) ScoreForgeButton(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }
        else ScoreForgeOutlinedButton(onClick = onToggleTie, enabled = tieEnabled) { Text("Tie →") }

        if (sharpInput) ScoreForgeButton(onClick = onToggleSharpInput) { Text("Staff ♯") }
        else ScoreForgeOutlinedButton(onClick = onToggleSharpInput) { Text("Staff ♯") }

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
