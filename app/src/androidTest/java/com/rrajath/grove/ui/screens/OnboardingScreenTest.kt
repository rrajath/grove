package com.rrajath.grove.ui.screens

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.ui.support.setGroveContent
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
 */
@RunWith(AndroidJUnit4::class)
class OnboardingScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

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
        composeRule.onNodeWithText("Choose a local folder").assertIsDisplayed()
        composeRule.onNodeWithText("I'll set this up later").assertIsDisplayed()
    }

    @Test
    fun tappingLaterSkipsOnboarding() {
        var done = false
        var picked: String? = null
        content(onDone = { done = true }, onFolderPicked = { picked = it })

        composeRule.onNodeWithText("I'll set this up later").performClick()

        composeRule.waitUntil(timeoutMillis = 3_000) { done }
        assertNull("folder was not picked", picked)
    }

    @Test
    fun tappingChooseFolderOpensThePickerWithoutFiringCallbacks() {
        var done = false
        var picked: String? = null
        content(onDone = { done = true }, onFolderPicked = { picked = it })

        composeRule.onNodeWithText("Choose a local folder").performClick()
        composeRule.waitForIdle()

        // The launcher opens; neither callback fires until a folder URI comes back.
        assertEquals(false, done)
        assertNull(picked)
    }
}
