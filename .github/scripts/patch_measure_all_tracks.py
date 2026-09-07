from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


# Composer toolbar: add scope model, callback parameters, and compact scope toggle.
toolbar_path = Path("app/src/main/java/com/scoreforge/app/ui/ComposerTransformToolbar.kt")
toolbar = toolbar_path.read_text()

toolbar = replace_once(
    toolbar,
    """enum class ComposerToolbarSection {
    ROOT,
    TEMPO,
    TIME,
    KEY,
    CLEF,
    NOTES,
    EDITOR,
    MEASURE,
}
""",
    """enum class ComposerToolbarSection {
    ROOT,
    TEMPO,
    TIME,
    KEY,
    CLEF,
    NOTES,
    EDITOR,
    MEASURE,
}

enum class MeasureEditScope(val label: String) {
    CURRENT_TRACK("Current Track"),
    ALL_TRACKS("All Tracks"),
    ;

    fun next(): MeasureEditScope = when (this) {
        CURRENT_TRACK -> ALL_TRACKS
        ALL_TRACKS -> CURRENT_TRACK
    }
}
""",
    "toolbar scope enum",
)

toolbar = replace_once(
    toolbar,
    """    measurePasteEnabled: Boolean = false,
    onCopyMeasure: () -> Unit = {},
""",
    """    measurePasteEnabled: Boolean = false,
    measureEditScope: MeasureEditScope = MeasureEditScope.CURRENT_TRACK,
    onMeasureEditScopeChanged: (MeasureEditScope) -> Unit = {},
    onCopyMeasure: () -> Unit = {},
""",
    "toolbar scope params",
)

toolbar = replace_once(
    toolbar,
    """            ComposerToolbarSection.MEASURE -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                Text("Measure ${measureNumber.coerceAtLeast(1)}", style = MaterialTheme.typography.labelLarge)
                ComposerSubmenuButton(
                    label = "−",
""",
    """            ComposerToolbarSection.MEASURE -> {
                BackButton { section = ComposerToolbarSection.ROOT }
                Text("Measure ${measureNumber.coerceAtLeast(1)}", style = MaterialTheme.typography.labelLarge)
                ComposerSubmenuButton(
                    label = measureEditScope.label,
                    onClick = { onMeasureEditScopeChanged(measureEditScope.next()) },
                    selected = measureEditScope == MeasureEditScope.ALL_TRACKS,
                )
                ComposerSubmenuButton(
                    label = "−",
""",
    "toolbar measure scope button",
)

toolbar_path.write_text(toolbar)


# Composer screen: make clipboard scope-aware and route operations across one/all tracks.
screen_path = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
screen = screen_path.read_text()

screen = replace_once(
    screen,
    """import com.scoreforge.app.music.ScoreClefs
import com.scoreforge.app.music.ScoreEditHistory
""",
    """import com.scoreforge.app.music.ScoreAllTracksMeasureClipboard
import com.scoreforge.app.music.ScoreClefs
import com.scoreforge.app.music.ScoreEditHistory
""",
    "screen all-track clipboard import",
)

screen = replace_once(
    screen,
    """import com.scoreforge.app.music.ScoreMeasureClipboard
import com.scoreforge.app.music.ScoreMeasureEdits
""",
    """import com.scoreforge.app.music.ScoreMeasureClipboard
import com.scoreforge.app.music.ScoreMeasureEdits
import com.scoreforge.app.music.ScoreMeasureTrackEdits
""",
    "screen track edits import",
)

screen = replace_once(
    screen,
    """private data class LiveHeldInput(
    val eventIndex: Int,
    val startedAtMs: Long,
    val bpmAtPress: Int,
)

@Composable
""",
    """private data class LiveHeldInput(
    val eventIndex: Int,
    val startedAtMs: Long,
    val bpmAtPress: Int,
)

private sealed interface MeasureClipboardPayload {
    val scope: MeasureEditScope

    data class CurrentTrack(
        val clipboard: ScoreMeasureClipboard,
    ) : MeasureClipboardPayload {
        override val scope: MeasureEditScope = MeasureEditScope.CURRENT_TRACK
    }

    data class AllTracks(
        val clipboard: ScoreAllTracksMeasureClipboard,
    ) : MeasureClipboardPayload {
        override val scope: MeasureEditScope = MeasureEditScope.ALL_TRACKS
    }
}

@Composable
""",
    "screen clipboard payload",
)

