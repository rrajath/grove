package com.rrajath.grove.capture

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.rrajath.grove.GroveApplication
import kotlinx.coroutines.launch

/**
 * Share-sheet target for ACTION_SEND (PRD §10). Sharing into Grove used to always
 * bring [com.rrajath.grove.MainActivity] to the foreground, because it was the
 * declared ACTION_SEND target and had to be created (and its Compose content set)
 * to run the save — this activity is the target instead, declared in the manifest
 * with `Theme.NoDisplay` and never calling `setContent`, so no window is ever
 * actually shown. [ShareIntake.consumeShare] runs on [GroveApplication.appScope]
 * rather than any activity-scoped scope (same reasoning as
 * [com.rrajath.grove.ui.reminders.RescheduleActivity]'s write), so finishing here
 * immediately can't cancel a page-title fetch or file write still in flight.
 */
class ShareReceiverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as GroveApplication
        val payload = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type?.startsWith("text/") == true }
            ?.let {
                SharedPayload.from(
                    subject = it.getStringExtra(Intent.EXTRA_SUBJECT),
                    sharedText = it.getStringExtra(Intent.EXTRA_TEXT),
                )
            }
        if (payload != null && !payload.isEmpty) {
            app.appScope.launch { ShareIntake.consumeShare(app, payload) }
        }
        finish()
    }
}
