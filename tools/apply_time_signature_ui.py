from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected one match, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Music-model editing helpers used by the UI.
path = "app/src/main/java/com/scoreforge/app/music/ScoreModels.kt"
anchor = "    /**\n     * Returns barline beats beginning with 0 and continuing through the first barline at or after\n"
helpers = """    fun measureStartAt(signatures: List<ScoreTimeSignature>, beat: Float): Float {
        val safeBeat = beat.coerceAtLeast(0f)
        val normalized = normalize(signatures)
        val active = atBeat(normalized, safeBeat)
        return measureBoundaries(
            normalized,
            safeBeat + active.beatsPerMeasure.coerceAtLeast(0.125f),
        ).lastOrNull { it <= safeBeat + EPSILON } ?: 0f
    }

    fun withChange(
        signatures: List<ScoreTimeSignature>,
        startBeat: Float,
        numerator: Int,
        denominator: Int,
    ): List<ScoreTimeSignature> {
        val safeStart = startBeat.coerceAtLeast(0f)
        val retained = normalize(signatures)
            .filterNot { abs(it.startBeat - safeStart) <= EPSILON }
        return normalize(
            retained + ScoreTimeSignature(safeStart, numerator, denominator).normalized()
        )
    }

    fun withoutChange(
        signatures: List<ScoreTimeSignature>,
        startBeat: Float,
    ): List<ScoreTimeSignature> {
        if (startBeat <= EPSILON) return normalize(signatures)
        return normalize(
            normalize(signatures).filterNot { abs(it.startBeat - startBeat) <= EPSILON }
        )
    }

"""
replace_once(path, anchor, helpers + anchor)

# Composer state, persistence, controls and editor wiring.
path = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    path,
    "import com.scoreforge.app.music.ScoreRest\nimport com.scoreforge.app.music.ScoreTies\n",
    "import com.scoreforge.app.music.ScoreRest\nimport com.scoreforge.app.music.ScoreTimeSignatures\nimport com.scoreforge.app.music.ScoreTies\n",
)
replace_once(
    path,
    "    var bpm by rememberSaveable { mutableIntStateOf(120) }\n    var isPlaying by remember { mutableStateOf(false) }\n",
    "    var bpm by rememberSaveable { mutableIntStateOf(120) }\n    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }\n    var isPlaying by remember { mutableStateOf(false) }\n",
)
replace_once(
    path,
    "            activeTrackIndex = index,\n            projectName = projectName,\n        )\n",
    "            activeTrackIndex = index,\n            projectName = projectName,\n            timeSignatures = timeSignatures,\n        )\n",
)
replace_once(
    path,
    "        staffSharpInput = snapshot.staffSharpInput\n        mixerGestureHistoryRecorded = false\n",
    "        staffSharpInput = snapshot.staffSharpInput\n        timeSignatures = snapshot.effectiveTimeSignatures()\n        mixerGestureHistoryRecorded = false\n",
)
replace_once(
    path,
    "        projectName,\n        bpm,\n        selectedDuration,\n",
    "        projectName,\n        bpm,\n        timeSignatures,\n        selectedDuration,\n",
)
replace_once(
    path,
    "                    measureCount = ScoreTimeline.measureCount(emptyList(), arrangementEndBeat),\n                    bpm = bpm,\n",
    "                    measureCount = ScoreTimeline.measureCount(\n                        emptyList(),\n                        arrangementEndBeat,\n                        timeSignatures = timeSignatures,\n                    ),\n                    timeSignatureLabel = ScoreTimeSignatures.atBeat(\n                        timeSignatures,\n                        activeCursorBeat,\n                    ).displayName,\n                    bpm = bpm,\n",
)
project_controls = """                ProjectFileControls(
                    projectName = projectName,
                    activeTrackName = activeTrack.name,
                    canClearTrack = activeEvents.isNotEmpty(),
                    snapshotProvider = ::currentProjectSnapshot,
                    onNewProject = ::newProject,
                    onClearTrack = ::clearActiveTrack,
                    onRenameProject = ::renameProject,
                    onOpenProject = ::openProject,
                )

                TrackControls(
"""
project_controls_new = """                ProjectFileControls(
                    projectName = projectName,
                    activeTrackName = activeTrack.name,
                    canClearTrack = activeEvents.isNotEmpty(),
                    snapshotProvider = ::currentProjectSnapshot,
                    onNewProject = ::newProject,
                    onClearTrack = ::clearActiveTrack,
                    onRenameProject = ::renameProject,
                    onOpenProject = ::openProject,
                )

                TimeSignatureControls(
                    timeSignatures = timeSignatures,
                    cursorBeat = activeCursorBeat,
                    onSetSignature = { startBeat, numerator, denominator ->
                        timeSignatures = ScoreTimeSignatures.withChange(
                            timeSignatures,
                            startBeat,
                            numerator,
                            denominator,
                        )
                    },
                    onRemoveSignature = { startBeat ->
                        timeSignatures = ScoreTimeSignatures.withoutChange(timeSignatures, startBeat)
                    },
                )

                TrackControls(
"""
replace_once(path, project_controls, project_controls_new)
replace_once(
    path,
    "                        cursorBeat = activeCursorBeat,\n                        selectedEventIndex = selectedEventIndex,\n",
    "                        cursorBeat = activeCursorBeat,\n                        timeSignatures = timeSignatures,\n                        selectedEventIndex = selectedEventIndex,\n",
)
replace_once(
    path,
    "                        cursorBeat = activeCursorBeat,\n                        octaveShift = pianoOctaveShift,\n",
    "                        cursorBeat = activeCursorBeat,\n                        timeSignatures = timeSignatures,\n                        octaveShift = pianoOctaveShift,\n",
)
replace_once(
    path,
    "    measureCount: Int,\n    bpm: Int,\n",
    "    measureCount: Int,\n    timeSignatureLabel: String,\n    bpm: Int,\n",
)
replace_once(
    path,
    '                "$projectName • $activeTrackName • $trackCount tracks • 4/4 • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",\n',
    '                "$projectName • $activeTrackName • $trackCount tracks • $timeSignatureLabel • $measureCount measures • beat ${formatBeat(cursorBeat)} • $noteCount notes • $restCount rests",\n',
)

