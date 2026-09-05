from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if old not in text:
        raise SystemExit(f"Pattern not found in {path}: {old[:120]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"Pattern occurs {text.count(old)} times in {path}: {old[:120]!r}")
    p.write_text(text.replace(old, new, 1))


# Version bump.
replace_once(
    "app/build.gradle.kts",
    '        versionCode = 35\n        versionName = "0.2.32"',
    '        versionCode = 36\n        versionName = "0.2.33"',
)

# Large-score playback: do the event/click preparation on the playback worker instead of the UI thread.
playback = Path("app/src/main/java/com/scoreforge/app/audio/ScorePlaybackEngine.kt")
text = playback.read_text()
old = '''        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)\n        val events = buildStreamingMidiEvents(\n            tracks = playableTracks,\n            startBeat = startBeat,\n            throughBeat = throughBeat,\n            secondsPerBeat = secondsPerBeat,\n        )\n        val clicks = if (metronomeEnabled) {\n            buildStreamingClicks(\n                timeSignatures = timeSignatures,\n                startBeat = startBeat,\n                throughBeat = throughBeat,\n                secondsPerBeat = secondsPerBeat,\n            )\n        } else {\n            emptyList()\n        }\n\n        thread(name = "ScoreForgePlaybackStream", isDaemon = true) {\n            var streamTrack: AudioTrack? = null\n            try {\n                if (myGeneration != generation) return@thread\n'''
new = '''        val secondsPerBeat = 60f / bpm.coerceIn(30, 300)\n\n        thread(name = "ScoreForgePlaybackStream", isDaemon = true) {\n            var streamTrack: AudioTrack? = null\n            try {\n                if (myGeneration != generation) return@thread\n                val events = buildStreamingMidiEvents(\n                    tracks = playableTracks,\n                    startBeat = startBeat,\n                    throughBeat = throughBeat,\n                    secondsPerBeat = secondsPerBeat,\n                )\n                val clicks = if (metronomeEnabled) {\n                    buildStreamingClicks(\n                        timeSignatures = timeSignatures,\n                        startBeat = startBeat,\n                        throughBeat = throughBeat,\n                        secondsPerBeat = secondsPerBeat,\n                    )\n                } else {\n                    emptyList()\n                }\n                if (myGeneration != generation) return@thread\n'''
if old not in text:
    raise SystemExit("Streaming preparation pattern not found")
playback.write_text(text.replace(old, new, 1))

# Switching the selected/editing track should not stop ordinary score playback.
composer = Path("app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt")
text = composer.read_text()
old = '''    fun selectTrack(index: Int) {\n        if (index !in tracks.indices || index == activeIndex()) return\n        stopPlayback()\n        stopLiveRecording()\n        cancelNaturalEntryGroup()\n        LiveInstrumentBus.allNotesOff()\n'''
new = '''    fun selectTrack(index: Int) {\n        if (index !in tracks.indices || index == activeIndex()) return\n        if (liveRecordingActive) stopLiveRecording()\n        cancelNaturalEntryGroup()\n        LiveInstrumentBus.allNotesOff()\n'''
if old not in text:
    raise SystemExit("selectTrack pattern not found")
text = text.replace(old, new, 1)
old_call = '''                SoundFontControls(\n                    engine = soundFontEngine,\n                    requestedPresetBank = activeTrack.presetBank,\n'''
new_call = '''                SoundFontControls(\n                    engine = soundFontEngine,\n                    playbackActive = isPlaying,\n                    requestedPresetBank = activeTrack.presetBank,\n'''
if old_call not in text:
    raise SystemExit("SoundFontControls call pattern not found")
composer.write_text(text.replace(old_call, new_call, 1))

# Keep track selection UI/live keyboard in sync during playback without reprogramming the synth that is streaming the song.
sfc = Path("app/src/main/java/com/scoreforge/app/ui/SoundFontControls.kt")
text = sfc.read_text()
old = '''fun SoundFontControls(\n    engine: SoundFontEngine?,\n    requestedPresetBank: Int? = null,\n'''
new = '''fun SoundFontControls(\n    engine: SoundFontEngine?,\n    playbackActive: Boolean = false,\n    requestedPresetBank: Int? = null,\n'''
if old not in text:
    raise SystemExit("SoundFontControls signature pattern not found")
text = text.replace(old, new, 1)
old = '''        presets,\n        requestedPresetBank,\n        requestedPresetProgram,\n    ) {\n        if (engine == null || presets.isEmpty()) return@LaunchedEffect\n        val bank = requestedPresetBank ?: return@LaunchedEffect\n        val program = requestedPresetProgram ?: return@LaunchedEffect\n        val requestedIndex = presets.indexOfFirst { it.bank == bank && it.program == program }\n        if (requestedIndex < 0 || requestedIndex == presetIndex) return@LaunchedEffect\n\n        if (engine.selectPresetAt(requestedIndex)) {\n            presetIndex = requestedIndex\n            presetMenuExpanded = false\n            val selected = presets[requestedIndex]\n            currentSoundFont?.let {\n                SoundFontRepository.saveActiveSelection(context, it, selected)\n            }\n            LiveInstrumentBus.selectPreset(selected)\n            status = "${presets.size} instruments ready"\n        }\n    }\n'''
new = '''        presets,\n        requestedPresetBank,\n        requestedPresetProgram,\n        playbackActive,\n    ) {\n        if (engine == null || presets.isEmpty()) return@LaunchedEffect\n        val bank = requestedPresetBank ?: return@LaunchedEffect\n        val program = requestedPresetProgram ?: return@LaunchedEffect\n        val requestedIndex = presets.indexOfFirst { it.bank == bank && it.program == program }\n        if (requestedIndex < 0) return@LaunchedEffect\n\n        // The playback engine and editor share this SoundFontEngine. During score playback,\n        // changing the selected track may update the live keyboard/preset display, but must not\n        // reprogram a channel underneath the streaming song. When playback ends this effect runs\n        // again and synchronizes the engine to the selected track.\n        if (!playbackActive && !engine.selectPresetAt(requestedIndex)) return@LaunchedEffect\n\n        presetIndex = requestedIndex\n        presetMenuExpanded = false\n        val selected = presets[requestedIndex]\n        currentSoundFont?.let {\n            SoundFontRepository.saveActiveSelection(context, it, selected)\n        }\n        LiveInstrumentBus.selectPreset(selected)\n        status = "${presets.size} instruments ready"\n    }\n'''
if old not in text:
    raise SystemExit("SoundFontControls sync effect pattern not found")
sfc.write_text(text.replace(old, new, 1))

# MIDI importer: when a file has more source-track/channel groups than the editor limit, combine
# groups that already share the same MIDI channel instead of dropping later groups. Also begin
# imported editing at beat zero and map General MIDI channel 10 to the SF2 percussion bank.
midi = Path("app/src/main/java/com/scoreforge/app/music/MidiImport.kt")
text = midi.read_text()
parsed_marker = '''    private data class ParsedMidi(\n        val ticksPerQuarter: Int,\n        val notes: List<RawNote>,\n        val states: Map<Pair<Int, Int>, TrackChannelState>,\n        val tempoEvents: List<TempoEvent>,\n        val timeSignatureEvents: List<TimeSignatureEvent>,\n        val keySignatureEvents: List<KeySignatureEvent>,\n        val sourceTrackNames: Map<Int, String>,\n        val warnings: MutableList<String>,\n    )\n\n'''
parsed_new = parsed_marker + '''    private data class ImportGroup(\n        val sourceTracks: List<Int>,\n        val channel: Int,\n        val notes: List<RawNote>,\n    )\n\n'''
if parsed_marker not in text:
    raise SystemExit("ParsedMidi marker not found")
text = text.replace(parsed_marker, parsed_new, 1)
start = text.index('        val grouped = parsed.notes.groupBy { it.sourceTrack to it.channel }')
end = text.index('\n\n        require(tracks.isNotEmpty())', start)
old_block = text[start:end]
new_block = '''        val sourceGroups = parsed.notes\n            .groupBy { it.sourceTrack to it.channel }\n            .entries\n            .sortedWith(compareBy({ it.key.first }, { it.key.second }))\n            .map { (key, rawNotes) ->\n                ImportGroup(\n                    sourceTracks = listOf(key.first),\n                    channel = key.second,\n                    notes = rawNotes,\n                )\n            }\n\n        val grouped = if (sourceGroups.size <= ScoreTracks.MAX_TRACKS) {\n            sourceGroups\n        } else {\n            val byChannel = parsed.notes\n                .groupBy { it.channel }\n                .entries\n                .sortedBy { it.key }\n                .map { (channel, rawNotes) ->\n                    ImportGroup(\n                        sourceTracks = rawNotes.map { it.sourceTrack }.distinct().sorted(),\n                        channel = channel,\n                        notes = rawNotes,\n                    )\n                }\n            warnings +=\n                "${sourceGroups.size} MIDI track/channel groups shared ${byChannel.size} MIDI channels; " +\n                    "groups on the same channel were combined so no note tracks were dropped."\n            byChannel\n        }\n\n        val sourceTrackGroupCounts = sourceGroups\n            .flatMap { it.sourceTracks }\n            .groupingBy { it }\n            .eachCount()\n\n        fun stateFor(group: ImportGroup): TrackChannelState {\n            val candidates = group.sourceTracks\n                .mapNotNull { parsed.states[it to group.channel] }\n            if (candidates.isEmpty()) return TrackChannelState()\n\n            val programs = candidates.mapNotNull { it.program }.distinct()\n            val banks = candidates.map { it.bankMsb to it.bankLsb }.distinct()\n            if (programs.size > 1) {\n                warnings +=\n                    "MIDI channel ${group.channel + 1} used multiple programs across source tracks; " +\n                        "program ${programs.first() + 1} was used."\n            }\n            if (banks.size > 1) {\n                warnings +=\n                    "MIDI channel ${group.channel + 1} used multiple bank selections across source tracks; " +\n                        "the first bank was used."\n            }\n\n            val bank = banks.firstOrNull() ?: (0 to 0)\n            return TrackChannelState(\n                bankMsb = bank.first,\n                bankLsb = bank.second,\n                program = programs.firstOrNull(),\n                volume = candidates.mapNotNull { it.volume }.firstOrNull(),\n                pan = candidates.mapNotNull { it.pan }.firstOrNull(),\n            )\n        }\n\n        var quantizedCount = 0\n        val tracks = grouped.take(ScoreTracks.MAX_TRACKS).mapIndexedNotNull { index, group ->\n            val sourceTrack = group.sourceTracks.first()\n            val channel = group.channel\n            val state = stateFor(group)\n            val events = group.notes\n                .sortedWith(compareBy<RawNote> { it.startTick }.thenBy { it.pitch }.thenBy { it.endTick })\n                .map { raw ->\n                    val rawStart = raw.startTick.toFloat() / parsed.ticksPerQuarter.toFloat()\n                    val rawLength = ((raw.endTick - raw.startTick).coerceAtLeast(1L)).toFloat() /\n                        parsed.ticksPerQuarter.toFloat()\n                    val startBeat = ScoreTimeline.quantizeBeat(rawStart)\n                    val written = nearestWrittenDuration(rawLength)\n                    if (abs(startBeat - rawStart) > 0.001f || abs(written.beats - rawLength) > 0.001f) {\n                        quantizedCount += 1\n                    }\n                    ScoreNote(\n                        midiPitch = raw.pitch.coerceIn(0, 127),\n                        duration = written.duration,\n                        startBeat = startBeat,\n                        velocity = raw.velocity.coerceIn(1, 127),\n                        dotted = written.dotted,\n                    )\n                }\n\n            if (events.isEmpty()) return@mapIndexedNotNull null\n            val sourceNames = group.sourceTracks\n                .mapNotNull { parsed.sourceTrackNames[it]?.trim()?.takeIf(String::isNotBlank) }\n                .distinct()\n            val baseName = when {\n                channel == 9 && group.sourceTracks.size > 1 -> "Drums"\n                sourceNames.size == 1 -> sourceNames.single()\n                sourceNames.size in 2..3 -> sourceNames.joinToString(" + ")\n                sourceNames.isNotEmpty() -> "MIDI Ch ${channel + 1} (${sourceNames.size} tracks)"\n                group.sourceTracks.size == 1 -> "MIDI Track ${sourceTrack + 1}"\n                else -> "MIDI Ch ${channel + 1}"\n            }\n            val channelSuffix = if (\n                group.sourceTracks.size == 1 &&\n                (sourceTrackGroupCounts[sourceTrack] ?: 0) > 1\n            ) {\n                " Ch ${channel + 1}"\n            } else {\n                ""\n            }\n            val trackName = (baseName + channelSuffix)\n                .replace('\\t', ' ')\n                .replace('\\n', ' ')\n                .take(80)\n\n            val explicitBank = ((state.bankMsb and 0x7F) shl 7) or (state.bankLsb and 0x7F)\n            val bank = if (channel == 9 && explicitBank == 0) 128 else explicitBank\n            val program = state.program ?: if (channel == 9) 0 else null\n            ScoreTrack(\n                id = index + 1,\n                name = trackName,\n                events = events,\n                cursorBeat = 0f,\n                presetBank = if (program != null || bank != 0) bank else null,\n                presetProgram = program,\n                volume = state.volume ?: ScoreTrack.DEFAULT_VOLUME,\n                pan = state.pan?.let { (it - 64).coerceIn(ScoreTrack.MIN_PAN, ScoreTrack.MAX_PAN) }\n                    ?: ScoreTrack.CENTER_PAN,\n            ).normalized()\n        }'''
text = text[:start] + new_block + text[end:]
midi.write_text(text)

# Extend importer regression tests with >16 source groups sharing MIDI channels and verify import starts at zero.
test = Path("app/src/test/java/com/scoreforge/app/music/MidiImporterTest.kt")
text = test.read_text()
anchor = '''        val importedTrack = result.snapshot.tracks.single()\n        assertEquals("Piano", importedTrack.name)\n        assertEquals(5, importedTrack.presetProgram)\n'''
replacement = '''        val importedTrack = result.snapshot.tracks.single()\n        assertEquals("Piano", importedTrack.name)\n        assertEquals(0f, importedTrack.cursorBeat, 0.0001f)\n        assertEquals(0f, result.snapshot.cursorBeat, 0.0001f)\n        assertEquals(5, importedTrack.presetProgram)\n'''
if anchor not in text:
    raise SystemExit("MidiImporterTest anchor not found")
text = text.replace(anchor, replacement, 1)
insert_before = '''    @Test\n    fun reportsTempoChangesAndUsesFirstTempo() {\n'''
new_test = '''    @Test\n    fun combinesSourceGroupsSharingChannelsInsteadOfDroppingNotes() {\n        val noteTracks = (0 until 17).map { index ->\n            val channel = if (index < 16) index else 9\n            val pitch = 48 + (index % 12)\n            track(\n                bytes(\n                    0x00, 0x90 or channel, pitch, 90,\n                ) + varLen(120) + bytes(\n                    0x80 or channel, pitch, 0,\n                    0x00, 0xFF, 0x2F, 0x00,\n                )\n            )\n        }\n        val midi = midiFile(ticksPerQuarter = 480, tracks = noteTracks)\n\n        val result = MidiImporter.import(midi)\n\n        assertEquals(16, result.importedTrackCount)\n        assertEquals(17, result.importedNoteCount)\n        assertTrue(result.warnings.any { it.contains("combined so no note tracks were dropped") })\n        assertFalse(result.warnings.any { it.contains("Only the first") })\n        val percussion = result.snapshot.tracks[9]\n        assertEquals(128, percussion.presetBank)\n        assertEquals(0, percussion.presetProgram)\n        assertEquals(2, percussion.notes.size)\n        assertTrue(result.snapshot.tracks.all { it.cursorBeat == 0f })\n    }\n\n'''
if insert_before not in text:
    raise SystemExit("MidiImporterTest insertion point not found")
test.write_text(text.replace(insert_before, new_test + insert_before, 1))
