from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
STAFF = ROOT / "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
BUILD = ROOT / "app/build.gradle.kts"


def replace_once(path: Path, old: str, new: str):
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path}, found {count}: {old[:120]!r}")
    path.write_text(text.replace(old, new, 1))


# Build/version bump.
replace_once(BUILD, '        versionCode = 21\n        versionName = "0.2.18"',
             '        versionCode = 22\n        versionName = "0.2.19"')

# Create a reusable notation-gap map for the whole editor and include it in content width.
replace_once(
    STAFF,
    '''    val contentBeats = StaffTimelineLayout.contentBeats(
        eventsEndBeat = ScoreTimeline.endBeat(events),
        editCursorBeat = cursorBeat,
        playheadBeat = transport.beat,
        timeSignatures = timeSignatures,
    )
''',
    '''    val contentBeats = StaffTimelineLayout.contentBeats(
        eventsEndBeat = ScoreTimeline.endBeat(events),
        editCursorBeat = cursorBeat,
        playheadBeat = transport.beat,
        timeSignatures = timeSignatures,
    )
    val notationGaps = remember(timeSignatures, keySignatures) {
        StaffNotationSpacing.gaps(timeSignatures, keySignatures)
    }
''',
)
replace_once(
    STAFF,
    '''            val contentWidth = maxOf(
                viewportWidth,
                NOTATION_HEADER_WIDTH + beatWidth * contentBeats + TIMELINE_RIGHT_PADDING,
            )''',
    '''            val contentWidth = maxOf(
                viewportWidth,
                NOTATION_HEADER_WIDTH +
                    beatWidth * (
                        contentBeats + StaffNotationSpacing.totalGapBeatsThrough(
                            contentBeats,
                            notationGaps,
                            includeGapAtBeat = true,
                        )
                    ) +
                    TIMELINE_RIGHT_PADDING,
            )''',
)

# Playback and edit-cursor auto-follow must use the visually expanded staff coordinate system.
replace_once(
    STAFF,
    '''                val playheadX = StaffTimelineLayout.xAtBeat(
                    transport.beat,
                    timelineLeftPx,
                    beatWidthPx,
                )''',
    '''                val playheadX = StaffNotationSpacing.xAtBeat(
                    transport.beat,
                    timelineLeftPx,
                    beatWidthPx,
                    notationGaps,
                    includeGapAtBeat = true,
                )''',
)
replace_once(
    STAFF,
    '''                val target = StaffTimelineLayout.entryAutoFollowTarget(
                    cursorBeat = cursorBeat,
                    currentScrollPx = scrollState.value,
                    maxScrollPx = scrollState.maxValue,
                    viewportWidthPx = viewportWidthPx,
                    timelineLeftPx = timelineLeftPx,
                    pixelsPerBeat = beatWidthPx,
                ) ?: return@LaunchedEffect

                if (target != scrollState.value) {
                    scrollState.animateScrollTo(target)
                }''',
    '''                val cursorX = StaffNotationSpacing.xAtBeat(
                    cursorBeat,
                    timelineLeftPx,
                    beatWidthPx,
                    notationGaps,
                    includeGapAtBeat = true,
                )
                val viewportLeft = scrollState.value.toFloat()
                val rightFollowEdge = viewportLeft + viewportWidthPx * 0.86f
                val leftFollowEdge = viewportLeft + viewportWidthPx * 0.12f
                val target = when {
                    cursorX > rightFollowEdge -> cursorX - viewportWidthPx * 0.72f
                    cursorX < leftFollowEdge -> cursorX - viewportWidthPx * 0.18f
                    else -> null
                }?.roundToInt()?.coerceIn(0, scrollState.maxValue) ?: return@LaunchedEffect

                if (target != scrollState.value) {
                    scrollState.animateScrollTo(target)
                }''',
)

# Include notation gaps in pointer-input invalidation.
replace_once(
    STAFF,
    '''                            timelineLeftPx,
                            staffInputEnabled,
                        ) {''',
    '''                            timelineLeftPx,
                            staffInputEnabled,
                            notationGaps,
                        ) {''',
)
replace_once(
    STAFF,
    '''                        .pointerInput(events, contentBeats, beatWidthPx, timelineLeftPx) {''',
    '''                        .pointerInput(events, contentBeats, beatWidthPx, timelineLeftPx, notationGaps) {''',
)

# All three nearest-event lookups need expanded event coordinates.
needle = '''                                        geometry,
                                        keySignatures,
                                    )'''
replacement = '''                                        geometry,
                                        keySignatures,
                                        notationGaps,
                                    )'''
text = STAFF.read_text()
count = text.count(needle)
if count != 3:
    raise RuntimeError(f"Expected 3 nearest-event call matches, found {count}")
STAFF.write_text(text.replace(needle, replacement))

