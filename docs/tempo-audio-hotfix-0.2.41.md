# Score Forge 0.2.42 phone-test focus

This test build keeps the approved tempo-map editing and visibility work, restores the 0.2.40 streaming playback path after the 0.2.41 offline-render regression caused UI freezes, and fixes MIDI imports that ended up at volume 0 because a file faded CC7 volume to zero at the end.

Phone checks:

1. Import `Paul Albrecht - Homeward.mid` and confirm the imported tracks start at audible volume instead of 0.
2. Play Homeward and confirm the app no longer freezes/stalls like the 0.2.41 test build.
3. Confirm the imported tempo map still starts at 140 BPM and changes later in the song.
4. Confirm staff tempo marks such as `♩ = 140` remain visible at tempo-change locations.
5. Confirm the Tempo row still lists the full tempo map.
6. Place notes manually and confirm preview/playback audio.
7. Confirm time-signature notation/bar grouping still works; with metronome enabled, accents should follow the active meter.
