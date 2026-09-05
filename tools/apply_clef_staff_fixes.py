from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if old not in text:
        raise SystemExit(f"missing patch target: {label}")
    return text.replace(old, new, 1)

# Version bump.
p = Path("app/build.gradle.kts")
s = p.read_text()
s = replace_once(s, 'versionCode = 32\n        versionName = "0.2.29"', 'versionCode = 33\n        versionName = "0.2.30"', 'version')
p.write_text(s)

# Clef model/helper.
Path("app/src/main/java/com/scoreforge/app/music/ScoreClefs.kt").write_text('''package com.scoreforge.app.music

enum class ScoreClefMode(val displayName: String) {
    AUTO("Auto"),
    TREBLE("Treble"),
    BASS("Bass"),
}

enum class ScoreClef(val displayName: String) {
    TREBLE("Treble"),
    BASS("Bass"),
}

/** Staff-clef selection. Auto deliberately chooses one clef for the whole active track. */
object ScoreClefs {
    private const val AUTO_BASS_MAX_MEDIAN_PITCH = 59 // B3 and below favors bass; C4+ favors treble.

    fun effective(mode: ScoreClefMode, events: List<ScoreEvent>): ScoreClef = when (mode) {
        ScoreClefMode.TREBLE -> ScoreClef.TREBLE
        ScoreClefMode.BASS -> ScoreClef.BASS
        ScoreClefMode.AUTO -> autoFor(events)
    }

    fun autoFor(events: List<ScoreEvent>): ScoreClef {
        val pitches = events.filterIsInstance<ScoreNote>().map { it.midiPitch }.sorted()
        if (pitches.isEmpty()) return ScoreClef.TREBLE
        val middle = pitches.size / 2
        val median = if (pitches.size % 2 == 1) {
            pitches[middle]
        } else {
            (pitches[middle - 1] + pitches[middle]) / 2
        }
        return if (median <= AUTO_BASS_MAX_MEDIAN_PITCH) ScoreClef.BASS else ScoreClef.TREBLE
    }

    /** Diatonic position of the bottom staff line: E4 for treble, G2 for bass. */
    fun bottomLineDiatonic(clef: ScoreClef): Int = when (clef) {
        ScoreClef.TREBLE -> 4 * 7 + 2 // E4
        ScoreClef.BASS -> 2 * 7 + 4 // G2
    }
}
''')

# Track model persistence field.
p = Path("app/src/main/java/com/scoreforge/app/music/ScoreTracks.kt")
s = p.read_text()
s = replace_once(
    s,
    '    val pan: Int = CENTER_PAN,\n    val timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),',
    '    val pan: Int = CENTER_PAN,\n    val clefMode: ScoreClefMode = ScoreClefMode.AUTO,\n    val timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),',
    'track clef field',
)
p.write_text(s)

# Project codec: optional TRACK field keeps old v2 files valid.
p = Path("app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt")
s = p.read_text()
s = replace_once(
    s,
    '                .append(safeTrack.volume).append(\'\\t\')\n                .append(safeTrack.pan).append(\'\\n\')',
    '                .append(safeTrack.volume).append(\'\\t\')\n                .append(safeTrack.pan).append(\'\\t\')\n                .append(safeTrack.clefMode.name).append(\'\\n\')',
    'encode clef',
)
s = replace_once(
    s,
    '        val pan = parts.getOrNull(9)?.toIntOrNull() ?: ScoreTrack.CENTER_PAN\n        return TrackBuilder(id, name, cursorBeat, bank, program, muted, solo, volume, pan)',
    '        val pan = parts.getOrNull(9)?.toIntOrNull() ?: ScoreTrack.CENTER_PAN\n        val clefMode = parts.getOrNull(10)?.let { stored ->\n            ScoreClefMode.entries.firstOrNull { it.name == stored }\n        } ?: ScoreClefMode.AUTO\n        return TrackBuilder(id, name, cursorBeat, bank, program, muted, solo, volume, pan, clefMode)',
    'decode clef',
)
s = replace_once(
    s,
    '        val pan: Int,\n        val events: MutableList<ScoreEvent> = mutableListOf(),',
    '        val pan: Int,\n        val clefMode: ScoreClefMode,\n        val events: MutableList<ScoreEvent> = mutableListOf(),',
    'track builder clef',
)
s = replace_once(
    s,
    '            volume = volume,\n            pan = pan,\n        )',
    '            volume = volume,\n            pan = pan,\n            clefMode = clefMode,\n        )',
    'track build clef',
)
p.write_text(s)

