package com.rrajath.grove.ui.agenda

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.ui.support.ScreenTestEnv
import com.rrajath.grove.ui.support.setGroveContent
import com.rrajath.grove.ui.vault.NoteRef
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * Layer-2 UI coverage for [AgendaScreen] (see internal/test-suite-02-ui-compose.md).
 *
 * A heading with a bare active timestamp and no TODO keyword is an event, not a
 * task: the agenda renders it with no "mark done" checkbox, while a real task on
 * the same day keeps its checkbox.
 */
@RunWith(AndroidJUnit4::class)
class AgendaScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var env: ScreenTestEnv
    private val vm by lazy { env.agendaViewModel() }

    private val today: LocalDate = LocalDate.now()

    private fun orgDate(d: LocalDate): String =
        "<$d ${d.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() }}>"

    private val vault = """
        #+TITLE: Agenda events fixture

        * TODO Ship the release
        SCHEDULED: ${orgDate(today)}
        * Team offsite
        ${orgDate(today)}
    """.trimIndent() + "\n"

    @Before
    fun setUp() {
        env = ScreenTestEnv(vaultFiles = mapOf("events.org" to vault))
    }

    @After
    fun tearDown() {
        env.close()
    }

    private fun content(onOpenNote: (NoteRef) -> Unit = {}) {
        composeRule.setGroveContent {
            AgendaScreen(onBack = {}, onOpenNote = onOpenNote, viewModel = vm)
        }
    }

    @Test
    fun eventRowHasNoCheckboxWhileTheTaskRowDoes() {
        content()

        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText("Team offsite").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("Ship the release").assertIsDisplayed()
        composeRule.onNodeWithText("Team offsite").assertIsDisplayed()

        // One checkbox: the task's. The bare-timestamp event has none.
        assertEquals(
            1,
            composeRule.onAllNodesWithTag("agenda_checkbox", useUnmergedTree = true)
                .fetchSemanticsNodes().size,
        )
    }
}
