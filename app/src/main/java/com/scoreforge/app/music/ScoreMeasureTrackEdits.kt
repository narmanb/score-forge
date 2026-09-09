package com.scoreforge.app.music

data class ScoreTrackMeasureClipboard(
    val trackId: Int,
    val clipboard: ScoreMeasureClipboard,
)

data class ScoreAllTracksMeasureClipboard(
    val sourceMeasureLengths: List<Float>,
    val tracks: List<ScoreTrackMeasureClipboard>,
) {
    val sourceMeasureCount: Int
        get() = sourceMeasureLengths.size.coerceAtLeast(1)

    val sourceLengthBeats: Float
        get() = sourceMeasureLengths.sum()
}

data class ScoreAllTracksPasteProblem(
    val trackId: Int,
    val problem: ScoreMeasurePasteProblem,
)

/** Meter-aware measure operations that apply one structural edit across every score track. */
object ScoreMeasureTrackEdits {
    fun copyAllTracks(
        tracks: List<ScoreTrack>,
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
        measureCount: Int,
    ): ScoreAllTracksMeasureClipboard {
        val bounds = ScoreMeasureEdits.boundsRange(timeSignatures, beat, measureCount)
        val sourceLengths = bounds.map { it.lengthBeats }
        return ScoreAllTracksMeasureClipboard(
            sourceMeasureLengths = sourceLengths,
            tracks = tracks.map { track ->
                ScoreTrackMeasureClipboard(
                    trackId = track.id,
                    clipboard = ScoreMeasureEdits.copyMeasures(
                        events = track.events,
                        timeSignatures = timeSignatures,
                        beat = beat,
                        measureCount = sourceLengths.size,
                    ),
                )
            },
        )
    }

    fun pasteProblemAt(
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreAllTracksMeasureClipboard,
    ): ScoreAllTracksPasteProblem? {
        clipboard.tracks.forEach { trackClipboard ->
            val problem = ScoreMeasureEdits.pasteProblemAt(
                timeSignatures = timeSignatures,
                destinationBeat = destinationBeat,
                clipboard = trackClipboard.clipboard,
            )
            if (problem != null) {
                return ScoreAllTracksPasteProblem(
                    trackId = trackClipboard.trackId,
                    problem = problem,
                )
            }
        }
        return null
    }

    fun pasteReplaceAllTracks(
        tracks: List<ScoreTrack>,
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreAllTracksMeasureClipboard,
    ): List<ScoreTrack> {
        if (pasteProblemAt(timeSignatures, destinationBeat, clipboard) != null) return tracks
        val copiedByTrackId = clipboard.tracks.associateBy { it.trackId }
        return tracks.map { track ->
            val source = copiedByTrackId[track.id]?.clipboard ?: emptyClipboard(clipboard)
            track.copy(
                events = ScoreMeasureEdits.pasteReplace(
                    events = track.events,
                    timeSignatures = timeSignatures,
                    destinationBeat = destinationBeat,
                    clipboard = source,
                ),
            )
        }
    }

    fun pasteInsertAllTracks(
        tracks: List<ScoreTrack>,
        timeSignatures: List<ScoreTimeSignature>,
        destinationBeat: Float,
        clipboard: ScoreAllTracksMeasureClipboard,
    ): List<ScoreTrack> {
        if (pasteProblemAt(timeSignatures, destinationBeat, clipboard) != null) return tracks
        val copiedByTrackId = clipboard.tracks.associateBy { it.trackId }
        return tracks.map { track ->
            val source = copiedByTrackId[track.id]?.clipboard ?: emptyClipboard(clipboard)
            track.copy(
                events = ScoreMeasureEdits.pasteInsert(
                    events = track.events,
                    timeSignatures = timeSignatures,
                    destinationBeat = destinationBeat,
                    clipboard = source,
                ),
            )
        }
    }

    fun duplicateMeasureAllTracks(
        tracks: List<ScoreTrack>,
        timeSignatures: List<ScoreTimeSignature>,
        beat: Float,
        copies: Int,
    ): List<ScoreTrack> = tracks.map { track ->
        track.copy(
            events = ScoreMeasureEdits.duplicateMeasure(
                events = track.events,
                timeSignatures = timeSignatures,
                beat = beat,
                copies = copies,
            ),
        )
    }

    private fun emptyClipboard(clipboard: ScoreAllTracksMeasureClipboard): ScoreMeasureClipboard =
        ScoreMeasureClipboard(
            sourceLengthBeats = clipboard.sourceLengthBeats,
            events = emptyList(),
            sourceMeasureLengths = clipboard.sourceMeasureLengths,
        )
}