# Gesture intent helper.
p = Path("app/src/main/java/com/scoreforge/app/ui/StaffCursorInteraction.kt")
s = p.read_text()
s = replace_once(
    s,
    'internal enum class StaffCursorZone {\n    PLAYBACK,\n    STAFF,\n    ENTRY,\n}\n',
    'internal enum class StaffCursorZone {\n    PLAYBACK,\n    STAFF,\n    ENTRY,\n}\n\ninternal enum class StaffCursorDragIntent {\n    CURSOR,\n    VERTICAL_SCROLL,\n}\n',
    'drag intent enum',
)
s = replace_once(
    s,
    '    fun zoneForY(\n',
    '    fun dragIntent(deltaX: Float, deltaY: Float): StaffCursorDragIntent =\n        if (kotlin.math.abs(deltaY) > kotlin.math.abs(deltaX)) {\n            StaffCursorDragIntent.VERTICAL_SCROLL\n        } else {\n            StaffCursorDragIntent.CURSOR\n        }\n\n    fun zoneForY(\n',
    'drag intent function',
)
p.write_text(s)

# Staff editor: clef-aware pitch layout plus tap-vs-vertical-drag routing.
p = Path("app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt")
s = p.read_text()
s = replace_once(s, 'import com.scoreforge.app.music.ScoreAccidental\n', 'import com.scoreforge.app.music.ScoreAccidental\nimport com.scoreforge.app.music.ScoreClef\nimport com.scoreforge.app.music.ScoreClefMode\nimport com.scoreforge.app.music.ScoreClefs\n', 'staff clef imports')
s = replace_once(s, '    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),\n    selectedEventIndex: Int,', '    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),\n    clefMode: ScoreClefMode = ScoreClefMode.AUTO,\n    selectedEventIndex: Int,', 'staff clef param')
s = replace_once(
    s,
    '    var draggingPlaybackCursor by remember { mutableStateOf(false) }\n    var draggingEntryCursor by remember { mutableStateOf(false) }\n    var manualBrowseNotified by remember { mutableStateOf(false) }',
    '    var draggingPlaybackCursor by remember { mutableStateOf(false) }\n    var draggingEntryCursor by remember { mutableStateOf(false) }\n    var pendingPlaybackCursorDrag by remember { mutableStateOf(false) }\n    var pendingEntryCursorDrag by remember { mutableStateOf(false) }\n    var draggingVerticalFromCursorGutter by remember { mutableStateOf(false) }\n    var manualBrowseNotified by remember { mutableStateOf(false) }',
    'staff gesture states',
)
s = replace_once(
    s,
    '    val density = LocalDensity.current\n    val transport by ScoreTransportBus.state.collectAsState()\n',
    '    val density = LocalDensity.current\n    val transport by ScoreTransportBus.state.collectAsState()\n    val effectiveClef = remember(events, clefMode) { ScoreClefs.effective(clefMode, events) }\n',
    'effective clef',
)
s = s.replace('staffGeometry(events, keySignatures, size.height.toFloat())', 'staffGeometry(events, keySignatures, size.height.toFloat(), effectiveClef)')
s = s.replace('staffGeometry(events, keySignatures, size.height)', 'staffGeometry(events, keySignatures, size.height, effectiveClef)')
# Hit testing calls all gain the clef argument before key signatures.
s = s.replace('                                        geometry,\n                                        keySignatures,\n                                        notationGaps,', '                                        geometry,\n                                        effectiveClef,\n                                        keySignatures,\n                                        notationGaps,')
s = replace_once(s, 'onAddPitch(pitchFromY(position.y, geometry), tappedBeat)', 'onAddPitch(pitchFromY(position.y, geometry, effectiveClef), tappedBeat)', 'tap pitch clef')
s = replace_once(s, '                                            geometry,\n                                            preferSharp = false,', '                                            geometry,\n                                            effectiveClef,\n                                            preferSharp = false,', 'drag pitch clef')
# Draw header, notes and curves use effective clef.
s = replace_once(
    s,
    '                    drawNotationHeader(\n                        geometry,\n                        timelineLeftPx,\n                        ScoreKeySignatures.atBeat(normalizedKeySignatures, 0f),',
    '                    drawNotationHeader(\n                        geometry,\n                        timelineLeftPx,\n                        effectiveClef,\n                        ScoreKeySignatures.atBeat(normalizedKeySignatures, 0f),',
    'header clef',
)
s = replace_once(
    s,
    '                                previous = previous,\n                                timelineLeftPx = timelineLeftPx,',
    '                                previous = previous,\n                                clef = effectiveClef,\n                                timelineLeftPx = timelineLeftPx,',
    'key change clef',
)
s = replace_once(
    s,
    '                                geometry,\n                                ScoreKeySignatures.atBeat(normalizedKeySignatures, event.startBeat),',
    '                                geometry,\n                                effectiveClef,\n                                ScoreKeySignatures.atBeat(normalizedKeySignatures, event.startBeat),',
    'note draw clef',
)
s = s.replace('                            geometry,\n                            normalizedKeySignatures,\n                            notationGaps,', '                            geometry,\n                            effectiveClef,\n                            normalizedKeySignatures,\n                            notationGaps,')
# Gesture start: defer cursor movement until drag direction is known.
old_drag_start = '''                                onDragStart = { position ->
                                    manualBrowseNotified = false
                                    draggingPlaybackCursor = false
                                    draggingEntryCursor = false
                                    val geometry = staffGeometry(events, keySignatures, size.height.toFloat(), effectiveClef)
                                    draggingEventIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                        effectiveClef,
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
                                },'''
