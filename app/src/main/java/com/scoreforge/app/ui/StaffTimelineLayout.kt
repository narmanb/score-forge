package com.scoreforge.app.ui

import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.ceil

object StaffTimelineLayout {
    const val MIN_ZOOM = 0.60f
    const val MAX_ZOOM = 2.00f
    const val ZOOM_STEP = 0.20f
    const val DEFAULT_VISIBLE_BEATS = 16f

    fun clampZoom(zoom: Float): Float = zoom.coerceIn(MIN_ZOOM, MAX_ZOOM)

    /**
     * Keep at least four measures visible and always leave one empty measure after the furthest
     * event/cursor/playhead so the user can continue composing without the timeline ending abruptly.
     */
    fun contentBeats(
        eventsEndBeat: Float,
        editCursorBeat: Float,
        playheadBeat: Float,
    ): Float {
        val furthest = maxOf(eventsEndBeat, editCursorBeat, playheadBeat, 0f)
        val withWorkingMeasure = furthest + ScoreTimeline.BEATS_PER_MEASURE
        val rounded = ceil(withWorkingMeasure / ScoreTimeline.BEATS_PER_MEASURE) *
            ScoreTimeline.BEATS_PER_MEASURE
        return maxOf(DEFAULT_VISIBLE_BEATS, rounded)
    }

    fun xAtBeat(beat: Float, timelineLeftPx: Float, pixelsPerBeat: Float): Float =
        timelineLeftPx + beat.coerceAtLeast(0f) * pixelsPerBeat.coerceAtLeast(0.001f)

    fun beatAtX(x: Float, timelineLeftPx: Float, pixelsPerBeat: Float): Float =
        ((x - timelineLeftPx) / pixelsPerBeat.coerceAtLeast(0.001f)).coerceAtLeast(0f)
}
