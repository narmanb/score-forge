from pathlib import Path

p = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
s = p.read_text()

block = '''    fun startPlayback() {
        if (playableNoteCount <= 0 || liveRecordingActive || isPlaying) return
        when (pianoEntryMode) {
            PianoEntryMode.NATURAL -> finishNaturalPhraseForStaffBrowse()
            PianoEntryMode.HOLD -> finishHoldGroupForUiBreak()
            else -> Unit
        }
        LiveInstrumentBus.allNotesOff()
        isPlaying = true
        playback.playTracks(
            tracks = tracks,
            bpm = bpm,
            throughBeat = ScoreTracks.endBeat(tracks),
            metronomeEnabled = metronomeEnabled,
            timeSignatures = timeSignatures,
        ) { isPlaying = false }
    }

'''
if s.count(block) != 1:
    raise SystemExit(f"expected one startPlayback block, found {s.count(block)}")
s = s.replace(block, "", 1)

anchor = '''    fun finishNaturalPhraseForStaffBrowse() {
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

'''
if s.count(anchor) != 1:
    raise SystemExit(f"expected one natural finalize anchor, found {s.count(anchor)}")
s = s.replace(anchor, anchor + block, 1)
p.write_text(s)
