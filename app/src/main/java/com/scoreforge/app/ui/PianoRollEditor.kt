package com.scoreforge.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.ScoreTransportBus
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreEvent
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTimeSignatures
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.abs
import kotlin.math.roundToInt

private val PIANO_ROLL_BEAT_WIDTH = 52.dp
private const val PIANO_ROLL_MIN_BEATS = 16f

/**
 * Deliberately simple piano roll based on Score Forge's original implementation.
 *
 * The old roll was easy to read because the entire song was squeezed into one viewport, but that
 * became unusably tiny as songs grew. This version keeps that original fixed-pitch layout and note
 * rendering while giving time a constant readable scale and a normal horizontal scroll container.
 */
@Composable
fun PianoRollEditor(
    events: List<ScoreEvent>,
    selectedDuration: NoteDuration,
    cursorBeat: Float,
    timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),
    octaveShift: Int,
    selectedEventIndex: Int,
    followPlayback: Boolean = true,
    onAddPitch: (pitch: Int, startBeat: Float) -> Unit,
    onSelectEvent: (eventIndex: Int) -> Unit,
    onBeginMove: (eventIndex: Int) -> Unit,
    onMoveNote: (eventIndex: Int, pitch: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    onVerticalPan: (dragY: Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    val horizontalScroll = rememberScrollState()
    val density = LocalDensity.current
    val transport by ScoreTransportBus.state.collectAsState()

    val lowPitch = PianoRollMapping.lowPitch(octaveShift)
    val highPitch = PianoRollMapping.highPitch(octaveShift)
    val visibleNotes = events.filterIsInstance<ScoreNote>()
    val furthestBeat = maxOf(cursorBeat, transport.beat, ScoreTimeline.endBeat(events))
    val activeMeter = ScoreTimeSignatures.atBeat(timeSignatures, furthestBeat)
    val contentBeats = maxOf(
        PIANO_ROLL_MIN_BEATS,
        ScoreTimeline.visibleBeats(
            events,
            throughBeat = furthestBeat + activeMeter.beatsPerMeasure,
            timeSignatures = timeSignatures,
        ),
    )

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val beatWidthPx = with(density) { PIANO_ROLL_BEAT_WIDTH.toPx() }
            val contentWidthPx = maxOf(
                viewportWidthPx,
                PianoRollMapping.LEFT_GUTTER_PX +
                    contentBeats * beatWidthPx +
                    PianoRollMapping.RIGHT_MARGIN_PX,
            )
            val contentWidth = with(density) { contentWidthPx.toDp() }

            LaunchedEffect(
                cursorBeat,
                events.size,
                transport.beat,
                transport.isPlaying,
                horizontalScroll.maxValue,
                viewportWidthPx,
                contentWidthPx,
            ) {
                if (horizontalScroll.maxValue <= 0) return@LaunchedEffect
                if (transport.isPlaying && !followPlayback) return@LaunchedEffect
                val followBeat = if (transport.isPlaying) transport.beat else cursorBeat
                val x = PianoRollMapping.xAtBeat(followBeat, contentBeats, contentWidthPx)
                val viewportLeft = horizontalScroll.value.toFloat()
                val leftEdge = viewportLeft + viewportWidthPx * 0.12f
                val rightEdge = viewportLeft + viewportWidthPx * 0.86f
                val target = when {
                    x > rightEdge -> x - viewportWidthPx * 0.72f
                    x < leftEdge -> x - viewportWidthPx * 0.18f
                    else -> null
                } ?: return@LaunchedEffect
                horizontalScroll.scrollTo(
                    target.roundToInt().coerceIn(0, horizontalScroll.maxValue)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScroll),
            ) {
                Canvas(
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight()
                        .pointerInput(
                            events,
                            contentBeats,
                            lowPitch,
                            highPitch,
                            selectedDuration,
                        ) {
                            detectTapGestures(
                                onLongPress = { position ->
                                    val noteIndex = noteIndexAtPoint(
                                        events,
                                        position,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        contentBeats,
                                        lowPitch,
                                        highPitch,
                                    )
                                    if (noteIndex >= 0) onDeleteEvent(noteIndex)
                                },
                                onTap = { position ->
                                    if (position.x < PianoRollMapping.LEFT_GUTTER_PX) {
                                        return@detectTapGestures
                                    }
                                    val noteIndex = noteIndexAtPoint(
                                        events,
                                        position,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        contentBeats,
                                        lowPitch,
                                        highPitch,
                                    )
                                    if (noteIndex >= 0) {
                                        onSelectEvent(noteIndex)
                                        return@detectTapGestures
                                    }
                                    val pitch = PianoRollMapping.pitchAtY(
                                        position.y,
                                        lowPitch,
                                        highPitch,
                                        size.height.toFloat(),
                                    )
                                    val startBeat = ScoreTimeline.quantizeBeat(
                                        PianoRollMapping.beatAtX(
                                            position.x,
                                            contentBeats,
                                            size.width.toFloat(),
                                        )
                                    ).coerceIn(0f, contentBeats)
                                    onAddPitch(pitch, startBeat)
                                },
                            )
                        }
                        .pointerInput(events, contentBeats, lowPitch, highPitch) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    draggingEventIndex = noteIndexAtPoint(
                                        events,
                                        position,
                                        size.width.toFloat(),
                                        size.height.toFloat(),
                                        contentBeats,
                                        lowPitch,
                                        highPitch,
                                    )
                                    if (draggingEventIndex >= 0) {
                                        onSelectEvent(draggingEventIndex)
                                        onBeginMove(draggingEventIndex)
                                    }
                                },
                                onDragEnd = { draggingEventIndex = -1 },
                                onDragCancel = { draggingEventIndex = -1 },
                            ) { change, dragAmount ->
                                val note = events.getOrNull(draggingEventIndex) as? ScoreNote
                                if (note == null) {
                                    if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                                        horizontalScroll.dispatchRawDelta(-dragAmount.x)
                                    } else {
                                        onVerticalPan(dragAmount.y)
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }

                                val pitch = PianoRollMapping.pitchAtY(
                                    change.position.y,
                                    lowPitch,
                                    highPitch,
                                    size.height.toFloat(),
                                )
                                val latestStart =
                                    (contentBeats - note.effectiveBeats).coerceAtLeast(0f)
                                val startBeat = ScoreTimeline.quantizeBeat(
                                    PianoRollMapping.beatAtX(
                                        change.position.x,
                                        contentBeats,
                                        size.width.toFloat(),
                                    )
                                ).coerceIn(0f, latestStart)
                                onMoveNote(draggingEventIndex, pitch, startBeat)
                                change.consume()
                            }
                        },
                ) {
                    val rowHeight = PianoRollMapping.rowHeight(size.height, lowPitch, highPitch)
                    val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = android.graphics.Color.rgb(48, 48, 48)
                        textSize = (rowHeight * 0.78f).coerceIn(8f, 16f)
                    }

                    for (pitch in lowPitch..highPitch) {
                        val row = highPitch - pitch
                        val top = row * rowHeight
                        val isSharp = PitchNames.hasSharp(pitch)
                        drawRect(
                            color = if (isSharp) Color(0xFFE1E4E8) else Color(0xFFF7F7F7),
                            topLeft = Offset(PianoRollMapping.LEFT_GUTTER_PX, top),
                            size = androidx.compose.ui.geometry.Size(
                                (size.width - PianoRollMapping.LEFT_GUTTER_PX).coerceAtLeast(0f),
                                rowHeight,
                            ),
                        )
                        drawLine(
                            color = Color(0xFFC8CCD1),
                            start = Offset(PianoRollMapping.LEFT_GUTTER_PX, top),
                            end = Offset(size.width, top),
                            strokeWidth = 1f,
                        )
                    }

                    drawRect(
                        color = Color(0xFFECEFF1),
                        topLeft = Offset.Zero,
                        size = androidx.compose.ui.geometry.Size(
                            PianoRollMapping.LEFT_GUTTER_PX,
                            size.height,
                        ),
                    )
                    for (pitch in lowPitch..highPitch) {
                        if (pitch % 12 == 0) {
                            val y = PianoRollMapping.yCenterForPitch(
                                pitch,
                                lowPitch,
                                highPitch,
                                size.height,
                            )
                            drawContext.canvas.nativeCanvas.drawText(
                                PitchNames.name(pitch),
                                5f,
                                y + labelPaint.textSize * 0.32f,
                                labelPaint,
                            )
                        }
                    }
                    drawLine(
                        color = Color(0xFF9EA4AA),
                        start = Offset(PianoRollMapping.LEFT_GUTTER_PX, 0f),
                        end = Offset(PianoRollMapping.LEFT_GUTTER_PX, size.height),
                        strokeWidth = 1.5f,
                    )

                    var gridBeat = 0f
                    while (gridBeat <= contentBeats + 0.001f) {
                        val x = PianoRollMapping.xAtBeat(gridBeat, contentBeats, size.width)
                        val isQuarter = kotlin.math.abs(gridBeat % 1f) < 0.001f
                        drawLine(
                            color = if (isQuarter) Color(0xFFAEB4BA) else Color(0xFFD7DADF),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = if (isQuarter) 1.2f else 0.7f,
                        )
                        gridBeat += ScoreTimeline.EDIT_GRID_BEATS
                    }

                    ScoreTimeSignatures.measureBoundaries(
                        ScoreTimeSignatures.normalize(timeSignatures),
                        contentBeats,
                    ).forEach { boundary ->
                        val x = PianoRollMapping.xAtBeat(boundary, contentBeats, size.width)
                        drawLine(
                            color = Color(0xFF6E747A),
                            start = Offset(x, 0f),
                            end = Offset(x, size.height),
                            strokeWidth = 2f,
                        )
                    }

                    events.forEachIndexed { index, event ->
                        val note = event as? ScoreNote ?: return@forEachIndexed
                        if (note.midiPitch !in lowPitch..highPitch) return@forEachIndexed
                        val rect = noteRect(
                            note,
                            size.width,
                            size.height,
                            contentBeats,
                            lowPitch,
                            highPitch,
                        )
                        drawRoundRect(
                            color = Color(0xFF355C8A),
                            topLeft = rect.topLeft,
                            size = rect.size,
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                        )
                        if (index == selectedEventIndex) {
                            drawRoundRect(
                                color = Color(0xFFB43B3B),
                                topLeft = rect.topLeft,
                                size = rect.size,
                                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f, 3f),
                                style = Stroke(width = 2.5f),
                            )
                        }
                    }

                    val cursorX = PianoRollMapping.xAtBeat(
                        cursorBeat.coerceIn(0f, contentBeats),
                        contentBeats,
                        size.width,
                    )
                    drawLine(
                        color = Color(0xFFB43B3B),
                        start = Offset(cursorX, 0f),
                        end = Offset(cursorX, size.height),
                        strokeWidth = 2f,
                    )

                    val playheadX = PianoRollMapping.xAtBeat(
                        transport.beat.coerceIn(0f, contentBeats),
                        contentBeats,
                        size.width,
                    )
                    drawLine(
                        color = Color(0xFF6A52A3),
                        start = Offset(playheadX, 0f),
                        end = Offset(playheadX, size.height),
                        strokeWidth = 2.5f,
                    )
                }
            }
        }

        if (visibleNotes.isEmpty()) {
            Text(
                "Tap the grid to place ${selectedDuration.displayName.lowercase()} notes. Swipe horizontally for time; drag notes to move them.",
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(10.dp),
                color = Color(0xFF555555),
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Text(
            "${PitchNames.name(lowPitch)}–${PitchNames.name(highPitch)}",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp),
            color = Color(0xFF555555),
            style = MaterialTheme.typography.labelSmall,
        )
    }
}

