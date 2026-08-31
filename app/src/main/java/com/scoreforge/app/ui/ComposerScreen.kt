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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.LiveInstrumentBus
import com.scoreforge.app.audio.ScorePlaybackEngine
import com.scoreforge.app.audio.SoundFontEngine
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.ScoreEvent
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreRest
import com.scoreforge.app.music.ScoreTimeline

@Composable
fun ScoreForgeComposerScreen() {
    val events = remember { mutableStateListOf<ScoreEvent>() }
    val playback = remember { ScorePlaybackEngine() }
    val soundFontEngine = remember { SoundFontEngine.createOrNull() }
    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    var cursorBeat by remember { mutableStateOf(0f) }
    var chordMode by remember { mutableStateOf(false) }

    val noteCount = events.count { it is ScoreNote }
    val restCount = events.count { it is ScoreRest }

    DisposableEffect(playback, soundFontEngine) {
        playback.setSoundFontEngine(soundFontEngine)
        onDispose {
            LiveInstrumentBus.allNotesOff()
            playback.setSoundFontEngine(null)
            playback.release()
            soundFontEngine?.close()
        }
    }

    fun insertNote(pitch: Int, preview: Boolean) {
        events.add(
            ScoreNote(
                midiPitch = pitch,
                duration = selectedDuration,
                startBeat = cursorBeat,
            )
        )
        if (preview) playback.previewPitch(pitch)
        if (!chordMode) cursorBeat += selectedDuration.beats
    }

    fun insertRest() {
        LiveInstrumentBus.allNotesOff()
        events.add(
            ScoreRest(
                duration = selectedDuration,
                startBeat = cursorBeat,
            )
        )
        cursorBeat += selectedDuration.beats
    }

    fun stopPlayback() {
        playback.stop()
        isPlaying = false
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
                    onUndo = {
                        stopPlayback()
                        LiveInstrumentBus.allNotesOff()
                        if (events.isNotEmpty()) {
                            events.removeAt(events.lastIndex)
                            if (!chordMode) cursorBeat = ScoreTimeline.endBeat(events)
                        }
                    },
                    onClear = {
                        stopPlayback()
                        LiveInstrumentBus.allNotesOff()
                        events.clear()
                        cursorBeat = 0f
                    },
                )

                SoundFontControls(engine = soundFontEngine)

                DurationSelector(
                    selected = selectedDuration,
                    onSelected = { selectedDuration = it },
                    onInsertRest = ::insertRest,
                )

                ScoreStaffEditor(
                    events = events,
                    selectedDuration = selectedDuration,
                    cursorBeat = cursorBeat,
                    onAddPitch = { insertNote(it, preview = true) },
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
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                MultitouchPianoKeyboard(
                    chordMode = chordMode,
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
                    onPitchDown = { pitch ->
                        insertNote(pitch, preview = false)
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
    onTempoDown: () -> Unit,
    onTempoUp: () -> Unit,
    onPlay: () -> Unit,
    onStop: () -> Unit,
    onUndo: () -> Unit,
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

        OutlinedButton(onClick = onUndo, enabled = eventCount > 0) { Text("Undo") }
        OutlinedButton(onClick = onClear, enabled = eventCount > 0) { Text("Clear") }
    }
}

private fun formatBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)

@Composable
private fun DurationSelector(
    selected: NoteDuration,
    onSelected: (NoteDuration) -> Unit,
    onInsertRest: () -> Unit,
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

        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Drag notes in pitch + time • rests preserve silence • 1/16 beat grid",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
