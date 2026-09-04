package com.scoreforge.app.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransportRepairPolicyTest {
    @Test
    fun `natural mode stops an orphaned moving transport`() {
        val decision = TransportRepairPolicy.decide(
            isLiveMode = false,
            liveRecordingActive = false,
            scorePlaybackActive = false,
            transportPlaying = true,
        )

        assertFalse(decision.cancelLiveRecording)
        assertTrue(decision.stopTransport)
    }

    @Test
    fun `natural mode cancels leaked live recording state`() {
        val decision = TransportRepairPolicy.decide(
            isLiveMode = false,
            liveRecordingActive = true,
            scorePlaybackActive = false,
            transportPlaying = true,
        )

        assertTrue(decision.cancelLiveRecording)
        assertTrue(decision.stopTransport)
    }

    @Test
    fun `normal score playback is not mistaken for an orphan`() {
        val decision = TransportRepairPolicy.decide(
            isLiveMode = false,
            liveRecordingActive = false,
            scorePlaybackActive = true,
            transportPlaying = true,
        )

        assertFalse(decision.cancelLiveRecording)
        assertFalse(decision.stopTransport)
    }

    @Test
    fun `live mode owns its moving transport`() {
        val decision = TransportRepairPolicy.decide(
            isLiveMode = true,
            liveRecordingActive = true,
            scorePlaybackActive = false,
            transportPlaying = true,
        )

        assertFalse(decision.cancelLiveRecording)
        assertFalse(decision.stopTransport)
    }
}
