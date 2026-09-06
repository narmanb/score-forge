package com.scoreforge.app

import android.net.Uri
import kotlin.math.min

data class ExternalOpenRequest(
    val uri: Uri,
    val mimeType: String?,
    val requestId: Long,
)

enum class ExternalFileKind {
    SCORE_FORGE_PROJECT,
    MIDI,
    UNKNOWN,
}

object ExternalFileTypes {
    const val SCORE_FORGE_PROJECT_MIME = "application/x-scoreforge-project"

    private val midiMimeTypes = setOf(
        "audio/midi",
        "audio/x-midi",
        "audio/sp-midi",
        "application/x-midi",
    )

    fun classify(displayName: String?, mimeType: String?): ExternalFileKind {
        val normalizedName = displayName.orEmpty().trim().lowercase()
        when {
            normalizedName.endsWith(".sfp") -> return ExternalFileKind.SCORE_FORGE_PROJECT
            normalizedName.endsWith(".mid") || normalizedName.endsWith(".midi") -> return ExternalFileKind.MIDI
        }

        return when (mimeType.orEmpty().trim().lowercase()) {
            SCORE_FORGE_PROJECT_MIME -> ExternalFileKind.SCORE_FORGE_PROJECT
            in midiMimeTypes -> ExternalFileKind.MIDI
            else -> ExternalFileKind.UNKNOWN
        }
    }

    /**
     * File managers do not consistently report MIME types for custom extensions such as .sfp.
     * Use the file signatures as a final fallback after filename/MIME classification fails.
     */
    fun detectContent(bytes: ByteArray): ExternalFileKind {
        if (
            bytes.size >= 4 &&
            bytes[0] == 'M'.code.toByte() &&
            bytes[1] == 'T'.code.toByte() &&
            bytes[2] == 'h'.code.toByte() &&
            bytes[3] == 'd'.code.toByte()
        ) {
            return ExternalFileKind.MIDI
        }

        if (bytes.isNotEmpty()) {
            val prefixLength = min(bytes.size, 64)
            val prefix = bytes.copyOfRange(0, prefixLength)
                .toString(Charsets.UTF_8)
                .trimStart('\uFEFF', ' ', '\t', '\r', '\n')
            if (prefix.startsWith("SCOREFORGE\t")) {
                return ExternalFileKind.SCORE_FORGE_PROJECT
            }
        }

        return ExternalFileKind.UNKNOWN
    }
}
