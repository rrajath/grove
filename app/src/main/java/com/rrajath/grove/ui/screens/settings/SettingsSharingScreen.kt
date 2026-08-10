package com.rrajath.grove.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.capture.FilenameValidation
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.capture.TemplatesViewModel
import com.rrajath.grove.ui.components.NotebookFileField
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Settings § Sharing. */
@Composable
fun SettingsSharingScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetShareTargetFile: (String) -> Unit,
    templatesViewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val notebooks by templatesViewModel.notebooks.collectAsStateWithLifecycle()
    var shareFileText by remember(settings.shareTargetFile) {
        mutableStateOf(settings.shareTargetFile)
    }

    // Apply a pending share-target edit when the screen leaves composition, however that
    // happens, so back doesn't drop it. A BackHandler would work too but steals the whole
    // predictive-back gesture from NavHost, breaking the previous screen's preview animation.
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            if (shareFileText != settings.shareTargetFile && FilenameValidation.errorFor(shareFileText) == null) {
                onSetShareTargetFile(shareFileText)
            }
        }
    }

    SettingsPageScaffold(title = "Sharing", onBack = onBack) {
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
                NotebookFileField(
                    value = shareFileText,
                    onValueChange = { shareFileText = it },
                    notebooks = notebooks,
                    placeholder = "inbox.org",
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}
