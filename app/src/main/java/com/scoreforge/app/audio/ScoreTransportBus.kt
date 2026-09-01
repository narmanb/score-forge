package com.scoreforge.app.audio

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ScoreTransportState(
    val beat: Float = 0f,
    val isPlaying: Boolean = false,
    val endBeat: Float = 0f,
)

/** Shared transport/playhead state used by playback and score editors. */
object ScoreTransportBus {
    private val _state = MutableStateFlow(ScoreTransportState())
    val state: StateFlow<ScoreTransportState> = _state.asStateFlow()

    fun seek(beat: Float) {
        val current = _state.value
        if (current.isPlaying) return
        _state.value = current.copy(beat = beat.coerceAtLeast(0f))
    }

    fun requestedStartBeat(endBeat: Float): Float {
        val safeEnd = endBeat.coerceAtLeast(0f)
        val requested = _state.value.beat.coerceIn(0f, safeEnd)
        return if (safeEnd > 0f && requested >= safeEnd - 0.001f) 0f else requested
    }

    internal fun begin(startBeat: Float, endBeat: Float) {
        _state.value = ScoreTransportState(
            beat = startBeat.coerceAtLeast(0f),
            isPlaying = true,
            endBeat = endBeat.coerceAtLeast(0f),
        )
    }

    internal fun progress(beat: Float) {
        val current = _state.value
        if (!current.isPlaying) return
        _state.value = current.copy(
            beat = beat.coerceIn(0f, current.endBeat.coerceAtLeast(0f)),
        )
    }

    internal fun finish(endBeat: Float) {
        val safeEnd = endBeat.coerceAtLeast(0f)
        _state.value = ScoreTransportState(beat = safeEnd, isPlaying = false, endBeat = safeEnd)
    }

    internal fun stop() {
        val current = _state.value
        _state.value = current.copy(isPlaying = false)
    }
}
