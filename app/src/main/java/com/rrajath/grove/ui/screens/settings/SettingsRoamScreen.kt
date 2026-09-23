package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
    directories: List<String>,
    onBack: () -> Unit,
    onSetRoamFeaturesEnabled: (Boolean) -> Unit,
    onSetRoamShowBacklinks: (Boolean) -> Unit,
    onSetRoamShowSuggestions: (Boolean) -> Unit,
    onSetRoamOpenWholeFile: (Boolean) -> Unit,
    onSetDailiesDirectory: (String) -> Unit,
    onSetDailiesFilenamePattern: (String) -> Unit,
    onSetDailiesHeaderTemplate: (String) -> Unit,
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
                RowDivider()
                SettingsRow(label = "Dailies location", description = "Where daily notes are created; blank = vault root.") {}
                Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 0.dp)) {
                    com.rrajath.grove.ui.components.DirectoryField(
                        value = settings.dailiesDirectory,
                        onValueChange = onSetDailiesDirectory,
                        directories = directories,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                RowDivider()
                SettingsRow(label = "File name pattern", description = "FilenamePattern tokens, e.g. %<%Y-%m-%d>.org.") {}
                Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp)) {
                    val patternError = com.rrajath.grove.capture.FilenamePattern.errorFor(settings.dailiesFilenamePattern)
                    androidx.compose.material3.OutlinedTextField(
                        value = settings.dailiesFilenamePattern,
                        onValueChange = onSetDailiesFilenamePattern,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        isError = patternError != null,
                        supportingText = {
                            if (patternError != null) {
                                Text(patternError, color = c.red, fontFamily = PlexSans, fontSize = 12.sp)
                            }
                        },
                        textStyle = androidx.compose.ui.text.TextStyle(fontFamily = com.rrajath.grove.ui.theme.PlexMono),
                    )
                }
                RowDivider()
                SettingsRow(label = "Header template", description = "Expanded into a new day's file. %? marks the cursor.") {}
                Box(Modifier.fillMaxWidth().padding(horizontal = 15.dp, vertical = 6.dp)) {
                    val headerInvalid = remember(settings.dailiesHeaderTemplate) {
                        com.rrajath.grove.capture.PlaceholderExpander.findInvalid(settings.dailiesHeaderTemplate)
                            .map { it.token }.distinct()
                    }
                    Column {
                        androidx.compose.material3.OutlinedTextField(
                            value = settings.dailiesHeaderTemplate,
                            onValueChange = onSetDailiesHeaderTemplate,
                            modifier = Modifier.fillMaxWidth().height(120.dp),
                            isError = headerInvalid.isNotEmpty(),
                            textStyle = androidx.compose.ui.text.TextStyle(
                                fontFamily = com.rrajath.grove.ui.theme.PlexMono, fontSize = 13.5.sp,
                            ),
                        )
                        if (headerInvalid.isNotEmpty()) {
                            Text(
                                "Unsupported placeholder${if (headerInvalid.size > 1) "s" else ""}: " +
                                    headerInvalid.joinToString(", "),
                                fontFamily = PlexSans, fontSize = 12.sp, color = c.red,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
