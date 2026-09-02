package com.scoreforge.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.ScoreTransportBus
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreEvent
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTies
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.roundToInt

private val PIANO_ROLL_BEAT_WIDTH = 52.dp
private val PIANO_ROLL_ROW_HEIGHT = 24.dp
private const val PIANO_ROLL_MIN_BEATS = 16f

@Composable
fun PianoRollEditor(
    events: List<ScoreEvent>,
    selectedDuration: NoteDuration,
    cursorBeat: Float,
    octaveShift: Int,
    selectedEventIndex: Int,
    onAddPitch: (pitch: Int, startBeat: Float) -> Unit,
    onSelectEvent: (eventIndex: Int) -> Unit,
    onBeginMove: (eventIndex: Int) -> Unit,
    onMoveNote: (eventIndex: Int, pitch: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var horizontalOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }
    var verticalOffsetPx by rememberSaveable { mutableFloatStateOf(0f) }
    val transport by ScoreTransportBus.state.collectAsState()
    val lowPitch = PianoRollMapping.lowPitch(octaveShift)
    val highPitch = PianoRollMapping.highPitch(octaveShift)
    val visibleNotes = events.filterIsInstance<ScoreNote>()
    val furthestBeat = maxOf(cursorBeat, transport.beat, ScoreTimeline.endBeat(events))
    val contentBeats = maxOf(
        PIANO_ROLL_MIN_BEATS,
        ScoreTimeline.visibleBeats(events, throughBeat = furthestBeat + ScoreTimeline.BEATS_PER_MEASURE),
    )
    val density = LocalDensity.current

    Box(
        modifier = modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(Color(0xFFF3F4F6), RoundedCornerShape(8.dp))
            .clipToBounds(),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportWidthPx = with(density) { maxWidth.toPx() }
            val viewportHeightPx = with(density) { maxHeight.toPx() }
            val beatWidthPx = with(density) { PIANO_ROLL_BEAT_WIDTH.toPx() }
            val rowHeightPx = with(density) { PIANO_ROLL_ROW_HEIGHT.toPx() }
            val contentWidthPx =
                PianoRollMapping.LEFT_GUTTER_PX +
                    contentBeats * beatWidthPx +
                    PianoRollMapping.RIGHT_MARGIN_PX
            val contentHeightPx = (highPitch - lowPitch + 1) * rowHeightPx
            val contentWidth = with(density) { contentWidthPx.toDp() }
            val contentHeight = with(density) { contentHeightPx.toDp() }
            val maxHorizontalOffset = (contentWidthPx - viewportWidthPx).coerceAtLeast(0f)
            val maxVerticalOffset = (contentHeightPx - viewportHeightPx).coerceAtLeast(0f)

            LaunchedEffect(maxHorizontalOffset, maxVerticalOffset) {
                horizontalOffsetPx = horizontalOffsetPx.coerceIn(0f, maxHorizontalOffset)
                verticalOffsetPx = verticalOffsetPx.coerceIn(0f, maxVerticalOffset)
            }

            LaunchedEffect(octaveShift, maxVerticalOffset, viewportHeightPx, contentHeightPx) {
                if (maxVerticalOffset <= 0f) return@LaunchedEffect
                val keyboardCenterPitch = (65 + octaveShift.coerceIn(-4, 3) * 12)
                    .coerceIn(lowPitch, highPitch)
                val centerY = PianoRollMapping.yCenterForPitch(
                    keyboardCenterPitch,
                    lowPitch,
                    highPitch,
                    contentHeightPx,
                )
                verticalOffsetPx =
                    (centerY - viewportHeightPx * 0.52f).coerceIn(0f, maxVerticalOffset)
            }

            LaunchedEffect(
                transport.beat,
                transport.isPlaying,
                maxHorizontalOffset,
                viewportWidthPx,
                contentWidthPx,
            ) {
                if (!transport.isPlaying || maxHorizontalOffset <= 0f) return@LaunchedEffect
                val playheadX = PianoRollMapping.xAtBeat(
                    transport.beat,
                    contentBeats,
                    contentWidthPx,
                )
                val viewportLeft = horizontalOffsetPx
                val rightFollowEdge = viewportLeft + viewportWidthPx * 0.82f
                val leftFollowEdge = viewportLeft + viewportWidthPx * 0.15f
                val target = when {
                    playheadX > rightFollowEdge -> playheadX - viewportWidthPx * 0.68f
                    playheadX < leftFollowEdge -> playheadX - viewportWidthPx * 0.20f
                    else -> null
                }
                if (target != null) {
                    horizontalOffsetPx = target.coerceIn(0f, maxHorizontalOffset)
                }
            }

            Canvas(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            -horizontalOffsetPx.roundToInt(),
                            -verticalOffsetPx.roundToInt(),
                        )
                    }
                    .width(contentWidth)
                    .height(contentHeight)
                    .pointerInput(events, contentBeats, lowPitch, highPitch, selectedDuration) {
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
                                )
                                onAddPitch(pitch, startBeat)
                            },
                        )
                    }
                    .pointerInput(
                        events,
                        contentBeats,
                        lowPitch,
                        highPitch,
                        maxHorizontalOffset,
                        maxVerticalOffset,
                    ) {
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
                                horizontalOffsetPx =
                                    (horizontalOffsetPx - dragAmount.x)
                                        .coerceIn(0f, maxHorizontalOffset)
                                verticalOffsetPx =
                                    (verticalOffsetPx - dragAmount.y)
                                        .coerceIn(0f, maxVerticalOffset)
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
                    textSize = (rowHeight * 0.72f).coerceIn(9f, 18f)
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
                        Color(0xFFC8CCD1),
                        Offset(PianoRollMapping.LEFT_GUTTER_PX, top),
                        Offset(size.width, top),
                        1f,
                    )
                }

                drawRect(
                    Color(0xFFECEFF1),
                    Offset.Zero,
                    androidx.compose.ui.geometry.Size(
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
                    Color(0xFF9EA4AA),
                    Offset(PianoRollMapping.LEFT_GUTTER_PX, 0f),
                    Offset(PianoRollMapping.LEFT_GUTTER_PX, size.height),
                    1.5f,
                )

                var gridBeat = 0f
                while (gridBeat <= contentBeats + 0.001f) {
                    val x = PianoRollMapping.xAtBeat(gridBeat, contentBeats, size.width)
                    val isMeasure = gridBeat % ScoreTimeline.BEATS_PER_MEASURE == 0f
                    val isQuarter = gridBeat % 1f == 0f
                    drawLine(
                        color = when {
                            isMeasure -> Color(0xFF6E747A)
                            isQuarter -> Color(0xFFAEB4BA)
                            else -> Color(0xFFD7DADF)
                        },
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = when {
                            isMeasure -> 2f
                            isQuarter -> 1.2f
                            else -> 0.7f
                        },
                    )
                    gridBeat += ScoreTimeline.EDIT_GRID_BEATS
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
                        Color(0xFF355C8A),
                        rect.topLeft,
                        rect.size,
                        androidx.compose.ui.geometry.CornerRadius(3f, 3f),
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

                events.forEachIndexed { sourceIndex, event ->
                    val source = event as? ScoreNote ?: return@forEachIndexed
                    if (!ScoreTies.hasValidTie(events, sourceIndex)) return@forEachIndexed
                    val targetIndex = ScoreTies.targetIndex(events, sourceIndex)
                        ?: return@forEachIndexed
                    val target = events.getOrNull(targetIndex) as? ScoreNote
                        ?: return@forEachIndexed
                    if (
                        source.midiPitch !in lowPitch..highPitch ||
                        target.midiPitch !in lowPitch..highPitch
                    ) {
                        return@forEachIndexed
                    }
                    val sourceRect = noteRect(
                        source,
                        size.width,
                        size.height,
                        contentBeats,
                        lowPitch,
                        highPitch,
                    )
                    val targetRect = noteRect(
                        target,
                        size.width,
                        size.height,
                        contentBeats,
                        lowPitch,
                        highPitch,
                    )
                    drawLine(
                        color = Color(0xFF355C8A),
                        start = Offset(sourceRect.right, sourceRect.center.y),
                        end = Offset(targetRect.left, targetRect.center.y),
                        strokeWidth = 3f,
                    )
                }

                val cursorX = PianoRollMapping.xAtBeat(
                    cursorBeat.coerceIn(0f, contentBeats),
                    contentBeats,
                    size.width,
                )
                drawLine(
                    Color(0xFFB43B3B),
                    Offset(cursorX, 0f),
                    Offset(cursorX, size.height),
                    1.6f,
                )

                if (transport.isPlaying) {
                    val playheadX = PianoRollMapping.xAtBeat(
                        transport.beat.coerceIn(0f, contentBeats),
                        contentBeats,
                        size.width,
                    )
                    drawLine(
                        Color(0xFF6A52A3),
                        Offset(playheadX, 0f),
                        Offset(playheadX, size.height),
                        3.2f,
                    )
                }
            }

            if (visibleNotes.isEmpty()) {
                Text(
                    "Tap to place ${selectedDuration.displayName.lowercase()} notes • drag empty grid to pan",
                    modifier = Modifier.align(Alignment.BottomCenter).padding(10.dp),
                    color = Color(0xFF555555),
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Text(
                "Drag empty grid ↔ ↕ • purple = playhead",
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                color = Color(0xFF555555),
                style = MaterialTheme.typography.labelSmall,
            )
        }
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
