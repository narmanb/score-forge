from pathlib import Path

path = Path('app/src/main/java/com/scoreforge/app/ui/ControlShapes.kt')
text = path.read_text()

text = text.replace(
    'import androidx.compose.foundation.BorderStroke\n',
    'import androidx.compose.foundation.BorderStroke\nimport androidx.compose.foundation.clickable\n',
    1,
)
text = text.replace(
    'import androidx.compose.foundation.layout.PaddingValues\n',
    'import androidx.compose.foundation.layout.Box\nimport androidx.compose.foundation.layout.PaddingValues\n',
    1,
)
text = text.replace(
    'import androidx.compose.foundation.layout.offset\n',
    'import androidx.compose.foundation.layout.offset\nimport androidx.compose.foundation.layout.size\n',
    1,
)
text = text.replace(
    'import androidx.compose.ui.Modifier\n',
    'import androidx.compose.ui.Alignment\nimport androidx.compose.ui.Modifier\n',
    1,
)
text = text.replace(
    'import androidx.compose.ui.unit.dp\n',
    'import androidx.compose.ui.unit.dp\nimport androidx.compose.ui.unit.sp\n',
    1,
)

needle = '''    val visuallyPressed = physicallyPressed || latchedPressed.value\n\n    Button(\n'''
replacement = '''    val visuallyPressed = physicallyPressed || latchedPressed.value\n\n    // The app-level Settings control is intentionally just a large accent gear.\n    // Keep a generous invisible touch target, haptic feedback, and no button chrome.\n    if (label == "⚙" && !compact) {\n        Box(\n            modifier = modifier\n                .size(50.dp)\n                .clickable(enabled = enabled) {\n                    view.performScoreForgeHaptic(UiHapticFeedback.TICK)\n                    onClick()\n                },\n            contentAlignment = Alignment.Center,\n        ) {\n            Text(\n                text = "⚙",\n                color = if (enabled) Color(0xFFD0B8FF) else Color(0xFF8A8197),\n                fontSize = 40.sp,\n            )\n        }\n        return\n    }\n\n    Button(\n'''
if needle not in text:
    raise SystemExit('ChamferedControlButton insertion point not found')
text = text.replace(needle, replacement, 1)
path.write_text(text)
