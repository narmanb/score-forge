from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAFF = ROOT / "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
BUILD = ROOT / "app/build.gradle.kts"
ZONE = ROOT / "app/src/main/java/com/scoreforge/app/ui/StaffCursorInteraction.kt"
TEST = ROOT / "app/src/test/java/com/scoreforge/app/ui/StaffCursorInteractionTest.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


staff = STAFF.read_text()
staff = replace_once(
    staff,
    '''    onVerticalPan: (dragY: Float) -> Unit = {},
    onManualBrowse: () -> Unit = {},
    modifier: Modifier = Modifier,
''',
    '''    onVerticalPan: (dragY: Float) -> Unit = {},
    onManualBrowse: () -> Unit = {},
    onMoveEntryCursor: (beat: Float) -> Unit = {},
    modifier: Modifier = Modifier,
''',
    "staff callback signature",
)
staff = replace_once(
    staff,
    '''    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var manualBrowseNotified by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
''',
    '''    var draggingEventIndex by remember { mutableIntStateOf(-1) }
    var draggingPlaybackCursor by remember { mutableStateOf(false) }
    var draggingEntryCursor by remember { mutableStateOf(false) }
    var manualBrowseNotified by remember { mutableStateOf(false) }
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
''',
    "staff cursor drag state",
)
staff = replace_once(
    staff,
    '''                                    val rulerTap =
                                        position.y <= geometry.staffTop - geometry.lineSpacing * 0.85f
                                    if (rulerTap || !staffInputEnabled) {
                                        ScoreTransportBus.seek(tappedBeat)
                                        onSelectEvent(-1)
                                        return@detectTapGestures
                                    }

                                    onAddPitch(pitchFromY(position.y, geometry), tappedBeat)
''',
    '''                                    when (
                                        StaffCursorInteraction.zoneForY(
                                            y = position.y,
                                            staffTop = geometry.staffTop,
                                            staffBottom = geometry.staffBottom,
                                            lineSpacing = geometry.lineSpacing,
                                        )
                                    ) {
                                        StaffCursorZone.PLAYBACK -> {
                                            ScoreTransportBus.seek(tappedBeat)
                                            onSelectEvent(-1)
                                            return@detectTapGestures
                                        }
                                        StaffCursorZone.ENTRY -> {
                                            onMoveEntryCursor(tappedBeat)
                                            onSelectEvent(-1)
                                            return@detectTapGestures
                                        }
                                        StaffCursorZone.STAFF -> Unit
                                    }

                                    if (!staffInputEnabled) {
                                        ScoreTransportBus.seek(tappedBeat)
                                        onSelectEvent(-1)
                                        return@detectTapGestures
                                    }

                                    onAddPitch(pitchFromY(position.y, geometry), tappedBeat)
''',
    "staff tap zones",
)
staff = replace_once(
    staff,
    '''                                onDragStart = { position ->
                                    manualBrowseNotified = false
                                    val geometry = staffGeometry(events, keySignatures, size.height.toFloat())
                                    draggingEventIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                        keySignatures,
                                        notationGaps,
                                    )
                                    if (draggingEventIndex >= 0) {
                                        onSelectEvent(draggingEventIndex)
                                        onBeginMove(draggingEventIndex)
                                    }
                                },
                                onDragEnd = {
                                    draggingEventIndex = -1
                                    manualBrowseNotified = false
                                },
                                onDragCancel = {
                                    draggingEventIndex = -1
                                    manualBrowseNotified = false
                                },
''',
    '''                                onDragStart = { position ->
                                    manualBrowseNotified = false
                                    draggingPlaybackCursor = false
                                    draggingEntryCursor = false
                                    val geometry = staffGeometry(events, keySignatures, size.height.toFloat())
                                    draggingEventIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                        keySignatures,
                                        notationGaps,
                                    )
                                    if (draggingEventIndex >= 0) {
                                        onSelectEvent(draggingEventIndex)
                                        onBeginMove(draggingEventIndex)
                                    } else {
                                        val startBeat = ScoreTimeline.quantizeBeat(
                                            StaffNotationSpacing.beatAtX(
                                                position.x,
                                                timelineLeftPx,
                                                beatWidthPx,
                                                notationGaps,
                                            )
                                        ).coerceIn(0f, contentBeats)
                                        when (
                                            StaffCursorInteraction.zoneForY(
                                                y = position.y,
                                                staffTop = geometry.staffTop,
                                                staffBottom = geometry.staffBottom,
                                                lineSpacing = geometry.lineSpacing,
                                            )
                                        ) {
                                            StaffCursorZone.PLAYBACK -> {
                                                draggingPlaybackCursor = true
                                                ScoreTransportBus.seek(startBeat)
                                                onSelectEvent(-1)
                                            }
                                            StaffCursorZone.ENTRY -> {
                                                draggingEntryCursor = true
                                                onMoveEntryCursor(startBeat)
                                                onSelectEvent(-1)
                                            }
                                            StaffCursorZone.STAFF -> Unit
                                        }
                                    }
                                },
                                onDragEnd = {
                                    draggingEventIndex = -1
                                    draggingPlaybackCursor = false
                                    draggingEntryCursor = false
                                    manualBrowseNotified = false
                                },
                                onDragCancel = {
                                    draggingEventIndex = -1
                                    draggingPlaybackCursor = false
                                    draggingEntryCursor = false
                                    manualBrowseNotified = false
                                },
''',
    "staff drag start zones",
)
staff = replace_once(
    staff,
    '''                            ) { change, dragAmount ->
                                val event = events.getOrNull(draggingEventIndex)
                                if (event == null) {
''',
    '''                            ) { change, dragAmount ->
                                if (draggingPlaybackCursor || draggingEntryCursor) {
                                    val movedBeat = ScoreTimeline.quantizeBeat(
                                        StaffNotationSpacing.beatAtX(
                                            change.position.x,
                                            timelineLeftPx,
                                            beatWidthPx,
                                            notationGaps,
                                        )
                                    ).coerceIn(0f, contentBeats)
                                    if (draggingPlaybackCursor) {
                                        ScoreTransportBus.seek(movedBeat)
                                    } else {
                                        onMoveEntryCursor(movedBeat)
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }

                                val event = events.getOrNull(draggingEventIndex)
                                if (event == null) {
''',
    "staff cursor dragging",
)
staff = replace_once(
    staff,
    '''                    drawCircle(
                        Color(0xFFB34747),
                        radius = 4f,
                        center = Offset(entryCursorX, geometry.rulerY + geometry.lineSpacing * 0.12f),
                    )

                    val playheadX = StaffNotationSpacing.xAtBeat(
''',
    '''                    drawCircle(
                        Color(0xFFB34747),
                        radius = 4f,
                        center = Offset(entryCursorX, geometry.rulerY + geometry.lineSpacing * 0.12f),
                    )
                    val entryHandleTipY = geometry.staffBottom + geometry.lineSpacing * 0.72f
                    val entryHandleBaseY = geometry.staffBottom + geometry.lineSpacing * 1.10f
                    val entryHandleHalfWidth = geometry.lineSpacing * 0.30f
                    drawPath(
                        path = Path().apply {
                            moveTo(entryCursorX, entryHandleTipY)
                            lineTo(entryCursorX - entryHandleHalfWidth, entryHandleBaseY)
                            lineTo(entryCursorX + entryHandleHalfWidth, entryHandleBaseY)
                            close()
                        },
                        color = Color(0xFFB34747),
                    )

                    val playheadX = StaffNotationSpacing.xAtBeat(
''',
    "entry cursor handle",
)
STAFF.write_text(staff)

