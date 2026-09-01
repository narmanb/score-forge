package com.scoreforge.app.ui

import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.scoreforge.app.audio.ScoreTransportBus
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.PitchNames
import com.scoreforge.app.music.ScoreEvent
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreRest
import com.scoreforge.app.music.ScoreTies
import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.abs
import kotlin.math.roundToInt

private val NOTATION_HEADER_WIDTH = 82.dp
private val TIMELINE_RIGHT_PADDING = 18.dp

private data class StaffGeometry(
    val staffTop: Float,
    val lineSpacing: Float,
    val staffBottom: Float,
) {
    val middleLine: Float get() = staffTop + lineSpacing * 2f
    val rulerY: Float get() = staffTop - lineSpacing * 1.55f
}

@Composable
fun ScoreStaffEditor(
    events: List<ScoreEvent>,
    selectedDuration: NoteDuration,
    cursorBeat: Float,
    selectedEventIndex: Int,
    onAddPitch: (pitch: Int, startBeat: Float) -> Unit,
    onSelectEvent: (eventIndex: Int) -> Unit,
    onBeginMove: (eventIndex: Int) -> Unit,
    onMoveNote: (eventIndex: Int, pitch: Int, startBeat: Float) -> Unit,
    onMoveRest: (eventIndex: Int, startBeat: Float) -> Unit,
    onDeleteEvent: (eventIndex: Int) -> Unit,
    onVerticalPan: (dragY: Float) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var zoom by remember { mutableFloatStateOf(1f) }
    var staffInputEnabled by rememberSaveable { mutableStateOf(true) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val transport by ScoreTransportBus.state.collectAsState()
    val contentBeats = StaffTimelineLayout.contentBeats(
        eventsEndBeat = ScoreTimeline.endBeat(events),
        editCursorBeat = cursorBeat,
        playheadBeat = transport.beat,
    )

    Box(
        modifier = Modifier
            .height(220.dp)
            .then(modifier)
            .padding(horizontal = 12.dp, vertical = 2.dp)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .background(Color(0xFFF9F7EF), RoundedCornerShape(8.dp)),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val viewportWidth = maxWidth
            val baseBeatWidth = maxOf(
                18.dp,
                (viewportWidth - NOTATION_HEADER_WIDTH - TIMELINE_RIGHT_PADDING) /
                    StaffTimelineLayout.DEFAULT_VISIBLE_BEATS,
            )
            val safeZoom = StaffTimelineLayout.clampZoom(zoom)
            val beatWidth = baseBeatWidth * safeZoom
            val contentWidth = maxOf(
                viewportWidth,
                NOTATION_HEADER_WIDTH + beatWidth * contentBeats + TIMELINE_RIGHT_PADDING,
            )
            val timelineLeftPx = with(density) { NOTATION_HEADER_WIDTH.toPx() }
            val beatWidthPx = with(density) { beatWidth.toPx() }
            val viewportWidthPx = with(density) { viewportWidth.toPx() }

            LaunchedEffect(transport.beat, transport.isPlaying, safeZoom, scrollState.maxValue) {
                if (!transport.isPlaying || scrollState.maxValue <= 0) return@LaunchedEffect
                val playheadX = StaffTimelineLayout.xAtBeat(
                    transport.beat,
                    timelineLeftPx,
                    beatWidthPx,
                )
                val viewportLeft = scrollState.value.toFloat()
                val rightFollowEdge = viewportLeft + viewportWidthPx * 0.82f
                val leftFollowEdge = viewportLeft + viewportWidthPx * 0.15f
                val target = when {
                    playheadX > rightFollowEdge -> playheadX - viewportWidthPx * 0.68f
                    playheadX < leftFollowEdge -> playheadX - viewportWidthPx * 0.20f
                    else -> null
                }
                if (target != null) {
                    scrollState.scrollTo(target.roundToInt().coerceIn(0, scrollState.maxValue))
                }
            }

            LaunchedEffect(
                cursorBeat,
                events.size,
                transport.isPlaying,
                safeZoom,
                scrollState.maxValue,
                viewportWidthPx,
                beatWidthPx,
            ) {
                if (transport.isPlaying) return@LaunchedEffect
                val target = StaffTimelineLayout.entryAutoFollowTarget(
                    cursorBeat = cursorBeat,
                    currentScrollPx = scrollState.value,
                    maxScrollPx = scrollState.maxValue,
                    viewportWidthPx = viewportWidthPx,
                    timelineLeftPx = timelineLeftPx,
                    pixelsPerBeat = beatWidthPx,
                ) ?: return@LaunchedEffect

                if (target != scrollState.value) {
                    scrollState.animateScrollTo(target)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(scrollState),
            ) {
                Canvas(
                    modifier = Modifier
                        .width(contentWidth)
                        .fillMaxHeight()
                        .pointerInput(
                            events,
                            selectedDuration,
                            cursorBeat,
                            contentBeats,
                            beatWidthPx,
                            timelineLeftPx,
                            staffInputEnabled,
                        ) {
                            detectTapGestures(
                                onLongPress = { position ->
                                    val geometry = staffGeometry(events, size.height.toFloat())
                                    val eventIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                    )
                                    if (eventIndex >= 0) onDeleteEvent(eventIndex)
                                },
                                onTap = { position ->
                                    val geometry = staffGeometry(events, size.height.toFloat())
                                    val existingIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                    )
                                    if (existingIndex >= 0) {
                                        onSelectEvent(existingIndex)
                                        return@detectTapGestures
                                    }

                                    val tappedBeat = ScoreTimeline.quantizeBeat(
                                        StaffTimelineLayout.beatAtX(
                                            position.x,
                                            timelineLeftPx,
                                            beatWidthPx,
                                        )
                                    ).coerceIn(0f, contentBeats)

                                    val rulerTap =
                                        position.y <= geometry.staffTop - geometry.lineSpacing * 0.85f
                                    if (rulerTap || !staffInputEnabled) {
                                        ScoreTransportBus.seek(tappedBeat)
                                        onSelectEvent(-1)
                                        return@detectTapGestures
                                    }

                                    onAddPitch(pitchFromY(position.y, geometry), tappedBeat)
                                },
                            )
                        }
                        .pointerInput(events, contentBeats, beatWidthPx, timelineLeftPx) {
                            detectDragGestures(
                                onDragStart = { position ->
                                    val geometry = staffGeometry(events, size.height.toFloat())
                                    draggingEventIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                    )
                                    if (draggingEventIndex >= 0) {
                                        onSelectEvent(draggingEventIndex)
                                        onBeginMove(draggingEventIndex)
                                    }
                                },
                                onDragEnd = { draggingEventIndex = -1 },
                                onDragCancel = { draggingEventIndex = -1 },
                            ) { change, dragAmount ->
                                val event = events.getOrNull(draggingEventIndex)
                                if (event == null) {
                                    if (abs(dragAmount.x) >= abs(dragAmount.y)) {
                                        scrollState.dispatchRawDelta(-dragAmount.x)
                                    } else {
                                        onVerticalPan(dragAmount.y)
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }

                                val geometry = staffGeometry(events, size.height.toFloat())
                                val latestStart =
                                    (contentBeats - event.effectiveBeats).coerceAtLeast(0f)
                                val movedBeat = ScoreTimeline.quantizeBeat(
                                    StaffTimelineLayout.beatAtX(
                                        change.position.x,
                                        timelineLeftPx,
                                        beatWidthPx,
                                    )
                                ).coerceIn(0f, latestStart)

                                when (event) {
                                    is ScoreNote -> onMoveNote(
                                        draggingEventIndex,
                                        pitchFromY(
                                            change.position.y,
                                            geometry,
                                            preferSharp = PitchNames.hasSharp(event.midiPitch),
                                        ),
                                        movedBeat,
                                    )
                                    is ScoreRest -> onMoveRest(draggingEventIndex, movedBeat)
                                }
                                change.consume()
                            }
                        },
                ) {
                    val geometry = staffGeometry(events, size.height)
                    val staffLeft = with(density) { 10.dp.toPx() }
                    val timelineRight = StaffTimelineLayout.xAtBeat(
                        contentBeats,
                        timelineLeftPx,
                        beatWidthPx,
                    )

                    repeat(5) { line ->
                        val y = geometry.staffTop + geometry.lineSpacing * line
                        drawLine(
                            Color(0xFF202020),
                            Offset(staffLeft, y),
                            Offset(timelineRight, y),
                            1.8f,
                        )
                    }
                    drawLine(
                        Color(0xFF202020),
                        Offset(staffLeft, geometry.staffTop),
                        Offset(staffLeft, geometry.staffBottom),
                        2.1f,
                    )
                    drawNotationHeader(geometry, timelineLeftPx)

                    var rulerBeat = 0f
                    while (rulerBeat <= contentBeats + 0.001f) {
                        val x = StaffTimelineLayout.xAtBeat(rulerBeat, timelineLeftPx, beatWidthPx)
                        val isMeasure =
                            (rulerBeat % ScoreTimeline.BEATS_PER_MEASURE) < 0.001f
                        drawLine(
                            Color(0xFF8A8880),
                            Offset(x, geometry.rulerY),
                            Offset(
                                x,
                                geometry.rulerY +
                                    geometry.lineSpacing * if (isMeasure) 0.45f else 0.24f,
                            ),
                            if (isMeasure) 1.5f else 1f,
                        )
                        rulerBeat += 1f
                    }

                    var measureBeat = 0f
                    while (measureBeat <= contentBeats + 0.001f) {
                        val x = StaffTimelineLayout.xAtBeat(
                            measureBeat,
                            timelineLeftPx,
                            beatWidthPx,
                        )
                        drawLine(
                            Color(0xFF303030),
                            Offset(x, geometry.staffTop),
                            Offset(x, geometry.staffBottom),
                            if (measureBeat == 0f) 2.1f else 1.3f,
                        )
                        measureBeat += ScoreTimeline.BEATS_PER_MEASURE
                    }

                    events.forEachIndexed { index, event ->
                        when (event) {
                            is ScoreNote -> drawScoreNote(
                                event,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                                index == selectedEventIndex,
                            )
                            is ScoreRest -> drawScoreRest(
                                event,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                                index == selectedEventIndex,
                            )
                        }
                    }

                    events.forEachIndexed { sourceIndex, event ->
                        if (event !is ScoreNote || !ScoreTies.hasValidTie(events, sourceIndex)) {
                            return@forEachIndexed
                        }
                        val targetIndex = ScoreTies.targetIndex(events, sourceIndex)
                            ?: return@forEachIndexed
                        val target = events.getOrNull(targetIndex) as? ScoreNote
                            ?: return@forEachIndexed
                        drawTieCurve(event, target, timelineLeftPx, beatWidthPx, geometry)
                    }

                    val playheadX = StaffTimelineLayout.xAtBeat(
                        transport.beat.coerceIn(0f, contentBeats),
                        timelineLeftPx,
                        beatWidthPx,
                    )
                    drawLine(
                        Color(0xFF6A52A3),
                        Offset(playheadX, geometry.rulerY - geometry.lineSpacing * 0.18f),
                        Offset(playheadX, geometry.staffBottom + geometry.lineSpacing * 1.35f),
                        3f,
                    )
                }
            }

            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 3.dp, end = 4.dp)
                    .background(Color(0xEAF9F7EF), RoundedCornerShape(7.dp))
                    .padding(horizontal = 3.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (staffInputEnabled) {
                    Button(
                        onClick = { staffInputEnabled = false },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) { Text("Input On", style = MaterialTheme.typography.labelSmall) }
                } else {
                    OutlinedButton(
                        onClick = { staffInputEnabled = true },
                        modifier = Modifier.height(28.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = Color(0xFF222222)
                        ),
                    ) { Text("Input Off", style = MaterialTheme.typography.labelSmall) }
                }

                OutlinedButton(
                    onClick = {
                        zoom = StaffTimelineLayout.clampZoom(
                            zoom - StaffTimelineLayout.ZOOM_STEP
                        )
                    },
                    enabled = zoom > StaffTimelineLayout.MIN_ZOOM + 0.001f,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF222222)
                    ),
                ) { Text("−") }
                Text(
                    "${(StaffTimelineLayout.clampZoom(zoom) * 100f).roundToInt()}%",
                    color = Color(0xFF333333),
                    style = MaterialTheme.typography.labelSmall,
                )
                OutlinedButton(
                    onClick = {
                        zoom = StaffTimelineLayout.clampZoom(
                            zoom + StaffTimelineLayout.ZOOM_STEP
                        )
                    },
                    enabled = zoom < StaffTimelineLayout.MAX_ZOOM - 0.001f,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFF222222)
                    ),
                ) { Text("+") }
            }
        }
    }
}

