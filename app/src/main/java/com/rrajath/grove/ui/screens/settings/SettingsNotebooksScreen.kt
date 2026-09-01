package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NotebookDisplayNameMode
import com.rrajath.grove.ui.components.SegmentedControl

/**
 * Settings § Notebooks: everything that shapes the Notebooks list itself. Split
 * out from § Look and Feel — the file-icon toggle, folder flattening and the
 * display-name mode moved here, joined by the sort-order controls.
 */
@Composable
fun SettingsNotebooksScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetShowNotebookFileIcons: (Boolean) -> Unit,
    onSetFlattenNotebookFolders: (Boolean) -> Unit,
    onSetNotebookDisplayNameMode: (NotebookDisplayNameMode) -> Unit,
) {
    SettingsPageScaffold(title = "Notebooks", onBack = onBack) {
        SettingsGroup {
            ToggleRow(
                label = "Show file icons in notebooks",
                description = "Display the icon tile on each row of the notebooks list",
                checked = settings.showNotebookFileIcons,
                onToggle = onSetShowNotebookFileIcons,
            )
            RowDivider()
            ToggleRow(
                label = "Flatten folders",
                description = "Hide folders and list every note as one flat list, each showing its folder path",
                checked = settings.flattenNotebookFolders,
                onToggle = onSetFlattenNotebookFolders,
            )
            RowDivider()
            SettingsRow(
                label = "Notebook display name",
                description = if (settings.notebookDisplayNameMode == NotebookDisplayNameMode.FILENAME) {
                    "Notebooks are displayed by their filenames"
                } else {
                    "Notebooks are displayed by their titles, falling back to filename"
                },
            ) {
                SegmentedControl(
                    options = listOf("Filename", "Title"),
                    selectedIndex = settings.notebookDisplayNameMode.ordinal,
                    onSelect = { onSetNotebookDisplayNameMode(NotebookDisplayNameMode.entries[it]) },
                    modifier = Modifier.width(200.dp),
                )
            }
        }
    }
}
