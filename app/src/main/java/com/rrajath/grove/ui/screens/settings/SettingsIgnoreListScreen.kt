package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * Settings § Notebooks § Ignore list: files/folders/patterns that are never
 * scanned, indexed, or synced. See internal/ignore-list-feature/00-overview.md.
 */
@Composable
fun SettingsIgnoreListScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetIgnoreList: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    var listText by remember(settings.ignoreList) { mutableStateOf(settings.ignoreList) }

    // Commit on leave, however that happens (back gesture included) — same
    // rationale as SettingsNotesScreen's todoKeywords field.
    DisposableEffect(Unit) {
        onDispose {
            if (listText != settings.ignoreList) onSetIgnoreList(listText)
        }
    }

    SettingsPageScaffold(title = "Ignore list", onBack = onBack) {
        SettingsGroup {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                Text(
                    "All .org files in this vault and its subfolders are indexed " +
                        "automatically. The files, folders, and patterns listed below " +
                        "are skipped entirely and never scanned.",
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                    modifier = Modifier.padding(bottom = 10.dp),
                )
                if (settings.ignoreListImportedFromFile) {
                    Text(
                        "Imported from .orgzlyignore.",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
                OutlinedTextField(
                    value = listText,
                    onValueChange = { listText = it },
                    singleLine = false,
                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp),
                    modifier = Modifier.fillMaxWidth().height(220.dp),
                )
            }
        }
    }
}
