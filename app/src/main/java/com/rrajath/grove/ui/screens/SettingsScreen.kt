package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.ui.capture.TemplatesViewModel
import com.rrajath.grove.settings.ChecklistStates
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NoteOpenMode
import com.rrajath.grove.settings.NotebookDisplayNameMode
import com.rrajath.grove.settings.SyncMode
import com.rrajath.grove.settings.ThemePreference
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.components.ThemeDropdownPicker
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings per design spec §11. M1: Appearance group functional; the rest land with their milestones. */
@Composable
fun SettingsScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetSyncAppIconWithTheme: (Boolean) -> Unit,
    onSetShowHeaderTags: (Boolean) -> Unit,
    onSetShowPropertyDrawers: (Boolean) -> Unit,
    onSetFontSize: (FontSizePreference) -> Unit,
    onSetNoteOpenMode: (NoteOpenMode) -> Unit,
    onEditTemplate: (String?) -> Unit,
    onSetSyncMode: (SyncMode) -> Unit,
    onSetPeriodicMinutes: (Int) -> Unit,
    onOpenSyncLog: () -> Unit,
    onSetTodoKeywords: (String) -> Unit,
    onSetDefaultPriority: (Char?) -> Unit,
    onSetAddId: (Boolean) -> Unit,
    onSetAddCreated: (Boolean) -> Unit,
    onSetCaptureNotification: (Boolean) -> Unit,
    onSetVaultUri: (String) -> Unit,
    onSetShareTargetFile: (String) -> Unit,
    onSetNotebookDisplayNameMode: (NotebookDisplayNameMode) -> Unit,
    onSetChecklistStates: (ChecklistStates) -> Unit,
    onExportSettings: (android.net.Uri) -> Unit,
    onImportSettings: (android.net.Uri) -> Unit,
    templatesViewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val context = androidx.compose.ui.platform.LocalContext.current
    val templates by templatesViewModel.templates.collectAsStateWithLifecycle()
    var keywordsText by remember(settings.todoKeywords) {
        mutableStateOf(settings.todoKeywords)
    }
    var shareFileText by remember(settings.shareTargetFile) {
        mutableStateOf(settings.shareTargetFile)
    }

    val folderPicker = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            onSetVaultUri(uri.toString())
        }
    }

    val settingsExporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportSettings(uri) }

    val settingsImporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportSettings(uri) }

    // Apply pending text edits on leave so back doesn't drop them.
    fun leave() {
        if (keywordsText != settings.todoKeywords && keywordsText.isNotBlank()) {
            onSetTodoKeywords(keywordsText)
        }
        if (shareFileText != settings.shareTargetFile && shareFileText.isNotBlank()) {
            onSetShareTargetFile(shareFileText)
        }
        onBack()
    }
    androidx.activity.compose.BackHandler { leave() }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = ::leave) },
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            SectionLabel("APPEARANCE")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(
                        "Theme",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    ThemeDropdownPicker(
                        selected = settings.theme,
                        onSelect = onSetTheme,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RowDivider()
                SyncAppIconRow(
                    checked = settings.syncAppIconWithTheme,
                    theme = settings.theme,
                    onToggle = onSetSyncAppIconWithTheme,
                )
                RowDivider()
                ToggleRow(
                    label = "Show header tags",
                    description = "Display file-level #+ keywords in org files",
                    checked = settings.showHeaderTags,
                    onToggle = onSetShowHeaderTags,
                )
                RowDivider()
                ToggleRow(
                    label = "Show property drawers",
                    description = "Display :PROPERTIES: drawers in org files",
                    checked = settings.showPropertyDrawers,
                    onToggle = onSetShowPropertyDrawers,
                )
                RowDivider()
                SettingsRow(label = "Font size") {
                    SegmentedControl(
                        options = listOf("Small", "Medium", "Large"),
                        selectedIndex = settings.fontSize.ordinal,
                        onSelect = { onSetFontSize(FontSizePreference.entries[it]) },
                        modifier = Modifier.width(220.dp),
                    )
                }
                RowDivider()
                SettingsRow(
                    label = "Default note mode",
                    description = "Open a note in read mode, or edit mode",
                ) {
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        selectedIndex = settings.defaultNoteOpenMode.ordinal,
                        onSelect = { onSetNoteOpenMode(NoteOpenMode.entries[it]) },
                        modifier = Modifier.width(160.dp),
                    )
                }
            }

            SectionLabel("CAPTURE TEMPLATES")
            SettingsGroup {
                templates.forEachIndexed { i, template ->
                    if (i > 0) RowDivider()
                    TemplateSettingsRow(
                        template = template,
                        onEdit = { onEditTemplate(template.id) },
                        onMoveUp = if (i > 0) ({ templatesViewModel.move(template.id, -1) }) else null,
                        onMoveDown = if (i < templates.lastIndex) ({ templatesViewModel.move(template.id, +1) }) else null,
                        onDelete = { templatesViewModel.delete(template.id) },
                    )
                }
                if (templates.isNotEmpty()) RowDivider()
                SettingsRow(label = "＋ New template", onClick = { onEditTemplate(null) }) {}
                RowDivider()
                val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                    androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
                ) { granted -> if (granted) onSetCaptureNotification(true) }
                ToggleRow(
                    label = "Capture from notification",
                    checked = settings.captureNotification,
                ) { enabled ->
                    if (enabled) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    else onSetCaptureNotification(false)
                }
            }

            SectionLabel("SYNC")
            SettingsGroup {
                SettingsRow(label = "Folder", onClick = { folderPicker.launch(null) }) {
                    Text(
                        settings.vaultTreeUri?.let { uriDisplayName(it) } ?: "tap to choose",
                        fontFamily = PlexMono, fontSize = 12.sp, color = c.accent,
                    )
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(
                        "Auto-sync",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    SyncMode.entries.forEach { mode ->
                        val active = settings.syncMode == mode
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) c.accentSoft else c.surface)
                                .clickable { onSetSyncMode(mode) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                        ) {
                            Text(
                                mode.label,
                                fontFamily = PlexSans,
                                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 13.5.sp,
                                color = if (active) c.accent else c.ink,
                            )
                        }
                    }
                }
                if (settings.syncMode == SyncMode.PERIODIC) {
                    RowDivider()
                    SettingsRow(label = "Interval") {
                        SegmentedControl(
                            options = listOf("15m", "30m", "60m"),
                            selectedIndex = when (settings.periodicSyncMinutes) {
                                15 -> 0
                                60 -> 2
                                else -> 1
                            },
                            onSelect = { onSetPeriodicMinutes(listOf(15, 30, 60)[it]) },
                            modifier = Modifier.width(180.dp),
                        )
                    }
                }
                RowDivider()
                SettingsRow(label = "View sync log", onClick = onOpenSyncLog) {
                    Text("›", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink2)
                }
            }

            SectionLabel("NOTES")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(
                        "TODO keywords",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                    )
                    Text(
                        "Keywords after | are done-type",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedTextField(
                        value = keywordsText,
                        onValueChange = { keywordsText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (keywordsText != settings.todoKeywords) {
                        Text(
                            "Apply (re-indexes all notebooks)",
                            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp, color = c.accent,
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onSetTodoKeywords(keywordsText) }
                                .padding(6.dp),
                        )
                    }
                }
                RowDivider()
                SettingsRow(label = "Default priority") {
                    SegmentedControl(
                        options = listOf("None", "A", "B", "C"),
                        selectedIndex = when (settings.defaultPriority) {
                            'A' -> 1; 'B' -> 2; 'C' -> 3; else -> 0
                        },
                        onSelect = { onSetDefaultPriority(listOf(null, 'A', 'B', 'C')[it]) },
                        modifier = Modifier.width(200.dp),
                    )
                }
                RowDivider()
                SettingsRow(
                    label = "Notebook display name",
                    description = "Filename: shown by filename. Title: shown by title, falling back to filename.",
                ) {
                    SegmentedControl(
                        options = listOf("Filename", "Title"),
                        selectedIndex = settings.notebookDisplayNameMode.ordinal,
                        onSelect = { onSetNotebookDisplayNameMode(NotebookDisplayNameMode.entries[it]) },
                        modifier = Modifier.width(200.dp),
                    )
                }
                RowDivider()
                SettingsRow(
                    label = "Checklist states",
                    description = "2-state: [ ] → [x]. 3-state: [ ] → [-] → [x].",
                ) {
                    SegmentedControl(
                        options = listOf("2-state", "3-state"),
                        selectedIndex = settings.checklistStates.ordinal,
                        onSelect = { onSetChecklistStates(ChecklistStates.entries[it]) },
                        modifier = Modifier.width(160.dp),
                    )
                }
                RowDivider()
                ToggleRow(
                    label = "Add ID to new notes",
                    description = "Adds an ID property to the property drawer when creating new notes",
                    checked = settings.addIdToNewNotes,
                    onToggle = onSetAddId,
                )
                RowDivider()
                ToggleRow(
                    label = "Add CREATED timestamp",
                    description = "Adds a CREATED property with the current timestamp when creating new notes",
                    checked = settings.addCreatedToNewNotes,
                    onToggle = onSetAddCreated,
                )
            }

            SectionLabel("SHARING")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(
                        "Shared content target",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                    )
                    Text(
                        "The .org file that receives links and text shared into Grove",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                    OutlinedTextField(
                        value = shareFileText,
                        onValueChange = { shareFileText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp),
                        placeholder = { Text("inbox.org", fontFamily = PlexMono, color = c.ink3) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            SectionLabel("BACKUP")
            SettingsGroup {
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(
                        "Import or export your preferences as a JSON file. The sync"
                            + " folder isn't included — it stays on this device.",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                RowDivider()
                SettingsRow(
                    label = "Export settings",
                    onClick = { settingsExporter.launch("grove-settings.json") },
                ) {
                    Text("↑", fontFamily = PlexMono, fontSize = 14.sp, color = c.accent)
                }
                RowDivider()
                SettingsRow(
                    label = "Import settings",
                    // Providers report .json inconsistently; accept text-ish types too.
                    onClick = {
                        settingsImporter.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    },
                ) {
                    Text("↓", fontFamily = PlexMono, fontSize = 14.sp, color = c.accent)
                }
            }

            Text(
                "Grove v${com.rrajath.grove.BuildConfig.VERSION_NAME}",
                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
            )
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
        fontSize = 12.sp, letterSpacing = 1.sp,
        color = MaterialTheme.grove.accent,
        modifier = Modifier.padding(start = 4.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun SettingsGroup(content: @Composable () -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(15.dp)),
    ) {
        content()
    }
}

@Composable
private fun RowDivider() {
    HorizontalDivider(color = MaterialTheme.grove.line, modifier = Modifier.padding(horizontal = 15.dp))
}

@Composable
private fun SettingsRow(
    label: String,
    description: String? = null,
    onClick: (() -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Row(
        Modifier
            .fillMaxWidth()
            .then(clickMod)
            .padding(horizontal = 15.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, color = MaterialTheme.grove.ink,
            )
            if (description != null) {
                Text(
                    description,
                    fontFamily = PlexSans, fontSize = 12.sp, color = MaterialTheme.grove.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        trailing()
    }
}

@Composable
private fun TemplateSettingsRow(
    template: CaptureTemplate,
    onEdit: () -> Unit,
    onMoveUp: (() -> Unit)?,
    onMoveDown: (() -> Unit)?,
    onDelete: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(horizontal = 15.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(template.icon, fontFamily = PlexMono, fontSize = 15.sp, color = c.accent)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                template.name,
                fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, color = c.ink,
            )
            Text(
                "${template.targetFile} · ${template.location.describe()}",
                fontFamily = PlexMono, fontSize = 12.sp, color = c.ink2,
            )
        }
        SmallAction("↑", enabled = onMoveUp != null) { onMoveUp?.invoke() }
        SmallAction("↓", enabled = onMoveDown != null) { onMoveDown?.invoke() }
        SmallAction("✕", enabled = true, onClick = onDelete)
    }
}

@Composable
private fun SmallAction(glyph: String, enabled: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Text(
            glyph,
            fontFamily = PlexMono, fontSize = 13.sp,
            color = if (enabled) c.ink2 else c.line2,
        )
    }
}

@Composable
private fun ToggleRow(
    label: String,
    checked: Boolean,
    description: String? = null,
    onToggle: (Boolean) -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                label,
                fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, color = c.ink,
            )
            if (description != null) {
                Text(
                    description,
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        androidx.compose.material3.Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = androidx.compose.material3.SwitchDefaults.colors(
                checkedTrackColor = c.accent,
                checkedThumbColor = c.accentInk,
                uncheckedTrackColor = c.surface3,
                uncheckedThumbColor = c.surface,
            ),
        )
    }
}

/**
 * "Sync App Icon with Theme" toggle row plus a live 60x60 launcher-icon preview
 * (design/Grove.dc.html lines 827-841 — the tile mirrors `iconSpokesLauncher`,
 * `spokeSet(16, 7)`). The preview shows [theme]'s colors when [checked], the
 * default light mark otherwise.
 */
@Composable
private fun SyncAppIconRow(
    checked: Boolean,
    theme: ThemePreference,
    onToggle: (Boolean) -> Unit,
) {
    val c = MaterialTheme.grove
    val previewColors = if (checked) com.rrajath.grove.ui.theme.groveColorsFor(theme) else com.rrajath.grove.ui.theme.GroveLightColors
    Column(Modifier.padding(horizontal = 15.dp, vertical = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable { onToggle(!checked) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Sync App Icon with Theme",
                fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, color = c.ink,
                modifier = Modifier.weight(1f),
            )
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onToggle,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedTrackColor = c.accent,
                    checkedThumbColor = c.accentInk,
                    uncheckedTrackColor = c.surface3,
                    uncheckedThumbColor = c.surface,
                ),
            )
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(60.dp)
                    .shadow(3.dp, RoundedCornerShape(16.dp), clip = false, ambientColor = ShadowColor, spotColor = ShadowColor)
                    .clip(RoundedCornerShape(16.dp))
                    .background(previewColors.accentSoft)
                    .border(1.dp, previewColors.line, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.Canvas(Modifier.size(32.dp)) {
                    val spokeLength = size.minDimension / 2f
                    val spokeWidth = size.minDimension * (7f / 32f)
                    val centerOffset = androidx.compose.ui.geometry.Offset(size.width / 2f, size.height / 2f)
                    val topLeft = androidx.compose.ui.geometry.Offset(centerOffset.x - spokeWidth / 2f, centerOffset.y)
                    val spokeSize = androidx.compose.ui.geometry.Size(spokeWidth, spokeLength)
                    val cornerRadius = androidx.compose.ui.geometry.CornerRadius(spokeWidth / 2f)
                    for (angle in listOf(36f, 108f, 180f, 252f, 324f)) {
                        rotate(angle, pivot = centerOffset) {
                            drawRoundRect(
                                color = previewColors.accent,
                                topLeft = topLeft,
                                size = spokeSize,
                                cornerRadius = cornerRadius,
                            )
                        }
                    }
                }
            }
        }
    }
}

private val ShadowColor = androidx.compose.ui.graphics.Color(0x243C2D19) // rgba(60,45,25,0.14)

/** Friendly folder name from a SAF tree URI ("content://…/tree/primary%3Aorg" → "org"). */
private fun uriDisplayName(uri: String): String {
    val last = java.net.URLDecoder.decode(uri.substringAfterLast("/"), "UTF-8")
    return last.substringAfterLast(':').ifEmpty { last }
}
