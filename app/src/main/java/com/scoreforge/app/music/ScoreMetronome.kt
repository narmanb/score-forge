package com.scoreforge.app.music

enum class MetronomeAccent {
    DOWNBEAT,
    GROUP,
    BEAT,
}

data class MetronomeClick(
    val beat: Float,
    val accent: MetronomeAccent,
)

/** Generates click positions from the score's time-signature map. */
object ScoreMetronome {
    private const val EPSILON = 0.001f

    fun clicks(
        timeSignatures: List<ScoreTimeSignature>,
        throughBeat: Float,
    ): List<MetronomeClick> {
        val target = throughBeat.coerceAtLeast(0f)
        val signatures = ScoreTimeSignatures.normalize(timeSignatures)
        val boundaries = ScoreTimeSignatures.measureBoundaries(signatures, target)
        if (boundaries.isEmpty()) return emptyList()

        return buildList {
            boundaries.forEachIndexed { measureIndex, measureStart ->
                if (measureStart > target + EPSILON) return@forEachIndexed

                val signature = ScoreTimeSignatures.atBeat(signatures, measureStart)
                val unitBeats = 4f / signature.denominator.coerceAtLeast(1).toFloat()
                val measureEnd = boundaries.getOrNull(measureIndex + 1)
                    ?: (measureStart + signature.beatsPerMeasure)
                val secondaryStarts = secondaryGroupStarts(signature)

                for (beatIndex in 0 until signature.numerator) {
                    val clickBeat = measureStart + beatIndex * unitBeats
                    if (clickBeat > target + EPSILON) break
                    if (beatIndex > 0 && clickBeat >= measureEnd - EPSILON) break

                    val accent = when {
                        beatIndex == 0 -> MetronomeAccent.DOWNBEAT
                        beatIndex in secondaryStarts -> MetronomeAccent.GROUP
                        else -> MetronomeAccent.BEAT
                    }
                    add(MetronomeClick(clickBeat, accent))
                }
            }
        }
    }

    /**
     * Default groupings for common compound/odd meters. These are intentionally defaults rather
     * than notation semantics: 5/8 uses 3+2, 7/8 uses 3+2+2, and compound multiples of three use
     * groups of three. Explicit beat-grouping controls can override these defaults in a later pass.
     */
    fun secondaryGroupStarts(signature: ScoreTimeSignature): Set<Int> {
        val safe = signature.normalized()
        if (safe.denominator !in setOf(8, 16)) return emptySet()

        return when (safe.numerator) {
            5 -> setOf(3)
            7 -> setOf(3, 5)
            else -> if (safe.numerator > 3 && safe.numerator % 3 == 0) {
                (3 until safe.numerator step 3).toSet()
            } else {
                emptySet()
            }
        }
    }
}
