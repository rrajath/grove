package com.rrajath.grove.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.OrgMutations
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Handles the notification's "Complete" action headlessly (no open ViewModel):
 * loads the file's current text from the vault, re-locates the heading by its
 * stored composite key, sets its TODO keyword to the first done-type keyword,
 * saves, and triggers a sync — then cleans up the notification/alarm/row.
 */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_COMPLETE) return
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val app = context.applicationContext as GroveApplication
        val pending = goAsync()
        app.appScope.launch {
            try {
                complete(app, context, key)
            } finally {
                pending.finish()
            }
        }
    }

    private suspend fun complete(app: GroveApplication, context: Context, key: String) {
        val reminder = app.database.reminderDao().get(key) ?: return
        val doneKeyword = app.keywords.value.done.firstOrNull()
        // The broadcast itself may have cold-started the process, in which case
        // `vault` (Eagerly-shared over an async combine) is very likely still null
        // at this instant. Wait briefly for it to become non-null rather than
        // silently no-op-ing the mutation. goAsync() gives us a limited execution
        // budget, so bound the wait.
        val vault = withTimeoutOrNull(VAULT_WAIT_MILLIS) { app.vault.filterNotNull().first() }

        val completed = if (doneKeyword != null && vault != null) {
            val doc = vault.open(reminder.fileName)
            val headline = doc?.let { ReminderKeys.findHeadline(it, reminder.headingPath, reminder.headingLevel) }
            if (doc != null && headline != null) {
                val newText = OrgMutations.setKeyword(doc, headline, doneKeyword)
                vault.save(reminder.fileName, newText)
                app.syncManager.requestSync("reminder completed")
                true
            } else false
        } else false

        // Only tear down the notification/alarm/row after a CONFIRMED mutation+save.
        // If the mutation didn't happen (vault never became ready, heading couldn't be
        // relocated, no done keyword), leave the reminder row/alarm and the shown
        // notification intact so the user can re-tap Complete or re-open the app —
        // never lose the reminder without actually completing the task.
        if (completed) {
            ReminderNotification.cancel(context, reminder.notificationId)
            AlarmScheduler.cancel(context, reminder)
            app.database.reminderDao().delete(key)
        }
    }

    companion object {
        const val ACTION_COMPLETE = "com.rrajath.grove.action.REMINDER_COMPLETE"
        const val EXTRA_KEY = "key"

        /** Bounded wait for the vault on a cold-started process (goAsync budget is ~10s). */
        private const val VAULT_WAIT_MILLIS = 5_000L
    }
}
