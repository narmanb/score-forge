package com.scoreforge.app.audio

import android.content.Context
import com.scoreforge.app.music.ScoreArticulations
import com.scoreforge.app.music.ScoreProjectSnapshot
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTempos
import com.scoreforge.app.music.ScoreTies
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
import java.io.BufferedOutputStream
import java.io.OutputStream
import kotlin.math.roundToInt

/**
 * Renders a Score Forge project to 16-bit stereo PCM WAV without buffering the entire song in RAM.
 *
 * FluidSynth is driven in small blocks using the same note/tie/articulation interpretation as
 * streaming playback. The WAV header is written up-front because project duration is known from the
 * score/tempo map, so output streams do not need to support seeking.
 */
object WavAudioExporter {
    const val DEFAULT_SAMPLE_RATE = 44_100
    private const val CHANNELS = 2
    private const val BITS_PER_SAMPLE = 16
    private const val BYTES_PER_SAMPLE = BITS_PER_SAMPLE / 8
    private const val RENDER_BLOCK_FRAMES = 2_048
    private const val TAIL_SECONDS = 0.45f
    private const val MAX_RIFF_DATA_BYTES = 0xffff_ffffL - 36L

    data class Result(
        val durationSeconds: Double,
        val exportedTrackCount: Int,
        val exportedNoteCount: Int,
        val soundFontName: String,
        val warnings: List<String>,
    )

    internal data class MidiEvent(
        val frame: Int,
        val noteOn: Boolean,
        val key: Int,
        val velocity: Int,
        val channel: Int,
    )

    internal data class RenderPlan(
        val tracks: List<ScoreTrack>,
        val tempoChanges: List<ScoreTempoChange>,
        val events: List<MidiEvent>,
        val scoreFrames: Int,
        val totalFrames: Int,
        val noteCount: Int,
    )

    fun export(
        context: Context,
        snapshot: ScoreProjectSnapshot,
        output: OutputStream,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        onProgress: (Float) -> Unit = {},
    ): Result {
        require(sampleRate in 8_000..192_000) { "Unsupported WAV sample rate: $sampleRate Hz." }
        val plan = buildRenderPlan(snapshot, sampleRate)
        require(plan.noteCount > 0) { "There are no audible notes to export." }

        val dataBytes = plan.totalFrames.toLong() * CHANNELS * BYTES_PER_SAMPLE
        require(dataBytes <= MAX_RIFF_DATA_BYTES) {
            "This project is too long for a standard WAV file."
        }

        onProgress(0f)

        val savedSelection = SoundFontRepository.loadActiveSelection(context)
        val sourceSelection = if (savedSelection != null) {
            savedSelection
        } else {
            val starter = SoundFontRepository.installBundledStarter(context).getOrElse { error ->
                throw IllegalStateException(
                    error.message ?: "No usable SoundFont is available for WAV export.",
                    error,
                )
            }
            SavedSoundFontSelection(starter, bank = null, program = null)
        }

        val engine = requireNotNull(SoundFontEngine.createOrNull(sampleRate)) {
            "FluidSynth is unavailable on this device, so WAV rendering cannot start."
        }

        val warnings = mutableListOf<String>()
        try {
            require(engine.loadSoundFont(sourceSelection.soundFont.localPath)) {
                "Could not load ${sourceSelection.soundFont.displayName} for WAV export."
            }

            if (sourceSelection.bank != null && sourceSelection.program != null) {
                engine.presets.firstOrNull {
                    it.bank == sourceSelection.bank && it.program == sourceSelection.program
                }?.let { engine.selectPreset(it) }
            }

            val availablePresets = engine.presets.map { it.bank to it.program }.toSet()
            plan.tracks.forEach { track ->
                val bank = track.presetBank
                val program = track.presetProgram
                if (bank != null && program != null && (bank to program) !in availablePresets) {
                    warnings += "${track.name}: preset bank $bank program ${program + 1} is unavailable; the active SoundFont fallback was used."
                }
            }

            require(engine.prepareStreamingTracks(plan.tracks)) {
                "The active SoundFont could not prepare the audible tracks for WAV export."
            }

            val buffered = if (output is BufferedOutputStream) output else BufferedOutputStream(output, 64 * 1024)
            buffered.write(wavHeader(sampleRate, plan.totalFrames))

            var frameCursor = 0
            var eventIndex = 0
            var lastReportedPercent = -1

            fun reportProgress() {
                val percent = if (plan.totalFrames <= 0) {
                    100
                } else {
                    ((frameCursor.toLong() * 100L) / plan.totalFrames.toLong())
                        .toInt()
                        .coerceIn(0, 100)
                }
                if (percent != lastReportedPercent) {
                    lastReportedPercent = percent
                    onProgress(percent / 100f)
                }
            }

            reportProgress()
            while (frameCursor < plan.totalFrames) {
                while (eventIndex < plan.events.size && plan.events[eventIndex].frame <= frameCursor) {
                    val event = plan.events[eventIndex++]
                    if (event.noteOn) {
                        require(engine.noteOn(event.key, event.velocity, event.channel)) {
                            "FluidSynth rejected a note-on event during WAV export."
                        }
                    } else {
                        engine.noteOff(event.key, event.channel)
                    }
                }

                val nextEventFrame = plan.events.getOrNull(eventIndex)?.frame ?: plan.totalFrames
                val untilEvent = (nextEventFrame - frameCursor).coerceAtLeast(1)
                val frames = minOf(
                    RENDER_BLOCK_FRAMES,
                    plan.totalFrames - frameCursor,
                    untilEvent,
                )
                val pcm = engine.renderStereo(frames)
                require(pcm.size == frames * CHANNELS) {
                    "FluidSynth returned an incomplete audio block during WAV export."
                }
                writePcm16Le(buffered, pcm)
                frameCursor += frames
                reportProgress()
            }
            buffered.flush()
            onProgress(1f)
        } finally {
            engine.finishStreamingTracks()
            engine.close()
        }

        return Result(
            durationSeconds = plan.totalFrames.toDouble() / sampleRate.toDouble(),
            exportedTrackCount = plan.tracks.count { it.notes.isNotEmpty() },
            exportedNoteCount = plan.noteCount,
            soundFontName = sourceSelection.soundFont.displayName,
            warnings = warnings.distinct(),
        )
    }

