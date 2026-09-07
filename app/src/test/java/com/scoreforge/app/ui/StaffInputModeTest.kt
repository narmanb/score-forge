package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StaffInputModeTest {
    @Test
    fun cyclesThroughInputRearrangeAndOff() {
        assertEquals(StaffInputMode.REARRANGE, StaffInputMode.INPUT_ON.next())
        assertEquals(StaffInputMode.OFF, StaffInputMode.REARRANGE.next())
        assertEquals(StaffInputMode.INPUT_ON, StaffInputMode.OFF.next())
    }

    @Test
    fun modesExposeExpectedEditingCapabilities() {
        assertTrue(StaffInputMode.INPUT_ON.allowsNoteEntry)
        assertTrue(StaffInputMode.INPUT_ON.allowsRearrange)
        assertTrue(StaffInputMode.INPUT_ON.allowsDelete)

        assertFalse(StaffInputMode.REARRANGE.allowsNoteEntry)
        assertTrue(StaffInputMode.REARRANGE.allowsRearrange)
        assertTrue(StaffInputMode.REARRANGE.allowsDelete)

        assertFalse(StaffInputMode.OFF.allowsNoteEntry)
        assertFalse(StaffInputMode.OFF.allowsRearrange)
        assertFalse(StaffInputMode.OFF.allowsDelete)
    }

    @Test
    fun existingBooleanSettingMapsToInputOrOff() {
        assertEquals(StaffInputMode.INPUT_ON, StaffInputMode.fromInputEnabled(true))
        assertEquals(StaffInputMode.OFF, StaffInputMode.fromInputEnabled(false))
    }
}
