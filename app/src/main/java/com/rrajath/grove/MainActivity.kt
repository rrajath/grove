package com.rrajath.grove

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.rrajath.grove.capture.SharedPayload
import com.rrajath.grove.ui.GroveApp

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        consumeShareIntent(intent)
        setContent {
            // Expose Compose testTags as Android resource-ids so Macrobenchmark /
            // UiAutomator can target views by tag (e.g. By.res("outline_list")).
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                GroveApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consumeShareIntent(intent)
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
