from pathlib import Path

# 1) Add an intermediate-size chamfered button specifically for the transforming toolbar.
control_path = Path("app/src/main/java/com/scoreforge/app/ui/ControlShapes.kt")
control = control_path.read_text()
marker = "@Composable\ninternal fun CompactCommandButton("
if "internal fun ComposerToolbarButton(" not in control:
    insert = '''@Composable
internal fun ComposerToolbarButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(36.dp),
        shape = ChamferedControlShape,
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (enabled) ComposerControlOutlineColor else ComposerControlDisabledOutline,
        ),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (selected) ComposerControlPressedColor else ComposerControlButtonColor,
            contentColor = Color.White,
            disabledContainerColor = ComposerControlDisabledColor,
            disabledContentColor = Color(0xFFAAA5B0),
        ),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = if (selected) 0.dp else 2.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelLarge)
    }
}

'''
    if marker not in control:
        raise SystemExit("Could not find CompactCommandButton insertion point")
    control = control.replace(marker, insert + marker, 1)
    control_path.write_text(control)

# 2) Replace root category buttons and Back with the new intermediate toolbar style.
toolbar_path = Path("app/src/main/java/com/scoreforge/app/ui/ComposerTransformToolbar.kt")
toolbar = toolbar_path.read_text()
replacements = {
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.TEMPO }) {
                    Text("Tempo ${activeTempo.bpm}")
                }''': '''                ComposerToolbarButton(
                    label = "Tempo ${activeTempo.bpm}",
                    onClick = { section = ComposerToolbarSection.TEMPO },
                )''',
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.TIME }) {
                    Text("Time ${activeTime.displayName}")
                }''': '''                ComposerToolbarButton(
                    label = "Time ${activeTime.displayName}",
                    onClick = { section = ComposerToolbarSection.TIME },
                )''',
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.KEY }) {
                    Text("Key ${activeKey.displayName}")
                }''': '''                ComposerToolbarButton(
                    label = "Key ${activeKey.displayName}",
                    onClick = { section = ComposerToolbarSection.KEY },
                )''',
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.CLEF }) {
                    Text("Clef ${clefMode.displayName}")
                }''': '''                ComposerToolbarButton(
                    label = "Clef ${clefMode.displayName}",
                    onClick = { section = ComposerToolbarSection.CLEF },
                )''',
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.NOTES }) {
                    Text("Notes ${selectedDuration.displayName}${if (dotted) " •" else ""}")
                }''': '''                ComposerToolbarButton(
                    label = "Notes ${selectedDuration.displayName}${if (dotted) " •" else ""}",
                    onClick = { section = ComposerToolbarSection.NOTES },
                )''',
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.EDITOR }) {
                    Text(if (editorMode == ScoreEditorMode.STAFF) "Editor Staff" else "Editor Piano Roll")
                }''': '''                ComposerToolbarButton(
                    label = if (editorMode == ScoreEditorMode.STAFF) "Editor Staff" else "Editor Piano Roll",
                    onClick = { section = ComposerToolbarSection.EDITOR },
                )''',
'''                OutlinedButton(onClick = { section = ComposerToolbarSection.MEASURE }) {
                    Text("Measure ${measureNumber.coerceAtLeast(1)}")
                }''': '''                ComposerToolbarButton(
                    label = "Measure ${measureNumber.coerceAtLeast(1)}",
                    onClick = { section = ComposerToolbarSection.MEASURE },
                )''',
'''private fun BackButton(onClick: () -> Unit) {
    OutlinedButton(onClick = onClick) { Text("← Back") }
}''': '''private fun BackButton(onClick: () -> Unit) {
    ComposerToolbarButton(label = "← Back", onClick = onClick)
}''',
}
for old, new in replacements.items():
    if old not in toolbar:
        raise SystemExit(f"Could not find toolbar block:\n{old}")
    toolbar = toolbar.replace(old, new, 1)
toolbar_path.write_text(toolbar)

# 3) Remove duplicate project label/name from the project command row.
project_path = Path("app/src/main/java/com/scoreforge/app/ui/ProjectFileControls.kt")
project = project_path.read_text()
duplicate = '''        Text("Project:", style = MaterialTheme.typography.labelLarge)
        Text(projectName, style = MaterialTheme.typography.labelLarge)

'''
if duplicate not in project:
    raise SystemExit("Could not find duplicate project label")
project = project.replace(duplicate, "", 1)
project_path.write_text(project)
