from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
COMPOSER = ROOT / "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected one match, found {count}")
    return text.replace(old, new, 1)


text = COMPOSER.read_text()

text = replace_once(
    text,
    '''    fun stopLiveRecording() {
        val startedAt = liveRecordingStartedAtMs ?: run {
            liveHeldInputs.clear()
            return
        }
''',
    '''    fun stopLiveRecording() {
        val startedAt = liveRecordingStartedAtMs ?: run {
            liveHeldInputs.clear()
            LiveInstrumentBus.allNotesOff()
            ScoreTransportBus.stop()
            return
        }
''',
    "stopLiveRecording empty-state cleanup",
)

text = replace_once(
    text,
    '''    fun cancelLiveRecording() {
        if (liveRecordingStartedAtMs == null && liveHeldInputs.isEmpty()) return
        liveHeldInputs.keys.toList().forEach { LiveInstrumentBus.noteOff(it) }
        liveHeldInputs.clear()
        LiveInstrumentBus.allNotesOff()
        ScoreTransportBus.stop()
        liveRecordingStartedAtMs = null
    }

    fun beginLivePitch(pitch: Int) {
''',
    '''    fun cancelLiveRecording() {
        // Always stop the shared transport, even if the local Live bookkeeping is already empty.
        // This repairs the impossible state seen on-device where the purple playhead kept moving
        // in Natural mode after the Live owner had disappeared.
        liveHeldInputs.keys.toList().forEach { LiveInstrumentBus.noteOff(it) }
        liveHeldInputs.clear()
        LiveInstrumentBus.allNotesOff()
        ScoreTransportBus.stop()
        liveRecordingStartedAtMs = null
    }

    fun repairUnexpectedTransportForEntry() {
        val decision = TransportRepairPolicy.decide(
            isLiveMode = pianoEntryMode == PianoEntryMode.LIVE,
            liveRecordingActive = liveRecordingStartedAtMs != null,
            scorePlaybackActive = isPlaying,
            transportPlaying = ScoreTransportBus.state.value.isPlaying,
        )
        if (decision.cancelLiveRecording) cancelLiveRecording()
        if (decision.stopTransport) playback.stop()
    }

    fun beginLivePitch(pitch: Int) {
''',
    "cancelLiveRecording + repair helper",
)

text = replace_once(
    text,
    '''    fun insertNoteAt(pitch: Int, startBeat: Float, preview: Boolean, advanceCursor: Boolean) {
        recordBeforeScoreEdit()
''',
    '''    fun insertNoteAt(pitch: Int, startBeat: Float, preview: Boolean, advanceCursor: Boolean) {
        repairUnexpectedTransportForEntry()
        recordBeforeScoreEdit()
''',
    "generic entry repair",
)

text = replace_once(
    text,
    '''    fun beginNaturalPitch(pitch: Int) {
        if (naturalHeldInputs.containsKey(pitch)) return
''',
    '''    fun beginNaturalPitch(pitch: Int) {
        repairUnexpectedTransportForEntry()
        if (naturalHeldInputs.containsKey(pitch)) return
''',
    "natural entry repair",
)

text = replace_once(
    text,
    '''    LaunchedEffect(activeTrack.id, activeTrack.volume, activeTrack.pan) {
        LiveInstrumentBus.setMixer(activeTrack.volume, activeTrack.pan)
    }
''',
    '''    LaunchedEffect(pianoEntryMode, liveRecordingStartedAtMs, isPlaying) {
        if (pianoEntryMode == PianoEntryMode.LIVE) return@LaunchedEffect
        val decision = TransportRepairPolicy.decide(
            isLiveMode = false,
            liveRecordingActive = liveRecordingStartedAtMs != null,
            scorePlaybackActive = isPlaying,
            transportPlaying = ScoreTransportBus.state.value.isPlaying,
        )
        if (decision.cancelLiveRecording) cancelLiveRecording()
        if (decision.stopTransport) playback.stop()
    }

    LaunchedEffect(activeTrack.id, activeTrack.volume, activeTrack.pan) {
        LiveInstrumentBus.setMixer(activeTrack.volume, activeTrack.pan)
    }
''',
    "non-live transport invariant",
)

COMPOSER.write_text(text)
