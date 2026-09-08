package com.rrajath.grove.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.ui.support.setGroveContent
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Layer-2 UI coverage for [OnboardingScreen] (see
 * internal/test-suite-02-ui-compose.md § OnboardingScreen). The screen is
 * stateless — no ViewModel, no vault — so it renders directly with recorded
 * callbacks. The SAF folder picker result itself is a Layer-3 (Maestro)
 * concern; here we only assert the CTA opens it without prematurely firing
 * either callback.
 *
 * The screen is one `verticalScroll` Column, so on a short viewport (the
 * default CI emulator AVD) the actions sit below the fold — every action node
 * is reached with `performScrollTo()` before being asserted or clicked.
 *
 * [tappingChooseFolderOpensThePickerWithoutFiringCallbacks] launches the real
 * SAF `OpenDocumentTree` activity (DocumentsUI). It MUST be dismissed before
 * the test process exits: an orphaned picker task, torn down while the next
 * test's process is forking, gets that process SIGKILLed by ActivityManager
 * ("remove task") — which surfaces as a "Test instrumentation process crashed"
 * on whatever test ran next. [tearDown] is the safety net.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    @After
    fun tearDown() {
        // Close anything this test stacked on top of the host activity (the SAF
        // picker); harmless when there's nothing to dismiss.
        runCatching { Espresso.pressBackUnconditionally() }
        runCatching { composeRule.waitForIdle() }
    }

    private fun content(
        onDone: () -> Unit = {},
        onFolderPicked: (String) -> Unit = {},
    ) {
        composeRule.setGroveContent {
            OnboardingScreen(onDone = onDone, onFolderPicked = onFolderPicked)
        }
    }

    @Test
    fun rendersTheBrandCopyAndBothActions() {
        content()
        composeRule.onNodeWithText("Your org-mode notes, at home on your phone.").assertIsDisplayed()
        composeRule.onNodeWithText("Choose a local folder").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("I'll set this up later").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun tappingLaterSkipsOnboarding() {
        var done = false
        var picked: String? = null
        content(onDone = { done = true }, onFolderPicked = { picked = it })

        composeRule.onNodeWithText("I'll set this up later").performScrollTo().performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { done }
        assertNull("folder was not picked", picked)
    }

    @Test
    fun tappingChooseFolderOpensThePickerWithoutFiringCallbacks() {
        var done = false
        var picked: String? = null
        content(onDone = { done = true }, onFolderPicked = { picked = it })

        composeRule.onNodeWithText("Choose a local folder").performScrollTo().performClick()
        composeRule.waitForIdle()

        // The launcher opens; neither callback fires until a folder URI comes back.
        assertEquals(false, done)
        assertNull(picked)

        // Dismiss the SAF picker now so it can't outlive this test process.
        Espresso.pressBackUnconditionally()
        composeRule.waitForIdle()
    }
}
