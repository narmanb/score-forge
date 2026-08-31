package com.scoreforge.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreEvent
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreRest
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.abs

@Composable
fun ScoreStaffEditor(
    events: List<ScoreEvent>,
    selectedDuration: NoteDuration,
    cursorBeat: Float,
    onAddPitch: (Int) -> Unit,
    onBeginMove: (eventIndex: Int) -> Unit,
    onMoveNote: (eventIndex: Int, pitch: Int, startBeat: Float) -> Unit,
    onMoveRest: (eventIndex: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    val visibleBeats = ScoreTimeline.visibleBeats(events, throughBeat = cursorBeat)

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
                .pointerInput(events.size, selectedDuration, cursorBeat, visibleBeats) {
                    detectTapGestures(
                        onLongPress = { position ->
                            val eventIndex = nearestEditableEventIndex(
                                events = events,
                                point = position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                visibleBeats = visibleBeats,
                            )
                            if (eventIndex >= 0) onDeleteEvent(eventIndex)
                        },
                        onTap = { position ->
                            onAddPitch(pitchFromY(position.y, size.height.toFloat()))
                        },
                    )
                }
                .pointerInput(events.size, visibleBeats) {
                    detectDragGestures(
                        onDragStart = { position ->
                            draggingEventIndex = nearestEditableEventIndex(
                                events = events,
                                point = position,
                                width = size.width.toFloat(),
                                height = size.height.toFloat(),
                                visibleBeats = visibleBeats,
                            )
                            if (draggingEventIndex >= 0) onBeginMove(draggingEventIndex)
                        },
                        onDragEnd = { draggingEventIndex = -1 },
                        onDragCancel = { draggingEventIndex = -1 },
                    ) { change, _ ->
                        val event = events.getOrNull(draggingEventIndex) ?: return@detectDragGestures
                        val latestStart = (visibleBeats - event.duration.beats).coerceAtLeast(0f)
                        val movedBeat = ScoreTimeline.quantizeBeat(
                            beatFromX(
                                x = change.position.x,
                                visibleBeats = visibleBeats,
                                width = size.width.toFloat(),
                            )
                        ).coerceIn(0f, latestStart)

                        when (event) {
                            is ScoreNote -> onMoveNote(
                                draggingEventIndex,
                                pitchFromY(
                                    y = change.position.y,
                                    height = size.height.toFloat(),
                                    preferSharp = PitchNames.hasSharp(event.midiPitch),
                                ),
                                movedBeat,
                            )

                            is ScoreRest -> onMoveRest(draggingEventIndex, movedBeat)
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

            events.forEach { event ->
                when (event) {
                    is ScoreNote -> drawScoreNote(
                        note = event,
                        visibleBeats = visibleBeats,
                        staffTop = staffTop,
                        staffBottom = staffBottom,
                        lineSpacing = lineSpacing,
                    )

                    is ScoreRest -> drawScoreRest(
                        rest = event,
                        visibleBeats = visibleBeats,
                        staffTop = staffTop,
                        lineSpacing = lineSpacing,
                    )
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

        if (events.isEmpty()) {
            Text(
                "Tap the staff to place a ${selectedDuration.displayName.lowercase()} note, use Rest above, or play the piano below.",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp),
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun DrawScope.drawScoreNote(
    note: ScoreNote,
    visibleBeats: Float,
    staffTop: Float,
    staffBottom: Float,
    lineSpacing: Float,
) {
    val x = beatX(note.startBeat + 0.10f, visibleBeats, size.width)
    val y = noteY(note.midiPitch, staffBottom, lineSpacing)
    drawLedgerLines(x, y, staffTop, staffBottom, lineSpacing)

    if (PitchNames.hasSharp(note.midiPitch)) {
        drawSharpAccidental(x = x - 20f, y = y)
    }

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

private fun DrawScope.drawSharpAccidental(x: Float, y: Float) {
    val ink = Color(0xFF111111)
    drawLine(ink, Offset(x - 3f, y - 12f), Offset(x - 5f, y + 12f), 2.2f)
    drawLine(ink, Offset(x + 4f, y - 12f), Offset(x + 2f, y + 12f), 2.2f)
    drawLine(ink, Offset(x - 8f, y - 4f), Offset(x + 7f, y - 7f), 2.5f)
    drawLine(ink, Offset(x - 9f, y + 5f), Offset(x + 6f, y + 2f), 2.5f)
}

private fun DrawScope.drawScoreRest(
    rest: ScoreRest,
    visibleBeats: Float,
    staffTop: Float,
    lineSpacing: Float,
) {
    val x = beatX(rest.startBeat + 0.10f, visibleBeats, size.width)
    val middleY = staffTop + lineSpacing * 2f
    val ink = Color(0xFF111111)

    when (rest.duration) {
        NoteDuration.WHOLE -> {
            val lineY = staffTop + lineSpacing
            drawRect(
                color = ink,
                topLeft = Offset(x - 9f, lineY),
                size = Size(18f, 6f),
            )
        }

        NoteDuration.HALF -> {
            drawRect(
                color = ink,
                topLeft = Offset(x - 9f, middleY - 6f),
                size = Size(18f, 6f),
            )
        }

        NoteDuration.QUARTER -> {
            val path = Path().apply {
                moveTo(x + 3f, middleY - lineSpacing * 1.15f)
                lineTo(x - 4f, middleY - lineSpacing * 0.45f)
                lineTo(x + 5f, middleY - lineSpacing * 0.05f)
                lineTo(x - 2f, middleY + lineSpacing * 0.55f)
                lineTo(x + 5f, middleY + lineSpacing * 0.95f)
            }
            drawPath(path, ink, style = Stroke(width = 4f))
        }

        NoteDuration.EIGHTH,
        NoteDuration.SIXTEENTH -> {
            val stemTop = middleY - lineSpacing * 0.95f
            val stemBottom = middleY + lineSpacing * 0.70f
            drawLine(ink, Offset(x + 2f, stemTop), Offset(x + 2f, stemBottom), 3f)
            drawOval(
                color = ink,
                topLeft = Offset(x - 5f, stemBottom - 3f),
                size = Size(10f, 7f),
            )
            drawLine(
                ink,
                Offset(x + 2f, stemTop),
                Offset(x + 12f, stemTop + lineSpacing * 0.38f),
                3f,
            )
            if (rest.duration == NoteDuration.SIXTEENTH) {
                drawLine(
                    ink,
                    Offset(x + 2f, stemTop + 6f),
                    Offset(x + 12f, stemTop + lineSpacing * 0.38f + 6f),
                    3f,
                )
            }
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

private fun pitchFromY(y: Float, height: Float, preferSharp: Boolean = false): Int {
    val staffTop = height * 0.24f
    val lineSpacing = height * 0.11f
    val staffBottom = staffTop + lineSpacing * 4f
    val e4Diatonic = 4 * 7 + 2
    val target = e4Diatonic + ((staffBottom - y) / (lineSpacing / 2f)).toInt()

    var bestPitch = 60
    var bestDistance = Int.MAX_VALUE
    for (pitch in 36..96) {
        val distance = abs(PitchNames.diatonicPosition(pitch) - target)
        val pitchIsSharp = PitchNames.hasSharp(pitch)
        val bestIsSharp = PitchNames.hasSharp(bestPitch)
        val preferredSpelling = if (preferSharp) pitchIsSharp && !bestIsSharp else !pitchIsSharp && bestIsSharp

        if (distance < bestDistance || (distance == bestDistance && preferredSpelling)) {
            bestDistance = distance
            bestPitch = pitch
        }
    }
    return bestPitch
}

private fun nearestEditableEventIndex(
    events: List<ScoreEvent>,
    point: Offset,
    width: Float,
    height: Float,
    visibleBeats: Float,
): Int {
    val staffTop = height * 0.24f
    val lineSpacing = height * 0.11f
    val staffBottom = staffTop + lineSpacing * 4f
    val restY = staffTop + lineSpacing * 2f

    var nearest = -1
    var bestDistanceSquared = Float.MAX_VALUE
    events.forEachIndexed { index, event ->
        val x = beatX(event.startBeat + 0.10f, visibleBeats, width)
        val y = when (event) {
            is ScoreNote -> noteY(event.midiPitch, staffBottom, lineSpacing)
            is ScoreRest -> restY
        }
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

private fun DrawScope.drawLedgerLines(
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
