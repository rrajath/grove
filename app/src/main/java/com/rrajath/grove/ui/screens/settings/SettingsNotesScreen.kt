package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.ChecklistStates
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.NotebookDisplayNameMode
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings § Notes. */
@Composable
fun SettingsNotesScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetTodoKeywords: (String) -> Unit,
    onSetDefaultPriority: (Char?) -> Unit,
    onSetNotebookDisplayNameMode: (NotebookDisplayNameMode) -> Unit,
    onSetChecklistStates: (ChecklistStates) -> Unit,
    onSetAddId: (Boolean) -> Unit,
    onSetAddCreated: (Boolean) -> Unit,
) {
    val c = MaterialTheme.grove
    var keywordsText by remember(settings.todoKeywords) {
        mutableStateOf(settings.todoKeywords)
    }

    // Apply a pending TODO-keywords edit on leave so back doesn't drop it.
    fun leave() {
        if (keywordsText != settings.todoKeywords && keywordsText.isNotBlank()) {
            onSetTodoKeywords(keywordsText)
        }
        onBack()
    }
    androidx.activity.compose.BackHandler { leave() }

    SettingsPageScaffold(title = "Notes", onBack = ::leave) {
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
                    optionIcons = listOf(Icons.Filled.Block, null, null, null),
                    modifier = Modifier.width(200.dp),
                )
            }
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
            RowDivider()
            SettingsRow(
                label = "Checklist states",
                descriptionContent = { ChecklistStatesDescription(settings.checklistStates) },
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
                description = "Adds an ID property while creating new notes",
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
    }
}
