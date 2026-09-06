package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MidiExporterTest {
    @Test
    fun exportRoundTripPreservesCoreProjectMetadataAndInstruments() {
        val melody = ScoreTrack(
            id = 1,
            name = "Lead",
            events = listOf(
                ScoreNote(60, NoteDuration.QUARTER, startBeat = 0f, velocity = 101),
                ScoreNote(64, NoteDuration.HALF, startBeat = 2f, velocity = 87),
            ),
            presetBank = 0,
            presetProgram = 40,
            volume = 111,
            pan = -20,
        )
        val drums = ScoreTrack(
            id = 2,
            name = "Drums",
            events = listOf(
                ScoreNote(36, NoteDuration.QUARTER, startBeat = 0f, velocity = 120),
                ScoreNote(38, NoteDuration.EIGHTH, startBeat = 1f, velocity = 96),
            ),
            presetBank = 128,
            presetProgram = 0,
            volume = 95,
            pan = 10,
        )
        val snapshot = ScoreProjectSnapshot(
            events = melody.events,
            bpm = 120,
            tempoChanges = listOf(
                ScoreTempoChange(0f, 120),
                ScoreTempoChange(4f, 150),
            ),
            tracks = listOf(melody, drums),
            activeTrackIndex = 0,
            projectName = "Export Round Trip",
            timeSignatures = listOf(
                ScoreTimeSignature(0f, 4, 4),
                ScoreTimeSignature(4f, 3, 4),
            ),
            keySignatures = listOf(
                ScoreKeySignature(0f, 0, false),
                ScoreKeySignature(4f, 2, false),
            ),
        )

        val exported = MidiExporter.export(snapshot)

        assertEquals("MThd", exported.bytes.copyOfRange(0, 4).toString(Charsets.US_ASCII))
        assertEquals(2, exported.exportedTrackCount)
        assertEquals(4, exported.exportedNoteCount)
        assertTrue(exported.warnings.isEmpty())

        val imported = MidiImporter.import(exported.bytes, "Round Trip")
        assertEquals(2, imported.importedTrackCount)
        assertEquals(4, imported.importedNoteCount)

        val importedTracks = imported.snapshot.effectiveTracks()
        val importedMelody = importedTracks[0]
        val importedDrums = importedTracks[1]

        assertEquals("Lead", importedMelody.name)
        assertEquals(0, importedMelody.presetBank)
        assertEquals(40, importedMelody.presetProgram)
        assertEquals(111, importedMelody.volume)
        assertEquals(-20, importedMelody.pan)
        assertEquals(listOf(60, 64), importedMelody.notes.map { it.midiPitch })
        assertEquals(listOf(101, 87), importedMelody.notes.map { it.velocity })

        assertEquals("Drums", importedDrums.name)
        assertEquals(128, importedDrums.presetBank)
        assertEquals(0, importedDrums.presetProgram)
        assertEquals(95, importedDrums.volume)
        assertEquals(10, importedDrums.pan)
        assertEquals(listOf(36, 38), importedDrums.notes.map { it.midiPitch })

        assertEquals(
            listOf(0f to 120, 4f to 150),
            imported.snapshot.effectiveTempoChanges().map { it.startBeat to it.bpm },
        )
        assertEquals(
            listOf(Triple(0f, 4, 4), Triple(4f, 3, 4)),
            imported.snapshot.effectiveTimeSignatures().map {
                Triple(it.startBeat, it.numerator, it.denominator)
            },
        )
        assertEquals(
            listOf(Triple(0f, 0, false), Triple(4f, 2, false)),
            imported.snapshot.effectiveKeySignatures().map {
                Triple(it.startBeat, it.fifths, it.minor)
            },
        )
    }

    @Test
    fun tiedNotesExportAsOneSustainedMidiNote() {
        val track = ScoreTrack(
            id = 1,
            name = "Tied",
            events = listOf(
                ScoreNote(
                    midiPitch = 60,
                    duration = NoteDuration.QUARTER,
                    startBeat = 0f,
                    velocity = 100,
                    tieToNext = true,
                ),
                ScoreNote(
                    midiPitch = 60,
                    duration = NoteDuration.QUARTER,
                    startBeat = 1f,
                    velocity = 100,
                ),
            ),
        )
        val snapshot = ScoreProjectSnapshot(
            events = track.events,
            tracks = listOf(track),
            projectName = "Tie export",
        )

        val exported = MidiExporter.export(snapshot)
        val imported = MidiImporter.import(exported.bytes, "Tie round trip")
        val note = imported.snapshot.effectiveTracks().single().notes.single()

        assertEquals(1, exported.exportedNoteCount)
        assertEquals(60, note.midiPitch)
        assertEquals(NoteDuration.HALF, note.duration)
        assertEquals(0f, note.startBeat, 0.001f)
    }
}