# Tap/drag beat mapping must invert the visual gaps.
needle = '''StaffTimelineLayout.beatAtX(
                                            position.x,
                                            timelineLeftPx,
                                            beatWidthPx,
                                        )'''
replace_once(
    STAFF,
    needle,
    '''StaffNotationSpacing.beatAtX(
                                            position.x,
                                            timelineLeftPx,
                                            beatWidthPx,
                                            notationGaps,
                                        )''',
)
needle = '''StaffTimelineLayout.beatAtX(
                                        change.position.x,
                                        timelineLeftPx,
                                        beatWidthPx,
                                    )'''
replace_once(
    STAFF,
    needle,
    '''StaffNotationSpacing.beatAtX(
                                        change.position.x,
                                        timelineLeftPx,
                                        beatWidthPx,
                                        notationGaps,
                                    )''',
)

# Canvas grid: the barline at a notation change stays at the real beat, while content at/after it shifts.
replace_once(
    STAFF,
    '''                    val timelineRight = StaffTimelineLayout.xAtBeat(
                        contentBeats,
                        timelineLeftPx,
                        beatWidthPx,
                    )''',
    '''                    val timelineRight = StaffNotationSpacing.xAtBeat(
                        contentBeats,
                        timelineLeftPx,
                        beatWidthPx,
                        notationGaps,
                        includeGapAtBeat = true,
                    )''',
)
replace_once(
    STAFF,
    '''                        val x = StaffTimelineLayout.xAtBeat(rulerBeat, timelineLeftPx, beatWidthPx)''',
    '''                        val x = StaffNotationSpacing.xAtBeat(
                            rulerBeat,
                            timelineLeftPx,
                            beatWidthPx,
                            notationGaps,
                            includeGapAtBeat = false,
                        )''',
)
replace_once(
    STAFF,
    '''                        val x = StaffTimelineLayout.xAtBeat(
                            measureBeat,
                            timelineLeftPx,
                            beatWidthPx,
                        )''',
    '''                        val x = StaffNotationSpacing.xAtBeat(
                            measureBeat,
                            timelineLeftPx,
                            beatWidthPx,
                            notationGaps,
                            includeGapAtBeat = false,
                        )''',
)

# Pass gaps into notation changes and events.
replace_once(
    STAFF,
    '''                                geometry,
                            )''',
    '''                                geometry,
                                notationGaps,
                            )''',
)
replace_once(
    STAFF,
    '''                                geometry = geometry,
                                afterTimeSignature = sameBeatAsMeter,
                            )''',
    '''                                geometry = geometry,
                                afterTimeSignature = sameBeatAsMeter,
                                notationGaps = notationGaps,
                            )''',
)
replace_once(
    STAFF,
    '''                                ScoreKeySignatures.atBeat(normalizedKeySignatures, event.startBeat),
                                index == selectedEventIndex,
                            )''',
    '''                                ScoreKeySignatures.atBeat(normalizedKeySignatures, event.startBeat),
                                index == selectedEventIndex,
                                notationGaps,
                            )''',
)
replace_once(
    STAFF,
    '''                                geometry,
                                index == selectedEventIndex,
                            )''',
    '''                                geometry,
                                index == selectedEventIndex,
                                notationGaps,
                            )''',
)
replace_once(
    STAFF,
    '''                        drawTieCurve(event, target, timelineLeftPx, beatWidthPx, geometry, normalizedKeySignatures)''',
    '''                        drawTieCurve(
                            event,
                            target,
                            timelineLeftPx,
                            beatWidthPx,
                            geometry,
                            normalizedKeySignatures,
                            notationGaps,
                        )''',
)
replace_once(
    STAFF,
    '''                            drawLegatoCurve(source, target, timelineLeftPx, beatWidthPx, geometry, normalizedKeySignatures)''',
    '''                            drawLegatoCurve(
                                source,
                                target,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                                normalizedKeySignatures,
                                notationGaps,
                            )''',
)

# Cursor and playhead live after the notation gap at an exact change beat.
replace_once(
    STAFF,
    '''                    val entryCursorX = StaffTimelineLayout.xAtBeat(
                        cursorBeat.coerceIn(0f, contentBeats),
                        timelineLeftPx,
                        beatWidthPx,
                    )''',
    '''                    val entryCursorX = StaffNotationSpacing.xAtBeat(
                        cursorBeat.coerceIn(0f, contentBeats),
                        timelineLeftPx,
                        beatWidthPx,
                        notationGaps,
                        includeGapAtBeat = true,
                    )''',
)
replace_once(
    STAFF,
    '''                    val playheadX = StaffTimelineLayout.xAtBeat(
                        transport.beat.coerceIn(0f, contentBeats),
                        timelineLeftPx,
                        beatWidthPx,
                    )''',
    '''                    val playheadX = StaffNotationSpacing.xAtBeat(
                        transport.beat.coerceIn(0f, contentBeats),
                        timelineLeftPx,
                        beatWidthPx,
                        notationGaps,
                        includeGapAtBeat = true,
                    )''',
)

