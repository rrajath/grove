package com.rrajath.grove.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rrajath.grove.GroveApplication
import kotlinx.coroutines.launch

/** Fired by `AlarmManager` at a reminder's trigger time: shows the "due now" notification. */
class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val app = context.applicationContext as GroveApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                val reminder = app.database.reminderDao().get(key) ?: return@launch
                // Date-only reminders (no explicit time-of-day) don't fire their own
                // notification; they're counted into the daily digest instead.
                if (reminder.hasExplicitTime) ReminderNotification.show(context, reminder)
                app.database.reminderDao().markFired(key, System.currentTimeMillis())
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_KEY = "key"
    }
}
