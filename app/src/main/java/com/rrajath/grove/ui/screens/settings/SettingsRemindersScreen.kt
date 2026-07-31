package com.rrajath.grove.ui.screens.settings

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
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.components.ReminderPermissionBanner
import com.rrajath.grove.ui.components.SimpleTimePicker
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Settings § Reminders. */
@Composable
fun SettingsRemindersScreen(
    settings: GroveSettings,
    onBack: () -> Unit,
    onSetRemindersEnabled: (Boolean) -> Unit,
    onSetDefaultReminderTime: (LocalTime) -> Unit,
    reminderPendingCount: Int,
) {
    val c = MaterialTheme.grove
    var showReminderTimePicker by remember { mutableStateOf(false) }

    SettingsPageScaffold(title = "Reminders", onBack = onBack) {
        ReminderPermissionBanner(pendingCount = reminderPendingCount, modifier = Modifier.padding(bottom = 10.dp))
        SettingsGroup {
            ToggleRow(
                label = "Enable reminders",
                description = "Notify when a note's SCHEDULED or DEADLINE time arrives",
                checked = settings.remindersEnabled,
                onToggle = onSetRemindersEnabled,
            )
            if (settings.remindersEnabled) {
                RowDivider()
                SettingsRow(
                    label = "Default reminder time",
                    description = "Used for SCHEDULED/DEADLINE stamps with no time of day",
                    onClick = { showReminderTimePicker = true },
                ) {
                    Text(
                        settings.defaultReminderTime.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)),
                        fontFamily = PlexMono, fontSize = 13.sp, color = c.accent,
                    )
                }
            }
        }
    }

    if (showReminderTimePicker) {
        SimpleTimePicker(
            initial = settings.defaultReminderTime,
            onDismiss = { showReminderTimePicker = false },
            onConfirm = { time ->
                onSetDefaultReminderTime(time)
                showReminderTimePicker = false
            },
        )
    }
}
