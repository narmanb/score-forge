from pathlib import Path


def replace_once(path: str, old: str, new: str) -> None:
    p = Path(path)
    text = p.read_text()
    if new in text:
        return
    if old not in text:
        raise SystemExit(f"Expected block not found in {path}: {old[:120]!r}")
    if text.count(old) != 1:
        raise SystemExit(f"Expected one match in {path}, found {text.count(old)}")
    p.write_text(text.replace(old, new, 1))


composer = "app/src/main/java/com/scoreforge/app/ui/ComposerScreen.kt"
replace_once(
    composer,
    "import com.scoreforge.app.music.ComfortTempo\nimport com.scoreforge.app.music.LiveEntryTiming\n",
    "import com.scoreforge.app.music.ComfortTempo\nimport com.scoreforge.app.music.HoldEntryTiming\nimport com.scoreforge.app.music.LiveEntryTiming\n",
)
replace_once(
    composer,
    "    var holdCurrentGroup by remember { mutableStateOf<HoldOnsetGroup?>(null) }\n    var holdPreviewWritten by remember { mutableStateOf<NaturalEntryTiming.WrittenDuration?>(null) }\n",
    "    var holdCurrentGroup by remember { mutableStateOf<HoldOnsetGroup?>(null) }\n    var holdPreviousGroup by remember { mutableStateOf<HoldOnsetGroup?>(null) }\n    var holdPreviewWritten by remember { mutableStateOf<NaturalEntryTiming.WrittenDuration?>(null) }\n",
)
replace_once(
    composer,
    "        holdHeldInputs.clear()\n        holdCurrentGroup = null\n        holdPreviewWritten = null\n",
    "        holdHeldInputs.clear()\n        holdCurrentGroup = null\n        holdPreviousGroup = null\n        holdPreviewWritten = null\n",
)
replace_once(
    composer,
    """        val previousGroup = holdCurrentGroup
        val joinsCurrentChord = previousGroup != null && holdHeldInputs.isNotEmpty()
        val startBeat = if (joinsCurrentChord) previousGroup!!.startBeat else currentTrack().cursorBeat
        val initialWritten = if (joinsCurrentChord) previousGroup!!.currentWritten
        else NaturalEntryTiming.writtenForHoldMs(0L, bpm)
""",
    """        val activeGroup = holdCurrentGroup
        val completedAnchor = holdPreviousGroup
        val joinsCurrentChord = activeGroup != null && holdHeldInputs.isNotEmpty()
        val startBeat = when {
            joinsCurrentChord -> activeGroup!!.startBeat
            completedAnchor != null -> HoldEntryTiming.nextStartBeat(
                previousStartBeat = completedAnchor.startBeat,
                previousOnsetMs = completedAnchor.onsetMs,
                currentOnsetMs = now,
                bpm = completedAnchor.bpm,
            )
            else -> currentTrack().cursorBeat
        }
        val initialWritten = if (joinsCurrentChord) activeGroup!!.currentWritten
        else NaturalEntryTiming.writtenForHoldMs(0L, bpm)
""",
)
replace_once(
    composer,
    """        val group = if (joinsCurrentChord) {
            previousGroup!!.copy(eventIndices = previousGroup.eventIndices + eventIndex)
        } else {
""",
    """        val group = if (joinsCurrentChord) {
            activeGroup!!.copy(eventIndices = activeGroup.eventIndices + eventIndex)
        } else {
""",
)
replace_once(
    composer,
    """        if (holdHeldInputs.values.none { it.groupOnsetMs == held.groupOnsetMs }) {
            holdCurrentGroup = null
            syncHistoryButtons()
        }
""",
    """        if (holdHeldInputs.values.none { it.groupOnsetMs == held.groupOnsetMs }) {
            // Keep the completed onset as the performance-timing anchor for the next Hold note.
            // Its written duration is already final; only the next note's start uses this anchor.
            holdPreviousGroup = holdCurrentGroup
            holdCurrentGroup = null
            syncHistoryButtons()
        }
""",
)
replace_once(
    composer,
    """        holdHeldInputs.clear()
        holdCurrentGroup = null
    }
""",
    """        holdHeldInputs.clear()
        holdCurrentGroup = null
        holdPreviousGroup = null
    }
""",
)

build = "app/build.gradle.kts"
replace_once(build, '        versionCode = 31\n        versionName = "0.2.28"\n', '        versionCode = 32\n        versionName = "0.2.29"\n')

Path("app/src/main/java/com/scoreforge/app/music/HoldEntryTiming.kt").write_text('''package com.scoreforge.app.music

/** Timing helpers for Hold entry that preserve performed attack spacing separately from note length. */
object HoldEntryTiming {
    fun nextStartBeat(
        previousStartBeat: Float,
        previousOnsetMs: Long,
        currentOnsetMs: Long,
        bpm: Int,
    ): Float {
        val intervalMs = (currentOnsetMs - previousOnsetMs).coerceAtLeast(0L)
        return previousStartBeat + NaturalEntryTiming.quantizedOnsetSpacingBeats(intervalMs, bpm)
    }
}
''')

Path("app/src/test/java/com/scoreforge/app/music/HoldPerformanceSpacingTest.kt").write_text('''package com.scoreforge.app.music

import org.junit.Assert.assertEquals
import org.junit.Test

class HoldPerformanceSpacingTest {
    @Test
    fun quarterBeatAttackSpacingIsPreservedAt120Bpm() {
        assertEquals(
            11.0f,
            HoldEntryTiming.nextStartBeat(
                previousStartBeat = 10f,
                previousOnsetMs = 1_000L,
                currentOnsetMs = 1_500L,
                bpm = 120,
            ),
            0.0001f,
        )
    }

    @Test
    fun halfNoteAttackSpacingIsIndependentOfWrittenHoldLength() {
        assertEquals(
            14.0f,
            HoldEntryTiming.nextStartBeat(
                previousStartBeat = 12f,
                previousOnsetMs = 2_000L,
                currentOnsetMs = 3_000L,
                bpm = 120,
            ),
            0.0001f,
        )
    }

    @Test
    fun dottedQuarterAttackSpacingIsPreserved() {
        assertEquals(
            21.5f,
            HoldEntryTiming.nextStartBeat(
                previousStartBeat = 20f,
                previousOnsetMs = 5_000L,
                currentOnsetMs = 5_750L,
                bpm = 120,
            ),
            0.0001f,
        )
    }
}
''')
