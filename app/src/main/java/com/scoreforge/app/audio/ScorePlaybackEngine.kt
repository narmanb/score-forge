package com.scoreforge.app.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.scoreforge.app.music.NoteDuration
import com.scoreforge.app.music.MetronomeAccent
import com.scoreforge.app.music.ScoreMetronome
import com.scoreforge.app.music.ScoreTimeSignature
import com.scoreforge.app.music.ScoreTimeSignatures
import com.scoreforge.app.music.ScoreArticulations
import com.scoreforge.app.music.ScoreNote
import com.scoreforge.app.music.ScoreTempoChange
import com.scoreforge.app.music.ScoreTempos
import com.scoreforge.app.music.ScoreTies
import com.scoreforge.app.music.ScoreTimeline
import com.scoreforge.app.music.ScoreTrack
import com.scoreforge.app.music.ScoreTracks
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class ScorePlaybackEngine {
    private val sampleRate = 44_100

    companion object {
        private const val STREAMING_BLOCK_FRAMES = 2_048
        private const val STREAMING_TAIL_SECONDS = 0.45f
        private const val STREAMING_DRAIN_TIMEOUT_MS = 2_000L
    }
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var activeTrack: AudioTrack? = null

    @Volatile
    private var generation = 0

    @Volatile
    private var soundFontEngine: SoundFontEngine? = null

    private data class RenderedAudio(
        val pcm: ShortArray,
        val channels: Int,
    )

    private data class StreamingMidiEvent(
        val frame: Int,
        val noteOn: Boolean,
        val key: Int,
        val velocity: Int,
        val channel: Int,
    )

    private data class StreamingClick(
        val frame: Int,
        val frequency: Double,
        val gain: Float,
    )

    fun setSoundFontEngine(engine: SoundFontEngine?) {
        soundFontEngine = engine
    }

    fun playScore(
        notes: List<ScoreNote>,
        bpm: Int,
        tempoChanges: List<ScoreTempoChange> = listOf(ScoreTempoChange(0f, bpm)),
        throughBeat: Float = ScoreTimeline.endBeat(notes),
        metronomeEnabled: Boolean = false,
        timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),
        onFinished: () -> Unit = {},
    ) {
        val compatibilityTrack = ScoreTrack(
            id = 1,
            name = "Track 1",
            events = notes,
            cursorBeat = throughBeat,
        )
        playTracks(
            tracks = listOf(compatibilityTrack),
            bpm = bpm,
            tempoChanges = tempoChanges,
            throughBeat = throughBeat,
            metronomeEnabled = metronomeEnabled,
            timeSignatures = timeSignatures,
            onFinished = onFinished,
        )
    }

    fun playTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        tempoChanges: List<ScoreTempoChange> = listOf(ScoreTempoChange(0f, bpm)),
        throughBeat: Float = ScoreTracks.endBeat(tracks),
        metronomeEnabled: Boolean = false,
        timeSignatures: List<ScoreTimeSignature> = listOf(ScoreTimeSignatures.DEFAULT),
        onFinished: () -> Unit = {},
    ) {
        val playableTracks = ScoreTracks.audibleTracks(tracks)
            .take(ScoreTracks.MAX_TRACKS)
            .map { it.copy(events = it.events.toList()) }
        val notes = playableTracks.flatMap { it.notes }
        if (notes.isEmpty()) {
            onFinished()
            return
        }

        stop()
        val myGeneration = ++generation
        val safeTempos = ScoreTempos.normalize(tempoChanges.ifEmpty { listOf(ScoreTempoChange(0f, bpm)) })
        val safeBpm = safeTempos.first().bpm
        val hasTempoChanges = safeTempos.size > 1
        val safeThroughBeat = maxOf(
            playableTracks.maxOfOrNull { ScoreTimeline.endBeat(it.events) } ?: 0f,
            throughBeat.coerceAtLeast(0f),
        )
        val startBeat = ScoreTransportBus.requestedStartBeat(safeThroughBeat)
        ScoreTransportBus.begin(startBeat, safeThroughBeat)

        if (
            soundFontEngine?.hasSoundFont == true &&
            (hasTempoChanges || PlaybackStreamingPolicy.shouldStream(
                throughBeat = safeThroughBeat - startBeat,
                bpm = safeBpm,
                noteCount = notes.size,
            ))
        ) {
            playStreamingSoundFontTracks(
                playableTracks = playableTracks,
                bpm = safeBpm,
                tempoChanges = safeTempos,
                startBeat = startBeat,
                throughBeat = safeThroughBeat,
                metronomeEnabled = metronomeEnabled,
                timeSignatures = timeSignatures,
                myGeneration = myGeneration,
                onFinished = onFinished,
            )
            return
        }

        thread(name = "ScoreForgePlayback", isDaemon = true) {
            var rendered = renderBestAvailableTracks(playableTracks, safeBpm, safeThroughBeat, safeTempos)
            if (metronomeEnabled && rendered.pcm.isNotEmpty()) {
                rendered = mixMetronome(
                    rendered = rendered,
                    tempoChanges = safeTempos,
                    throughBeat = safeThroughBeat,
                    timeSignatures = timeSignatures,
                )
            }
            if (myGeneration != generation || rendered.pcm.isEmpty()) {
                if (myGeneration == generation) ScoreTransportBus.stop()
                return@thread
            }

            val track = createStaticTrack(rendered.pcm, rendered.channels)
            if (myGeneration != generation) {
                track.release()
                return@thread
            }

            activeTrack = track
            track.write(rendered.pcm, 0, rendered.pcm.size)

            val totalFrames = rendered.pcm.size / rendered.channels.coerceAtLeast(1)
            val startSeconds = ScoreTempos.secondsAtBeat(safeTempos, startBeat)
            val requestedStartFrame = (startSeconds * sampleRate).toInt()
            val startFrame = requestedStartFrame.coerceIn(0, (totalFrames - 1).coerceAtLeast(0))
            if (startFrame > 0) {
                try {
                    track.setPlaybackHeadPosition(startFrame)
                } catch (_: IllegalStateException) {
                    // If a device rejects static seeking, playback still safely starts at zero.
                }
            }

            track.play()

            val remainingFrames = (totalFrames - startFrame).coerceAtLeast(0)
            val durationMs = (remainingFrames.toDouble() / sampleRate * 1000.0).toLong() + 80L
            val startedAt = SystemClock.elapsedRealtime()

            try {
                while (myGeneration == generation) {
                    val elapsedMs = SystemClock.elapsedRealtime() - startedAt
                    if (elapsedMs >= durationMs) break
                    val absoluteSeconds = startSeconds + elapsedMs / 1000.0
                    val beat = ScoreTempos.beatAtSeconds(safeTempos, absoluteSeconds)
                    ScoreTransportBus.progress(beat.coerceAtMost(safeThroughBeat))
                    Thread.sleep(16L)
                }
            } catch (_: InterruptedException) {
                // A stop/restart invalidates this generation below.
            }

            if (myGeneration == generation) {
                ScoreTransportBus.finish(safeThroughBeat)
                releaseTrack(track)
                activeTrack = null
                mainHandler.post(onFinished)
            } else {
                releaseTrack(track)
            }
        }
    }

    fun previewPitch(midiPitch: Int) {
        if (LiveInstrumentBus.previewPitch(midiPitch, velocity = 82)) return

        val preview = listOf(
            ScoreNote(
                midiPitch = midiPitch,
                duration = NoteDuration.QUARTER,
                startBeat = 0f,
                velocity = 82,
            )
        )

        thread(name = "ScoreForgePreview", isDaemon = true) {
            val pcm = renderFallbackScore(preview, bpm = 240, tailSeconds = 0.08f)
            if (pcm.isEmpty()) return@thread
            val track = createStaticTrack(pcm, channels = 1)
            track.write(pcm, 0, pcm.size)
            track.play()
            try {
                Thread.sleep(((pcm.size.toDouble() / sampleRate) * 1000.0).toLong() + 30L)
            } catch (_: InterruptedException) {
                // Nothing else to do for a short preview voice.
            }
            releaseTrack(track)
        }
    }

    fun stop() {
        generation++
        activeTrack?.let(::releaseTrack)
        activeTrack = null
        ScoreTransportBus.stop()
    }

    fun release() = stop()

    private fun renderBestAvailableTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        throughBeat: Float,
        tempoChanges: List<ScoreTempoChange>,
    ): RenderedAudio {
        val soundFont = soundFontEngine
        if (soundFont != null && soundFont.hasSoundFont && ScoreTempos.normalize(tempoChanges).size <= 1) {
            val pcm = soundFont.renderTracks(tracks, bpm, throughBeat = throughBeat)
            if (pcm.isNotEmpty()) return RenderedAudio(pcm, channels = 2)
        }

        return RenderedAudio(
            pcm = renderFallbackTracks(
                tracks = tracks,
                bpm = bpm,
                tempoChanges = tempoChanges,
                throughBeat = throughBeat,
            ),
            channels = 2,
        )
    }


    private fun playStreamingSoundFontTracks(
        playableTracks: List<ScoreTrack>,
        bpm: Int,
        tempoChanges: List<ScoreTempoChange>,
        startBeat: Float,
        throughBeat: Float,
        metronomeEnabled: Boolean,
        timeSignatures: List<ScoreTimeSignature>,
        myGeneration: Int,
        onFinished: () -> Unit,
    ) {
        val soundFont = soundFontEngine ?: run {
            ScoreTransportBus.stop()
            mainHandler.post(onFinished)
            return
        }
        val safeTempos = ScoreTempos.normalize(tempoChanges)
        val startSeconds = ScoreTempos.secondsAtBeat(safeTempos, startBeat)

        thread(name = "ScoreForgePlaybackStream", isDaemon = true) {
            var streamTrack: AudioTrack? = null
            try {
                if (myGeneration != generation) return@thread
                val events = buildStreamingMidiEvents(
                    tracks = playableTracks,
                    startBeat = startBeat,
                    throughBeat = throughBeat,
                    tempoChanges = safeTempos,
                )
                val clicks = if (metronomeEnabled) {
                    buildStreamingClicks(
                        timeSignatures = timeSignatures,
                        startBeat = startBeat,
                        throughBeat = throughBeat,
                        tempoChanges = safeTempos,
                    )
                } else {
                    emptyList()
                }
                if (myGeneration != generation) return@thread
                if (!soundFont.prepareStreamingTracks(playableTracks)) {
                    if (myGeneration == generation) {
                        ScoreTransportBus.stop()
                        mainHandler.post(onFinished)
                    }
                    return@thread
                }

                val track = createStreamingTrack()
                streamTrack = track
                if (myGeneration != generation) return@thread
                activeTrack = track
                track.play()

                val scoreFrames = (
                    (ScoreTempos.durationSeconds(safeTempos, startBeat, throughBeat) * sampleRate)
                    ).toInt().coerceAtLeast(1)
                val tailFrames = (sampleRate * STREAMING_TAIL_SECONDS).toInt().coerceAtLeast(1)
                val totalFrames = scoreFrames + tailFrames
                var frameCursor = 0
                var eventIndex = 0
                var failed = false

                while (myGeneration == generation && frameCursor < totalFrames) {
                    while (eventIndex < events.size && events[eventIndex].frame <= frameCursor) {
                        val event = events[eventIndex++]
                        if (event.noteOn) {
                            soundFont.noteOn(event.key, event.velocity, event.channel)
                        } else {
                            soundFont.noteOff(event.key, event.channel)
                        }
                    }

                    val nextEventFrame = events.getOrNull(eventIndex)?.frame ?: totalFrames
                    val untilEvent = (nextEventFrame - frameCursor).coerceAtLeast(1)
                    val frames = minOf(
                        STREAMING_BLOCK_FRAMES,
                        totalFrames - frameCursor,
                        untilEvent,
                    )
                    val pcm = soundFont.renderStereo(frames)
                    if (pcm.isEmpty()) {
                        failed = true
                        break
                    }
                    if (clicks.isNotEmpty()) {
                        mixStreamingMetronome(pcm, frameCursor, clicks)
                    }
                    if (!writeStreamingBlock(track, pcm)) {
                        failed = true
                        break
                    }
                    frameCursor += frames

                    val playedFrames = track.playbackHeadPosition.toLong().coerceAtLeast(0L)
                    val beat = ScoreTempos.beatAtSeconds(
                        safeTempos,
                        startSeconds + playedFrames.toDouble() / sampleRate.toDouble(),
                    )
                    ScoreTransportBus.progress(beat.coerceAtMost(throughBeat))
                }

                if (myGeneration == generation && !failed) {
                    val drainDeadline = SystemClock.elapsedRealtime() + STREAMING_DRAIN_TIMEOUT_MS
                    while (
                        myGeneration == generation &&
                        track.playbackHeadPosition < totalFrames - 128 &&
                        SystemClock.elapsedRealtime() < drainDeadline
                    ) {
                        val playedFrames = track.playbackHeadPosition.toLong().coerceAtLeast(0L)
                        val beat = ScoreTempos.beatAtSeconds(
                            safeTempos,
                            startSeconds + playedFrames.toDouble() / sampleRate.toDouble(),
                        )
                        ScoreTransportBus.progress(beat.coerceAtMost(throughBeat))
                        Thread.sleep(16L)
                    }
                    if (myGeneration == generation) {
                        ScoreTransportBus.finish(throughBeat)
                        mainHandler.post(onFinished)
                    }
                } else if (myGeneration == generation) {
                    ScoreTransportBus.stop()
                    mainHandler.post(onFinished)
                }
            } catch (_: InterruptedException) {
                // Stop/restart invalidates the generation and cleanup happens below.
            } catch (_: IllegalStateException) {
                if (myGeneration == generation) {
                    ScoreTransportBus.stop()
                    mainHandler.post(onFinished)
                }
            } finally {
                soundFont.finishStreamingTracks()
                streamTrack?.let(::releaseTrack)
                if (activeTrack === streamTrack) activeTrack = null
            }
        }
    }

    private fun buildStreamingMidiEvents(
        tracks: List<ScoreTrack>,
        startBeat: Float,
        throughBeat: Float,
        tempoChanges: List<ScoreTempoChange>,
    ): List<StreamingMidiEvent> = buildList {
        val startSeconds = ScoreTempos.secondsAtBeat(tempoChanges, startBeat)
        tracks.forEachIndexed trackLoop@{ channel, track ->
            val notes = track.notes
            notes.forEachIndexed noteLoop@{ index, note ->
                if (ScoreTies.isContinuation(notes, index)) return@noteLoop
                val playbackEndBeat = if (ScoreTies.hasValidTie(notes, index)) {
                    ScoreTies.chainEndBeat(notes, index)
                } else {
                    ScoreArticulations.playbackEndBeat(notes, index)
                }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)

                if (playbackEndBeat <= startBeat || note.startBeat >= throughBeat) return@noteLoop
                val onBeat = maxOf(note.startBeat, startBeat)
                val offBeat = minOf(playbackEndBeat, throughBeat)
                val onFrame = (
                    ((ScoreTempos.secondsAtBeat(tempoChanges, onBeat) - startSeconds) * sampleRate)
                    ).toInt().coerceAtLeast(0)
                val offFrame = (
                    ((ScoreTempos.secondsAtBeat(tempoChanges, offBeat) - startSeconds) * sampleRate)
                    ).toInt().coerceAtLeast(onFrame + 1)
                add(
                    StreamingMidiEvent(
                        frame = onFrame,
                        noteOn = true,
                        key = note.midiPitch,
                        velocity = ScoreArticulations.playbackVelocity(note),
                        channel = channel.coerceIn(0, 15),
                    )
                )
                add(
                    StreamingMidiEvent(
                        frame = offFrame,
                        noteOn = false,
                        key = note.midiPitch,
                        velocity = 0,
                        channel = channel.coerceIn(0, 15),
                    )
                )
            }
        }
    }.sortedWith(
        compareBy<StreamingMidiEvent> { it.frame }
            .thenBy { if (it.noteOn) 1 else 0 }
            .thenBy { it.channel }
            .thenBy { it.key }
    )

    private fun buildStreamingClicks(
        timeSignatures: List<ScoreTimeSignature>,
        startBeat: Float,
        throughBeat: Float,
        tempoChanges: List<ScoreTempoChange>,
    ): List<StreamingClick> {
        val startSeconds = ScoreTempos.secondsAtBeat(tempoChanges, startBeat)
        return ScoreMetronome.clicks(timeSignatures, throughBeat)
        .asSequence()
        .filter { it.beat + 0.001f >= startBeat }
        .map { click ->
            val (frequency, gain) = when (click.accent) {
                MetronomeAccent.DOWNBEAT -> 1_600.0 to 0.34f
                MetronomeAccent.GROUP -> 1_250.0 to 0.26f
                MetronomeAccent.BEAT -> 950.0 to 0.18f
            }
            StreamingClick(
                frame = (
                    ((ScoreTempos.secondsAtBeat(tempoChanges, click.beat) - startSeconds) * sampleRate)
                    ).toInt(),
                frequency = frequency,
                gain = gain,
            )
        }
        .toList()
    }

    private fun mixStreamingMetronome(
        pcm: ShortArray,
        chunkStartFrame: Int,
        clicks: List<StreamingClick>,
    ) {
        val chunkFrames = pcm.size / 2
        if (chunkFrames <= 0) return
        val chunkEndFrame = chunkStartFrame + chunkFrames
        val clickFrames = (sampleRate * 0.032f).toInt().coerceAtLeast(1)
        clicks.forEach { click ->
            val clickEnd = click.frame + clickFrames
            if (click.frame >= chunkEndFrame || clickEnd <= chunkStartFrame) return@forEach
            val overlapStart = maxOf(chunkStartFrame, click.frame)
            val overlapEnd = minOf(chunkEndFrame, clickEnd)
            for (absoluteFrame in overlapStart until overlapEnd) {
                val clickIndex = absoluteFrame - click.frame
                val t = clickIndex.toDouble() / sampleRate
                val progress = clickIndex.toFloat() / clickFrames
                val envelope = (1f - progress).coerceIn(0f, 1f).let { it * it }
                val clickSample = (
                    sin(2.0 * PI * click.frequency * t) *
                        envelope * click.gain * Short.MAX_VALUE
                    ).toInt()
                val localFrame = absoluteFrame - chunkStartFrame
                repeat(2) { channel ->
                    val sampleIndex = localFrame * 2 + channel
                    pcm[sampleIndex] = (pcm[sampleIndex].toInt() + clickSample)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }
        }
    }

    private fun createStreamingTrack(): AudioTrack {
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val minBytes = AudioTrack.getMinBufferSize(
            sampleRate,
            channelMask,
            AudioFormat.ENCODING_PCM_16BIT,
        ).coerceAtLeast(4096)
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(minBytes * 2)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
    }

    private fun writeStreamingBlock(track: AudioTrack, pcm: ShortArray): Boolean {
        var offset = 0
        while (offset < pcm.size) {
            val written = track.write(
                pcm,
                offset,
                pcm.size - offset,
                AudioTrack.WRITE_BLOCKING,
            )
            if (written <= 0) return false
            offset += written
        }
        return true
    }

    private fun mixMetronome(
        rendered: RenderedAudio,
        tempoChanges: List<ScoreTempoChange>,
        throughBeat: Float,
        timeSignatures: List<ScoreTimeSignature>,
    ): RenderedAudio {
        if (rendered.pcm.isEmpty()) return rendered

        val mixed = rendered.pcm.copyOf()
        val channels = rendered.channels.coerceAtLeast(1)
        val totalFrames = mixed.size / channels
        val safeTempos = ScoreTempos.normalize(tempoChanges)
        val clickFrames = (sampleRate * 0.032f).toInt().coerceAtLeast(1)

        ScoreMetronome.clicks(timeSignatures, throughBeat).forEach { click ->
            val startFrame = (ScoreTempos.secondsAtBeat(safeTempos, click.beat) * sampleRate).toInt()
            val (frequency, gain) = when (click.accent) {
                MetronomeAccent.DOWNBEAT -> 1_600.0 to 0.34f
                MetronomeAccent.GROUP -> 1_250.0 to 0.26f
                MetronomeAccent.BEAT -> 950.0 to 0.18f
            }

            for (i in 0 until clickFrames) {
                val frame = startFrame + i
                if (frame !in 0 until totalFrames) break
                val t = i.toDouble() / sampleRate
                val progress = i.toFloat() / clickFrames
                val envelope = (1f - progress).coerceIn(0f, 1f).let { it * it }
                val clickSample = (
                    sin(2.0 * PI * frequency * t) *
                        envelope *
                        gain *
                        Short.MAX_VALUE
                    ).toInt()

                repeat(channels) { channel ->
                    val sampleIndex = frame * channels + channel
                    mixed[sampleIndex] = (mixed[sampleIndex].toInt() + clickSample)
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort()
                }
            }
        }

        return RenderedAudio(mixed, rendered.channels)
    }

    private fun createStaticTrack(pcm: ShortArray, channels: Int): AudioTrack =
        AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(
                        if (channels == 2) AudioFormat.CHANNEL_OUT_STEREO else AudioFormat.CHANNEL_OUT_MONO
                    )
                    .build()
            )
            .setBufferSizeInBytes(pcm.size * 2)
            .setTransferMode(AudioTrack.MODE_STATIC)
            .build()

    private fun releaseTrack(track: AudioTrack) {
        try {
            if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.stop()
        } catch (_: IllegalStateException) {
            // Already stopped/released.
        }
        try {
            track.release()
        } catch (_: Exception) {
            // Best-effort during rapid transport changes.
        }
    }

    private fun renderFallbackTracks(
        tracks: List<ScoreTrack>,
        bpm: Int,
        tempoChanges: List<ScoreTempoChange> = listOf(ScoreTempoChange(0f, bpm)),
        tailSeconds: Float = 0.35f,
        throughBeat: Float = ScoreTracks.endBeat(tracks),
    ): ShortArray {
        val audible = ScoreTracks.audibleTracks(tracks).filter { it.notes.isNotEmpty() }
        if (audible.isEmpty()) return ShortArray(0)

        val safeTempos = ScoreTempos.normalize(tempoChanges)
        val notesEndBeat = audible.maxOfOrNull { ScoreTimeline.endBeat(it.notes) } ?: 0f
        val endBeat = maxOf(notesEndBeat, throughBeat.coerceAtLeast(0f))
        val totalSeconds = ScoreTempos.secondsAtBeat(safeTempos, endBeat).toFloat() + tailSeconds
        val totalFrames = (totalSeconds * sampleRate).toInt().coerceAtLeast(1)
        val left = FloatArray(totalFrames)
        val right = FloatArray(totalFrames)

        audible.forEach { track ->
            val volumeGain = track.volume.coerceIn(ScoreTrack.MIN_VOLUME, ScoreTrack.MAX_VOLUME) / 127f
            val panNorm = track.pan.coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN) / 64f
            val panAngle = ((panNorm + 1f) * (PI.toFloat() / 4f)).coerceIn(0f, PI.toFloat() / 2f)
            val leftGain = cos(panAngle) * volumeGain
            val rightGain = sin(panAngle) * volumeGain
            val notes = track.notes

            notes.forEachIndexed { index, note ->
                if (ScoreTies.isContinuation(notes, index)) return@forEachIndexed
                val end = if (ScoreTies.hasValidTie(notes, index)) {
                    ScoreTies.chainEndBeat(notes, index)
                } else {
                    ScoreArticulations.playbackEndBeat(notes, index)
                }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)
                renderFallbackNote(
                    note = note,
                    startSeconds = ScoreTempos.secondsAtBeat(safeTempos, note.startBeat).toFloat(),
                    noteSeconds = ScoreTempos.durationSeconds(safeTempos, note.startBeat, end).toFloat(),
                    left = left,
                    right = right,
                    leftGain = leftGain,
                    rightGain = rightGain,
                )
            }
        }

        var peak = 0f
        for (i in left.indices) peak = maxOf(peak, kotlin.math.abs(left[i]), kotlin.math.abs(right[i]))
        val normalization = if (peak > 0.92f) 0.92f / peak else 1f

        return ShortArray(totalFrames * 2) { sampleIndex ->
            val frame = sampleIndex / 2
            val source = if (sampleIndex % 2 == 0) left[frame] else right[frame]
            val sample = (source * normalization).coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }

    private fun renderFallbackNote(
        note: ScoreNote,
        startSeconds: Float,
        noteSeconds: Float,
        left: FloatArray,
        right: FloatArray,
        leftGain: Float,
        rightGain: Float,
    ) {
        val startSample = (startSeconds.coerceAtLeast(0f) * sampleRate).toInt()
        val safeNoteSeconds = noteSeconds.coerceAtLeast(0.001f)
        val noteSamples = (safeNoteSeconds * sampleRate).toInt().coerceAtLeast(1)
        val frequency = 440.0 * Math.pow(2.0, (note.midiPitch - 69) / 12.0)
        val velocityGain = ScoreArticulations.playbackVelocity(note) / 127f

        for (i in 0 until noteSamples) {
            val target = startSample + i
            if (target !in left.indices) break

            val t = i.toFloat() / sampleRate
            val remaining = (noteSamples - i).toFloat() / sampleRate
            val attack = (t / 0.012f).coerceIn(0f, 1f)
            val release = (remaining / 0.07f).coerceIn(0f, 1f)
            val decay = exp((-1.8f * t).toDouble()).toFloat()
            val phase = 2.0 * PI * frequency * t

            val timbre =
                sin(phase).toFloat() * 0.72f +
                    sin(phase * 2.0).toFloat() * 0.20f +
                    sin(phase * 3.0).toFloat() * 0.08f
            val voice = timbre * attack * release * decay * velocityGain * 0.55f
            left[target] += voice * leftGain
            right[target] += voice * rightGain
        }
    }

    private fun renderFallbackScore(
        notes: List<ScoreNote>,
        bpm: Int,
        tailSeconds: Float = 0.35f,
        throughBeat: Float = ScoreTimeline.endBeat(notes),
    ): ShortArray {
        if (notes.isEmpty()) return ShortArray(0)

        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)
        val endBeat = maxOf(ScoreTimeline.endBeat(notes), throughBeat.coerceAtLeast(0f))
        val totalSeconds = endBeat * secondsPerBeat + tailSeconds
        val totalSamples = (totalSeconds * sampleRate).toInt().coerceAtLeast(1)
        val mix = FloatArray(totalSamples)

        notes.forEachIndexed { index, note ->
            if (ScoreTies.isContinuation(notes, index)) return@forEachIndexed
            val chainEnd = if (ScoreTies.hasValidTie(notes, index)) {
                ScoreTies.chainEndBeat(notes, index)
            } else {
                ScoreArticulations.playbackEndBeat(notes, index)
            }.takeIf { it > note.startBeat } ?: (note.startBeat + note.effectiveBeats)
            val startSample = (note.startBeat * secondsPerBeat * sampleRate).toInt()
            val noteSeconds = (chainEnd - note.startBeat) * secondsPerBeat
            val noteSamples = (noteSeconds * sampleRate).toInt().coerceAtLeast(1)
            val frequency = 440.0 * Math.pow(2.0, (note.midiPitch - 69) / 12.0)
            val velocityGain = ScoreArticulations.playbackVelocity(note) / 127f

            for (i in 0 until noteSamples) {
                val target = startSample + i
                if (target !in mix.indices) break
                val t = i.toFloat() / sampleRate
                val remaining = (noteSamples - i).toFloat() / sampleRate
                val attack = (t / 0.012f).coerceIn(0f, 1f)
                val release = (remaining / 0.07f).coerceIn(0f, 1f)
                val decay = exp((-1.8f * t).toDouble()).toFloat()
                val phase = 2.0 * PI * frequency * t
                val timbre =
                    sin(phase).toFloat() * 0.72f +
                        sin(phase * 2.0).toFloat() * 0.20f +
                        sin(phase * 3.0).toFloat() * 0.08f
                mix[target] += timbre * attack * release * decay * velocityGain * 0.55f
            }
        }

        var peak = 0f
        for (sample in mix) peak = maxOf(peak, kotlin.math.abs(sample))
        val normalization = if (peak > 0.92f) 0.92f / peak else 1f

        return ShortArray(mix.size) { index ->
            val sample = (mix[index] * normalization).coerceIn(-1f, 1f)
            (sample * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
