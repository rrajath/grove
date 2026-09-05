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
import com.rrajath.grove.ui.GroveApp

class MainActivity : ComponentActivity() {
    // grove:// deep links (shortcuts, widget, notification) arrive as the
    // launch Intent on a cold start or via onNewIntent on a warm one
    // (singleTask launchMode). Navigation Compose does not consume the
    // hosting Activity's Intent on its own, so it's threaded through as
    // Compose state and handed to NavController.handleDeepLink explicitly.
    //
    // It is a *pending* deep link only until it has been navigated once:
    // GroveApp calls back into onDeepLinkConsumed() to clear it. Without that,
    // any later Activity recreation (process death, or a configuration change
    // not covered by android:configChanges) re-reads getIntent() and
    // re-dispatches the original grove:// target -- e.g. the capture sheet the
    // user already cancelled would reappear.
    private var deepLinkIntent by mutableStateOf<Intent?>(null)

    // True only for the Intent that created this Activity instance fresh (no
    // Notebooks screen has been shown to the user yet). A capture opened from
    // that Intent should close by finishing the Activity -- landing the user
    // back wherever they were before Grove -- rather than by popping the nav
    // back stack, which would reveal Notebooks underneath (see GroveApp's
    // closeActivityOnExit). A warm-started deep link (onNewIntent) arrives on
    // top of a real, user-visited back stack, so popping is correct there.
    private var deepLinkIsColdStart = false

    @OptIn(ExperimentalComposeUiApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Only a genuine cold start carries a pending deep link. After process
        // death savedInstanceState is non-null and the launch Intent is stale;
        // configuration changes (rotation, dark mode, font scale, ...) no longer
        // recreate this Activity at all -- see android:configChanges in the
        // manifest -- so in-progress editor/capture state survives them.
        if (savedInstanceState == null) {
            deepLinkIntent = intent
            deepLinkIsColdStart = true
        }
        setContent {
            // Expose Compose testTags as Android resource-ids so Macrobenchmark /
            // UiAutomator can target views by tag (e.g. By.res("outline_list")).
            Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = true }) {
                GroveApp(
                    deepLinkIntent = deepLinkIntent,
                    deepLinkIsColdStart = deepLinkIsColdStart,
                    onDeepLinkConsumed = { consumed ->
                        // Ignore a stale callback: if onNewIntent has already
                        // swapped in a newer deep link while this one was still
                        // resolving, that newer Intent must not be cleared.
                        if (deepLinkIntent === consumed) {
                            deepLinkIntent = null
                            // Replace the stored Intent so a later getIntent()
                            // (e.g. from a recreation that slips past
                            // configChanges) can't resurrect the same deep link.
                            setIntent(Intent())
                        }
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkIntent = intent
    }
}
