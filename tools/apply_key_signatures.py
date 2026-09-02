from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    p = ROOT / path
    text = p.read_text()
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"Expected exactly one match in {path}, found {count}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Version bump.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 20\n        versionName = "0.2.17"',
    '        versionCode = 21\n        versionName = "0.2.18"',
)

# Project persistence.
path = "app/src/main/java/com/scoreforge/app/music/ScoreProjectStorage.kt"
replace_once(
    path,
    '    val timeSignatures: List<ScoreTimeSignature> =\n        tracks.firstOrNull()?.timeSignatures ?: listOf(ScoreTimeSignatures.DEFAULT),\n    val metronomeEnabled: Boolean = false,',
    '    val timeSignatures: List<ScoreTimeSignature> =\n        tracks.firstOrNull()?.timeSignatures ?: listOf(ScoreTimeSignatures.DEFAULT),\n    val keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),\n    val metronomeEnabled: Boolean = false,',
)
replace_once(
    path,
    '    fun effectiveTimeSignatures(): List<ScoreTimeSignature> =\n        ScoreTimeSignatures.normalize(timeSignatures)\n\n    fun safeProjectName(): String = cleanProjectName(projectName)',
    '    fun effectiveTimeSignatures(): List<ScoreTimeSignature> =\n        ScoreTimeSignatures.normalize(timeSignatures)\n\n    fun effectiveKeySignatures(): List<ScoreKeySignature> =\n        ScoreKeySignatures.normalize(keySignatures)\n\n    fun safeProjectName(): String = cleanProjectName(projectName)',
)
replace_once(
    path,
    '        snapshot.effectiveTimeSignatures().forEach { signature ->\n            append("TIME_SIGNATURE\\t")\n                .append(signature.startBeat).append(\'\\t\')\n                .append(signature.numerator).append(\'\\t\')\n                .append(signature.denominator).append(\'\\n\')\n        }\n        append("DURATION\\t")',
    '        snapshot.effectiveTimeSignatures().forEach { signature ->\n            append("TIME_SIGNATURE\\t")\n                .append(signature.startBeat).append(\'\\t\')\n                .append(signature.numerator).append(\'\\t\')\n                .append(signature.denominator).append(\'\\n\')\n        }\n        snapshot.effectiveKeySignatures().forEach { signature ->\n            append("KEY_SIGNATURE\\t")\n                .append(signature.startBeat).append(\'\\t\')\n                .append(signature.fifths).append(\'\\t\')\n                .append(if (signature.minor) 1 else 0).append(\'\\n\')\n        }\n        append("DURATION\\t")',
)
replace_once(
    path,
    '        val timeSignatures = mutableListOf<ScoreTimeSignature>()\n        val tracks = mutableListOf<ScoreTrack>()',
    '        val timeSignatures = mutableListOf<ScoreTimeSignature>()\n        val keySignatures = mutableListOf<ScoreKeySignature>()\n        val tracks = mutableListOf<ScoreTrack>()',
)
replace_once(
    path,
    '                "TIME_SIGNATURE" -> decodeTimeSignature(parts)?.let(timeSignatures::add)\n                "DURATION" ->',
    '                "TIME_SIGNATURE" -> decodeTimeSignature(parts)?.let(timeSignatures::add)\n                "KEY_SIGNATURE" -> decodeKeySignature(parts)?.let(keySignatures::add)\n                "DURATION" ->',
)
replace_once(
    path,
    '            timeSignatures = ScoreTimeSignatures.normalize(timeSignatures),\n            metronomeEnabled = metronomeEnabled,',
    '            timeSignatures = ScoreTimeSignatures.normalize(timeSignatures),\n            keySignatures = ScoreKeySignatures.normalize(keySignatures),\n            metronomeEnabled = metronomeEnabled,',
)
replace_once(
    path,
    '    private fun decodeNote(parts: List<String>): ScoreNote? {',
    '''    private fun decodeKeySignature(parts: List<String>): ScoreKeySignature? {
        if (parts.size < 4) return null
        val startBeat = parts[1].toFloatOrNull()?.takeIf { it >= 0f } ?: return null
        val fifths = parts[2].toIntOrNull()?.takeIf { it in -7..7 } ?: return null
        val minor = when (parts[3]) {
            "0" -> false
            "1" -> true
            else -> return null
        }
        return ScoreKeySignature(startBeat, fifths, minor).normalized()
    }

    private fun decodeNote(parts: List<String>): ScoreNote? {''',
)

