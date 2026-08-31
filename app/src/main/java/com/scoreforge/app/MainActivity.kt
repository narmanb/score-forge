package com.scoreforge.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreNote
import kotlin.math.abs

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScoreForgeApp()
        }
    }
}

@Composable
private fun ScoreForgeApp() {
    val notes = remember { mutableStateListOf<ScoreNote>() }
    var selectedDuration by remember { mutableStateOf(NoteDuration.QUARTER) }

    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                HeaderBar(
                    noteCount = notes.size,
                    onUndo = { if (notes.isNotEmpty()) notes.removeAt(notes.lastIndex) },
                    onClear = { notes.clear() },
                )

                DurationSelector(
                    selected = selectedDuration,
                    onSelected = { selectedDuration = it },
                )

                StaffEditor(
                    notes = notes,
                    selectedDuration = selectedDuration,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )

                PianoKeyboard(
                    onPitchPressed = { midiPitch ->
                        notes.add(ScoreNote(midiPitch = midiPitch, duration = selectedDuration))
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(136.dp),
                )
            }
        }
    }
}

@Composable
private fun HeaderBar(
    noteCount: Int,
    onUndo: () -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Score Forge", style = MaterialTheme.typography.titleLarge)
            Text(
                "Untitled • Piano • 4/4 • 120 BPM • $noteCount notes",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        OutlinedButton(onClick = onUndo, enabled = noteCount > 0) {
            Text("Undo")
        }
        OutlinedButton(onClick = onClear, enabled = noteCount > 0) {
            Text("Clear")
        }
    }
}

