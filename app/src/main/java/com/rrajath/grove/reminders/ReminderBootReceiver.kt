package com.rrajath.grove.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rrajath.grove.GroveApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * `AlarmManager` alarms don't survive a reboot: re-arm every stored reminder
 * (schedules future ones, immediately fires any that were missed while off),
 * plus the daily digest alarm.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val app = context.applicationContext as GroveApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                app.reminderReconciler.rearmAll()
                val settings = app.settingsRepository.settings.first()
                if (settings.remindersEnabled && settings.morningBriefEnabled) {
                    ReminderDigestScheduler.scheduleNext(context, settings.defaultReminderTime)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
