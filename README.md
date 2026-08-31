# Score Forge

Score Forge is an Android-first, touch-focused music composition app built around traditional staff notation, virtual instruments, MIDI, SoundFonts, synths, samples, and full song arrangement.

## Current foundation

- Kotlin + Jetpack Compose Android app
- Landscape-first touch interface
- Traditional five-line staff editor prototype
- Whole, half, quarter, eighth, and sixteenth note entry
- Tap the staff to place notes
- Drag existing staff notes vertically to change pitch
- Two-octave touchscreen piano for step entry at any speed
- Undo and clear controls

## Planned architecture

- **Compose UI:** staff editor, piano, piano roll, mixer, track arrangement, instrument browser
- **Score/sequencer core:** measures, tempo, time signatures, note events, playback timeline, project files
- **Instrument engine:** SoundFont support through FluidSynth, synth voices, sampled instruments, one-shot sound effects
- **Android audio:** low-latency native audio path, with Oboe planned for performance-sensitive playback
- **MIDI:** touchscreen entry first, external USB/Bluetooth MIDI later

## Near-term roadmap

1. Establish a reliable Android CI build and downloadable debug APK.
2. Expand staff editing: horizontal drag/reordering, rests, accidentals, dotted notes, ties, measures, clefs, key/time signatures.
3. Add real-time piano input and multitouch chords alongside step entry.
4. Add track management and a basic sequencer/playhead.
5. Integrate real instrument playback and SoundFont importing.
6. Add piano-roll editing, mixer controls, synths, samples, and arbitrary sound-effect tracks.

No third-party SoundFonts or commercial samples are bundled in the repository.
