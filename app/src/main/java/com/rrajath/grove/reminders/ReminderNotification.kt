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
import com.rrajath.grove.ui.nav.Routes

/**
 * The "<heading> is due now" notification: title = heading text, body = fixed
 * copy, with Complete/Reschedule actions. Separate high-importance channel
 * from `capture-shortcut` (PRD/CLAUDE.md notification pattern) so it heads-up.
 */
object ReminderNotification {

    private const val CHANNEL_ID = "reminders"

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
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.headingTitle)
            .setContentText("Your task is due now")
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

    private fun completeAction(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(context, ReminderActionReceiver::class.java)
            .setAction(ReminderActionReceiver.ACTION_COMPLETE)
            .putExtra(ReminderActionReceiver.EXTRA_KEY, reminder.key)
        return PendingIntent.getBroadcast(
            context, reminder.notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    private fun rescheduleAction(context: Context, reminder: ReminderEntity): PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, rescheduleUri(reminder))
            .setClass(context, MainActivity::class.java)
        return PendingIntent.getActivity(
            context, reminder.notificationId, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }

    /**
     * `grove://reminder/{fileName}?headingPath=&level=&type=&notifId=` — resolved
     * by `ReminderResolveScreen` into the note's current line, then handed to
     * `EditNoteScreen` to open [PlanningDatePicker] for [reminder]'s planning type.
     */
    private fun rescheduleUri(reminder: ReminderEntity): android.net.Uri =
        ("grove://reminder/${Routes.encode(reminder.fileName)}" +
                "?headingPath=${Routes.encode(reminder.headingPath)}" +
                "&level=${reminder.headingLevel}" +
                "&type=${reminder.planningType}" +
                "&notifId=${reminder.notificationId}").toUri()

    /**
     * Tapping the notification body (as opposed to its "Reschedule" action)
     * should just land on the due heading, not auto-open [PlanningDatePicker] —
     * an empty `type` makes `ReminderResolveScreen` resolve the same heading
     * while `GroveApp`'s `takeIf { isNotBlank() }` drops the blank planning arg.
     */
    private fun contentUri(reminder: ReminderEntity): android.net.Uri =
        ("grove://reminder/${Routes.encode(reminder.fileName)}" +
                "?headingPath=${Routes.encode(reminder.headingPath)}" +
                "&level=${reminder.headingLevel}" +
                "&type=").toUri()
}
