package com.rrajath.grove.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import com.rrajath.grove.data.ReminderEntity

/**
 * Thin `AlarmManager` wrapper: permission checks plus schedule/cancel for a
 * single [ReminderEntity]. Falls back to an inexact alarm when exact-alarm
 * access isn't granted; still better than dropping the reminder entirely.
 */
object AlarmScheduler {

    fun hasNotificationPermission(context: Context): Boolean {
        // Below API 33 POST_NOTIFICATIONS isn't a runtime-gated permission at all;
        // checkSelfPermission always reports it denied there, which would otherwise
        // strand every reminder in pendingPermission with no dialog for Grant to show.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
    }

    fun canScheduleExactAlarms(context: Context): Boolean {
        // Below API 31 exact alarms aren't gated by a runtime permission at all.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.canScheduleExactAlarms() ?: false
    }

    fun schedule(context: Context, reminder: ReminderEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntentFor(context, reminder, create = true) ?: return
        if (canScheduleExactAlarms(context)) {
            // Exact-alarm access can be revoked between the check above and this call
            // (TOCTOU); setExactAndAllowWhileIdle would then throw SecurityException
            // inside a receiver-driven coroutine. Fall back to the inexact path.
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
            }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pending)
        }
    }

    fun cancel(context: Context, reminder: ReminderEntity) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pending = pendingIntentFor(context, reminder, create = false) ?: return
        am.cancel(pending)
        pending.cancel()
    }

    private fun pendingIntentFor(context: Context, reminder: ReminderEntity, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderAlarmReceiver::class.java)
            .putExtra(ReminderAlarmReceiver.EXTRA_KEY, reminder.key)
        val flags = PendingIntent.FLAG_IMMUTABLE or
                (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, reminder.notificationId, intent, flags)
    }
}
