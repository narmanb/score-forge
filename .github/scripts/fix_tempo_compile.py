from pathlib import Path

p = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
s = p.read_text()
old = """    fun setTempoChange(startBeat: Float, newBpm: Int) {
        if (isPlaying) stopPlayback()
        tempoChanges = ScoreTempos.withChange(tempoChanges, startBeat, newBpm)
        bpm = tempoChanges.first().bpm
    }

    fun removeTempoChange(startBeat: Float) {
        if (isPlaying) stopPlayback()
        tempoChanges = ScoreTempos.withoutChange(tempoChanges, startBeat)
        bpm = tempoChanges.first().bpm
    }
"""
new = """    fun setTempoChange(startBeat: Float, newBpm: Int) {
        if (isPlaying) {
            playback.stop()
            isPlaying = false
        }
        tempoChanges = ScoreTempos.withChange(tempoChanges, startBeat, newBpm)
        bpm = tempoChanges.first().bpm
    }

    fun removeTempoChange(startBeat: Float) {
        if (isPlaying) {
            playback.stop()
            isPlaying = false
        }
        tempoChanges = ScoreTempos.withoutChange(tempoChanges, startBeat)
        bpm = tempoChanges.first().bpm
    }
"""
if old not in s:
    raise SystemExit("Composer tempo helper anchor not found")
p.write_text(s.replace(old, new, 1))

p = Path("app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt")
s = p.read_text()
old = """                        val playedFrames = track.playbackHeadPosition.toLong().coerceAtLeast(0L)
                        val beat = startBeat +
                            (playedFrames.toDouble() / sampleRate.toDouble() / secondsPerBeat.toDouble()).toFloat()
                        ScoreTransportBus.progress(beat.coerceAtMost(throughBeat))"""
new = """                        val playedFrames = track.playbackHeadPosition.toLong().coerceAtLeast(0L)
                        val beat = ScoreTempos.beatAtSeconds(
                            safeTempos,
                            startSeconds + playedFrames.toDouble() / sampleRate.toDouble(),
                        )
                        ScoreTransportBus.progress(beat.coerceAtMost(throughBeat))"""
if old not in s:
    raise SystemExit("Streaming drain progress anchor not found")
p.write_text(s.replace(old, new, 1))
