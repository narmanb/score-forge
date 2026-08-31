# Score Forge

Score Forge is an Android-first, touch-focused music composition app built around traditional staff notation, a piano roll, multitouch performance, multi-track arrangement, SoundFonts, and project-based song editing.

## Current version

**0.2.0**

## Current features

### Composition and notation

- Traditional five-line staff editor
- Piano-roll editor using the same underlying note data as the staff
- Whole, half, quarter, eighth, and sixteenth notes and rests
- Dotted notes and dotted rests with correct 1.5× timing, playback, staff rendering, piano-roll length, and project persistence
- Measure/barline display in 4/4
- Tap-to-place notation with 1/16-note timing quantization
- Drag notes horizontally to change time and vertically to change pitch
- Drag rests horizontally in time
- Long-press individual notes or rests to delete them
- Sharp accidental entry and rendering
- Chord step-entry mode
- 100-step Undo/Redo history

### Touch input

- Multitouch on-screen piano
- Independent note-on/note-off for held fingers and chords
- Piano octave shifting
- Optional Show/Hide Piano control for more editing space on phones
- Staff and piano entry preview through the active instrument

### Tracks and mixing

- Up to 16 tracks
- Rename, mute, solo, and delete tracks
- Per-track instrument preset
- Per-track volume and stereo pan
- Mixer settings affect both SoundFont and fallback playback
- Solo routing isolates soloed tracks during playback

### Audio and instruments

- Native FluidSynth integration through C++/JNI
- Import `.sf2` and `.sf3` SoundFonts
- Enumerates the presets actually present in imported SoundFonts
- Direct preset picker with arbitrary SoundFont banks/programs
- Separate live SoundFont engine for touchscreen performance
- Separate score-rendering engine for arrangement playback
- Built-in synthesized fallback voice when no SoundFont is loaded
- Stereo multi-track playback

### Projects and persistence

- Automatic draft saving/restoration
- Persistent imported SoundFont and selected preset
- Project names
- New Project
- Save As `.sfp`
- Open `.sfp`
- Backward-compatible migration from the original single-track project format
- Project files store tracks, score events, tempo, editor settings, dotted input/events, instruments, volume, pan, mute, and solo state

### Android

- Kotlin + Jetpack Compose interface
- Native C++ audio bridge
- Landscape-first touch layout
- Android API 26+ minimum
- Automated GitHub Actions unit-test and APK builds

## Architecture

- **Compose UI:** staff, piano roll, touchscreen piano, track controls, mixer, project controls, SoundFont browser
- **Music model:** timed score events, tracks, rests, notation state, quantized timeline, project codec
- **Audio:** FluidSynth for SoundFont instruments plus a built-in fallback synthesizer
- **Native bridge:** C++/JNI for FluidSynth access
- **Persistence:** versioned `.sfp` project format plus automatic draft storage

## Next priorities

- Ties and slurs
- Additional accidentals and notation spelling
- Clefs, key signatures, and configurable time signatures
- Better long-score scrolling/zooming
- Note velocity editing
- External USB/Bluetooth MIDI input
- Sample/one-shot tracks, drum pads, and additional synthesized instruments
- Export options such as MIDI and rendered audio

No third-party SoundFonts or commercial samples are bundled in the repository. Users can import their own compatible SoundFont files.
