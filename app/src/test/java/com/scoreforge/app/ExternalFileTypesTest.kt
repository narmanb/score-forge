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

    @Test
    fun scoreForgeSignatureRecoversMissingFilenameAndMime() {
        val bytes = "SCOREFORGE\t2\nPROJECT_NAME\tRecovered\n".toByteArray()
        assertEquals(
            ExternalFileKind.SCORE_FORGE_PROJECT,
            ExternalFileTypes.detectContent(bytes),
        )
    }

    @Test
    fun midiHeaderRecoversMissingFilenameAndMime() {
        val bytes = byteArrayOf('M'.code.toByte(), 'T'.code.toByte(), 'h'.code.toByte(), 'd'.code.toByte(), 0, 0)
        assertEquals(
            ExternalFileKind.MIDI,
            ExternalFileTypes.detectContent(bytes),
        )
    }

    @Test
    fun unsupportedContentRemainsUnknown() {
        assertEquals(
            ExternalFileKind.UNKNOWN,
            ExternalFileTypes.detectContent("not a score forge file".toByteArray()),
        )
    }
}
