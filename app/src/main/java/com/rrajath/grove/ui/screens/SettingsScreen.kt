package com.rrajath.grove.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.R
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.newbadge.NewAnchors
import com.rrajath.grove.ui.newbadge.NewDot
import com.rrajath.grove.ui.screens.settings.RowDivider
import com.rrajath.grove.ui.screens.settings.SectionLabel
import com.rrajath.grove.ui.screens.settings.SettingsGroup
import com.rrajath.grove.ui.screens.settings.SettingsRow
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove

private data class SettingsPage(val title: String, val description: String, val onClick: () -> Unit)

/**
 * Settings hub (design spec §11, split into one page per section): a plain
 * list of pages rather than one long scroll, so each destination (Look and
 * Feel, Capture Templates, etc.) is its own back-stack entry.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAppearance: () -> Unit,
    onOpenNotebooks: () -> Unit,
    onOpenCaptureTemplates: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenAgenda: () -> Unit,
    onOpenReminders: () -> Unit,
    onOpenSharing: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenBugReport: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenDeveloper: () -> Unit,
) {
    val c = MaterialTheme.grove
    val pages = listOf(
        SettingsPage("Look and Feel", "Theme, app icon, font size", onOpenAppearance),
        SettingsPage("Notebooks", "File icons, folder flattening, display names, sort order", onOpenNotebooks),
        SettingsPage("Capture Templates", "Quick-capture targets and shortcuts", onOpenCaptureTemplates),
        SettingsPage("Sync", "Folder, auto-sync, sync log", onOpenSync),
        SettingsPage("Notes", "TODO keywords, priorities, note display", onOpenNotes),
        SettingsPage("Agenda", "Swipe actions on agenda rows", onOpenAgenda),
        SettingsPage("Reminders", "Notifications for SCHEDULED/DEADLINE", onOpenReminders),
        SettingsPage("Sharing", "Where shared content lands", onOpenSharing),
        SettingsPage("Backup", "Export/import your preferences", onOpenBackup),
    )

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    Text(
                        "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = c.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            SettingsGroup {
                pages.forEachIndexed { i, page ->
                    if (i > 0) RowDivider()
                    SettingsRow(label = page.title, description = page.description, onClick = page.onClick) {
                        Text("›", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink2)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            SectionLabel("HELP")
            SettingsGroup {
                SettingsRow(
                    label = "Report a bug",
                    description = "Send an email or create an issue on GitHub",
                    onClick = onOpenBugReport,
                ) {
                    Text("›", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink2)
                }
                RowDivider()
                SettingsRow(
                    label = "Tips & Tricks",
                    description = "Shortcuts and gestures hiding in the app",
                    labelBadge = { NewDot(NewAnchors.SETTINGS_TIPS) },
                    onClick = onOpenTips,
                ) {
                    Text("›", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink2)
                }
            }

            if (com.rrajath.grove.BuildConfig.DEBUG) {
                Spacer(Modifier.height(10.dp))
                SectionLabel("DEVELOPER")
                SettingsGroup {
                    SettingsRow(
                        label = "Developer tools",
                        description = "Reset New badges and other debug actions",
                        onClick = onOpenDeveloper,
                    ) {
                        Text("›", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink2)
                    }
                }
            }

            Text(
                "${stringResource(R.string.app_name)} v${com.rrajath.grove.BuildConfig.VERSION_NAME}",
                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 20.dp),
            )
        }
    }
}
