package com.scoreforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.LiveInstrumentBus
import com.scoreforge.app.audio.ScorePlaybackEngine
import com.scoreforge.app.audio.SoundFontEngine
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.abs

@Composable
fun ScoreForgeComposerScreen() {
    val notes = remember { mutableStateListOf<ScoreNote>() }
    val playback = remember { ScorePlaybackEngine() }
    val soundFontEngine = remember { SoundFontEngine.createOrNull() }
    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    var cursorBeat by remember { mutableStateOf(0f) }
    var chordMode by remember { mutableStateOf(false) }

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
        notes.add(
            ScoreNote(
                midiPitch = pitch,
                duration = selectedDuration,
                startBeat = cursorBeat,
            )
        )
        if (preview) playback.previewPitch(pitch)
        if (!chordMode) cursorBeat += selectedDuration.beats
    }

    fun addStaffNote(pitch: Int) = insertNote(pitch, preview = true)

    fun stopPlayback() {
        playback.stop()
        isPlaying = false
    }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderBar(
                    noteCount = notes.size,
                    measureCount = ScoreTimeline.measureCount(notes, cursorBeat),
                    bpm = bpm,
                    cursorBeat = cursorBeat,
                    isPlaying = isPlaying,
                    onTempoDown = { bpm = (bpm - 5).coerceAtLeast(30) },
                    onTempoUp = { bpm = (bpm + 5).coerceAtMost(300) },
                    onPlay = {
                        if (notes.isNotEmpty()) {
                            isPlaying = true
                            playback.playScore(notes.toList(), bpm) { isPlaying = false }
                        }
                    },
                    onStop = ::stopPlayback,
                    onUndo = {
                        stopPlayback()
                        if (notes.isNotEmpty()) {
                            notes.removeAt(notes.lastIndex)
                            if (!chordMode) cursorBeat = ScoreTimeline.endBeat(notes)
                        }
                    },
                    onClear = {
                        stopPlayback()
                        LiveInstrumentBus.allNotesOff()
                        notes.clear()
                        cursorBeat = 0f
                    },
                )

                SoundFontControls(engine = soundFontEngine)

                DurationSelector(
                    selected = selectedDuration,
                    onSelected = { selectedDuration = it },
                )

                StaffEditor(
                    notes = notes,
                    selectedDuration = selectedDuration,
                    cursorBeat = cursorBeat,
                    onAddPitch = ::addStaffNote,
                    onMoveNote = { index, pitch, startBeat ->
                        if (index in notes.indices) {
                            notes[index] = notes[index].copy(
                                midiPitch = pitch,
                                startBeat = startBeat,
                            )
                            if (!chordMode) cursorBeat = ScoreTimeline.endBeat(notes)
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
                            cursorBeat = maxOf(cursorBeat, ScoreTimeline.endBeat(notes))
                        } else {
                            chordMode = true
                            cursorBeat = ScoreTimeline.endBeat(notes)
                        }
                    },
                    onAdvanceChord = {
                        LiveInstrumentBus.allNotesOff()
                        cursorBeat = maxOf(
                            cursorBeat + selectedDuration.beats,
                            ScoreTimeline.endBeat(notes),
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
                "Untitled • Piano • 4/4 • $bpm BPM • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes",
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

        OutlinedButton(onClick = onUndo, enabled = noteCount > 0) { Text("Undo") }
        OutlinedButton(onClick = onClear, enabled = noteCount > 0) { Text("Clear") }
    }
}

private fun formatBeat(beat: Float): String =
    if (beat % 1f == 0f) beat.toInt().toString() else "%.2f".format(beat)

@Composable
private fun DurationSelector(
    selected: NoteDuration,
    onSelected: (NoteDuration) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Note:", style = MaterialTheme.typography.labelLarge)
        NoteDuration.entries.forEach { duration ->
            if (duration == selected) {
                Button(onClick = { onSelected(duration) }) { Text(duration.displayName) }
            } else {
                OutlinedButton(onClick = { onSelected(duration) }) { Text(duration.displayName) }
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Drag notes in pitch + time • 1/16 beat grid",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StaffEditor(
    notes: List<ScoreNote>,
    selectedDuration: NoteDuration,
    cursorBeat: Float,
    onAddPitch: (Int) -> Unit,
    onMoveNote: (Int, Int, Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }
    val visibleBeats = ScoreTimeline.visibleBeats(notes, throughBeat = cursorBeat)

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(Color(0xFFF7F4EA), RoundedCornerShape(8.dp)),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp)
                .pointerInput(notes.size, selectedDuration, cursorBeat) {
                    detectTapGestures { position ->
                        onAddPitch(pitchFromY(position.y, size.height.toFloat()))
                    }
                }
                .pointerInput(notes.size, visibleBeats) {
                    detectDragGestures(
                        onDragStart = { position ->
                            draggingIndex = nearestNoteIndex(
                                notes = notes,
                                point = position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                visibleBeats = visibleBeats,
                            )
                        },
                        onDragEnd = { draggingIndex = -1 },
                        onDragCancel = { draggingIndex = -1 },
                    ) { change, _ ->
                        if (draggingIndex in notes.indices) {
                            val note = notes[draggingIndex]
                            val latestStart = (visibleBeats - note.duration.beats).coerceAtLeast(0f)
                            val movedBeat = ScoreTimeline.quantizeBeat(
                                beatFromX(
                                    x = change.position.x,
                                    visibleBeats = visibleBeats,
                                    width = size.width.toFloat(),
                                )
                            ).coerceIn(0f, latestStart)

                            onMoveNote(
                                draggingIndex,
                                pitchFromY(change.position.y, size.height.toFloat()),
                                movedBeat,
                            )
                        }
                    }
                },
        ) {
            val staffTop = size.height * 0.24f
            val lineSpacing = size.height * 0.11f
            val staffBottom = staffTop + lineSpacing * 4f
            val left = 24f
            val right = size.width - 14f

            repeat(5) { line ->
                val y = staffTop + lineSpacing * line
                drawLine(Color(0xFF202020), Offset(left, y), Offset(right, y), 2f)
            }

            var measureBeat = 0f
            while (measureBeat <= visibleBeats + 0.001f) {
                val x = beatX(measureBeat, visibleBeats, size.width)
                drawLine(
                    color = Color(0xFF383838),
                    start = Offset(x, staffTop),
                    end = Offset(x, staffBottom),
                    strokeWidth = if (measureBeat == 0f || measureBeat >= visibleBeats) 2.4f else 1.4f,
                )
                measureBeat += ScoreTimeline.BEATS_PER_MEASURE
            }

            notes.forEach { note ->
                val x = beatX(note.startBeat + 0.10f, visibleBeats, size.width)
                val y = noteY(note.midiPitch, staffBottom, lineSpacing)
                drawLedgerLines(x, y, staffTop, staffBottom, lineSpacing)

                val filled = note.duration != NoteDuration.WHOLE && note.duration != NoteDuration.HALF
                if (filled) {
                    drawOval(
                        Color(0xFF111111),
                        topLeft = Offset(x - 8f, y - 5.5f),
                        size = Size(16f, 11f),
                    )
                } else {
                    drawOval(
                        Color(0xFF111111),
                        topLeft = Offset(x - 8f, y - 5.5f),
                        size = Size(16f, 11f),
                        style = Stroke(2.5f),
                    )
                }

                if (note.duration != NoteDuration.WHOLE) {
                    val stemTop = y - lineSpacing * 2.8f
                    drawLine(Color(0xFF111111), Offset(x + 7f, y), Offset(x + 7f, stemTop), 2.5f)
                    if (note.duration == NoteDuration.EIGHTH || note.duration == NoteDuration.SIXTEENTH) {
                        drawLine(
                            Color(0xFF111111),
                            Offset(x + 7f, stemTop),
                            Offset(x + 17f, stemTop + lineSpacing * 0.6f),
                            2.5f,
                        )
                        if (note.duration == NoteDuration.SIXTEENTH) {
                            drawLine(
                                Color(0xFF111111),
                                Offset(x + 7f, stemTop + 5f),
                                Offset(x + 17f, stemTop + lineSpacing * 0.6f + 5f),
                                2.5f,
                            )
                        }
                    }
                }
            }

            val cursorX = beatX(cursorBeat, visibleBeats, size.width)
            drawLine(
                color = Color(0xFF5E6A73),
                start = Offset(cursorX, staffTop - lineSpacing * 0.70f),
                end = Offset(cursorX, staffBottom + lineSpacing * 0.70f),
                strokeWidth = 2f,
            )
        }

        if (notes.isEmpty()) {
            Text(
                "Tap the staff to place a ${selectedDuration.displayName.lowercase()} note, or use the piano below.",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp),
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun beatX(beat: Float, visibleBeats: Float, width: Float): Float {
    val left = 30f
    val right = 20f
    val usable = (width - left - right).coerceAtLeast(1f)
    return left + usable * (beat.coerceIn(0f, visibleBeats) / visibleBeats.coerceAtLeast(1f))
}

private fun beatFromX(x: Float, visibleBeats: Float, width: Float): Float {
    val left = 30f
    val right = 20f
    val usable = (width - left - right).coerceAtLeast(1f)
    val fraction = ((x - left) / usable).coerceIn(0f, 1f)
    return fraction * visibleBeats
}

private fun noteY(midiPitch: Int, staffBottom: Float, lineSpacing: Float): Float {
    val e4Diatonic = 4 * 7 + 2
    val steps = PitchNames.diatonicPosition(midiPitch) - e4Diatonic
    return staffBottom - steps * (lineSpacing / 2f)
}

private fun pitchFromY(y: Float, height: Float): Int {
    val staffTop = height * 0.24f
    val lineSpacing = height * 0.11f
    val staffBottom = staffTop + lineSpacing * 4f
    val e4Diatonic = 4 * 7 + 2
    val target = e4Diatonic + ((staffBottom - y) / (lineSpacing / 2f)).toInt()

    var bestPitch = 60
    var bestDistance = Int.MAX_VALUE
    for (pitch in 36..96) {
        val distance = abs(PitchNames.diatonicPosition(pitch) - target)
        if (distance < bestDistance || (distance == bestDistance && !PitchNames.hasSharp(pitch))) {
            bestDistance = distance
            bestPitch = pitch
        }
    }
    return bestPitch
}

private fun nearestNoteIndex(
    notes: List<ScoreNote>,
    point: Offset,
    width: Float,
    height: Float,
    visibleBeats: Float,
): Int {
    if (notes.isEmpty()) return -1
    val staffTop = height * 0.24f
    val lineSpacing = height * 0.11f
    val staffBottom = staffTop + lineSpacing * 4f

    var nearest = -1
    var bestDistanceSquared = Float.MAX_VALUE
    notes.forEachIndexed { index, note ->
        val dx = point.x - beatX(note.startBeat + 0.10f, visibleBeats, width)
        val dy = point.y - noteY(note.midiPitch, staffBottom, lineSpacing)
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared < bestDistanceSquared) {
            bestDistanceSquared = distanceSquared
            nearest = index
        }
    }
    return if (bestDistanceSquared <= 48f * 48f) nearest else -1
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLedgerLines(
    x: Float,
    y: Float,
    staffTop: Float,
    staffBottom: Float,
    lineSpacing: Float,
) {
    var ledgerY = staffBottom + lineSpacing
    while (y >= ledgerY - lineSpacing / 4f) {
        drawLine(Color(0xFF202020), Offset(x - 13f, ledgerY), Offset(x + 13f, ledgerY), 2f)
        ledgerY += lineSpacing
    }

    ledgerY = staffTop - lineSpacing
    while (y <= ledgerY + lineSpacing / 4f) {
        drawLine(Color(0xFF202020), Offset(x - 13f, ledgerY), Offset(x + 13f, ledgerY), 2f)
        ledgerY -= lineSpacing
    }
}
