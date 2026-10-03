package com.rrajath.grove.ui.reminders

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.core.net.toUri

/**
 * System screens for reminder access, shared by Settings › Reminders' rows and the
 * one-time exact-alarm dialog. Every launch falls back to the app's system info
 * page if nothing handles the specific action.
 */
internal object ReminderAccessIntents {

    /** "Alarms & reminders" for this app (API 31+; a no-op below, where access is always granted). */
    fun openExactAlarmSettings(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        launchOrAppDetails(
            context,
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, "package:${context.packageName}".toUri()),
        )
    }

    /** The app's notification page on API 26+; that action doesn't exist on 23-25, so app info there. */
    fun openNotificationSettings(context: Context) {
        val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        } else {
            appDetailsIntent(context)
        }
        launchOrAppDetails(context, intent)
    }

    private fun appDetailsIntent(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, "package:${context.packageName}".toUri())

    private fun launchOrAppDetails(context: Context, intent: Intent) {
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(appDetailsIntent(context))
        }
    }
}