private fun noteRect(
    note: ScoreNote,
    width: Float,
    height: Float,
    visibleBeats: Float,
    lowPitch: Int,
    highPitch: Int,
): Rect {
    val rowHeight = PianoRollMapping.rowHeight(height, lowPitch, highPitch)
    val centerY = PianoRollMapping.yCenterForPitch(note.midiPitch, lowPitch, highPitch, height)
    val x0 = PianoRollMapping.xAtBeat(note.startBeat, visibleBeats, width)
    val x1 = PianoRollMapping.xAtBeat(note.startBeat + note.effectiveBeats, visibleBeats, width)
    return Rect(
        left = x0 + 1f,
        top = centerY - rowHeight * 0.40f,
        right = maxOf(x0 + 5f, x1 - 1f),
        bottom = centerY + rowHeight * 0.40f,
    )
}

private fun noteIndexAtPoint(
    events: List<ScoreEvent>,
    point: Offset,
    width: Float,
    height: Float,
    visibleBeats: Float,
    lowPitch: Int,
    highPitch: Int,
): Int {
    for (index in events.indices.reversed()) {
        val note = events[index] as? ScoreNote ?: continue
        if (note.midiPitch !in lowPitch..highPitch) continue
        val rect = noteRect(note, width, height, visibleBeats, lowPitch, highPitch)
        if (rect.contains(point)) return index
    }
    return -1
}
