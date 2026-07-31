package com.rrajath.grove.ui.screens.settings

import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.capture.TemplatesViewModel

/** Settings § Capture Templates. */
@Composable
fun SettingsCaptureTemplatesScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onEditTemplate: (String?) -> Unit,
    onSetCaptureNotification: (Boolean) -> Unit,
    templatesViewModel: TemplatesViewModel = viewModel(factory = TemplatesViewModel.Factory),
) {
    val templates by templatesViewModel.templates.collectAsStateWithLifecycle()
    SettingsPageScaffold(title = "Capture Templates", onBack = onBack) {
        SettingsGroup {
            templates.forEachIndexed { i, template ->
                if (i > 0) RowDivider()
                TemplateSettingsRow(
                    template = template,
                    onEdit = { onEditTemplate(template.id) },
                    onMoveUp = if (i > 0) ({ templatesViewModel.move(template.id, -1) }) else null,
                    onMoveDown = if (i < templates.lastIndex) ({ templatesViewModel.move(template.id, +1) }) else null,
                    onDelete = { templatesViewModel.delete(template.id) },
                )
            }
            if (templates.isNotEmpty()) RowDivider()
            SettingsRow(label = "＋ New template", onClick = { onEditTemplate(null) }) {}
            RowDivider()
            val notifPermission = androidx.activity.compose.rememberLauncherForActivityResult(
                androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
            ) { granted -> if (granted) onSetCaptureNotification(true) }
            ToggleRow(
                label = "Capture from notification",
                checked = settings.captureNotification,
            ) { enabled ->
                if (enabled) notifPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                else onSetCaptureNotification(false)
            }
        }
    }
}
