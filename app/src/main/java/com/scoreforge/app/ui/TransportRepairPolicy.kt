package com.scoreforge.app.ui

/**
 * Keeps the shared playback/live playhead from surviving in an impossible entry-mode state.
 *
 * Natural and Step entry never own a continuously moving transport. If the transport says it is
 * playing while neither normal score playback nor Live entry owns it, it is orphaned and should
 * be stopped. A leaked Live recording state in a non-Live mode is also cancelled rather than
 * allowed to keep advancing the playhead/cursor in the background.
 */
internal object TransportRepairPolicy {
    data class Decision(
        val cancelLiveRecording: Boolean,
        val stopTransport: Boolean,
    )

    fun decide(
        isLiveMode: Boolean,
        liveRecordingActive: Boolean,
        scorePlaybackActive: Boolean,
        transportPlaying: Boolean,
    ): Decision {
        if (isLiveMode) return Decision(false, false)
        return Decision(
            cancelLiveRecording = liveRecordingActive,
            stopTransport = transportPlaying && !scorePlaybackActive,
        )
    }
}
