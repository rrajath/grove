package com.rrajath.grove.ui.screens.settings

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.ui.support.setGroveContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings › Reminders' access rows render only while access is off: access state
 * is injected into the stateless [ReminderAccessRows], so no real permission is touched.
 */
@RunWith(AndroidJUnit4::class)
class ReminderAccessRowsTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private var exactTaps = 0
    private var notificationTaps = 0

    private fun content(access: ReminderAccess) {
        composeRule.setGroveContent {
            ReminderAccessRows(
                access = access,
                onAllowExactAlarms = { exactTaps++ },
                onAllowNotifications = { notificationTaps++ },
            )
        }
    }

    @Test
    fun rendersNothingWhenAllAccessGranted() {
        content(ReminderAccess(exactAlarmsOff = false, notificationsOff = false))

        composeRule.onAllNodesWithText("Exact timing").assertCountEquals(0)
        composeRule.onAllNodesWithText("Notifications").assertCountEquals(0)
    }

    @Test
    fun exactTimingOffShowsOnlyThatRow() {
        content(ReminderAccess(exactAlarmsOff = true, notificationsOff = false))

        composeRule.onNodeWithText("Exact timing").assertIsDisplayed()
        composeRule.onAllNodesWithText("Notifications").assertCountEquals(0)
        composeRule.onNodeWithText("Allow").performClick()
        assertEquals(1, exactTaps)
        assertEquals(0, notificationTaps)
    }

    @Test
    fun notificationsOffShowsOnlyThatRow() {
        content(ReminderAccess(exactAlarmsOff = false, notificationsOff = true))

        composeRule.onNodeWithText("Notifications").assertIsDisplayed()
        composeRule.onAllNodesWithText("Exact timing").assertCountEquals(0)
        composeRule.onNodeWithText("Allow").performClick()
        assertEquals(0, exactTaps)
        assertEquals(1, notificationTaps)
    }

    @Test
    fun bothOffShowsBothRowsEachWithItsOwnAction() {
        content(ReminderAccess(exactAlarmsOff = true, notificationsOff = true))

        composeRule.onAllNodesWithText("Allow").assertCountEquals(2)
        composeRule.onNodeWithText("Exact timing").performClick()
        composeRule.onNodeWithText("Notifications").performClick()
        assertEquals(1, exactTaps)
        assertEquals(1, notificationTaps)
    }
}