composer = COMPOSER.read_text()
composer = replace_once(
    composer,
    '''    fun finishNaturalPhraseForStaffBrowse() {
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

    fun finalizeNaturalGroupForNextAttack(group: NaturalOnsetGroup, nextOnsetMs: Long): Float {
''',
    '''    fun finishNaturalPhraseForStaffBrowse() {
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

    fun moveEntryCursor(beat: Float) {
        if (pianoEntryMode == PianoEntryMode.NATURAL) {
            finishNaturalPhraseForStaffBrowse()
            LiveInstrumentBus.allNotesOff()
        } else if (pianoEntryMode == PianoEntryMode.LIVE && liveRecordingActive) {
            stopLiveRecording()
        }
        val targetBeat = ScoreTimeline.quantizeBeat(beat).coerceAtLeast(0f)
        replaceActiveTrack { it.copy(cursorBeat = targetBeat) }
        selectedEventIndex = -1
    }

    fun finalizeNaturalGroupForNextAttack(group: NaturalOnsetGroup, nextOnsetMs: Long): Float {
''',
    "composer entry cursor helper",
)
composer = replace_once(
    composer,
    '''                        onManualBrowse = {
                            if (pianoEntryMode == PianoEntryMode.NATURAL) {
                                finishNaturalPhraseForStaffBrowse()
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(300.dp),
''',
    '''                        onManualBrowse = {
                            if (pianoEntryMode == PianoEntryMode.NATURAL) {
                                finishNaturalPhraseForStaffBrowse()
                            }
                        },
                        onMoveEntryCursor = ::moveEntryCursor,
                        modifier = Modifier.fillMaxWidth().height(300.dp),
''',
    "composer staff callback",
)
COMPOSER.write_text(composer)

