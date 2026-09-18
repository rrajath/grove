package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.newbadge.MarkNewFeatureSeen
import com.rrajath.grove.ui.newbadge.NewAnchors
import com.rrajath.grove.ui.newbadge.NewDot
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.WHOLE_FILE_LINE_LIMIT

/**
 * Settings § Roam Features (experimental): a master toggle plus three sub-toggles
 * for the Linked References bar (backlinks), inline auto-link suggestions, and
 * opening small files as one note. Every sub-toggle is gated on
 * [GroveSettings.roamFeaturesEnabled] as well as its own stored value — see call
 * sites in `GroveApp` (all `settings.roamFeaturesEnabled && settings.roamX`).
 */
@Composable
fun SettingsRoamScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetRoamFeaturesEnabled: (Boolean) -> Unit,
    onSetRoamShowBacklinks: (Boolean) -> Unit,
    onSetRoamShowSuggestions: (Boolean) -> Unit,
    onSetRoamOpenWholeFile: (Boolean) -> Unit,
) {
    val c = MaterialTheme.grove
    SettingsPageScaffold(title = "Roam Features", onBack = onBack) {
        // Leaving this screen retires the "Open roam files directly in read mode"
        // dot from its whole trail (menu glyph, drawer, Settings hub row, the row itself).
        MarkNewFeatureSeen(NewAnchors.SETTINGS_ROAM_WHOLE_FILE)

        Column(Modifier.padding(bottom = 10.dp)) {
            Text(
                "These are experimental features and are subject to change.",
                fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
            )
        }
        SettingsGroup {
            ToggleRow(
                label = "Roam Features",
                checked = settings.roamFeaturesEnabled,
                description = "Adds backlinks (a Linked References bar showing notes that reference " +
                    "this one) and auto suggestions for files or headings as you type.",
                onToggle = onSetRoamFeaturesEnabled,
            )
            if (settings.roamFeaturesEnabled) {
                RowDivider()
                ToggleRow(
                    label = "Show backlinks",
                    checked = settings.roamShowBacklinks,
                    description = "Show the Linked References bar in Outline, Read, and Edit mode.",
                    onToggle = onSetRoamShowBacklinks,
                )
                RowDivider()
                ToggleRow(
                    label = "Show suggestions while typing",
                    checked = settings.roamShowSuggestions,
                    description = "Suggest matching files or headings to link to as you type in Edit mode.",
                    onToggle = onSetRoamShowSuggestions,
                )
                RowDivider()
                ToggleRow(
                    label = "Open roam files directly in read mode",
                    checked = settings.roamOpenWholeFile,
                    description = "For org-roam files (ones with a file-level :ID:) of up to " +
                        "$WHOLE_FILE_LINE_LIMIT lines, opening the notebook skips the outline and " +
                        "shows the whole file in your default note mode, like a single note. Larger " +
                        "and non-roam files still open in the outline. Turning this off opens roam " +
                        "files in the outline too, regardless of size.",
                    labelBadge = { NewDot(NewAnchors.SETTINGS_ROAM_WHOLE_FILE, Modifier.padding(start = 6.dp)) },
                    onToggle = onSetRoamOpenWholeFile,
                )
            }
        }
    }
}
