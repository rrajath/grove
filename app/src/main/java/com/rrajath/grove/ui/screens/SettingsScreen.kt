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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.ui.capture.TemplatesViewModel
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NoteOpenMode
import com.rrajath.grove.settings.SyncMode
import com.rrajath.grove.settings.ThemePreference
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings per design spec §11. M1: Appearance group functional; the rest land with their milestones. */
@Composable
fun SettingsScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
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
    templatesViewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val templates by templatesViewModel.templates.collectAsState()
    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
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
                SettingsRow(label = "Theme") {
                    SegmentedControl(
                        options = listOf("System", "Light", "Dark"),
                        selectedIndex = settings.theme.ordinal,
                        onSelect = { onSetTheme(ThemePreference.entries[it]) },
                        modifier = Modifier.width(220.dp),
                    )
                }
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
                val modeLabel = if (settings.defaultNoteOpenMode == NoteOpenMode.READ) "Read" else "Edit"
                SettingsRow(
                    label = "Default note mode",
                    onClick = {
                        onSetNoteOpenMode(
                            if (settings.defaultNoteOpenMode == NoteOpenMode.READ) NoteOpenMode.EDIT
                            else NoteOpenMode.READ
                        )
                    },
                ) {
                    Text(
                        "$modeLabel ›",
                        fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
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
            }

            SectionLabel("SYNC")
            SettingsGroup {
                SettingsRow(label = "Folder") {
                    Text(
                        settings.vaultTreeUri?.let { uriDisplayName(it) } ?: "not set",
                        fontFamily = PlexMono, fontSize = 12.sp, color = c.ink2,
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
                    var keywordsText by remember(settings.todoKeywords) {
                        mutableStateOf(settings.todoKeywords)
                    }
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
                ToggleRow(
                    label = "Add ID to new notes",
                    checked = settings.addIdToNewNotes,
                    onToggle = onSetAddId,
                )
                RowDivider()
                ToggleRow(
                    label = "Add CREATED timestamp",
                    checked = settings.addCreatedToNewNotes,
                    onToggle = onSetAddCreated,
                )
            }

            Text(
                "com.rrajath.grove",
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
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.Medium,
            fontSize = 14.5.sp, color = MaterialTheme.grove.ink,
            modifier = Modifier.weight(1f),
        )
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
private fun ToggleRow(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onToggle(!checked) }
            .padding(horizontal = 15.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
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
}

/** Friendly folder name from a SAF tree URI ("content://…/tree/primary%3Aorg" → "org"). */
private fun uriDisplayName(uri: String): String {
    val last = java.net.URLDecoder.decode(uri.substringAfterLast("/"), "UTF-8")
    return last.substringAfterLast(':').ifEmpty { last }
}

@Composable
private fun PlaceholderRow(text: String) {
    Text(
        text,
        fontFamily = PlexSans, fontSize = 13.sp, color = MaterialTheme.grove.ink3,
        modifier = Modifier.padding(15.dp),
    )
}
