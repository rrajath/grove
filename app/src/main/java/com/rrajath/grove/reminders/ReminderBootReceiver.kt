package com.rrajath.grove.reminders

import android.app.AlarmManager
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
 *
 * Also receives `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (API 31+,
 * sent only on a grant) and hands off to [ExactAlarmAccessWatcher], so alarms
 * armed inexact before the grant switch to exact. The system sender passes this
 * receiver's `RECEIVE_BOOT_COMPLETED` permission gate.
 */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED
        ) return
        val app = context.applicationContext as GroveApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                if (action == Intent.ACTION_BOOT_COMPLETED) {
                    app.reminderReconciler.rearmAll()
                    val settings = app.settingsRepository.settings.first()
                    if (settings.remindersEnabled && settings.morningBriefEnabled) {
                        ReminderDigestScheduler.scheduleNext(context, settings.defaultReminderTime)
                    }
                } else {
                    app.exactAlarmAccessWatcher.check()
                }
            } finally {
                pending.finish()
            }
        }
    }
}
