# Score Forge 0.2.45 — WAV export phone test

Do not merge this branch until the APK is verified on a real Android device.

## Core export

1. Open or create a project with at least two audible notes.
2. Tap **Export WAV**.
3. Confirm a progress dialog appears while Score Forge renders the audio privately.
4. Confirm the Android save-location picker opens only after rendering reaches 100%.
5. Save with the suggested `.wav` filename.
6. Confirm the export finishes without freezing Score Forge.
7. Open the WAV in an ordinary Android audio player and confirm it plays.

The final destination file should not sit in Downloads or another public folder as a partially rendered song. Score Forge renders to app-private cache first, then copies the completed WAV after the user chooses the final destination.

## Musical fidelity

Use a project with more than one track if possible.

- Confirm the expected instruments/SoundFont are heard.
- Confirm muted tracks are absent.
- Confirm solo behaves the same as Score Forge playback.
- Confirm track volume and pan are audible in the render.
- Confirm note velocity/articulation sounds consistent with playback.
- Confirm tied notes are sustained rather than re-attacked.
- Confirm percussion tracks render as percussion.

## Tempo map

1. Create or open a project with a mid-song tempo change.
2. Play it in Score Forge and note where the tempo changes.
3. Export WAV.
4. Confirm the WAV changes tempo at the same musical position.

## Long-song / memory regression

Export a reasonably long imported MIDI project.

- Score Forge should remain responsive enough to complete the export.
- The progress dialog should advance during the render.
- The public destination file should not appear until after private rendering is complete and the save picker is shown.
- The export should not require allocating the full PCM song in memory; the implementation renders in blocks.
- The resulting WAV should play to the end and contain a short synth/reverb tail.

## Filename behavior

1. Let the WAV render finish and wait for the save picker.
2. Delete `.wav` from the filename before saving.
3. Confirm Score Forge restores `.wav` and the resulting file opens normally in an audio player.

## Duration behavior

The renderer honors the project timeline/cursor when it extends beyond the last notation event, then adds a short synth/reverb tail. If an imported MIDI reports a longer duration than the WAV but the music itself is not cut off, note whether the difference is only trailing silence in the MIDI file.

## Expected scope for 0.2.45

- 44.1 kHz
- 16-bit PCM
- stereo WAV
- active `.sf2` SoundFont / starter instruments
- tempo-map aware
- progress UI
- private render before final save
- no metronome click in exported audio

FLAC, OGG, MP3, and AAC are intentionally not part of this PR.