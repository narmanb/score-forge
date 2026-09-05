package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class StaffCursorInteractionTest {
    private val staffTop = 80f
    private val staffBottom = 160f
    private val spacing = 20f

    @Test
    fun `top gutter controls playback position`() {
        assertEquals(
            StaffCursorZone.PLAYBACK,
            StaffCursorInteraction.zoneForY(60f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun `staff body remains normal editing territory`() {
        assertEquals(
            StaffCursorZone.STAFF,
            StaffCursorInteraction.zoneForY(120f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun `bottom gutter controls note entry cursor`() {
        assertEquals(
            StaffCursorZone.ENTRY,
            StaffCursorInteraction.zoneForY(180f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun `gutter boundary classification is deterministic`() {
        assertEquals(
            StaffCursorZone.PLAYBACK,
            StaffCursorInteraction.zoneForY(63f, staffTop, staffBottom, spacing),
        )
        assertEquals(
            StaffCursorZone.ENTRY,
            StaffCursorInteraction.zoneForY(171f, staffTop, staffBottom, spacing),
        )
    }

    @Test
    fun verticalDragInCursorGutterRoutesToPageScroll() {
        assertEquals(
            StaffCursorDragIntent.VERTICAL_SCROLL,
            StaffCursorInteraction.dragIntent(deltaX = 3f, deltaY = 18f),
        )
    }

    @Test
    fun horizontalDragInCursorGutterRoutesToCursor() {
        assertEquals(
            StaffCursorDragIntent.CURSOR,
            StaffCursorInteraction.dragIntent(deltaX = 18f, deltaY = 3f),
        )
    }
}
