package com.scoreforge.app.audio

/** Selects bounded-memory streaming before static PCM rendering becomes expensive. */
internal object PlaybackStreamingPolicy {
    private const val DURATION_THRESHOLD_SECONDS = 45.0
    private const val NOTE_THRESHOLD = 1_500

    fun shouldStream(throughBeat: Float, bpm: Int, noteCount: Int): Boolean {
        val safeBpm = bpm.coerceIn(30, 300)
        val estimatedSeconds = throughBeat.coerceAtLeast(0f) * (60.0 / safeBpm.toDouble())
        return estimatedSeconds >= DURATION_THRESHOLD_SECONDS || noteCount >= NOTE_THRESHOLD
    }
}