new_drag_start = '''                                onDragStart = { position ->
                                    manualBrowseNotified = false
                                    draggingPlaybackCursor = false
                                    draggingEntryCursor = false
                                    pendingPlaybackCursorDrag = false
                                    pendingEntryCursorDrag = false
                                    draggingVerticalFromCursorGutter = false
                                    val geometry = staffGeometry(events, keySignatures, size.height.toFloat(), effectiveClef)
                                    draggingEventIndex = nearestEditableEventIndex(
                                        events,
                                        position,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        geometry,
                                        effectiveClef,
                                        keySignatures,
                                        notationGaps,
                                    )
                                    if (draggingEventIndex >= 0) {
                                        onSelectEvent(draggingEventIndex)
                                        onBeginMove(draggingEventIndex)
                                    } else {
                                        when (
                                            StaffCursorInteraction.zoneForY(
                                                y = position.y,
                                                staffTop = geometry.staffTop,
                                                staffBottom = geometry.staffBottom,
                                                lineSpacing = geometry.lineSpacing,
                                            )
                                        ) {
                                            StaffCursorZone.PLAYBACK -> pendingPlaybackCursorDrag = true
                                            StaffCursorZone.ENTRY -> pendingEntryCursorDrag = true
                                            StaffCursorZone.STAFF -> Unit
                                        }
                                    }
                                },'''
s = replace_once(s, old_drag_start, new_drag_start, 'deferred cursor drag start')
s = s.replace(
    '                                    draggingPlaybackCursor = false\n                                    draggingEntryCursor = false\n                                    manualBrowseNotified = false',
    '                                    draggingPlaybackCursor = false\n                                    draggingEntryCursor = false\n                                    pendingPlaybackCursorDrag = false\n                                    pendingEntryCursorDrag = false\n                                    draggingVerticalFromCursorGutter = false\n                                    manualBrowseNotified = false',
)
old_drag_body = '''                            ) { change, dragAmount ->
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

                                val event = events.getOrNull(draggingEventIndex)'''
