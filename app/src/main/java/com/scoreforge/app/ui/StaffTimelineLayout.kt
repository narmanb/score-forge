package com.scoreforge.app.ui

import com.scoreforge.app.music.ScoreTimeline
import kotlin.math.ceil
import kotlin.math.roundToInt

object StaffTimelineLayout {
    const val MIN_ZOOM = 0.60f
    const val MAX_ZOOM = 2.00f
    const val ZOOM_STEP = 0.20f
    const val DEFAULT_VISIBLE_BEATS = 16f
    const val ENTRY_FOLLOW_ANCHOR_FRACTION = 0.64f

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

    /**
     * Keeps step entry around the middle of the second-to-last measure in the normal four-measure
     * viewport. At the default zoom this anchor lands at roughly beat 10 of 16. Once the cursor
     * passes it, each new note advances the scroll by the same amount that the cursor advanced.
     */
    fun entryAutoFollowTarget(
        cursorBeat: Float,
        currentScrollPx: Int,
        maxScrollPx: Int,
        viewportWidthPx: Float,
        timelineLeftPx: Float,
        pixelsPerBeat: Float,
    ): Int? {
        if (
            maxScrollPx <= 0 ||
            viewportWidthPx <= 0f ||
            pixelsPerBeat <= 0f ||
            cursorBeat < 0f
        ) {
            return null
        }

        val anchorOffset = viewportWidthPx * ENTRY_FOLLOW_ANCHOR_FRACTION
        val cursorX = xAtBeat(cursorBeat, timelineLeftPx, pixelsPerBeat)
        val triggerX = currentScrollPx + anchorOffset
        if (cursorX <= triggerX + 0.5f) return null

        return (cursorX - anchorOffset)
            .roundToInt()
            .coerceIn(0, maxScrollPx)
    }
}