# Staff: dynamic initial signature, meter changes and true barlines.
path = "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
replace_once(
    path,
    "import com.scoreforge.app.music.ScoreRest\nimport com.scoreforge.app.music.ScoreTies\n",
    "import com.scoreforge.app.music.ScoreRest\nimport com.scoreforge.app.music.ScoreTimeSignature\nimport com.scoreforge.app.music.ScoreTimeSignatures\nimport com.scoreforge.app.music.ScoreTies\n",
)
replace_once(
    path,
    "    cursorBeat: Float,\n    selectedEventIndex: Int,\n",
    "    cursorBeat: Float,\n    timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),\n    selectedEventIndex: Int,\n",
)
replace_once(
    path,
    """    val contentBeats = StaffTimelineLayout.contentBeats(
        eventsEndBeat = ScoreTimeline.endBeat(events),
        editCursorBeat = cursorBeat,
        playheadBeat = transport.beat,
    )
""",
    """    val contentBeats = StaffTimelineLayout.contentBeats(
        eventsEndBeat = ScoreTimeline.endBeat(events),
        editCursorBeat = cursorBeat,
        playheadBeat = transport.beat,
        timeSignatures = timeSignatures,
    )
""",
)
replace_once(
    path,
    "                    drawNotationHeader(geometry, timelineLeftPx)\n\n                    var rulerBeat = 0f\n",
    """                    val normalizedTimeSignatures = ScoreTimeSignatures.normalize(timeSignatures)
                    val measureBoundaries = ScoreTimeSignatures.measureBoundaries(
                        normalizedTimeSignatures,
                        contentBeats,
                    )
                    drawNotationHeader(
                        geometry,
                        timelineLeftPx,
                        ScoreTimeSignatures.atBeat(normalizedTimeSignatures, 0f),
                    )

                    var rulerBeat = 0f
""",
)
replace_once(
    path,
    """                        val isMeasure =
                            (rulerBeat % ScoreTimeline.BEATS_PER_MEASURE) < 0.001f
""",
    """                        val isMeasure = measureBoundaries.any {
                            kotlin.math.abs(it - rulerBeat) < 0.001f
                        }
""",
)
replace_once(
    path,
    """                    var measureBeat = 0f
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
""",
    """                    measureBoundaries.forEach { measureBeat ->
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
                    }

                    normalizedTimeSignatures.drop(1).forEach { signature ->
                        if (signature.startBeat <= contentBeats + 0.001f) {
                            drawTimeSignatureChange(
                                signature,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                            )
                        }
                    }

                    events.forEachIndexed { index, event ->
""",
)
replace_once(
    path,
    """private fun DrawScope.drawNotationHeader(
    geometry: StaffGeometry,
    timelineLeftPx: Float,
) {
""",
    """private fun DrawScope.drawNotationHeader(
    geometry: StaffGeometry,
    timelineLeftPx: Float,
    timeSignature: ScoreTimeSignature,
) {
""",
)
replace_once(
    path,
    '            "4",\n            signatureX,\n            geometry.staffTop + geometry.lineSpacing * 1.72f,\n',
    '            timeSignature.numerator.toString(),\n            signatureX,\n            geometry.staffTop + geometry.lineSpacing * 1.72f,\n',
)
replace_once(
    path,
    '            "4",\n            signatureX,\n            geometry.staffTop + geometry.lineSpacing * 3.70f,\n',
    '            timeSignature.denominator.toString(),\n            signatureX,\n            geometry.staffTop + geometry.lineSpacing * 3.70f,\n',
)
replace_once(
    path,
    "private fun DrawScope.drawScoreNote(\n",
    """private fun DrawScope.drawTimeSignatureChange(
    signature: ScoreTimeSignature,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
) {
    val x = StaffTimelineLayout.xAtBeat(
        signature.startBeat,
        timelineLeftPx,
        pixelsPerBeat,
    ) + geometry.lineSpacing * 0.38f
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(32, 32, 32)
            textAlign = Paint.Align.LEFT
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            textSize = geometry.lineSpacing * 0.78f
        }
        canvas.nativeCanvas.drawText(
            signature.displayName,
            x,
            geometry.staffTop - geometry.lineSpacing * 0.34f,
            paint,
        )
    }
}

private fun DrawScope.drawScoreNote(
""",
)

