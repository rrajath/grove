package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * Debug-only tools, reached from Settings › Developer (that entry is gated on
 * `BuildConfig.DEBUG`). For now just the "NEW" feature-badge reset used to test
 * the badge trail; structured so future debug actions slot in as more rows.
 */
@Composable
fun SettingsDeveloperScreen(
    onBack: () -> Unit,
    onResetNewBadges: () -> Unit,
) {
    val c = MaterialTheme.grove
    var confirmation by remember { mutableStateOf<String?>(null) }

    SettingsPageScaffold(title = "Developer", onBack = onBack) {
        SectionLabel("NEW BADGES")
        SettingsGroup {
            SettingsRow(
                label = "Reset New badges",
                description = "Re-arm every New badge so the trail shows again",
                onClick = {
                    onResetNewBadges()
                    confirmation = "New badges re-armed. Open the drawer to see them."
                },
            ) {
                Text("↺", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink2)
            }
        }
        confirmation?.let {
            Spacer(Modifier.height(12.dp))
            Text(
                it,
                fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
    }
}
