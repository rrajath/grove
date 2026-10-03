package com.rrajath.grove.ui.reminders

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.R
import com.rrajath.grove.ui.support.setGroveContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ExactAlarmPromptDialogTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var allows = 0
    private var notNows = 0

    private fun content() = composeRule.setGroveContent {
        ExactAlarmPromptDialog(onAllow = { allows++ }, onNotNow = { notNows++ })
    }

    @Test
    fun bodyNamesTheAppFromResourcesNotHardcoded() {
        content()
        val appName = composeRule.activity.getString(R.string.app_name)

        composeRule.onNodeWithText("Get reminders on time").assertIsDisplayed()
        composeRule.onNodeWithText("turn on Alarms & reminders for $appName.", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun allowAndNotNowEachFireTheirOwnCallback() {
        content()

        composeRule.onNodeWithText("Allow").performClick()
        composeRule.onNodeWithText("Not now").performClick()

        assertEquals(1, allows)
        assertEquals(1, notNows)
    }
}