# Piano roll: meter-aware extent, barlines and change labels.
path = "app/src/main/java/com/scoreforge/app/ui/PianoRollEditor.kt"
replace_once(
    path,
    "import com.scoreforge.app.music.ScoreNote\nimport com.scoreforge.app.music.ScoreTies\n",
    "import com.scoreforge.app.music.ScoreNote\nimport com.scoreforge.app.music.ScoreTimeSignature\nimport com.scoreforge.app.music.ScoreTimeSignatures\nimport com.scoreforge.app.music.ScoreTies\n",
)
replace_once(
    path,
    "    cursorBeat: Float,\n    octaveShift: Int,\n",
    "    cursorBeat: Float,\n    timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),\n    octaveShift: Int,\n",
)
replace_once(
    path,
    """    val contentBeats = maxOf(
        PIANO_ROLL_MIN_BEATS,
        ScoreTimeline.visibleBeats(events, throughBeat = furthestBeat + ScoreTimeline.BEATS_PER_MEASURE),
    )
""",
    """    val activeMeter = ScoreTimeSignatures.atBeat(timeSignatures, furthestBeat)
    val contentBeats = maxOf(
        PIANO_ROLL_MIN_BEATS,
        ScoreTimeline.visibleBeats(
            events,
            throughBeat = furthestBeat + activeMeter.beatsPerMeasure,
            timeSignatures = timeSignatures,
        ),
    )
""",
)
replace_once(
    path,
    """                var gridBeat = 0f
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
""",
    """                var gridBeat = 0f
                while (gridBeat <= contentBeats + 0.001f) {
                    val x = PianoRollMapping.xAtBeat(gridBeat, contentBeats, size.width)
                    val isQuarter = gridBeat % 1f == 0f
                    drawLine(
                        color = if (isQuarter) Color(0xFFAEB4BA) else Color(0xFFD7DADF),
                        start = Offset(x, 0f),
                        end = Offset(x, size.height),
                        strokeWidth = if (isQuarter) 1.2f else 0.7f,
                    )
                    gridBeat += ScoreTimeline.EDIT_GRID_BEATS
                }

                val normalizedTimeSignatures = ScoreTimeSignatures.normalize(timeSignatures)
                ScoreTimeSignatures.measureBoundaries(
                    normalizedTimeSignatures,
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

                val meterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = android.graphics.Color.rgb(64, 64, 64)
                    textSize = 15f
                    isFakeBoldText = true
                }
                normalizedTimeSignatures.drop(1).forEach { signature ->
                    if (signature.startBeat <= contentBeats + 0.001f) {
                        val x = PianoRollMapping.xAtBeat(
                            signature.startBeat,
                            contentBeats,
                            size.width,
                        )
                        drawContext.canvas.nativeCanvas.drawText(
                            signature.displayName,
                            x + 4f,
                            17f,
                            meterPaint,
                        )
                    }
                }

                events.forEachIndexed { index, event ->
""",
)

