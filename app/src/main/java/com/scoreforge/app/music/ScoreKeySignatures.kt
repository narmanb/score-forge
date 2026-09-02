package com.scoreforge.app.music

import kotlin.math.abs

/** A project key signature beginning at [startBeat], using the MIDI circle-of-fifths value. */
data class ScoreKeySignature(
    val startBeat: Float = 0f,
    val fifths: Int = 0,
    val minor: Boolean = false,
) {
    val displayName: String
        get() = ScoreKeySignatures.displayName(this)

    fun normalized(): ScoreKeySignature = ScoreKeySignatures.normalizeOne(this)
}

object ScoreKeySignatures {
    private const val EPSILON = 0.001f
    val DEFAULT = ScoreKeySignature()

    private val majorNames = mapOf(
        -7 to "C♭ major",
        -6 to "G♭ major",
        -5 to "D♭ major",
        -4 to "A♭ major",
        -3 to "E♭ major",
        -2 to "B♭ major",
        -1 to "F major",
        0 to "C major",
        1 to "G major",
        2 to "D major",
        3 to "A major",
        4 to "E major",
        5 to "B major",
        6 to "F♯ major",
        7 to "C♯ major",
    )

    private val minorNames = mapOf(
        -7 to "A♭ minor",
        -6 to "E♭ minor",
        -5 to "B♭ minor",
        -4 to "F minor",
        -3 to "C minor",
        -2 to "G minor",
        -1 to "D minor",
        0 to "A minor",
        1 to "E minor",
        2 to "B minor",
        3 to "F♯ minor",
        4 to "C♯ minor",
        5 to "G♯ minor",
        6 to "D♯ minor",
        7 to "A♯ minor",
    )

    fun normalizeOne(signature: ScoreKeySignature): ScoreKeySignature = signature.copy(
        startBeat = signature.startBeat.coerceAtLeast(0f),
        fifths = signature.fifths.coerceIn(-7, 7),
    )

    fun normalize(signatures: List<ScoreKeySignature>): List<ScoreKeySignature> {
        val sorted = signatures.map(::normalizeOne).sortedBy { it.startBeat }
        val merged = mutableListOf<ScoreKeySignature>()
        sorted.forEach { candidate ->
            val previous = merged.lastOrNull()
            if (previous != null && abs(previous.startBeat - candidate.startBeat) <= EPSILON) {
                merged[merged.lastIndex] = candidate.copy(startBeat = previous.startBeat)
            } else {
                merged += candidate
            }
        }

        if (merged.isEmpty()) return listOf(DEFAULT)
        if (merged.first().startBeat > EPSILON) {
            merged.add(0, DEFAULT)
        } else {
            merged[0] = merged[0].copy(startBeat = 0f)
        }
        return merged
    }

    fun atBeat(signatures: List<ScoreKeySignature>, beat: Float): ScoreKeySignature {
        val safeBeat = beat.coerceAtLeast(0f)
        return normalize(signatures)
            .lastOrNull { it.startBeat <= safeBeat + EPSILON }
            ?: DEFAULT
    }

    fun withChange(
        signatures: List<ScoreKeySignature>,
        startBeat: Float,
        fifths: Int,
        minor: Boolean,
    ): List<ScoreKeySignature> {
        val safeStart = startBeat.coerceAtLeast(0f)
        val retained = normalize(signatures)
            .filterNot { abs(it.startBeat - safeStart) <= EPSILON }
        return normalize(retained + ScoreKeySignature(safeStart, fifths, minor).normalized())
    }

    fun withoutChange(signatures: List<ScoreKeySignature>, startBeat: Float): List<ScoreKeySignature> {
        if (startBeat <= EPSILON) return normalize(signatures)
        return normalize(normalize(signatures).filterNot { abs(it.startBeat - startBeat) <= EPSILON })
    }

    fun displayName(signature: ScoreKeySignature): String {
        val safe = normalizeOne(signature)
        return (if (safe.minor) minorNames else majorNames)[safe.fifths]
            ?: if (safe.minor) "A minor" else "C major"
    }

    /** Letter indexes C=0, D=1, E=2, F=3, G=4, A=5, B=6 and their signature alterations. */
    fun alterationForLetter(signature: ScoreKeySignature, letterIndex: Int): Int {
        val safe = normalizeOne(signature)
        val letter = ((letterIndex % 7) + 7) % 7
        val sharpOrder = intArrayOf(3, 0, 4, 1, 5, 2, 6) // F C G D A E B
        val flatOrder = intArrayOf(6, 2, 5, 1, 4, 0, 3) // B E A D G C F
        return when {
            safe.fifths > 0 && sharpOrder.take(safe.fifths).contains(letter) -> 1
            safe.fifths < 0 && flatOrder.take(-safe.fifths).contains(letter) -> -1
            else -> 0
        }
    }
}

enum class ScoreAccidental {
    NONE,
    SHARP,
    FLAT,
    NATURAL,
}

data class ScoreSpelledPitch(
    val diatonicPosition: Int,
    val accidental: ScoreAccidental,
)

/**
 * Chooses an enharmonic staff spelling from MIDI pitch plus the active key signature.
 * This lets flat keys render B♭/E♭ etc. instead of forcing every black key to a sharp.
 */
object ScorePitchSpelling {
    private val naturalPitchClasses = intArrayOf(0, 2, 4, 5, 7, 9, 11)

    fun spell(midiPitch: Int, keySignature: ScoreKeySignature): ScoreSpelledPitch {
        val pitch = midiPitch.coerceIn(0, 127)
        val key = keySignature.normalized()
        val candidates = mutableListOf<Candidate>()

        for (letter in 0..6) {
            val naturalPc = naturalPitchClasses[letter]
            for (alteration in -1..1) {
                val base = pitch - naturalPc - alteration
                if (base % 12 != 0) continue
                val octave = base / 12 - 1
                val keyAlteration = ScoreKeySignatures.alterationForLetter(key, letter)
                val preference = when {
                    // Best: the pitch is exactly what the active key signature already specifies.
                    alteration == keyAlteration -> 0
                    // Next: an explicit natural cancels a sharp/flat from the active signature.
                    alteration == 0 && keyAlteration != 0 -> 1
                    // Then prefer chromatic spellings that match the key's sharp/flat direction.
                    key.fifths < 0 && alteration == -1 -> 2
                    key.fifths > 0 && alteration == 1 -> 2
                    key.fifths == 0 && alteration == 1 -> 2
                    // Remaining natural spelling is still preferable to an opposite-direction accidental.
                    alteration == 0 -> 3
                    else -> 4
                }
                candidates += Candidate(letter, octave, alteration, keyAlteration, preference)
            }
        }

        val chosen = candidates.minWithOrNull(
            compareBy<Candidate> { it.preference }
                .thenBy { abs(it.alteration) }
                .thenBy { it.letter }
        ) ?: return ScoreSpelledPitch(PitchNames.diatonicPosition(pitch), ScoreAccidental.NONE)

        val accidental = when {
            chosen.alteration == chosen.keyAlteration -> ScoreAccidental.NONE
            chosen.alteration == 0 && chosen.keyAlteration != 0 -> ScoreAccidental.NATURAL
            chosen.alteration > 0 -> ScoreAccidental.SHARP
            else -> ScoreAccidental.FLAT
        }
        return ScoreSpelledPitch(
            diatonicPosition = chosen.octave * 7 + chosen.letter,
            accidental = accidental,
        )
    }

    private data class Candidate(
        val letter: Int,
        val octave: Int,
        val alteration: Int,
        val keyAlteration: Int,
        val preference: Int,
    )
}