# MIDI import: preserve FF 59 key-signature events project-wide.
path = "app/src/main/java/com/scoreforge/app/music/MidiImport.kt"
replace_once(
    path,
    ' * start, written duration, velocity, track/channel grouping, tempo, time signatures, program,',
    ' * start, written duration, velocity, track/channel grouping, tempo, time signatures, key signatures, program,',
)
replace_once(
    path,
    '''    private data class TimeSignatureEvent(
        val tick: Long,
        val numerator: Int,
        val denominator: Int,
    )

    private data class ParsedMidi(''',
    '''    private data class TimeSignatureEvent(
        val tick: Long,
        val numerator: Int,
        val denominator: Int,
    )

    private data class KeySignatureEvent(
        val tick: Long,
        val fifths: Int,
        val minor: Boolean,
    )

    private data class ParsedMidi(''',
)
replace_once(
    path,
    '        val timeSignatureEvents: List<TimeSignatureEvent>,\n        val sourceTrackNames:',
    '        val timeSignatureEvents: List<TimeSignatureEvent>,\n        val keySignatureEvents: List<KeySignatureEvent>,\n        val sourceTrackNames:',
)
replace_once(
    path,
    '''        val timeSignatures = resolveTimeSignatures(
            events = parsed.timeSignatureEvents,
            ticksPerQuarter = parsed.ticksPerQuarter,
            warnings = warnings,
        )
        val grouped = parsed.notes.groupBy''',
    '''        val timeSignatures = resolveTimeSignatures(
            events = parsed.timeSignatureEvents,
            ticksPerQuarter = parsed.ticksPerQuarter,
            warnings = warnings,
        )
        val keySignatures = resolveKeySignatures(
            events = parsed.keySignatureEvents,
            ticksPerQuarter = parsed.ticksPerQuarter,
            warnings = warnings,
        )
        val grouped = parsed.notes.groupBy''',
)
replace_once(
    path,
    '            timeSignatures = timeSignatures,\n        )',
    '            timeSignatures = timeSignatures,\n            keySignatures = keySignatures,\n        )',
)
replace_once(
    path,
    '    private fun formatBeat(beat: Float): String {',
    '''    private fun resolveKeySignatures(
        events: List<KeySignatureEvent>,
        ticksPerQuarter: Int,
        warnings: MutableList<String>,
    ): List<ScoreKeySignature> {
        if (events.isEmpty()) return listOf(ScoreKeySignatures.DEFAULT)

        val resolved = mutableListOf<ScoreKeySignature>()
        events.sortedBy { it.tick }
            .groupBy { it.tick }
            .forEach { (tick, atTick) ->
                val distinct = atTick.map { it.fifths to it.minor }.distinct()
                val chosen = atTick.last()
                if (distinct.size > 1) {
                    val beat = tick.toFloat() / ticksPerQuarter.toFloat()
                    warnings += "Conflicting MIDI key signatures at beat ${formatBeat(beat)}; ${ScoreKeySignature(0f, chosen.fifths, chosen.minor).displayName} was used."
                }
                resolved += ScoreKeySignature(
                    startBeat = tick.toFloat() / ticksPerQuarter.toFloat(),
                    fifths = chosen.fifths,
                    minor = chosen.minor,
                )
            }

        return ScoreKeySignatures.normalize(resolved)
    }

    private fun formatBeat(beat: Float): String {''',
)
replace_once(
    path,
    '        val timeSignatureEvents = mutableListOf<TimeSignatureEvent>()\n        val sourceTrackNames',
    '        val timeSignatureEvents = mutableListOf<TimeSignatureEvent>()\n        val keySignatureEvents = mutableListOf<KeySignatureEvent>()\n        val sourceTrackNames',
)
replace_once(
    path,
    '                timeSignatureEvents = timeSignatureEvents,\n                sourceTrackNames = sourceTrackNames,',
    '                timeSignatureEvents = timeSignatureEvents,\n                keySignatureEvents = keySignatureEvents,\n                sourceTrackNames = sourceTrackNames,',
)
replace_once(
    path,
    '            timeSignatureEvents = timeSignatureEvents,\n            sourceTrackNames = sourceTrackNames,',
    '            timeSignatureEvents = timeSignatureEvents,\n            keySignatureEvents = keySignatureEvents,\n            sourceTrackNames = sourceTrackNames,',
)
replace_once(
    path,
    '        timeSignatureEvents: MutableList<TimeSignatureEvent>,\n        sourceTrackNames:',
    '        timeSignatureEvents: MutableList<TimeSignatureEvent>,\n        keySignatureEvents: MutableList<KeySignatureEvent>,\n        sourceTrackNames:',
)
replace_once(
    path,
    '''                        0x58 -> if (payload.size >= 2) {
                            val numerator = payload[0].toInt() and 0xFF
                            val denominatorPower = payload[1].toInt() and 0xFF
                            if (numerator in 1..32 && denominatorPower in 0..7) {
                                timeSignatureEvents += TimeSignatureEvent(
                                    tick = tick,
                                    numerator = numerator,
                                    denominator = 1 shl denominatorPower,
                                )
                            }
                        }
                    }''',
    '''                        0x58 -> if (payload.size >= 2) {
                            val numerator = payload[0].toInt() and 0xFF
                            val denominatorPower = payload[1].toInt() and 0xFF
                            if (numerator in 1..32 && denominatorPower in 0..7) {
                                timeSignatureEvents += TimeSignatureEvent(
                                    tick = tick,
                                    numerator = numerator,
                                    denominator = 1 shl denominatorPower,
                                )
                            }
                        }
                        0x59 -> if (payload.size >= 2) {
                            val fifths = payload[0].toInt()
                            val mode = payload[1].toInt() and 0xFF
                            if (fifths in -7..7 && mode in 0..1) {
                                keySignatureEvents += KeySignatureEvent(
                                    tick = tick,
                                    fifths = fifths,
                                    minor = mode == 1,
                                )
                            }
                        }
                    }''',
)

