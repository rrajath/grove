package com.rrajath.grove.ui.components

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.ui.support.setGroveContent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Layer-2 UI coverage for the "Dates B — tabs" [PlanningDatesScreen]: the
 * three-way tab control and the ACTIVE tab's timestamp list + Apply path.
 */
@RunWith(AndroidJUnit4::class)
class PlanningDatesScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val event = LocalDate.now().plusDays(9)

    @Test
    fun rendersTheThreeTabs() {
        composeRule.setGroveContent {
            PlanningDatesScreen(
                title = "Pay rent",
                scheduled = null,
                deadline = null,
                active = emptyList(),
                focus = PlanningKind.SCHEDULED,
                onDismiss = {},
                onConfirm = { _, _, _ -> },
            )
        }
        composeRule.onNodeWithText("◷ SCHEDULED").assertIsDisplayed()
        composeRule.onNodeWithText("⚑ DEADLINE").assertIsDisplayed()
        composeRule.onNodeWithText("● ACTIVE").assertIsDisplayed()
    }

    @Test
    fun activeTabListsAPrefilledEventAndAppliesIt() {
        var applied: List<OrgTimestamp>? = null
        composeRule.setGroveContent {
            PlanningDatesScreen(
                title = "Team offsite",
                scheduled = null,
                deadline = null,
                active = listOf(OrgTimestamp(event)),
                focus = PlanningKind.ACTIVE,
                onDismiss = {},
                onConfirm = { _, _, active -> applied = active },
            )
        }

        // The ACTIVE tab opens focused: its count line and the stamp chip show.
        composeRule.onNodeWithText("1 timestamp", substring = true).assertIsDisplayed()
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Apply dates").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Apply dates").performClick()

        assertEquals(listOf(event), applied?.map { it.date })
    }

    @Test
    fun presetChipsShowOnLoadWithNothingSetAndApply() {
        var applied: OrgTimestamp? = null
        composeRule.setGroveContent {
            PlanningDatesScreen(
                title = "Pay rent",
                scheduled = null,
                deadline = null,
                active = emptyList(),
                focus = PlanningKind.SCHEDULED,
                onDismiss = {},
                onConfirm = { scheduled, _, _ -> applied = scheduled },
            )
        }

        // No date set yet, but the presets are visible immediately.
        composeRule.onNodeWithText("Today ·", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Today ·", substring = true).performClick()

        composeRule.onNodeWithText("Apply dates").performClick()
        assertEquals(LocalDate.now(), applied?.date)
    }

    @Test
    fun switchingTabsMovesTheAccent() {
        composeRule.setGroveContent {
            PlanningDatesScreen(
                title = "Pay rent",
                scheduled = OrgTimestamp(LocalDate.now().plusDays(2)),
                deadline = null,
                active = emptyList(),
                focus = PlanningKind.SCHEDULED,
                onDismiss = {},
                onConfirm = { _, _, _ -> },
            )
        }
        // SCHEDULED starts focused: its hint is shown.
        composeRule.onNodeWithText("One day only", substring = true).assertIsDisplayed()

        composeRule.onNodeWithText("● ACTIVE").performClick()

        // The ACTIVE hint replaces the single-day one.
        composeRule.waitUntil(timeoutMillis = 3_000) {
            composeRule.onAllNodesWithText("Long-press and drag", substring = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
    }
}
