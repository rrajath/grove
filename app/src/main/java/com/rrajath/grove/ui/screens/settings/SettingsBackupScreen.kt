package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings § Backup. */
@Composable
fun SettingsBackupScreen(
    onBack: () -> Unit,
    onExportSettings: (android.net.Uri) -> Unit,
    onImportSettings: (android.net.Uri) -> Unit,
) {
    val c = MaterialTheme.grove
    val settingsExporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> if (uri != null) onExportSettings(uri) }

    val settingsImporter = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) onImportSettings(uri) }

    SettingsPageScaffold(title = "Backup", onBack = onBack) {
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
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 15.dp, vertical = 14.dp),
            ) {
                BackupSecondaryButton(
                    label = "Export settings",
                    modifier = Modifier.weight(1f),
                    onClick = { settingsExporter.launch("grove-settings.json") },
                )
                Spacer(Modifier.width(10.dp))
                BackupSecondaryButton(
                    label = "Import settings",
                    modifier = Modifier.weight(1f),
                    // Providers report .json inconsistently; accept text-ish types too.
                    onClick = {
                        settingsImporter.launch(
                            arrayOf("application/json", "text/plain", "application/octet-stream")
                        )
                    },
                )
            }
        }
    }
}

/**
 * Secondary button matching [com.rrajath.grove.ui.screens.ConflictScreen]'s `SecondaryButton` styling
 * (46dp height, 13dp corner radius, `surface` bg + 1dp `line` border,
 * PlexSans SemiBold 14sp `ink` text) for the paired Export/Import buttons.
 */
@Composable
private fun BackupSecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, color = c.ink,
        )
    }
}