# Composer state, editing UI, and staff wiring.
path = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    path,
    'import com.scoreforge.app.music.ScoreEditState\nimport com.scoreforge.app.music.ScoreNote',
    'import com.scoreforge.app.music.ScoreEditState\nimport com.scoreforge.app.music.ScoreKeySignatures\nimport com.scoreforge.app.music.ScoreNote',
)
replace_once(
    path,
    '    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }\n    var metronomeEnabled',
    '    var timeSignatures by remember { mutableStateOf(listOf(ScoreTimeSignatures.DEFAULT)) }\n    var keySignatures by remember { mutableStateOf(listOf(ScoreKeySignatures.DEFAULT)) }\n    var metronomeEnabled',
)
replace_once(
    path,
    '            timeSignatures = timeSignatures,\n            metronomeEnabled = metronomeEnabled,',
    '            timeSignatures = timeSignatures,\n            keySignatures = keySignatures,\n            metronomeEnabled = metronomeEnabled,',
)
replace_once(
    path,
    '        timeSignatures = snapshot.effectiveTimeSignatures()\n        metronomeEnabled',
    '        timeSignatures = snapshot.effectiveTimeSignatures()\n        keySignatures = snapshot.effectiveKeySignatures()\n        metronomeEnabled',
)
replace_once(
    path,
    '        timeSignatures,\n        metronomeEnabled,',
    '        timeSignatures,\n        keySignatures,\n        metronomeEnabled,',
)
replace_once(
    path,
    '''                    timeSignatureLabel = ScoreTimeSignatures.atBeat(
                        timeSignatures,
                        activeCursorBeat,
                    ).displayName,
                    bpm = bpm,''',
    '''                    timeSignatureLabel = ScoreTimeSignatures.atBeat(
                        timeSignatures,
                        activeCursorBeat,
                    ).displayName,
                    keySignatureLabel = ScoreKeySignatures.atBeat(
                        keySignatures,
                        activeCursorBeat,
                    ).displayName,
                    bpm = bpm,''',
)
replace_once(
    path,
    '''                TimeSignatureControls(
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

                TrackControls(''',
    '''                TimeSignatureControls(
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

                KeySignatureControls(
                    keySignatures = keySignatures,
                    timeSignatures = timeSignatures,
                    cursorBeat = activeCursorBeat,
                    onSetSignature = { startBeat, fifths, minor ->
                        keySignatures = ScoreKeySignatures.withChange(
                            keySignatures,
                            startBeat,
                            fifths,
                            minor,
                        )
                    },
                    onRemoveSignature = { startBeat ->
                        keySignatures = ScoreKeySignatures.withoutChange(keySignatures, startBeat)
                    },
                )

                TrackControls(''',
)
replace_once(
    path,
    '                        timeSignatures = timeSignatures,\n                        selectedEventIndex = selectedEventIndex,',
    '                        timeSignatures = timeSignatures,\n                        keySignatures = keySignatures,\n                        selectedEventIndex = selectedEventIndex,',
)
replace_once(
    path,
    '    timeSignatureLabel: String,\n    bpm: Int,',
    '    timeSignatureLabel: String,\n    keySignatureLabel: String,\n    bpm: Int,',
)
replace_once(
    path,
    '                "$projectName • $activeTrackName • $trackCount tracks • $timeSignatureLabel • $measureCount measures',
    '                "$projectName • $activeTrackName • $trackCount tracks • $timeSignatureLabel • $keySignatureLabel • $measureCount measures',
)

