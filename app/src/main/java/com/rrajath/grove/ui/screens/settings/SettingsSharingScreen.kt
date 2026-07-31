package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings § Sharing. */
@Composable
fun SettingsSharingScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetShareTargetFile: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    var shareFileText by remember(settings.shareTargetFile) {
        mutableStateOf(settings.shareTargetFile)
    }

    // Apply a pending share-target edit on leave so back doesn't drop it.
    fun leave() {
        if (shareFileText != settings.shareTargetFile && shareFileText.isNotBlank()) {
            onSetShareTargetFile(shareFileText)
        }
        onBack()
    }
    androidx.activity.compose.BackHandler { leave() }

    SettingsPageScaffold(title = "Sharing", onBack = ::leave) {
        SettingsGroup {
            Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                Text(
                    "Shared content target",
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 14.5.sp, color = c.ink,
                )
                Text(
                    "The .org file that receives links and text shared into Grove",
                    fontFamily = PlexSans, fontSize = 12.sp, color = c.ink3,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                OutlinedTextField(
                    value = shareFileText,
                    onValueChange = { shareFileText = it },
                    singleLine = true,
                    textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.sp),
                    placeholder = { Text("inbox.org", fontFamily = PlexMono, color = c.ink3) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
