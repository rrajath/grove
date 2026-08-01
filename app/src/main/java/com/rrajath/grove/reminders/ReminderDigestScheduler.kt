package com.rrajath.grove.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * `AlarmManager` wrapper for the daily reminder digest ("You have X tasks due
 * today"): a single recurring alarm at the configured default reminder time,
 * independent of the per-heading exact-time alarms [AlarmScheduler] manages.
 * [ReminderDigestReceiver] reschedules the next day's fire itself on receipt,
 * the same one-shot-then-reschedule pattern the rest of the reminders feature
 * uses rather than `AlarmManager.setRepeating` (which drifts and can't be
 * exact past API 19).
 */
object ReminderDigestScheduler {

    /** Negative so it can never collide with a [ReminderKeys.notificationId] CRC32 hash (always >= 0). */
    private const val REQUEST_CODE = -1000

    fun scheduleNext(context: Context, defaultReminderTime: LocalTime, zone: ZoneId = ZoneId.systemDefault()) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, create = true) ?: return
        val triggerAt = nextTriggerMillis(defaultReminderTime, zone)
        if (AlarmScheduler.canScheduleExactAlarms(context)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            }
        } else {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = pendingIntent(context, create = false) ?: return
        am.cancel(pending)
        pending.cancel()
    }

    /** Today's occurrence of [defaultReminderTime] if it hasn't passed yet, else tomorrow's. */
    internal fun nextTriggerMillis(defaultReminderTime: LocalTime, zone: ZoneId, now: LocalDateTime = LocalDateTime.now(zone)): Long {
        var next = LocalDateTime.of(now.toLocalDate(), defaultReminderTime)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return next.atZone(zone).toInstant().toEpochMilli()
    }

    private fun pendingIntent(context: Context, create: Boolean): PendingIntent? {
        val intent = Intent(context, ReminderDigestReceiver::class.java)
        val flags = PendingIntent.FLAG_IMMUTABLE or
                (if (create) PendingIntent.FLAG_UPDATE_CURRENT else PendingIntent.FLAG_NO_CREATE)
        return PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
    }
}