screen = replace_once(
    screen,
    """    var mixerGestureHistoryRecorded by remember { mutableStateOf(false) }
    var measureClipboard by remember { mutableStateOf<ScoreMeasureClipboard?>(null) }
    var measurePasteMessage by remember { mutableStateOf<String?>(null) }
""",
    """    var mixerGestureHistoryRecorded by remember { mutableStateOf(false) }
    var measureEditScope by rememberSaveable { mutableStateOf(MeasureEditScope.CURRENT_TRACK) }
    var measureClipboard by remember { mutableStateOf<MeasureClipboardPayload?>(null) }
    var measurePasteMessage by remember { mutableStateOf<String?>(null) }
""",
    "screen clipboard state",
)

screen = replace_once(
    screen,
    """    fun copyActiveMeasure() {
        measurePasteMessage = null
        measureClipboard = ScoreMeasureEdits.copyMeasure(
            events = currentTrack().events,
            timeSignatures = timeSignatures,
            beat = currentTrack().cursorBeat,
        )
    }

    fun copyActiveMeasureRange(measureCount: Int) {
        measurePasteMessage = null
        measureClipboard = ScoreMeasureEdits.copyMeasures(
            events = currentTrack().events,
            timeSignatures = timeSignatures,
            beat = currentTrack().cursorBeat,
            measureCount = measureCount,
        )
    }
""",
    """    fun copyMeasureSelection(measureCount: Int) {
        measurePasteMessage = null
        val track = currentTrack()
        measureClipboard = when (measureEditScope) {
            MeasureEditScope.CURRENT_TRACK -> MeasureClipboardPayload.CurrentTrack(
                ScoreMeasureEdits.copyMeasures(
                    events = track.events,
                    timeSignatures = timeSignatures,
                    beat = track.cursorBeat,
                    measureCount = measureCount,
                )
            )
            MeasureEditScope.ALL_TRACKS -> MeasureClipboardPayload.AllTracks(
                ScoreMeasureTrackEdits.copyAllTracks(
                    tracks = tracks.toList(),
                    timeSignatures = timeSignatures,
                    beat = track.cursorBeat,
                    measureCount = measureCount,
                )
            )
        }
    }

    fun copyActiveMeasure() = copyMeasureSelection(1)

    fun copyActiveMeasureRange(measureCount: Int) = copyMeasureSelection(measureCount)
""",
    "screen copy functions",
)