@Composable
private fun DurationSelector(
    selected: NoteDuration,
    onSelected: (NoteDuration) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Note:", style = MaterialTheme.typography.labelLarge)
        NoteDuration.entries.forEach { duration ->
            val active = duration == selected
            Button(
                onClick = { onSelected(duration) },
                modifier = Modifier.widthIn(min = 70.dp),
                colors = if (active) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.outlinedButtonColors()
                },
            ) {
                Text(duration.displayName)
            }
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(
            "Tap staff or piano • Drag staff notes vertically",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StaffEditor(
    notes: MutableList<ScoreNote>,
    selectedDuration: NoteDuration,
    modifier: Modifier = Modifier,
) {
    var draggingIndex by remember { mutableIntStateOf(-1) }

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
                .pointerInput(notes.size, selectedDuration) {
                    detectTapGestures { position ->
                        val pitch = pitchFromY(position.y, size.height.toFloat())
                        notes.add(ScoreNote(pitch, selectedDuration))
                    }
                }
                .pointerInput(notes.size) {
                    detectDragGestures(
                        onDragStart = { start ->
                            draggingIndex = nearestNoteIndex(
                                notes = notes,
                                point = start,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                            )
                        },
                        onDragEnd = { draggingIndex = -1 },
                        onDragCancel = { draggingIndex = -1 },
                    ) { change, _ ->
                        change.consume()
                        if (draggingIndex in notes.indices) {
                            val old = notes[draggingIndex]
                            notes[draggingIndex] = old.copy(
                                midiPitch = pitchFromY(change.position.y, size.height.toFloat()),
                            )
                        }
                    }
                },
        ) {
            val staffTop = size.height * 0.24f
            val lineSpacing = size.height * 0.11f
            val staffBottom = staffTop + lineSpacing * 4f
            val left = 22f
            val right = size.width - 12f

            repeat(5) { line ->
                val y = staffTop + lineSpacing * line
                drawLine(
                    color = Color(0xFF202020),
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 2f,
                )
            }

            drawLine(Color(0xFF202020), Offset(left, staffTop), Offset(left, staffBottom), 2f)
            drawLine(Color(0xFF202020), Offset(right, staffTop), Offset(right, staffBottom), 2f)

            notes.forEachIndexed { index, note ->
                val x = noteX(index, notes.size, size.width)
                val y = noteY(note.midiPitch, staffBottom, lineSpacing)

                drawLedgerLinesIfNeeded(x, y, staffTop, staffBottom, lineSpacing)

                val filled = note.duration != NoteDuration.WHOLE && note.duration != NoteDuration.HALF
                if (filled) {
                    drawOval(
                        color = Color(0xFF111111),
                        topLeft = Offset(x - 8f, y - 5.5f),
                        size = Size(16f, 11f),
                    )
                } else {
                    drawOval(
                        color = Color(0xFF111111),
                        topLeft = Offset(x - 8f, y - 5.5f),
                        size = Size(16f, 11f),
                        style = Stroke(width = 2.5f),
                    )
                }

                if (note.duration != NoteDuration.WHOLE) {
                    drawLine(
                        color = Color(0xFF111111),
                        start = Offset(x + 7f, y),
                        end = Offset(x + 7f, y - lineSpacing * 2.8f),
                        strokeWidth = 2.5f,
                    )
                }

                if (note.duration == NoteDuration.EIGHTH || note.duration == NoteDuration.SIXTEENTH) {
                    drawLine(
                        color = Color(0xFF111111),
                        start = Offset(x + 7f, y - lineSpacing * 2.8f),
                        end = Offset(x + 17f, y - lineSpacing * 2.2f),
                        strokeWidth = 2.5f,
                    )
                }
            }
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

private fun noteX(index: Int, count: Int, width: Float): Float {
    val left = 58f
    val right = 34f
    val usable = (width - left - right).coerceAtLeast(1f)
    val slots = (count + 1).coerceAtLeast(8)
    return left + usable * (index + 1f) / slots
}

private fun noteY(midiPitch: Int, staffBottom: Float, lineSpacing: Float): Float {
    val e4Diatonic = 4 * 7 + 2
    val offsetSteps = PitchNames.diatonicPosition(midiPitch) - e4Diatonic
    return staffBottom - offsetSteps * (lineSpacing / 2f)
}

private fun pitchFromY(y: Float, height: Float): Int {
    val staffTop = height * 0.24f
    val lineSpacing = height * 0.11f
    val staffBottom = staffTop + lineSpacing * 4f
    val e4Diatonic = 4 * 7 + 2
    val diatonicOffset = ((staffBottom - y) / (lineSpacing / 2f)).toInt()
    val targetDiatonic = e4Diatonic + diatonicOffset

    var bestPitch = 60
    var bestDistance = Int.MAX_VALUE
    for (pitch in 36..96) {
        val distance = abs(PitchNames.diatonicPosition(pitch) - targetDiatonic)
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
): Int {
    if (notes.isEmpty()) return -1
    val staffTop = height * 0.24f
    val lineSpacing = height * 0.11f
    val staffBottom = staffTop + lineSpacing * 4f

    var nearest = -1
    var bestDistanceSquared = Float.MAX_VALUE
    notes.forEachIndexed { index, note ->
        val x = noteX(index, notes.size, width)
        val y = noteY(note.midiPitch, staffBottom, lineSpacing)
        val dx = point.x - x
        val dy = point.y - y
        val distanceSquared = dx * dx + dy * dy
        if (distanceSquared < bestDistanceSquared) {
            bestDistanceSquared = distanceSquared
            nearest = index
        }
    }
    return if (bestDistanceSquared <= 48f * 48f) nearest else -1
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawLedgerLinesIfNeeded(
    x: Float,
    y: Float,
    staffTop: Float,
    staffBottom: Float,
    lineSpacing: Float,
) {
    val halfStep = lineSpacing / 2f
    var ledgerY = staffBottom + lineSpacing
    while (y >= ledgerY - halfStep / 2f) {
        drawLine(Color(0xFF202020), Offset(x - 13f, ledgerY), Offset(x + 13f, ledgerY), 2f)
        ledgerY += lineSpacing
    }

    ledgerY = staffTop - lineSpacing
    while (y <= ledgerY + halfStep / 2f) {
        drawLine(Color(0xFF202020), Offset(x - 13f, ledgerY), Offset(x + 13f, ledgerY), 2f)
        ledgerY -= lineSpacing
    }
}

@Composable
private fun PianoKeyboard(
    onPitchPressed: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val whitePitches = listOf(60, 62, 64, 65, 67, 69, 71, 72, 74, 76, 77, 79, 81, 83)
    val blackKeys = listOf(
        0 to 61,
        1 to 63,
        3 to 66,
        4 to 68,
        5 to 70,
        7 to 73,
        8 to 75,
        10 to 78,
        11 to 80,
        12 to 82,
    )

    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Piano step entry", style = MaterialTheme.typography.labelLarge)
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "C4–B5",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 12.dp, vertical = 3.dp),
        ) {
            val whiteKeyWidth = maxWidth / whitePitches.size

            Row(modifier = Modifier.fillMaxSize()) {
                whitePitches.forEach { pitch ->
                    Box(
                        modifier = Modifier
                            .width(whiteKeyWidth)
                            .fillMaxHeight()
                            .background(Color.White)
                            .border(0.6.dp, Color(0xFF555555))
                            .clickable { onPitchPressed(pitch) },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Text(
                            PitchNames.name(pitch),
                            modifier = Modifier.padding(bottom = 5.dp),
                            color = Color(0xFF222222),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            blackKeys.forEach { (whiteIndex, pitch) ->
                Box(
                    modifier = Modifier
                        .offset(x = whiteKeyWidth * (whiteIndex + 0.68f))
                        .width(whiteKeyWidth * 0.64f)
                        .fillMaxHeight(0.62f)
                        .zIndex(2f)
                        .background(Color(0xFF151515), RoundedCornerShape(bottomStart = 3.dp, bottomEnd = 3.dp))
                        .clickable { onPitchPressed(pitch) },
                )
            }
        }
    }
}
