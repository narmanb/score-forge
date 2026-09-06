# Score Forge 0.2.46 composer toolbar phone test

Verify on-device before merge.

- Track and instrument controls remain directly above the editor controls.
- The composer toolbar occupies one fixed-height row above the staff/piano roll.
- Root row shows Tempo, Time, Key, Clef, Notes, Editor, and Measure categories with current values.
- Tapping a category replaces the root row with that category's controls; Back restores the root row.
- Opening categories does not add a second row or push the staff down.
- Tempo supports +/-5, remove-change when applicable, and Comfort Tempo measurement.
- Time and Key changes still apply at the red cursor's measure.
- Clef Auto/Treble/Bass still changes the active track.
- Notes still controls duration, dot, rest, tie, and Staff sharp input.
- Editor still switches Staff/Piano Roll and shows/hides the piano keyboard.
- Measure currently shows the active measure placeholder; copy/paste/duplicate comes next.
- The note controls below the piano keyboard remain unchanged for now.
