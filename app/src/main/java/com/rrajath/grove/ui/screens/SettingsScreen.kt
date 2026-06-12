package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NoteOpenMode
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
) {
    val c = MaterialTheme.grove
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
                PlaceholderRow("Template management arrives with Capture (M3)")
            }

            SectionLabel("SYNC")
            SettingsGroup {
                PlaceholderRow("Repositories and auto-sync arrive with Sync (M4)")
            }

            SectionLabel("NOTES")
            SettingsGroup {
                PlaceholderRow("TODO keywords and note options arrive with the editor (M5)")
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
private fun PlaceholderRow(text: String) {
    Text(
        text,
        fontFamily = PlexSans, fontSize = 13.sp, color = MaterialTheme.grove.ink3,
        modifier = Modifier.padding(15.dp),
    )
}