# Staff rendering and key-aware enharmonic spelling.
path = "app/src/main/java/com/scoreforge/app/ui/ScoreStaffEditor.kt"
replace_once(
    path,
    'import com.scoreforge.app.music.ScoreEvent\nimport com.scoreforge.app.music.ScoreNote',
    'import com.scoreforge.app.music.ScoreAccidental\nimport com.scoreforge.app.music.ScoreEvent\nimport com.scoreforge.app.music.ScoreKeySignature\nimport com.scoreforge.app.music.ScoreKeySignatures\nimport com.scoreforge.app.music.ScoreNote',
)
replace_once(
    path,
    'import com.scoreforge.app.music.ScoreRest\nimport com.scoreforge.app.music.ScoreTimeSignature',
    'import com.scoreforge.app.music.ScorePitchSpelling\nimport com.scoreforge.app.music.ScoreRest\nimport com.scoreforge.app.music.ScoreTimeSignature',
)
replace_once(path, 'private val NOTATION_HEADER_WIDTH = 82.dp', 'private val NOTATION_HEADER_WIDTH = 132.dp')
replace_once(
    path,
    '    timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),\n    selectedEventIndex:',
    '    timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),\n    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),\n    selectedEventIndex:',
)
# Make all staff geometry/hit-test calls key-aware.
text_path = ROOT / path
text = text_path.read_text()
text = text.replace('staffGeometry(events, size.height.toFloat())', 'staffGeometry(events, keySignatures, size.height.toFloat())')
text = text.replace('staffGeometry(events, size.height)', 'staffGeometry(events, keySignatures, size.height)')
text = text.replace('                                        geometry,\n                                    )', '                                        geometry,\n                                        keySignatures,\n                                    )')
text_path.write_text(text)
replace_once(
    path,
    '''                    val normalizedTimeSignatures = ScoreTimeSignatures.normalize(timeSignatures)
                    val measureBoundaries = ScoreTimeSignatures.measureBoundaries(''',
    '''                    val normalizedTimeSignatures = ScoreTimeSignatures.normalize(timeSignatures)
                    val normalizedKeySignatures = ScoreKeySignatures.normalize(keySignatures)
                    val measureBoundaries = ScoreTimeSignatures.measureBoundaries(''',
)
replace_once(
    path,
    '''                    drawNotationHeader(
                        geometry,
                        timelineLeftPx,
                        ScoreTimeSignatures.atBeat(normalizedTimeSignatures, 0f),
                    )''',
    '''                    drawNotationHeader(
                        geometry,
                        timelineLeftPx,
                        ScoreKeySignatures.atBeat(normalizedKeySignatures, 0f),
                        ScoreTimeSignatures.atBeat(normalizedTimeSignatures, 0f),
                    )''',
)
replace_once(
    path,
    '''                    normalizedTimeSignatures.drop(1).forEach { signature ->
                        if (signature.startBeat <= contentBeats + 0.001f) {
                            drawTimeSignatureChange(
                                signature,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                            )
                        }
                    }

                    events.forEachIndexed''',
    '''                    normalizedTimeSignatures.drop(1).forEach { signature ->
                        if (signature.startBeat <= contentBeats + 0.001f) {
                            drawTimeSignatureChange(
                                signature,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                            )
                        }
                    }

                    normalizedKeySignatures.drop(1).forEachIndexed { index, signature ->
                        if (signature.startBeat <= contentBeats + 0.001f) {
                            val previous = normalizedKeySignatures[index]
                            val sameBeatAsMeter = normalizedTimeSignatures.drop(1).any {
                                abs(it.startBeat - signature.startBeat) <= 0.001f
                            }
                            drawKeySignatureChange(
                                signature = signature,
                                previous = previous,
                                timelineLeftPx = timelineLeftPx,
                                pixelsPerBeat = beatWidthPx,
                                geometry = geometry,
                                afterTimeSignature = sameBeatAsMeter,
                            )
                        }
                    }

                    events.forEachIndexed''',
)
replace_once(
    path,
    '''                            is ScoreNote -> drawScoreNote(
                                event,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                                index == selectedEventIndex,
                            )''',
    '''                            is ScoreNote -> drawScoreNote(
                                event,
                                timelineLeftPx,
                                beatWidthPx,
                                geometry,
                                ScoreKeySignatures.atBeat(normalizedKeySignatures, event.startBeat),
                                index == selectedEventIndex,
                            )''',
)
replace_once(
    path,
    '                        drawTieCurve(event, target, timelineLeftPx, beatWidthPx, geometry)',
    '                        drawTieCurve(event, target, timelineLeftPx, beatWidthPx, geometry, normalizedKeySignatures)',
)
replace_once(
    path,
    '                            drawLegatoCurve(source, target, timelineLeftPx, beatWidthPx, geometry)',
    '                            drawLegatoCurve(source, target, timelineLeftPx, beatWidthPx, geometry, normalizedKeySignatures)',
)
replace_once(
    path,
    'private fun staffGeometry(events: List<ScoreEvent>, height: Float): StaffGeometry {',
    'private fun staffGeometry(\n    events: List<ScoreEvent>,\n    keySignatures: List<ScoreKeySignature>,\n    height: Float,\n): StaffGeometry {',
)
replace_once(
    path,
    '''    fun noteYAt(pitch: Int, candidateSpacing: Float): Float {
        val bottom = center + candidateSpacing * 2f
        val e4Diatonic = 4 * 7 + 2
        val steps = PitchNames.diatonicPosition(pitch) - e4Diatonic
        return bottom - steps * (candidateSpacing / 2f)
    }''',
    '''    fun noteYAt(note: ScoreNote, candidateSpacing: Float): Float {
        val bottom = center + candidateSpacing * 2f
        val e4Diatonic = 4 * 7 + 2
        val key = ScoreKeySignatures.atBeat(keySignatures, note.startBeat)
        val steps = ScorePitchSpelling.spell(note.midiPitch, key).diatonicPosition - e4Diatonic
        return bottom - steps * (candidateSpacing / 2f)
    }''',
)
replace_once(path, '            val y = noteYAt(it.midiPitch, spacing)', '            val y = noteYAt(it, spacing)')
replace_once(
    path,
    '''private fun DrawScope.drawNotationHeader(
    geometry: StaffGeometry,
    timelineLeftPx: Float,
    timeSignature: ScoreTimeSignature,
) {''',
    '''private fun DrawScope.drawNotationHeader(
    geometry: StaffGeometry,
    timelineLeftPx: Float,
    keySignature: ScoreKeySignature,
    timeSignature: ScoreTimeSignature,
) {''',
)
replace_once(path, '            timelineLeftPx * 0.34f,', '            timelineLeftPx * 0.16f,')
replace_once(
    path,
    '''        val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink''',
    '''        drawKeySignatureSymbols(
            signature = keySignature,
            startX = timelineLeftPx * 0.30f,
            geometry = geometry,
        )

        val signaturePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ink''',
)
replace_once(path, '        val signatureX = timelineLeftPx * 0.70f', '        val signatureX = timelineLeftPx * 0.86f')
# Insert key-change drawing helpers before time-signature change function.
replace_once(
    path,
    'private fun DrawScope.drawTimeSignatureChange(',
    '''private data class KeySymbolPosition(val letter: Int, val diatonicPosition: Int)

private val sharpKeyPositions = listOf(
    KeySymbolPosition(3, 5 * 7 + 3), // F5
    KeySymbolPosition(0, 5 * 7 + 0), // C5
    KeySymbolPosition(4, 5 * 7 + 4), // G5
    KeySymbolPosition(1, 5 * 7 + 1), // D5
    KeySymbolPosition(5, 4 * 7 + 5), // A4
    KeySymbolPosition(2, 5 * 7 + 2), // E5
    KeySymbolPosition(6, 4 * 7 + 6), // B4
)

private val flatKeyPositions = listOf(
    KeySymbolPosition(6, 4 * 7 + 6), // B4
    KeySymbolPosition(2, 5 * 7 + 2), // E5
    KeySymbolPosition(5, 4 * 7 + 5), // A4
    KeySymbolPosition(1, 5 * 7 + 1), // D5
    KeySymbolPosition(4, 4 * 7 + 4), // G4
    KeySymbolPosition(0, 5 * 7 + 0), // C5
    KeySymbolPosition(3, 4 * 7 + 3), // F4
)

private fun DrawScope.drawKeySignatureSymbols(
    signature: ScoreKeySignature,
    startX: Float,
    geometry: StaffGeometry,
    symbolOverride: String? = null,
    positionsOverride: List<KeySymbolPosition>? = null,
): Float {
    val safe = signature.normalized()
    val positions = positionsOverride ?: when {
        safe.fifths > 0 -> sharpKeyPositions.take(safe.fifths)
        safe.fifths < 0 -> flatKeyPositions.take(-safe.fifths)
        else -> emptyList()
    }
    if (positions.isEmpty()) return startX
    val symbol = symbolOverride ?: if (safe.fifths >= 0) "♯" else "♭"
    val spacing = geometry.lineSpacing * 0.62f
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(28, 28, 28)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = geometry.lineSpacing * 1.34f
        }
        positions.forEachIndexed { index, item ->
            canvas.nativeCanvas.drawText(
                symbol,
                startX + index * spacing,
                yForDiatonicPosition(item.diatonicPosition, geometry) + geometry.lineSpacing * 0.34f,
                paint,
            )
        }
    }
    return startX + positions.size * spacing
}

private fun DrawScope.drawKeySignatureChange(
    signature: ScoreKeySignature,
    previous: ScoreKeySignature,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    afterTimeSignature: Boolean,
) {
    var x = StaffTimelineLayout.xAtBeat(signature.startBeat, timelineLeftPx, pixelsPerBeat) +
        geometry.lineSpacing * if (afterTimeSignature) 2.35f else 0.58f

    val previousPositions = if (previous.fifths > 0) {
        sharpKeyPositions.take(previous.fifths)
    } else {
        flatKeyPositions.take(-previous.fifths)
    }
    val cancellations = previousPositions.filter { item ->
        val oldAlteration = ScoreKeySignatures.alterationForLetter(previous, item.letter)
        val newAlteration = ScoreKeySignatures.alterationForLetter(signature, item.letter)
        oldAlteration != 0 && newAlteration != oldAlteration
    }
    if (cancellations.isNotEmpty()) {
        x = drawKeySignatureSymbols(
            signature = previous,
            startX = x,
            geometry = geometry,
            symbolOverride = "♮",
            positionsOverride = cancellations,
        ) + geometry.lineSpacing * 0.18f
    }
    drawKeySignatureSymbols(signature, x, geometry)
}

private fun yForDiatonicPosition(position: Int, geometry: StaffGeometry): Float {
    val e4Diatonic = 4 * 7 + 2
    val steps = position - e4Diatonic
    return geometry.staffBottom - steps * (geometry.lineSpacing / 2f)
}

private fun DrawScope.drawTimeSignatureChange(''',
)
replace_once(
    path,
    '''private fun DrawScope.drawScoreNote(
    note: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    selected: Boolean,
) {''',
    '''private fun DrawScope.drawScoreNote(
    note: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    keySignature: ScoreKeySignature,
    selected: Boolean,
) {''',
)
replace_once(
    path,
    '    val y = noteY(note.midiPitch, geometry)\n    drawLedgerLines(x, y, geometry)',
    '    val spelling = ScorePitchSpelling.spell(note.midiPitch, keySignature)\n    val y = noteY(note.midiPitch, geometry, keySignature)\n    drawLedgerLines(x, y, geometry)',
)
replace_once(
    path,
    '''    if (PitchNames.hasSharp(note.midiPitch)) {
        drawSharpAccidental(
            x - geometry.lineSpacing * 0.62f,
            y,
            geometry.lineSpacing / 40f,
        )
    }
''',
    '''    when (spelling.accidental) {
        ScoreAccidental.SHARP -> drawSharpAccidental(
            x - geometry.lineSpacing * 0.62f,
            y,
            geometry.lineSpacing / 40f,
        )
        ScoreAccidental.FLAT -> drawTextAccidental("♭", x, y, geometry)
        ScoreAccidental.NATURAL -> drawTextAccidental("♮", x, y, geometry)
        ScoreAccidental.NONE -> Unit
    }
''',
)
# Add text accidental helper before articulation marks.
replace_once(
    path,
    'private fun DrawScope.drawArticulationMark(',
    '''private fun DrawScope.drawTextAccidental(
    symbol: String,
    noteX: Float,
    noteY: Float,
    geometry: StaffGeometry,
) {
    drawIntoCanvas { canvas ->
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.rgb(17, 17, 17)
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = geometry.lineSpacing * 1.20f
        }
        canvas.nativeCanvas.drawText(
            symbol,
            noteX - geometry.lineSpacing * 0.66f,
            noteY + geometry.lineSpacing * 0.34f,
            paint,
        )
    }
}

private fun DrawScope.drawArticulationMark(''',
)
replace_once(
    path,
    '''private fun DrawScope.drawLegatoCurve(
    source: ScoreNote,
    target: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
) {''',
    '''private fun DrawScope.drawLegatoCurve(
    source: ScoreNote,
    target: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature>,
) {''',
)
replace_once(
    path,
    '    val sourceY = noteY(source.midiPitch, geometry)\n    val targetY = noteY(target.midiPitch, geometry)',
    '    val sourceY = noteY(source.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, source.startBeat))\n    val targetY = noteY(target.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, target.startBeat))',
)
replace_once(
    path,
    '''private fun DrawScope.drawTieCurve(
    source: ScoreNote,
    target: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
) {''',
    '''private fun DrawScope.drawTieCurve(
    source: ScoreNote,
    target: ScoreNote,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature>,
) {''',
)
# The tie function has the same sourceY/targetY snippet a second time.
replace_once(
    path,
    '    val sourceY = noteY(source.midiPitch, geometry)\n    val targetY = noteY(target.midiPitch, geometry)',
    '    val sourceY = noteY(source.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, source.startBeat))\n    val targetY = noteY(target.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, target.startBeat))',
)
replace_once(
    path,
    '''private fun noteY(midiPitch: Int, geometry: StaffGeometry): Float {
    val e4Diatonic = 4 * 7 + 2
    val steps = PitchNames.diatonicPosition(midiPitch) - e4Diatonic
    return geometry.staffBottom - steps * (geometry.lineSpacing / 2f)
}''',
    '''private fun noteY(
    midiPitch: Int,
    geometry: StaffGeometry,
    keySignature: ScoreKeySignature = ScoreKeySignatures.DEFAULT,
): Float {
    return yForDiatonicPosition(
        ScorePitchSpelling.spell(midiPitch, keySignature).diatonicPosition,
        geometry,
    )
}''',
)
replace_once(
    path,
    '''private fun nearestEditableEventIndex(
    events: List<ScoreEvent>,
    point: Offset,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
): Int {''',
    '''private fun nearestEditableEventIndex(
    events: List<ScoreEvent>,
    point: Offset,
    timelineLeftPx: Float,
    pixelsPerBeat: Float,
    geometry: StaffGeometry,
    keySignatures: List<ScoreKeySignature> = listOf(ScoreKeySignatures.DEFAULT),
): Int {''',
)
replace_once(
    path,
    '            is ScoreNote -> noteY(event.midiPitch, geometry)',
    '            is ScoreNote -> noteY(event.midiPitch, geometry, ScoreKeySignatures.atBeat(keySignatures, event.startBeat))',
)

print("Applied Score Forge 0.2.18 key-signature integration")