screen = replace_once(
    screen,
    """    fun explainPasteProblem(clipboard: ScoreMeasureClipboard, destinationBeat: Float): Boolean {
        val problem = ScoreMeasureEdits.pasteProblemAt(
            timeSignatures = timeSignatures,
            destinationBeat = destinationBeat,
            clipboard = clipboard,
        ) ?: return false
        val message = "Can't paste: copied measure ${problem.sourceMeasureNumber} has an event at beat " +
            "${pasteBeatLabel(problem.onsetWithinMeasure)}; destination measure " +
            "${problem.sourceMeasureNumber} is only ${pasteBeatLabel(problem.destinationMeasureLength)} beats."
        measurePasteMessage = message
        return true
    }
""",
    """    fun explainPasteProblem(payload: MeasureClipboardPayload, destinationBeat: Float): Boolean {
        if (payload.scope != measureEditScope) {
            measurePasteMessage = "Clipboard was copied in ${payload.scope.label} mode. " +
                "Switch back to ${payload.scope.label} or copy again."
            return true
        }

        val message = when (payload) {
            is MeasureClipboardPayload.CurrentTrack -> {
                val problem = ScoreMeasureEdits.pasteProblemAt(
                    timeSignatures = timeSignatures,
                    destinationBeat = destinationBeat,
                    clipboard = payload.clipboard,
                ) ?: return false
                "Can't paste: copied measure ${problem.sourceMeasureNumber} has an event at beat " +
                    "${pasteBeatLabel(problem.onsetWithinMeasure)}; destination measure " +
                    "${problem.sourceMeasureNumber} is only ${pasteBeatLabel(problem.destinationMeasureLength)} beats."
            }
            is MeasureClipboardPayload.AllTracks -> {
                val allTracksProblem = ScoreMeasureTrackEdits.pasteProblemAt(
                    timeSignatures = timeSignatures,
                    destinationBeat = destinationBeat,
                    clipboard = payload.clipboard,
                ) ?: return false
                val problem = allTracksProblem.problem
                val trackName = tracks.firstOrNull { it.id == allTracksProblem.trackId }?.name
                    ?: "Track ${allTracksProblem.trackId}"
                "Can't paste all tracks: $trackName, copied measure ${problem.sourceMeasureNumber} " +
                    "has an event at beat ${pasteBeatLabel(problem.onsetWithinMeasure)}; destination measure " +
                    "${problem.sourceMeasureNumber} is only ${pasteBeatLabel(problem.destinationMeasureLength)} beats."
            }
        }
        measurePasteMessage = message
        return true
    }
""",
    "screen paste explanation",
)

screen = replace_once(
    screen,
    """    fun pasteActiveMeasure() {
        val clipboard = measureClipboard ?: return
        val track = currentTrack()
        if (explainPasteProblem(clipboard, track.cursorBeat)) return
        measurePasteMessage = null
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        val updatedEvents = ScoreMeasureEdits.pasteReplace(
            events = track.events,
            timeSignatures = timeSignatures,
            destinationBeat = track.cursorBeat,
            clipboard = clipboard,
        )
        replaceActiveTrack { it.copy(events = updatedEvents) }
        selectedEventIndex = -1
        syncHistoryButtons()
    }
""",
    """    fun pasteActiveMeasure() {
        val payload = measureClipboard ?: return
        val track = currentTrack()
        if (explainPasteProblem(payload, track.cursorBeat)) return
        measurePasteMessage = null
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        when (payload) {
            is MeasureClipboardPayload.CurrentTrack -> {
                val updatedEvents = ScoreMeasureEdits.pasteReplace(
                    events = track.events,
                    timeSignatures = timeSignatures,
                    destinationBeat = track.cursorBeat,
                    clipboard = payload.clipboard,
                )
                replaceActiveTrack { it.copy(events = updatedEvents) }
            }
            is MeasureClipboardPayload.AllTracks -> {
                val updatedTracks = ScoreMeasureTrackEdits.pasteReplaceAllTracks(
                    tracks = tracks.toList(),
                    timeSignatures = timeSignatures,
                    destinationBeat = track.cursorBeat,
                    clipboard = payload.clipboard,
                )
                updatedTracks.forEachIndexed { index, updated -> replaceTrack(index, updated) }
            }
        }
        selectedEventIndex = -1
        syncHistoryButtons()
    }
""",
    "screen replace paste",
)

screen = replace_once(
    screen,
    """    fun insertActiveMeasure() {
        val clipboard = measureClipboard ?: return
        val track = currentTrack()
        if (explainPasteProblem(clipboard, track.cursorBeat)) return
        measurePasteMessage = null
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        val updatedEvents = ScoreMeasureEdits.pasteInsert(
            events = track.events,
            timeSignatures = timeSignatures,
            destinationBeat = track.cursorBeat,
            clipboard = clipboard,
        )
        replaceActiveTrack { it.copy(events = updatedEvents) }
        selectedEventIndex = -1
        syncHistoryButtons()
    }
""",
    """    fun insertActiveMeasure() {
        val payload = measureClipboard ?: return
        val track = currentTrack()
        if (explainPasteProblem(payload, track.cursorBeat)) return
        measurePasteMessage = null
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        recordBeforeScoreEdit()
        when (payload) {
            is MeasureClipboardPayload.CurrentTrack -> {
                val updatedEvents = ScoreMeasureEdits.pasteInsert(
                    events = track.events,
                    timeSignatures = timeSignatures,
                    destinationBeat = track.cursorBeat,
                    clipboard = payload.clipboard,
                )
                replaceActiveTrack { it.copy(events = updatedEvents) }
            }
            is MeasureClipboardPayload.AllTracks -> {
                val updatedTracks = ScoreMeasureTrackEdits.pasteInsertAllTracks(
                    tracks = tracks.toList(),
                    timeSignatures = timeSignatures,
                    destinationBeat = track.cursorBeat,
                    clipboard = payload.clipboard,
                )
                updatedTracks.forEachIndexed { index, updated -> replaceTrack(index, updated) }
            }
        }
        selectedEventIndex = -1
        syncHistoryButtons()
    }
""",
    "screen insert paste",
)

