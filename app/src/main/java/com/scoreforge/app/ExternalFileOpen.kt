package com.scoreforge.app

import android.net.Uri

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
}
