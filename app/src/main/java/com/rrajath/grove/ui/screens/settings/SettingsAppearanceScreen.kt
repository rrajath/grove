package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NoteOpenMode
import com.rrajath.grove.settings.ThemePreference
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.components.ThemeDropdownPicker
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings § Look and Feel (was the "APPEARANCE" section before the settings-page split). */
@Composable
fun SettingsAppearanceScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetTheme: (ThemePreference) -> Unit,
    onSetSyncAppIconWithTheme: (Boolean) -> Unit,
    onSetShowPreface: (Boolean) -> Unit,
    onSetShowPropertyDrawers: (Boolean) -> Unit,
    onSetFontSize: (FontSizePreference) -> Unit,
    onSetNoteOpenMode: (NoteOpenMode) -> Unit,
) {
    val c = MaterialTheme.grove
    SettingsPageScaffold(title = "Look and Feel", onBack = onBack) {
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
                label = "Show preface",
                description = "Display file-level #+ keywords in org files",
                checked = settings.showPreface,
                onToggle = onSetShowPreface,
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
                description = if (settings.defaultNoteOpenMode == NoteOpenMode.READ) {
                    "Notes open in read mode"
                } else {
                    "Notes open in edit mode"
                },
            ) {
                SegmentedControl(
                    options = listOf("Read", "Edit"),
                    selectedIndex = settings.defaultNoteOpenMode.ordinal,
                    onSelect = { onSetNoteOpenMode(NoteOpenMode.entries[it]) },
                    modifier = Modifier.width(160.dp),
                )
            }
        }
    }
}

/**
 * "Sync App Icon with Theme" toggle row plus a live 60x60 launcher-icon preview
 * (design/Grove.dc.html lines 827-841: the tile mirrors `iconSpokesLauncher`,
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