screen = replace_once(
    screen,
    """    fun duplicateActiveMeasure(copies: Int) {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        val track = currentTrack()
        recordBeforeScoreEdit()
        val updatedEvents = ScoreMeasureEdits.duplicateMeasure(
            events = track.events,
            timeSignatures = timeSignatures,
            beat = track.cursorBeat,
            copies = copies,
        )
        val newCursorBeat = ScoreMeasureEdits.duplicateCursorBeat(
            timeSignatures = timeSignatures,
            beat = track.cursorBeat,
            copies = copies,
        )
        replaceActiveTrack {
            it.copy(
                events = updatedEvents,
                cursorBeat = newCursorBeat,
            )
        }
        selectedEventIndex = -1
        syncHistoryButtons()
    }
""",
    """    fun duplicateActiveMeasure(copies: Int) {
        stopPlayback()
        stopLiveRecording()
        cancelNaturalEntryGroup()
        LiveInstrumentBus.allNotesOff()
        val track = currentTrack()
        recordBeforeScoreEdit()
        val newCursorBeat = ScoreMeasureEdits.duplicateCursorBeat(
            timeSignatures = timeSignatures,
            beat = track.cursorBeat,
            copies = copies,
        )
        when (measureEditScope) {
            MeasureEditScope.CURRENT_TRACK -> {
                val updatedEvents = ScoreMeasureEdits.duplicateMeasure(
                    events = track.events,
                    timeSignatures = timeSignatures,
                    beat = track.cursorBeat,
                    copies = copies,
                )
                replaceActiveTrack {
                    it.copy(
                        events = updatedEvents,
                        cursorBeat = newCursorBeat,
                    )
                }
            }
            MeasureEditScope.ALL_TRACKS -> {
                val updatedTracks = ScoreMeasureTrackEdits.duplicateMeasureAllTracks(
                    tracks = tracks.toList(),
                    timeSignatures = timeSignatures,
                    beat = track.cursorBeat,
                    copies = copies,
                )
                updatedTracks.forEachIndexed { index, updated -> replaceTrack(index, updated) }
                replaceActiveTrack { it.copy(cursorBeat = newCursorBeat) }
            }
        }
        selectedEventIndex = -1
        syncHistoryButtons()
    }
""",
    "screen duplicate",
)

screen = replace_once(
    screen,
    """                    measurePasteEnabled = measureClipboard != null,
                    onCopyMeasure = ::copyActiveMeasure,
""",
    """                    measurePasteEnabled = measureClipboard != null,
                    measureEditScope = measureEditScope,
                    onMeasureEditScopeChanged = { scope ->
                        measureEditScope = scope
                        measurePasteMessage = null
                    },
                    onCopyMeasure = ::copyActiveMeasure,
""",
    "screen toolbar scope wiring",
)

screen_path.write_text(screen)


# Version bump for the phone-test APK.
build_path = Path("app/build.gradle.kts")
build = build_path.read_text()
build = replace_once(build, '        versionCode = 58\n        versionName = "0.2.55"\n', '        versionCode = 59\n        versionName = "0.2.56"\n', "version bump")
build_path.write_text(build)