new_drag_body = '''                            ) { change, dragAmount ->
                                if (pendingPlaybackCursorDrag || pendingEntryCursorDrag) {
                                    when (StaffCursorInteraction.dragIntent(dragAmount.x, dragAmount.y)) {
                                        StaffCursorDragIntent.VERTICAL_SCROLL -> {
                                            pendingPlaybackCursorDrag = false
                                            pendingEntryCursorDrag = false
                                            draggingVerticalFromCursorGutter = true
                                            onVerticalPan(dragAmount.y)
                                        }
                                        StaffCursorDragIntent.CURSOR -> {
                                            draggingPlaybackCursor = pendingPlaybackCursorDrag
                                            draggingEntryCursor = pendingEntryCursorDrag
                                            pendingPlaybackCursorDrag = false
                                            pendingEntryCursorDrag = false
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
                                            onSelectEvent(-1)
                                        }
                                    }
                                    change.consume()
                                    return@detectDragGestures
                                }

                                if (draggingVerticalFromCursorGutter) {
                                    onVerticalPan(dragAmount.y)
                                    change.consume()
                                    return@detectDragGestures
                                }

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

                                val event = events.getOrNull(draggingEventIndex)'''
s = replace_once(s, old_drag_body, new_drag_body, 'cursor drag direction routing')
# Function signatures and pitch reference math.
s = replace_once(
    s,
    'private fun staffGeometry(\n    events: List<ScoreEvent>,\n    keySignatures: List<ScoreKeySignature>,\n    height: Float,\n): StaffGeometry {',
    'private fun staffGeometry(\n    events: List<ScoreEvent>,\n    keySignatures: List<ScoreKeySignature>,\n    height: Float,\n    clef: ScoreClef,\n): StaffGeometry {',
    'staff geometry clef signature',
)
s = replace_once(
    s,
    '        val e4Diatonic = 4 * 7 + 2\n        val key = ScoreKeySignatures.atBeat(keySignatures, note.startBeat)\n        val steps = ScorePitchSpelling.spell(note.midiPitch, key).diatonicPosition - e4Diatonic',
    '        val bottomLineDiatonic = ScoreClefs.bottomLineDiatonic(clef)\n        val key = ScoreKeySignatures.atBeat(keySignatures, note.startBeat)\n        val steps = ScorePitchSpelling.spell(note.midiPitch, key).diatonicPosition - bottomLineDiatonic',
    'staff geometry pitch reference',
)
s = replace_once(
    s,
    'private fun DrawScope.drawNotationHeader(\n    geometry: StaffGeometry,\n    timelineLeftPx: Float,\n    keySignature: ScoreKeySignature,',
    'private fun DrawScope.drawNotationHeader(\n    geometry: StaffGeometry,\n    timelineLeftPx: Float,\n    clef: ScoreClef,\n    keySignature: ScoreKeySignature,',
    'header signature',
)
s = replace_once(
    s,
    '        canvas.nativeCanvas.drawText(\n            "𝄞",\n            timelineLeftPx * 0.16f,\n            geometry.staffTop + geometry.lineSpacing * 3.24f,\n            clefPaint,\n        )\n\n        drawKeySignatureSymbols(\n            signature = keySignature,\n            startX = timelineLeftPx * 0.30f,\n            geometry = geometry,\n        )',
    '        val clefSymbol = if (clef == ScoreClef.BASS) "𝄢" else "𝄞"\n        val clefBaseline = if (clef == ScoreClef.BASS) {\n            geometry.staffTop + geometry.lineSpacing * 2.88f\n        } else {\n            geometry.staffTop + geometry.lineSpacing * 3.24f\n        }\n        canvas.nativeCanvas.drawText(\n            clefSymbol,\n            timelineLeftPx * 0.16f,\n            clefBaseline,\n            clefPaint,\n        )\n\n        drawKeySignatureSymbols(\n            signature = keySignature,\n            startX = timelineLeftPx * 0.30f,\n            geometry = geometry,\n            clef = clef,\n        )',
    'header glyph and key clef',
)
# Key signature positions for bass.
s = s.replace('private val sharpKeyPositions = listOf(', 'private val trebleSharpKeyPositions = listOf(', 1)
s = s.replace('private val flatKeyPositions = listOf(', 'private val trebleFlatKeyPositions = listOf(', 1)
insert_after_flats = '''private val trebleFlatKeyPositions = listOf(
    KeySymbolPosition(6, 4 * 7 + 6), // B4
    KeySymbolPosition(2, 5 * 7 + 2), // E5
    KeySymbolPosition(5, 4 * 7 + 5), // A4
    KeySymbolPosition(1, 5 * 7 + 1), // D5
    KeySymbolPosition(4, 4 * 7 + 4), // G4
    KeySymbolPosition(0, 5 * 7 + 0), // C5
    KeySymbolPosition(3, 4 * 7 + 3), // F4
)
'''
replacement_flats = insert_after_flats + '''
private val bassSharpKeyPositions = listOf(
    KeySymbolPosition(3, 3 * 7 + 3), // F3
    KeySymbolPosition(0, 3 * 7 + 0), // C3
    KeySymbolPosition(4, 3 * 7 + 4), // G3
    KeySymbolPosition(1, 3 * 7 + 1), // D3
    KeySymbolPosition(5, 2 * 7 + 5), // A2
    KeySymbolPosition(2, 3 * 7 + 2), // E3
    KeySymbolPosition(6, 2 * 7 + 6), // B2
)

private val bassFlatKeyPositions = listOf(
    KeySymbolPosition(6, 2 * 7 + 6), // B2
    KeySymbolPosition(2, 3 * 7 + 2), // E3
    KeySymbolPosition(5, 2 * 7 + 5), // A2
    KeySymbolPosition(1, 3 * 7 + 1), // D3
    KeySymbolPosition(4, 2 * 7 + 4), // G2
    KeySymbolPosition(0, 3 * 7 + 0), // C3
    KeySymbolPosition(3, 3 * 7 + 3), // F3
)

private fun keyPositions(signature: ScoreKeySignature, clef: ScoreClef): List<KeySymbolPosition> = when {
    signature.fifths > 0 && clef == ScoreClef.BASS -> bassSharpKeyPositions.take(signature.fifths)
    signature.fifths < 0 && clef == ScoreClef.BASS -> bassFlatKeyPositions.take(-signature.fifths)
    signature.fifths > 0 -> trebleSharpKeyPositions.take(signature.fifths)
    signature.fifths < 0 -> trebleFlatKeyPositions.take(-signature.fifths)
    else -> emptyList()
}
'''
s = replace_once(s, insert_after_flats, replacement_flats, 'bass key positions')
s = replace_once(
    s,
    '    geometry: StaffGeometry,\n    symbolOverride: String? = null,',
    '    geometry: StaffGeometry,\n    clef: ScoreClef,\n    symbolOverride: String? = null,',
    'key symbols clef param',
)
s = replace_once(
    s,
    '    val positions = positionsOverride ?: when {\n        safe.fifths > 0 -> sharpKeyPositions.take(safe.fifths)\n        safe.fifths < 0 -> flatKeyPositions.take(-safe.fifths)\n        else -> emptyList()\n    }',
    '    val positions = positionsOverride ?: keyPositions(safe, clef)',
    'key positions selection',
)
s = replace_once(s, 'yForDiatonicPosition(item.diatonicPosition, geometry)', 'yForDiatonicPosition(item.diatonicPosition, geometry, clef)', 'key y clef')
s = replace_once(
    s,
    '    previous: ScoreKeySignature,\n    timelineLeftPx: Float,',
    '    previous: ScoreKeySignature,\n    clef: ScoreClef,\n    timelineLeftPx: Float,',
    'key change signature clef',
)
s = replace_once(
    s,
    '    val previousPositions = if (previous.fifths > 0) {\n        sharpKeyPositions.take(previous.fifths)\n    } else {\n        flatKeyPositions.take(-previous.fifths)\n    }',
    '    val previousPositions = keyPositions(previous, clef)',
    'previous key positions clef',
)
s = s.replace('            geometry = geometry,\n            symbolOverride = "♮",', '            geometry = geometry,\n            clef = clef,\n            symbolOverride = "♮",')
s = replace_once(s, '    drawKeySignatureSymbols(signature, x, geometry)', '    drawKeySignatureSymbols(signature, x, geometry, clef)', 'key change draw clef')
s = replace_once(
    s,
    'private fun yForDiatonicPosition(position: Int, geometry: StaffGeometry): Float {\n    val e4Diatonic = 4 * 7 + 2\n    val steps = position - e4Diatonic',
    'private fun yForDiatonicPosition(position: Int, geometry: StaffGeometry, clef: ScoreClef): Float {\n    val bottomLineDiatonic = ScoreClefs.bottomLineDiatonic(clef)\n    val steps = position - bottomLineDiatonic',
    'diatonic y clef',
)
s = replace_once(
    s,
    '    geometry: StaffGeometry,\n    keySignature: ScoreKeySignature,\n    selected: Boolean,',
    '    geometry: StaffGeometry,\n    clef: ScoreClef,\n    keySignature: ScoreKeySignature,\n    selected: Boolean,',
    'draw note clef param',
)
s = replace_once(s, '    val y = noteY(note.midiPitch, geometry, keySignature)', '    val y = noteY(note.midiPitch, geometry, clef, keySignature)', 'note y clef')
# Curve functions both receive clef and pass it into noteY.
s = s.replace('    geometry: StaffGeometry,\n    keySignatures: List<ScoreKeySignature>,\n    notationGaps: List<StaffNotationGap>,', '    geometry: StaffGeometry,\n    clef: ScoreClef,\n    keySignatures: List<ScoreKeySignature>,\n    notationGaps: List<StaffNotationGap>,')
s = s.replace('noteY(source.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, source.startBeat))', 'noteY(source.midiPitch, geometry, clef, ScoreKeySignatures.atBeat(keySignatures, source.startBeat))')
s = s.replace('noteY(target.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, target.startBeat))', 'noteY(target.midiPitch, geometry, clef, ScoreKeySignatures.atBeat(keySignatures, target.startBeat))')
# noteY / pitchFromY / hit test.
s = replace_once(
    s,
    'private fun noteY(\n    midiPitch: Int,\n    geometry: StaffGeometry,\n    keySignature: ScoreKeySignature = ScoreKeySignatures.DEFAULT,',
    'private fun noteY(\n    midiPitch: Int,\n    geometry: StaffGeometry,\n    clef: ScoreClef,\n    keySignature: ScoreKeySignature = ScoreKeySignatures.DEFAULT,',
    'noteY signature',
)
s = replace_once(s, '        geometry,\n    )\n}\n\nprivate fun pitchFromY(', '        geometry,\n        clef,\n    )\n}\n\nprivate fun pitchFromY(', 'noteY clef forwarding')
s = replace_once(
    s,
    'private fun pitchFromY(\n    y: Float,\n    geometry: StaffGeometry,\n    preferSharp: Boolean = false,\n): Int {\n    val e4Diatonic = 4 * 7 + 2\n    val target = e4Diatonic +',
    'private fun pitchFromY(\n    y: Float,\n    geometry: StaffGeometry,\n    clef: ScoreClef,\n    preferSharp: Boolean = false,\n): Int {\n    val bottomLineDiatonic = ScoreClefs.bottomLineDiatonic(clef)\n    val target = bottomLineDiatonic +',
    'pitchFromY clef',
)
s = replace_once(
    s,
    '    geometry: StaffGeometry,\n    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),',
    '    geometry: StaffGeometry,\n    clef: ScoreClef,\n    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),',
    'hit test clef signature',
)
s = replace_once(s, 'is ScoreNote -> noteY(event.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, event.startBeat))', 'is ScoreNote -> noteY(event.midiPitch, geometry, clef, ScoreKeySignatures.atBeat(keySignatures, event.startBeat))', 'hit test note clef')
p.write_text(s)