# Version the APK used for the dedicated time-signature test.
path = "app/build.gradle.kts"
replace_once(
    path,
    '        versionCode = 18\n        versionName = "0.2.15"\n',
    '        versionCode = 19\n        versionName = "0.2.16"\n',
)

# Focused pure-JVM regression coverage.
Path("app/src/test/java/com/scoreforge/app/music/TimeSignatureEditingTest.kt").write_text(
    """package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TimeSignatureEditingTest {
    @Test
    fun measureStartFollowsThreeFourBarlines() {
        val signatures = listOf(ScoreTimeSignature(0f, 3, 4))
        assertEquals(0f, ScoreTimeSignatures.measureStartAt(signatures, 2.99f), 0.0001f)
        assertEquals(3f, ScoreTimeSignatures.measureStartAt(signatures, 3f), 0.0001f)
        assertEquals(6f, ScoreTimeSignatures.measureStartAt(signatures, 8.5f), 0.0001f)
    }

    @Test
    fun editingAtMeasureStartAddsAndReplacesChange() {
        var signatures = listOf(ScoreTimeSignature(0f, 4, 4))
        signatures = ScoreTimeSignatures.withChange(signatures, 8f, 6, 8)
        assertEquals("6/8", ScoreTimeSignatures.atBeat(signatures, 8f).displayName)
        signatures = ScoreTimeSignatures.withChange(signatures, 8f, 5, 8)
        assertEquals(2, signatures.size)
        assertEquals("5/8", ScoreTimeSignatures.atBeat(signatures, 9f).displayName)
    }

    @Test
    fun removingChangeRestoresPreviousMeterButCannotRemoveInitialMeter() {
        val withChange = ScoreTimeSignatures.withChange(
            listOf(ScoreTimeSignature(0f, 3, 4)),
            6f,
            7,
            8,
        )
        val removed = ScoreTimeSignatures.withoutChange(withChange, 6f)
        assertEquals(1, removed.size)
        assertEquals("3/4", ScoreTimeSignatures.atBeat(removed, 20f).displayName)
        val attemptedInitialRemoval = ScoreTimeSignatures.withoutChange(removed, 0f)
        assertFalse(attemptedInitialRemoval.isEmpty())
        assertTrue(attemptedInitialRemoval.first().startBeat == 0f)
    }
}
"""
)

Path("app/src/test/java/com/scoreforge/app/ui/StaffTimelineMeterTest.kt").write_text(
    """package com.scoreforge.app.ui

import com.scoreforge.app.music.ScoreTimeSignature
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffTimelineMeterTest {
    @Test
    fun threeFourTimelineLeavesAFullWorkingMeasure() {
        val content = StaffTimelineLayout.contentBeats(
            eventsEndBeat = 7f,
            editCursorBeat = 7f,
            playheadBeat = 0f,
            timeSignatures = listOf(ScoreTimeSignature(0f, 3, 4)),
        )
        assertTrue(content >= 10f)
    }

    @Test
    fun defaultFourFourBehaviorStillKeepsSixteenBeatViewport() {
        assertEquals(
            16f,
            StaffTimelineLayout.contentBeats(0f, 0f, 0f),
            0.0001f,
        )
    }
}
"""
)
