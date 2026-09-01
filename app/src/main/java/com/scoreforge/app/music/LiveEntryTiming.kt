package com.scoreforge.app.music

/** Timing helpers for real-time piano recording. */
object LiveEntryTiming {
    fun beatAtElapsedMs(startBeat: Float, elapsedMs: Long, bpm: Int): Float {
        val safeBpm = bpm.coerceIn(30, 300)
        val safeElapsedMs = elapsedMs.coerceAtLeast(0L)
        val beatsElapsed = safeElapsedMs.toDouble() / (60_000.0 / safeBpm.toDouble())
        return startBeat.coerceAtLeast(0f) + beatsElapsed.toFloat()
    }

    fun quantizedBeatAtElapsedMs(startBeat: Float, elapsedMs: Long, bpm: Int): Float =
        ScoreTimeline.quantizeBeat(beatAtElapsedMs(startBeat, elapsedMs, bpm))
}
