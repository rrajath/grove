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
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.settings.ReminderLeadTime
import com.rrajath.grove.ui.components.DropdownPicker
import com.rrajath.grove.ui.components.ReminderPermissionBanner
import com.rrajath.grove.ui.components.SimpleTimePicker
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
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
    onSetMorningBriefEnabled: (Boolean) -> Unit,
    onSetDefaultReminderTime: (LocalTime) -> Unit,
    onSetReminderLeadTime: (ReminderLeadTime) -> Unit,
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
                Column(Modifier.padding(horizontal = 15.dp, vertical = 10.dp)) {
                    Text(
                        "Notify me",
                        fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                        fontSize = 14.5.sp, color = c.ink,
                        modifier = Modifier.padding(bottom = 2.dp),
                    )
                    Text(
                        "When a task with a specific time is due",
                        fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                    DropdownPicker(
                        options = ReminderLeadTime.entries.map { it.label },
                        selectedIndex = settings.reminderLeadTime.ordinal,
                        onSelect = { onSetReminderLeadTime(ReminderLeadTime.entries[it]) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            RowDivider()
            ToggleRow(
                label = "Morning Brief",
                description = "Organize your day",
                checked = settings.morningBriefEnabled,
                onToggle = onSetMorningBriefEnabled,
            )
            if (settings.morningBriefEnabled) {
                RowDivider()
                SettingsRow(
                    label = "Send reminder at",
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