# Composer: reset Hold/Natural timing when playback interrupts entry, expose per-track clef selector.
p = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
s = p.read_text()
s = replace_once(s, 'import com.scoreforge.app.music.ScoreEditHistory\n', 'import com.scoreforge.app.music.ScoreClef\nimport com.scoreforge.app.music.ScoreClefMode\nimport com.scoreforge.app.music.ScoreClefs\nimport com.scoreforge.app.music.ScoreEditHistory\n', 'composer clef imports')
s = replace_once(
    s,
    '    fun startPlayback() {\n        if (playableNoteCount <= 0 || liveRecordingActive || isPlaying) return\n        isPlaying = true',
    '    fun startPlayback() {\n        if (playableNoteCount <= 0 || liveRecordingActive || isPlaying) return\n        when (pianoEntryMode) {\n            PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()\n            PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()\n            else -> Unit\n        }\n        LiveInstrumentBus.allNotesOff()\n        isPlaying = true',
    'playback clears entry timing anchor',
)
s = replace_once(
    s,
    '    fun finishMixerGesture() {\n        mixerGestureHistoryRecorded = false\n        syncHistoryButtons()\n    }',
    '    fun finishMixerGesture() {\n        mixerGestureHistoryRecorded = false\n        syncHistoryButtons()\n    }\n\n    fun setActiveTrackClefMode(mode: ScoreClefMode) {\n        if (currentTrack().clefMode == mode) return\n        when (pianoEntryMode) {\n            PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()\n            PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()\n            else -> Unit\n        }\n        recordBeforeScoreEdit()\n        replaceActiveTrack { it.copy(clefMode = mode) }\n        selectedEventIndex = -1\n        syncHistoryButtons()\n    }',
    'set clef mode function',
)
s = replace_once(
    s,
    '                EditorModeControls(\n                    mode = editorMode,\n                    showPianoKeyboard = showPianoKeyboard,\n                    onModeChanged = { editorMode = it },\n                    onTogglePianoKeyboard = {\n                        stopLiveRecording()\n                        cancelNaturalEntryGroup()\n                        LiveInstrumentBus.allNotesOff()\n                        showPianoKeyboard = !showPianoKeyboard\n                    },\n                )\n\n                when (editorMode) {',
    '                EditorModeControls(\n                    mode = editorMode,\n                    showPianoKeyboard = showPianoKeyboard,\n                    onModeChanged = { editorMode = it },\n                    onTogglePianoKeyboard = {\n                        stopLiveRecording()\n                        cancelNaturalEntryGroup()\n                        LiveInstrumentBus.allNotesOff()\n                        showPianoKeyboard = !showPianoKeyboard\n                    },\n                )\n\n                ClefControls(\n                    mode = activeTrack.clefMode,\n                    effectiveClef = ScoreClefs.effective(activeTrack.clefMode, activeEvents),\n                    onModeChanged = ::setActiveTrackClefMode,\n                )\n\n                when (editorMode) {',
    'clef controls placement',
)
s = replace_once(
    s,
    '                        keySignatures = keySignatures,\n                        selectedEventIndex = selectedEventIndex,',
    '                        keySignatures = keySignatures,\n                        clefMode = activeTrack.clefMode,\n                        selectedEventIndex = selectedEventIndex,',
    'staff clef wiring',
)
# Add selector composable before HeaderBar.
marker = '\n@Composable\nprivate fun HeaderBar('
clef_ui = '''
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
                Button(onClick = { onModeChanged(option) }) { Text(option.displayName) }
            } else {
                OutlinedButton(onClick = { onModeChanged(option) }) { Text(option.displayName) }
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
'''
if marker not in s:
    raise SystemExit('missing HeaderBar marker')
