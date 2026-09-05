package com.scoreforge.app.audio

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStreamingPolicyTest {
    @Test
    fun shortSmallScoreKeepsStaticPath() {
        assertFalse(PlaybackStreamingPolicy.shouldStream(16f, 120, 64))
    }

    @Test
    fun longMidiUsesStreamingPath() {
        assertTrue(PlaybackStreamingPolicy.shouldStream(989f, 120, 8979))
    }

    @Test
    fun denseScoreStreamsEvenWhenShort() {
        assertTrue(PlaybackStreamingPolicy.shouldStream(24f, 120, 2000))
    }
}