    internal fun buildRenderPlan(
        snapshot: ScoreProjectSnapshot,
        sampleRate: Int = DEFAULT_SAMPLE_RATE,
        tailSeconds: Float = TAIL_SECONDS,
    ): RenderPlan {
        val tracks = ScoreTracks.audibleTracks(snapshot.effectiveTracks())
            .take(ScoreTracks.MAX_TRACKS)
            .map { it.copy(events = it.events.toList()) }
        val tempoChanges = snapshot.effectiveTempoChanges()
        val throughBeat = tracks.maxOfOrNull { it.endBeat } ?: 0f
        val scoreFrames = (ScoreTempos.secondsAtBeat(tempoChanges, throughBeat) * sampleRate)
            .roundToInt()
            .coerceAtLeast(1)
        val tailFrames = (tailSeconds.coerceAtLeast(0f) * sampleRate).roundToInt()
        val totalFrames = (scoreFrames.toLong() + tailFrames.toLong())
            .coerceAtLeast(1L)
            .coerceAtMost(Int.MAX_VALUE.toLong())
            .toInt()

        var noteCount = 0
        val events = buildList {
            tracks.forEachIndexed { channel, track ->
                val notes = track.notes
                noteCount += notes.size
                notes.forEachIndexed noteLoop@{ index, note ->
                    if (ScoreTies.isContinuation(notes, index)) return@noteLoop
                    val playbackEndBeat = if (ScoreTies.hasValidTie(notes, index)) {
                        ScoreTies.chainEndBeat(notes, index)
                    } else {
                        ScoreArticulations.playbackEndBeat(notes, index)
                    }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)

                    val onFrame = (ScoreTempos.secondsAtBeat(tempoChanges, note.startBeat) * sampleRate)
                        .roundToInt()
                        .coerceAtLeast(0)
                    val offFrame = (ScoreTempos.secondsAtBeat(tempoChanges, playbackEndBeat) * sampleRate)
                        .roundToInt()
                        .coerceAtLeast(onFrame + 1)
                    add(
                        MidiEvent(
                            frame = onFrame,
                            noteOn = true,
                            key = note.midiPitch.coerceIn(0, 127),
                            velocity = ScoreArticulations.playbackVelocity(note),
                            channel = channel.coerceIn(0, 15),
                        )
                    )
                    add(
                        MidiEvent(
                            frame = offFrame,
                            noteOn = false,
                            key = note.midiPitch.coerceIn(0, 127),
                            velocity = 0,
                            channel = channel.coerceIn(0, 15),
                        )
                    )
                }
            }
        }.sortedWith(
            compareBy<MidiEvent> { it.frame }
                .thenBy { if (it.noteOn) 1 else 0 }
                .thenBy { it.channel }
                .thenBy { it.key }
        )

        return RenderPlan(
            tracks = tracks,
            tempoChanges = tempoChanges,
            events = events,
            scoreFrames = scoreFrames,
            totalFrames = totalFrames,
            noteCount = noteCount,
        )
    }

    internal fun wavHeader(sampleRate: Int, totalFrames: Int): ByteArray {
        val dataSize = totalFrames.toLong() * CHANNELS * BYTES_PER_SAMPLE
        require(dataSize <= MAX_RIFF_DATA_BYTES)
        val byteRate = sampleRate * CHANNELS * BYTES_PER_SAMPLE
        val blockAlign = CHANNELS * BYTES_PER_SAMPLE
        return ByteArray(44).also { header ->
            putAscii(header, 0, "RIFF")
            putLe32(header, 4, 36L + dataSize)
            putAscii(header, 8, "WAVE")
            putAscii(header, 12, "fmt ")
            putLe32(header, 16, 16L)
            putLe16(header, 20, 1)
            putLe16(header, 22, CHANNELS)
            putLe32(header, 24, sampleRate.toLong())
            putLe32(header, 28, byteRate.toLong())
            putLe16(header, 32, blockAlign)
            putLe16(header, 34, BITS_PER_SAMPLE)
            putAscii(header, 36, "data")
            putLe32(header, 40, dataSize)
        }
    }

    private fun writePcm16Le(output: OutputStream, pcm: ShortArray) {
        val bytes = ByteArray(pcm.size * 2)
        var target = 0
        pcm.forEach { sample ->
            val value = sample.toInt()
            bytes[target++] = (value and 0xff).toByte()
            bytes[target++] = ((value ushr 8) and 0xff).toByte()
        }
        output.write(bytes)
    }

    private fun putAscii(target: ByteArray, offset: Int, text: String) {
        text.forEachIndexed { index, char -> target[offset + index] = char.code.toByte() }
    }

    private fun putLe16(target: ByteArray, offset: Int, value: Int) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
    }

    private fun putLe32(target: ByteArray, offset: Int, value: Long) {
        target[offset] = (value and 0xff).toByte()
        target[offset + 1] = ((value ushr 8) and 0xff).toByte()
        target[offset + 2] = ((value ushr 16) and 0xff).toByte()
        target[offset + 3] = ((value ushr 24) and 0xff).toByte()
    }
}