# Slightly tighten glyph spacing now that the staff also reserves real notation room.
replace_once(STAFF, '    val spacing = geometry.lineSpacing * 0.62f',
             '    val spacing = geometry.lineSpacing * 0.52f')
replace_once(STAFF, '            textSize = geometry.lineSpacing * 1.34f',
             '            textSize = geometry.lineSpacing * 1.22f')

# Key/time signature change functions draw immediately after the unshifted barline.
replace_once(
    STAFF,
    '''    geometry: StaffGeometry,
    afterTimeSignature: Boolean,
) {
    var x = StaffTimelineLayout.xAtBeat(signature.startBeat, timelineLeftPx, pixelsPerBeat) +''',
    '''    geometry: StaffGeometry,
    afterTimeSignature: Boolean,
    notationGaps: List<StaffNotationGap>,
) {
    var x = StaffNotationSpacing.xAtBeat(
        signature.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
        notationGaps,
        includeGapAtBeat = false,
    ) +''',
)
replace_once(
    STAFF,
    '''    pixelsPerBeat: Float,
    geometry: StaffGeometry,
) {
    val x = StaffTimelineLayout.xAtBeat(
        signature.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
    ) + geometry.lineSpacing * 0.58f''',
    '''    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    notationGaps: List<StaffNotationGap>,
) {
    val x = StaffNotationSpacing.xAtBeat(
        signature.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
        notationGaps,
        includeGapAtBeat = false,
    ) + geometry.lineSpacing * 0.58f''',
)

# Notes/rests are placed after an exact-beat notation change. The 0.10 beat note inset remains rhythmic.
replace_once(
    STAFF,
    '''    keySignature: ScoreKeySignature,
    selected: Boolean,
) {
    val x = StaffTimelineLayout.xAtBeat(
        note.startBeat + 0.10f,
        timelineLeftPx,
        pixelsPerBeat,
    )''',
    '''    keySignature: ScoreKeySignature,
    selected: Boolean,
    notationGaps: List<StaffNotationGap>,
) {
    val x = StaffNotationSpacing.xAtBeat(
        note.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
        notationGaps,
        includeGapAtBeat = true,
    ) + pixelsPerBeat * 0.10f''',
)
replace_once(
    STAFF,
    '''    geometry: StaffGeometry,
    selected: Boolean,
) {
    val x = StaffTimelineLayout.xAtBeat(
        rest.startBeat + 0.10f,
        timelineLeftPx,
        pixelsPerBeat,
    )''',
    '''    geometry: StaffGeometry,
    selected: Boolean,
    notationGaps: List<StaffNotationGap>,
) {
    val x = StaffNotationSpacing.xAtBeat(
        rest.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
        notationGaps,
        includeGapAtBeat = true,
    ) + pixelsPerBeat * 0.10f''',
)

# Ties/slurs follow visually shifted noteheads.
for function_name in ("drawLegatoCurve", "drawTieCurve"):
    signature_old = '''    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature>,
) {'''
    signature_new = '''    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature>,
    notationGaps: List<StaffNotationGap>,
) {'''
    replace_once(STAFF, signature_old, signature_new)
    source_old = '''    val sourceX = StaffTimelineLayout.xAtBeat(
        source.startBeat + 0.16f,
        timelineLeftPx,
        pixelsPerBeat,
    )
    val targetX = StaffTimelineLayout.xAtBeat(
        target.startBeat + 0.04f,
        timelineLeftPx,
        pixelsPerBeat,
    )'''
    source_new = '''    val sourceX = StaffNotationSpacing.xAtBeat(
        source.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
        notationGaps,
        includeGapAtBeat = true,
    ) + pixelsPerBeat * 0.16f
    val targetX = StaffNotationSpacing.xAtBeat(
        target.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
        notationGaps,
        includeGapAtBeat = true,
    ) + pixelsPerBeat * 0.04f'''
    replace_once(STAFF, source_old, source_new)

# Hit testing follows the shifted event positions.
replace_once(
    STAFF,
    '''    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),
): Int {''',
    '''    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),
    notationGaps: List<StaffNotationGap> = emptyList(),
): Int {''',
)
replace_once(
    STAFF,
    '''        val x = StaffTimelineLayout.xAtBeat(
            event.startBeat + 0.10f,
            timelineLeftPx,
            pixelsPerBeat,
        )''',
    '''        val x = StaffNotationSpacing.xAtBeat(
            event.startBeat,
            timelineLeftPx,
            pixelsPerBeat,
            notationGaps,
            includeGapAtBeat = true,
        ) + pixelsPerBeat * 0.10f''',
)

print("Applied staff notation spacing integration and bumped version to 0.2.19")
