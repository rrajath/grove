package com.rrajath.grove.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import com.rrajath.grove.MainActivity
import com.rrajath.grove.R
import com.rrajath.grove.data.ReminderEntity
import com.rrajath.grove.icon.NotificationAppearance
import com.rrajath.grove.settings.ReminderLeadTime
import com.rrajath.grove.ui.components.orgInlinePlainText
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.reminders.RescheduleActivity

/**
 * The "<heading> is due now" notification: title = heading text, body = fixed
 * copy, with Complete/Reschedule actions. Separate high-importance channel
 * from `capture-shortcut` (PRD/CLAUDE.md notification pattern) so it heads-up.
 */
object ReminderNotification {

    private const val CHANNEL_ID = "reminders"

    /** Negative so it can never collide with a [ReminderKeys.notificationId] CRC32 hash (always >= 0). */
    private const val DIGEST_NOTIFICATION_ID = -1000

    fun canShow(context: Context): Boolean = AlarmScheduler.hasNotificationPermission(context)

    fun show(context: Context, reminder: ReminderEntity) {
        if (!canShow(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )

        val contentIntent = PendingIntent.getActivity(
            context, reminder.notificationId,
            Intent(Intent.ACTION_VIEW, contentUri(reminder)).setClass(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NotificationAppearance.markColor(context))
            .setContentTitle(orgInlinePlainText(reminder.headingTitle))
            .setContentText(ReminderLeadTime.fromStorage(reminder.leadTime).dueMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .addAction(0, "Complete", completeAction(context, reminder))
            .addAction(0, "Reschedule", rescheduleAction(context, reminder))
            .build()
        nm.notify(reminder.notificationId, notification)
    }

    fun cancel(context: Context, notificationId: Int) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId)
    }

    /**
     * The daily digest: "You have X tasks due today", bundling every
     * overdue/due-today reminder (see [ReminderDigest]) into one notification
     * instead of firing each date-only reminder individually. Tapping opens
     * the Agenda screen rather than a specific note, since it summarizes many.
     */
    fun showDigest(context: Context, count: Int) {
        if (!canShow(context)) return
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        )

        val contentIntent = PendingIntent.getActivity(
            context, DIGEST_NOTIFICATION_ID,
            Intent(Intent.ACTION_VIEW, "grove://agenda".toUri()).setClass(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(NotificationAppearance.markColor(context))
            .setContentTitle("You have $count ${if (count == 1) "task" else "tasks"} due today")
            .setContentText("Tap to see what's on your plate.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
        nm.notify(DIGEST_NOTIFICATION_ID, notification)
    }

    private fun completeAction(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_COMPLETE)
            .putExtra(ReminderActionReceiver.EXTRA_KEY, reminder.key)
        return PendingIntent.getBroadcast(
            context, reminder.notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * "Reschedule" opens [RescheduleActivity] (its own task, not Grove's), so
     * picking a date and confirming returns the user to whatever app they pulled
     * the shade down from, rather than leaving them parked in Grove's editor.
     */
    private fun rescheduleAction(context: Context, reminder: ReminderEntity): PendingIntent =
        PendingIntent.getActivity(
            context, reminder.notificationId,
            RescheduleActivity.intent(context, reminder.key),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    /**
     * Tapping the notification body (as opposed to its "Reschedule" action)
     * lands on the due heading in read mode. `ReminderResolveScreen` turns the
     * composite key into the heading's current line index.
     */
    private fun contentUri(reminder: ReminderEntity): android.net.Uri =
        ("grove://reminder/${Routes.encode(reminder.fileName)}" +
                "?headingPath=${Routes.encode(reminder.headingPath)}" +
                "&level=${reminder.headingLevel}").toUri()
}