private fun staffGeometry(events: List<ScoreEvent>, height: Float): StaffGeometry {
    val center = height * 0.52f
    val baseSpacing = height * 0.085f
    val minSpacing = height * 0.03f
    val notes = events.filterIsInstance<ScoreNote>()
    var spacing = baseSpacing

    fun noteYAt(pitch: Int, candidateSpacing: Float): Float {
        val bottom = center + candidateSpacing * 2f
        val e4Diatonic = 4 * 7 + 2
        val steps = PitchNames.diatonicPosition(pitch) - e4Diatonic
        return bottom - steps * (candidateSpacing / 2f)
    }

    val topLimit = height * 0.045f
    val bottomLimit = height * 0.955f
    while (
        spacing > minSpacing &&
        notes.any {
            val y = noteYAt(it.midiPitch, spacing)
            y < topLimit || y > bottomLimit
        }
    ) {
        spacing = (spacing - 0.5f).coerceAtLeast(minSpacing)
    }

    return StaffGeometry(
        staffTop = center - spacing * 2f,
        lineSpacing = spacing,
        staffBottom = center + spacing * 2f,
    )
}

private fun DrawScope.drawNotationHeader(
    geometry: StaffGeometry,
    timelineLeftPx: Float,
) {
    drawIntoCanvas { canvas ->
        val ink = android.graphics.Color.rgb(20, 20, 20)
        val clefPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
            textSize = geometry.lineSpacing * 2.35f
        }
        canvas.nativeCanvas.drawText(
            "𝄞",
            timelineLeftPx * 0.34f,
            geometry.staffTop + geometry.lineSpacing * 3.24f,
            clefPaint,
        )

        val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = geometry.lineSpacing * 1.20f
        }
        val signatureX = timelineLeftPx * 0.70f
        canvas.nativeCanvas.drawText(
            "4",
            signatureX,
            geometry.staffTop + geometry.lineSpacing * 1.72f,
            signaturePaint,
        )
        canvas.nativeCanvas.drawText(
            "4",
            signatureX,
            geometry.staffTop + geometry.lineSpacing * 3.70f,
            signaturePaint,
        )
    }
}