build = BUILD.read_text()
build = replace_once(build, 'versionCode = 28', 'versionCode = 29', 'version code')
build = replace_once(build, 'versionName = "0.2.25"', 'versionName = "0.2.26"', 'version name')
BUILD.write_text(build)

ZONE.write_text('''package com.scoreforge.app.ui

internal enum class StaffCursorZone {
    PLAYBACK,
    STAFF,
    ENTRY,
}

/** Keeps transport seeking and note-entry positioning in separate touch gutters. */
internal object StaffCursorInteraction {
    private const val PLAYBACK_GUTTER_SPACING = 0.85f
    private const val ENTRY_GUTTER_SPACING = 0.55f

    fun zoneForY(
        y: Float,
        staffTop: Float,
        staffBottom: Float,
        lineSpacing: Float,
    ): StaffCursorZone {
        val safeSpacing = lineSpacing.coerceAtLeast(0f)
        val playbackBottom = staffTop - safeSpacing * PLAYBACK_GUTTER_SPACING
        val entryTop = staffBottom + safeSpacing * ENTRY_GUTTER_SPACING
        return when {
            y <= playbackBottom -> StaffCursorZone.PLAYBACK
            y >= entryTop -> StaffCursorZone.ENTRY
            else -> StaffCursorZone.STAFF
        }
    }
}
''')

TEST.write_text('''package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StaffCursorInteractionTest {
    private val staffTop = 80f
    private val staffBottom = 160f
    private val spacing = 20f

    @Test
    fun `top gutter controls playback position`() {
        assertEquals(
            StaffCursorZone.PLAYBACK,
            StaffCursorInteraction.zoneForY(60f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun `staff body remains normal editing territory`() {
        assertEquals(
            StaffCursorZone.STAFF,
            StaffCursorInteraction.zoneForY(120f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun `bottom gutter controls note entry cursor`() {
        assertEquals(
            StaffCursorZone.ENTRY,
            StaffCursorInteraction.zoneForY(180f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun `gutter boundary classification is deterministic`() {
        assertEquals(
            StaffCursorZone.PLAYBACK,
            StaffCursorInteraction.zoneForY(63f, staffTop, staffBottom, spacing),
        )
        assertEquals(
            StaffCursorZone.ENTRY,
            StaffCursorInteraction.zoneForY(171f, staffTop, staffBottom, spacing),
        )
    }
}
''')
