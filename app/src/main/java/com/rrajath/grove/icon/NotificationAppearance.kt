package com.rrajath.grove.icon

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.settings.ThemePreference

/**
 * Ties notification chrome to the "Sync App Icon with Theme" setting.
 *
 * [markColor] feeds `NotificationCompat.Builder.setColor`. Where the shade draws
 * a notification's small icon as an alpha mask tinted with that color, matching
 * the launcher mark's fill matches the icon — every `ic_launcher_foreground_*`
 * is the same five-spoke path differing only in fill.
 *
 * Android 16+ redesigned that: the shade renders the *app's* icon instead, and
 * resolves it from the package's `<application android:icon>`. Neither `setColor`
 * nor the `activity-alias` switching in [AppIconManager] moves it — measured on
 * an API 37 device, with the alias on one theme, `setColor` on a second, and an
 * overridden `android.appInfo` extra on a third, all three were ignored in
 * favour of the manifest icon. So on those versions the shade icon stays the
 * default mark no matter what this object does, and there is no app-side lever
 * to change it short of shipping a different manifest icon.
 */
object NotificationAppearance {

    /**
     * Current mark tint. Reads a `StateFlow`'s cached value, so it is safe to
     * call from a `BroadcastReceiver` or service on any thread without
     * suspending. Falls back to the default mark when the app object isn't a
     * [GroveApplication] (unit tests, isolated process).
     */
    fun markColor(context: Context): Int =
        (context.applicationContext as? GroveApplication)?.notificationMarkColor?.value
            ?: AppIconManager.markColor(enabled = false, theme = ThemePreference.LIGHT)

    /**
     * Re-tints notifications that are *already* in the shade to the current
     * [markColor].
     *
     * A notification's color is baked in when it is posted, so switching themes
     * only affected notifications posted afterwards: the launcher icon changed
     * instantly while a reminder sitting in the shade (they live up to 72h) and
     * the ongoing capture notification kept the old theme's mark. Reposting the
     * same `Notification` object with only `color` changed preserves its title,
     * actions, and pending intents — nothing has to be rebuilt from the entity
     * that produced it, which matters for reminders whose `ReminderEntity` isn't
     * at hand here.
     */
    fun retintActive(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val color = markColor(context)
        val active = try {
            nm.activeNotifications
        } catch (e: SecurityException) {
            // Notification access can be revoked out from under us.
            return
        }
        for (sbn in active) {
            val notification = sbn.notification
            // The system posts its own autogroup summary on our behalf; it is
            // rebuilt from the children, so reposting it here would fight it.
            if (notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) continue
            if (notification.color == color) continue
            notification.color = color
            // These already alerted when first posted — a re-post purely to
            // recolor must not buzz or pop a second heads-up.
            notification.flags = notification.flags or Notification.FLAG_ONLY_ALERT_ONCE
            nm.notify(sbn.tag, sbn.id, notification)
        }
    }
}