private fun DrawScope.drawScoreNote(
    note: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    selected: Boolean,
) {
    val x = StaffTimelineLayout.xAtBeat(
        note.startBeat + 0.10f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val y = noteY(note.midiPitch, geometry)
    drawLedgerLines(x, y, geometry)

    if (selected) {
        drawCircle(
            Color(0xFF5B78A5),
            maxOf(10f, geometry.lineSpacing * 0.34f),
            Offset(x, y),
            style = Stroke(width = 2.3f),
        )
    }

    if (PitchNames.hasSharp(note.midiPitch)) {
        drawSharpAccidental(
            x - geometry.lineSpacing * 0.62f,
            y,
            geometry.lineSpacing / 40f,
        )
    }

    val noteWidth = maxOf(10f, geometry.lineSpacing * 0.42f)
    val noteHeight = maxOf(7f, geometry.lineSpacing * 0.27f)
    val filled = note.duration != NoteDuration.WHOLE && note.duration != NoteDuration.HALF
    if (filled) {
        drawOval(
            Color(0xFF111111),
            Offset(x - noteWidth / 2f, y - noteHeight / 2f),
            Size(noteWidth, noteHeight),
        )
    } else {
        drawOval(
            Color(0xFF111111),
            Offset(x - noteWidth / 2f, y - noteHeight / 2f),
            Size(noteWidth, noteHeight),
            style = Stroke(maxOf(1.8f, geometry.lineSpacing * 0.055f)),
        )
    }

    if (note.duration != NoteDuration.WHOLE) {
        val stemUp = y >= geometry.middleLine
        val stemX = x + if (stemUp) noteWidth * 0.43f else -noteWidth * 0.43f
        val stemEndY = y +
            if (stemUp) -geometry.lineSpacing * 2.6f else geometry.lineSpacing * 2.6f
        val stemWidth = maxOf(1.6f, geometry.lineSpacing * 0.055f)
        drawLine(Color(0xFF111111), Offset(stemX, y), Offset(stemX, stemEndY), stemWidth)

        if (note.duration == NoteDuration.EIGHTH || note.duration == NoteDuration.SIXTEENTH) {
            val direction = if (stemUp) 1f else -1f
            drawLine(
                Color(0xFF111111),
                Offset(stemX, stemEndY),
                Offset(
                    stemX + geometry.lineSpacing * 0.52f * direction,
                    stemEndY + geometry.lineSpacing * 0.58f * direction,
                ),
                stemWidth,
            )
            if (note.duration == NoteDuration.SIXTEENTH) {
                drawLine(
                    Color(0xFF111111),
                    Offset(stemX, stemEndY + geometry.lineSpacing * 0.18f * direction),
                    Offset(
                        stemX + geometry.lineSpacing * 0.52f * direction,
                        stemEndY + geometry.lineSpacing * 0.76f * direction,
                    ),
                    stemWidth,
                )
            }
        }
    }

    if (note.dotted) {
        drawCircle(
            Color(0xFF111111),
            maxOf(2.2f, geometry.lineSpacing * 0.07f),
            Offset(x + noteWidth * 0.95f, y),
        )
    }
}

private fun DrawScope.drawTieCurve(
    source: ScoreNote,
    target: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
) {
    val sourceX = StaffTimelineLayout.xAtBeat(
        source.startBeat + 0.16f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val targetX = StaffTimelineLayout.xAtBeat(
        target.startBeat + 0.04f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val sourceY = noteY(source.midiPitch, geometry)
    val targetY = noteY(target.midiPitch, geometry)
    val baseline = maxOf(sourceY, targetY) + geometry.lineSpacing * 0.55f
    val controlY = baseline + geometry.lineSpacing * 0.65f
    val path = Path().apply {
        moveTo(sourceX, baseline)
        quadraticBezierTo((sourceX + targetX) / 2f, controlY, targetX, baseline)
    }
    drawPath(
        path,
        Color(0xFF111111),
        style = Stroke(width = maxOf(1.5f, geometry.lineSpacing * 0.05f)),
    )
}

private fun DrawScope.drawSharpAccidental(x: Float, y: Float, scale: Float) {
    val ink = Color(0xFF111111)
    val s = scale.coerceIn(0.55f, 1.25f)
    drawLine(ink, Offset(x - 3f * s, y - 12f * s), Offset(x - 5f * s, y + 12f * s), 2f * s)
    drawLine(ink, Offset(x + 4f * s, y - 12f * s), Offset(x + 2f * s, y + 12f * s), 2f * s)
    drawLine(ink, Offset(x - 8f * s, y - 4f * s), Offset(x + 7f * s, y - 7f * s), 2.3f * s)
    drawLine(ink, Offset(x - 9f * s, y + 5f * s), Offset(x + 6f * s, y + 2f * s), 2.3f * s)
}

private fun DrawScope.drawScoreRest(
    rest: ScoreRest,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    selected: Boolean,
) {
    val x = StaffTimelineLayout.xAtBeat(
        rest.startBeat + 0.10f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val middleY = geometry.middleLine
    val ink = Color(0xFF111111)

    if (selected) {
        drawCircle(
            Color(0xFF5B78A5),
            maxOf(11f, geometry.lineSpacing * 0.38f),
            Offset(x, middleY),
            style = Stroke(width = 2.3f),
        )
    }

    val scale = (geometry.lineSpacing / 40f).coerceIn(0.60f, 1.25f)
    when (rest.duration) {
        NoteDuration.WHOLE -> {
            val lineY = geometry.staffTop + geometry.lineSpacing
            drawRect(ink, Offset(x - 9f * scale, lineY), Size(18f * scale, 6f * scale))
        }
        NoteDuration.HALF -> drawRect(
            ink,
            Offset(x - 9f * scale, middleY - 6f * scale),
            Size(18f * scale, 6f * scale),
        )
        NoteDuration.QUARTER -> {
            val path = Path().apply {
                moveTo(x + 3f * scale, middleY - geometry.lineSpacing * 1.05f)
                lineTo(x - 4f * scale, middleY - geometry.lineSpacing * 0.40f)
                lineTo(x + 5f * scale, middleY - geometry.lineSpacing * 0.05f)
                lineTo(x - 2f * scale, middleY + geometry.lineSpacing * 0.50f)
                lineTo(x + 5f * scale, middleY + geometry.lineSpacing * 0.86f)
            }
            drawPath(path, ink, style = Stroke(width = maxOf(2.4f, 3.5f * scale)))
        }
        NoteDuration.EIGHTH,
        NoteDuration.SIXTEENTH -> {
            val stemTop = middleY - geometry.lineSpacing * 0.90f
            val stemBottom = middleY + geometry.lineSpacing * 0.65f
            val width = maxOf(2f, 2.7f * scale)
            drawLine(
                ink,
                Offset(x + 2f * scale, stemTop),
                Offset(x + 2f * scale, stemBottom),
                width,
            )
            drawOval(
                ink,
                Offset(x - 5f * scale, stemBottom - 3f * scale),
                Size(10f * scale, 7f * scale),
            )
            drawLine(
                ink,
                Offset(x + 2f * scale, stemTop),
                Offset(x + 12f * scale, stemTop + geometry.lineSpacing * 0.34f),
                width,
            )
            if (rest.duration == NoteDuration.SIXTEENTH) {
                drawLine(
                    ink,
                    Offset(x + 2f * scale, stemTop + 6f * scale),
                    Offset(
                        x + 12f * scale,
                        stemTop + geometry.lineSpacing * 0.34f + 6f * scale,
                    ),
                    width,
                )
            }
        }
    }

    if (rest.dotted) {
        drawCircle(ink, maxOf(2.2f, 3f * scale), Offset(x + 16f * scale, middleY))
    }
}

private fun noteY(midiPitch: Int, geometry: StaffGeometry): Float {
    val e4Diatonic = 4 * 7 + 2
    val steps = PitchNames.diatonicPosition(midiPitch) - e4Diatonic
    return geometry.staffBottom - steps * (geometry.lineSpacing / 2f)
}

private fun pitchFromY(
    y: Float,
    geometry: StaffGeometry,
    preferSharp: Boolean = false,
): Int {
    val e4Diatonic = 4 * 7 + 2
    val target = e4Diatonic +
        ((geometry.staffBottom - y) / (geometry.lineSpacing / 2f)).roundToInt()

    var bestPitch = 60
    var bestDistance = Int.MAX_VALUE
    for (pitch in 0..127) {
        val distance = abs(PitchNames.diatonicPosition(pitch) - target)
        val pitchIsSharp = PitchNames.hasSharp(pitch)
        val bestIsSharp = PitchNames.hasSharp(bestPitch)
        val preferredSpelling = if (preferSharp) {
            pitchIsSharp && !bestIsSharp
        } else {
            !pitchIsSharp && bestIsSharp
        }
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
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
): Int {
    val restY = geometry.middleLine
    var nearest = -1
    var bestDistanceSquared = Float.MAX_VALUE

    events.forEachIndexed { index, event ->
        val x = StaffTimelineLayout.xAtBeat(
            event.startBeat + 0.10f,
            timelineLeftPx,
            pixelsPerBeat,
        )
        val y = when (event) {
            is ScoreNote -> noteY(event.midiPitch, geometry)
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

    val radius = maxOf(34f, geometry.lineSpacing * 1.15f)
    return if (bestDistanceSquared <= radius * radius) nearest else -1
}

private fun DrawScope.drawLedgerLines(
    x: Float,
    y: Float,
    geometry: StaffGeometry,
) {
    val halfWidth = maxOf(10f, geometry.lineSpacing * 0.42f)
    val stroke = maxOf(1.4f, geometry.lineSpacing * 0.045f)

    var ledgerY = geometry.staffBottom + geometry.lineSpacing
    while (y >= ledgerY - geometry.lineSpacing / 4f) {
        drawLine(
            Color(0xFF202020),
            Offset(x - halfWidth, ledgerY),
            Offset(x + halfWidth, ledgerY),
            stroke,
        )
        ledgerY += geometry.lineSpacing
    }

    ledgerY = geometry.staffTop - geometry.lineSpacing
    while (y <= ledgerY + geometry.lineSpacing / 4f) {
        drawLine(
            Color(0xFF202020),
            Offset(x - halfWidth, ledgerY),
            Offset(x + halfWidth, ledgerY),
            stroke,
        )
        ledgerY -= geometry.lineSpacing
    }
}
