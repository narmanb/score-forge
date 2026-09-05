from pathlib import Path

composer = Path('app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt')
s = composer.read_text()
old = '''    fun stopLiveRecording() {
        val startedAt = liveRecordingStartedAtMs ?: run {
            liveHeldInputs.clear()
            LiveInstrumentBus.allNotesOff()
            ScoreTransportBus.stop()
            return
        }'''
new = '''    fun stopLiveRecording() {
        val startedAt = liveRecordingStartedAtMs ?: run {
            liveHeldInputs.clear()
            LiveInstrumentBus.allNotesOff()
            // A normal score may now legitimately own the shared transport in the background.
            // Only repair/stop it here when score playback is not the owner.
            if (!isPlaying) ScoreTransportBus.stop()
            return
        }'''
assert old in s, 'stopLiveRecording anchor not found'
s = s.replace(old, new, 1)

old = '''    fun applyProjectSnapshot(snapshot: ScoreProjectSnapshot, clearHistory: Boolean) {
        cancelNaturalEntryGroup()
        cancelLiveRecording()
        val restoredTracks = snapshot.effectiveTracks()'''
new = '''    fun applyProjectSnapshot(snapshot: ScoreProjectSnapshot, clearHistory: Boolean) {
        cancelNaturalEntryGroup()
        // Draft restoration also runs after Activity recreation. Do not stop a legitimate score
        // that is owned by the foreground playback service merely because local Live state is empty.
        if (liveRecordingStartedAtMs != null) {
            cancelLiveRecording()
        } else {
            liveHeldInputs.clear()
            LiveInstrumentBus.allNotesOff()
        }
        val restoredTracks = snapshot.effectiveTracks()'''
assert old in s, 'applyProjectSnapshot anchor not found'
s = s.replace(old, new, 1)
composer.write_text(s)

service = Path('app/src/main/java/com/scoreforge/app/audio/ScorePlaybackService.kt')
s = service.read_text()
s = s.replace('''            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,\n            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK,\n            -> {''', '''            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,\n            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {''')
service.write_text(s)
