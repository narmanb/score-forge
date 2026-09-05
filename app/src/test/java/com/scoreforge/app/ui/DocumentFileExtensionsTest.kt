package com.scoreforge.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class DocumentFileExtensionsTest {
    @Test
    fun appendsMissingScoreForgeExtension() {
        assertEquals(
            "My Song.sfp",
            DocumentFileExtensions.correctedName("My Song", ".sfp"),
        )
    }

    @Test
    fun keepsExistingExtensionCaseInsensitively() {
        assertEquals(
            "My Song.SFP",
            DocumentFileExtensions.correctedName("My Song.SFP", ".sfp"),
        )
    }

    @Test
    fun appendsMidiExtensionWhenUserDeletesIt() {
        assertEquals(
            "Export.mid",
            DocumentFileExtensions.correctedName("Export", "mid"),
        )
    }

    @Test
    fun preservesUserDotsInBaseName() {
        assertEquals(
            "Final.mix.v2.mid",
            DocumentFileExtensions.correctedName("Final.mix.v2", ".mid"),
        )
    }
}
