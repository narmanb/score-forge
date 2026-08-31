# Score Forge

Score Forge is an Android-first, touch-focused music composition app built around traditional staff notation, a piano roll, multitouch performance, multi-track arrangement, SoundFonts, and project-based song editing.

## Current version

**0.2.2**

## Current features

### Composition and notation

- Traditional five-line staff editor
- Piano-roll editor using the same underlying note data as the staff
- Whole, half, quarter, eighth, and sixteenth notes and rests
- Dotted notes and dotted rests with correct 1.5× timing, playback, staff rendering, piano-roll length, and project persistence
- Tied-note data/playback and editor support
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
- Release APKs include the compact FluidR3Mono General MIDI starter bank, providing a broad instrument collection immediately after installation
- Starter instruments load automatically on a fresh install; no SoundFont knowledge or setup is required
- Compact **Library** menu keeps advanced SoundFont management out of the main composition workflow
- Import additional `.sf2` and `.sf3` SoundFonts from **Library → Import SoundFont…**
- Switch back to the bundled bank with **Library → Use Starter Instruments**
- Enumerates the presets actually present in the active SoundFont
- Direct preset picker with arbitrary SoundFont banks/programs
- Separate live SoundFont engine for touchscreen performance
- Separate score-rendering engine for arrangement playback
- Built-in synthesized fallback voice if the native SoundFont path is unavailable
- Stereo multi-track playback

### Projects and persistence

- Automatic draft saving/restoration
- Persistent active SoundFont and selected preset
- Project names
- New Project
- Save As `.sfp`
- Open `.sfp`
- Backward-compatible migration from the original single-track project format
- Project files store tracks, score events, tempo, editor settings, dotted input/events, instruments, volume, pan, mute, and solo state

### Android

- Kotlin + Jetpack Compose interface
- Native C++ audio bridge
- Landscape-first touch layout with both landscape rotations supported
- Vertically scrollable composer and horizontally scrollable dense control rows
- Android safe-area/system-bar handling
- Android API 26+ minimum
- Automated GitHub Actions unit-test and APK builds
- Stable development signing certificate for upgradeable test APKs

## Architecture

- **Compose UI:** staff, piano roll, touchscreen piano, track controls, mixer, project controls, compact sound library controls
- **Music model:** timed score events, tracks, rests, notation state, quantized timeline, project codec
- **Audio:** FluidSynth for SoundFont instruments plus a built-in fallback synthesizer
- **Native bridge:** C++/JNI for FluidSynth access
- **Persistence:** versioned `.sfp` project format plus automatic draft storage

## Bundled starter instruments

Release APKs fetch and package `FluidR3Mono_GM.sf3` during the reproducible CI build. The build verifies the downloaded file against the expected upstream Git blob ID before packaging it. The large binary is intentionally not committed to this repository.

FluidR3Mono is distributed under the MIT License. Required copyright and attribution information is preserved in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Next priorities

- Additional accidentals and proper notation spelling
- Clefs, key signatures, and configurable time signatures
- Better long-score scrolling/zooming
- Note velocity editing
- External USB/Bluetooth MIDI input
- Sample/one-shot tracks, drum pads, and additional synthesized instruments
- Export options such as MIDI and rendered audio
