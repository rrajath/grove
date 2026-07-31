package com.rrajath.grove.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rrajath.grove.GroveApplication
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * Fires once daily at the configured default reminder time: bundles every
 * overdue/due-today reminder into one "You have X tasks due today"
 * notification (see [ReminderDigest]) instead of the flood of individual
 * "due now" notifications date-only reminders would otherwise produce, then
 * reschedules itself for tomorrow.
 */
class ReminderDigestReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext as GroveApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                val settings = app.settingsRepository.settings.first()
                if (settings.remindersEnabled) {
                    val reminders = app.database.reminderDao().all()
                    val count = ReminderDigest.count(reminders, LocalDate.now())
                    if (count > 0) ReminderNotification.showDigest(context, count)
                    ReminderDigestScheduler.scheduleNext(context, settings.defaultReminderTime)
                }
            } finally {
                pending.finish()
            }
        }
    }
}