s = s.replace(marker, '\n' + clef_ui + marker, 1)
p.write_text(s)

# Tests.
Path("app/src/test/java/com/scoreforge/app/music/ScoreClefsTest.kt").write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreClefsTest {
    @Test fun emptyAutoDefaultsToTreble() {
        assertEquals(ScoreClef.TREBLE, ScoreClefs.effective(ScoreClefMode.AUTO, emptyList()))
    }

    @Test fun lowTrackAutomaticallyUsesBass() {
        val events = listOf(
            ScoreNote(36, NoteDuration.QUARTER, 0f),
            ScoreNote(43, NoteDuration.QUARTER, 1f),
            ScoreNote(48, NoteDuration.QUARTER, 2f),
        )
        assertEquals(ScoreClef.BASS, ScoreClefs.effective(ScoreClefMode.AUTO, events))
    }

    @Test fun highTrackAutomaticallyUsesTreble() {
        val events = listOf(
            ScoreNote(64, NoteDuration.QUARTER, 0f),
            ScoreNote(67, NoteDuration.QUARTER, 1f),
            ScoreNote(72, NoteDuration.QUARTER, 2f),
        )
        assertEquals(ScoreClef.TREBLE, ScoreClefs.effective(ScoreClefMode.AUTO, events))
    }

    @Test fun manualModeOverridesRange() {
        val low = listOf(ScoreNote(36, NoteDuration.QUARTER, 0f))
        assertEquals(ScoreClef.TREBLE, ScoreClefs.effective(ScoreClefMode.TREBLE, low))
        assertEquals(ScoreClef.BASS, ScoreClefs.effective(ScoreClefMode.BASS, emptyList()))
    }

    @Test fun bottomLineReferencesMatchStandardClefs() {
        assertEquals(4 * 7 + 2, ScoreClefs.bottomLineDiatonic(ScoreClef.TREBLE)) // E4
        assertEquals(2 * 7 + 4, ScoreClefs.bottomLineDiatonic(ScoreClef.BASS)) // G2
    }
}
''')

Path("app/src/test/java/com/scoreforge/app/music/ClefProjectPersistenceTest.kt").write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ClefProjectPersistenceTest {
    @Test fun trackClefRoundTripsThroughProjectCodec() {
        val track = ScoreTrack(id = 1, name = "Bass", clefMode = ScoreClefMode.BASS)
        val snapshot = ScoreProjectSnapshot(events = emptyList(), tracks = listOf(track))
        val decoded = ScoreProjectCodec.decode(ScoreProjectCodec.encode(snapshot))
        assertNotNull(decoded)
        assertEquals(ScoreClefMode.BASS, decoded!!.effectiveTracks().first().clefMode)
    }

    @Test fun oldTrackHeaderWithoutClefDefaultsToAuto() {
        val oldV2 = """SCOREFORGE\t2
PROJECT_NAME\tOld
BPM\t120
ACTIVE_TRACK\t0
TRACK\t1\tTrack 1\t0.0\t0\t-1\t-1\t0\t100\t0
END_TRACK
"""
        val decoded = ScoreProjectCodec.decode(oldV2)
        assertNotNull(decoded)
        assertEquals(ScoreClefMode.AUTO, decoded!!.effectiveTracks().first().clefMode)
    }
}
''')

# Extend existing cursor interaction tests with drag classification regression coverage.
p = Path("app/src/test/java/com/scoreforge/app/ui/StaffCursorInteractionTest.kt")
s = p.read_text()
insert = '''
    @Test
    fun verticalDragInCursorGutterRoutesToPageScroll() {
        assertEquals(
            StaffCursorDragIntent.VERTICAL_SCROLL,
            StaffCursorInteraction.dragIntent(deltaX = 3f, deltaY = 18f),
        )
    }

    @Test
    fun horizontalDragInCursorGutterRoutesToCursor() {
        assertEquals(
            StaffCursorDragIntent.CURSOR,
            StaffCursorInteraction.dragIntent(deltaX = 18f, deltaY = 3f),
        )
    }
'''
idx = s.rfind('}')
if idx < 0:
    raise SystemExit('missing test class close')
s = s[:idx] + insert + s[idx:]
p.write_text(s)
