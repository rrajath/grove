package com.rrajath.grove.icon

import android.content.Context
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.settings.ThemePreference

/**
 * Ties notification chrome to the "Sync App Icon with Theme" setting.
 *
 * Android draws a notification's small icon as an alpha mask and tints it with
 * `NotificationCompat.Builder.setColor`, so the *color* — not the drawable — is
 * what makes the notification icon track the launcher icon. Every
 * `ic_launcher_foreground_*` variant is the same five-spoke path differing only
 * in fill, so matching the fill matches the icon.
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
}
