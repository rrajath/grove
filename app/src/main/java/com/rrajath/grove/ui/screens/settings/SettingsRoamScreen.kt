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
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * Settings § Roam Features (experimental): a master toggle plus two sub-toggles
 * for the Linked References bar (backlinks) and inline auto-link suggestions.
 * Both sub-toggles are gated on [GroveSettings.roamFeaturesEnabled] as well as
 * their own stored value — see call sites in `OutlineScreen`/`ReadNoteScreen`/
 * `EditNoteScreen` (all `settings.roamFeaturesEnabled && settings.roamShowX`).
 */
@Composable
fun SettingsRoamScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetRoamFeaturesEnabled: (Boolean) -> Unit,
    onSetRoamShowBacklinks: (Boolean) -> Unit,
    onSetRoamShowSuggestions: (Boolean) -> Unit,
) {
    val c = MaterialTheme.grove
    SettingsPageScaffold(title = "Roam Features", onBack = onBack) {
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
            }
        }
    }
}
