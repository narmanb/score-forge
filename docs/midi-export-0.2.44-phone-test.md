# Score Forge 0.2.44 — native MIDI export phone test

## Purpose

Verify the first native export stage on a real Android device. CI/unit tests are not the final gate.

## Install

Install the 0.2.44 test APK over the verified 0.2.43 build.

## Test A — basic export

1. Open or create a project with at least two tracks and several notes.
2. Set different instruments, track volumes and pans.
3. Tap **Export MIDI**.
4. Save the suggested `<project name>.mid` file through Android's document picker.
5. Confirm Score Forge stays responsive and reports the exported track/note count.
6. Confirm the `.mid` file exists in the selected folder.

## Test B — Score Forge round trip

1. From Android's file manager, open the exported `.mid` with Score Forge.
2. Confirm the expected musical tracks return.
3. Confirm notes/pitches and velocities are present.
4. Confirm track names and instruments are sensible.
5. Confirm track volume and pan values return.
6. If the project contains a drum/percussion track, confirm it still imports as percussion.

## Test C — project-wide MIDI metadata

Use a project containing changes during the song, not only at beat 0.

1. Add at least two tempo points.
2. Add a time-signature change.
3. Add a key-signature change.
4. Export the MIDI and open that exported MIDI back in Score Forge.
5. Confirm the tempo map, time signatures and key signatures survived the round trip.

## Test D — larger imported MIDI

1. Open `Homeward.mid` or another larger MIDI that already plays correctly in 0.2.43.
2. Export it to a new MIDI file.
3. Confirm export completes without the stalls/freezes seen in the abandoned offline-audio-render experiment.
4. Open the exported MIDI back in Score Forge and confirm the expected tracks and notes are present.

### Expected current limitation

Full CC7/CC10 automation is not implemented yet. If a source MIDI contains a volume or pan automation curve, Score Forge currently keeps its safe static imported value. MIDI export therefore writes that static mixer value; it is **not** expected to recreate Homeward's end-of-song CC7 fade yet.

## Pass gate

Do not merge based on CI alone. Merge only after the APK passes the phone checks above.
