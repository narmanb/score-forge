package com.scoreforge.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalFileTypesTest {
    @Test
    fun projectExtensionWinsOverGenericMime() {
        assertEquals(
            ExternalFileKind.SCORE_FORGE_PROJECT,
            ExternalFileTypes.classify("My Song.sfp", "application/octet-stream"),
        )
    }

    @Test
    fun midiExtensionsAreRecognized() {
        assertEquals(
            ExternalFileKind.MIDI,
            ExternalFileTypes.classify("song.mid", "application/octet-stream"),
        )
        assertEquals(
            ExternalFileKind.MIDI,
            ExternalFileTypes.classify("song.MIDI", null),
        )
    }

    @Test
    fun midiMimeIsRecognizedWithoutFilename() {
        assertEquals(
            ExternalFileKind.MIDI,
            ExternalFileTypes.classify(null, "audio/midi"),
        )
    }

    @Test
    fun scoreForgeMimeIsRecognizedWithoutFilename() {
        assertEquals(
            ExternalFileKind.SCORE_FORGE_PROJECT,
            ExternalFileTypes.classify(null, ExternalFileTypes.SCORE_FORGE_PROJECT_MIME),
        )
    }

    @Test
    fun genericBinaryWithoutSupportedNameIsUnknown() {
        assertEquals(
            ExternalFileKind.UNKNOWN,
            ExternalFileTypes.classify("archive.bin", "application/octet-stream"),
        )
    }
}
