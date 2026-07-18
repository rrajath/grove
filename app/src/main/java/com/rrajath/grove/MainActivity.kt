package com.rrajath.grove

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.rrajath.grove.capture.SharedPayload
import com.rrajath.grove.ui.GroveApp

class MainActivity : ComponentActivity() {
    // grove:// deep links (shortcuts, widget, notification) arrive as the
    // launch Intent on a cold start or via onNewIntent on a warm one
    // (singleTask launchMode). Navigation Compose does not consume the
    // hosting Activity's Intent on its own, so it's threaded through as
    // Compose state and handed to NavController.handleDeepLink explicitly.
    private var deepLinkIntent by mutableStateOf<Intent?>(null)

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShareIntent(intent)
        deepLinkIntent = intent
        setContent {
            // Expose Compose testTags as Android resource-ids so Macrobenchmark /
            // UiAutomator can target views by tag (e.g. By.res("outline_list")).
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                GroveApp(deepLinkIntent = deepLinkIntent)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
        deepLinkIntent = intent
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type?.startsWith("text/") == true) {
            val payload = SharedPayload.from(
                subject = intent.getStringExtra(Intent.EXTRA_SUBJECT),
                sharedText = intent.getStringExtra(Intent.EXTRA_TEXT),
            )
            if (!payload.isEmpty) {
                (application as GroveApplication).pendingShare.value = payload
            }
        }
    }
}
