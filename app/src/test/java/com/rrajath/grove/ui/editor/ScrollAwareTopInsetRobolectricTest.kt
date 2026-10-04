package com.rrajath.grove.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.rrajath.grove.testing.TestGroveApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Scrolling a focused editor whose cursor sits at the end of the text must not snap
 * back to the cursor when the scroll passes through the top inset's shrinking range.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestGroveApplication::class, qualifiers = "w411dp-h891dp")
class ScrollAwareTopInsetRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setUp(withInset: Boolean): ScrollState {
        val text = (1..80).joinToString("\n") { "Line $it of a long capture draft" }
        val state = TextFieldState(text, TextRange(text.length))
        val focusRequester = FocusRequester()
        lateinit var scrollState: ScrollState
        composeRule.setContent {
            scrollState = rememberScrollState()
            Box(Modifier.height(400.dp)) {
                BasicTextField(
                    state = state,
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    scrollState = scrollState,
                    modifier = Modifier
                        .fillMaxSize()
                        .let { if (withInset) it.scrollAwareTopInset(scrollState, 20.dp) else it }
                        .focusRequester(focusRequester)
                        .testTag("field"),
                )
            }
        }
        composeRule.runOnIdle { focusRequester.requestFocus() }
        composeRule.waitForIdle()
        // Cursor at the end: the field starts scrolled to the bottom.
        assertTrue(scrollState.value > 0)
        assertEquals(scrollState.maxValue, scrollState.value)
        return scrollState
    }

    private fun scrollTo(scrollState: ScrollState, px: Int) {
        composeRule.runOnIdle { runBlocking { scrollState.scrollTo(px) } }
        composeRule.waitForIdle()
    }

    @Test
    fun scrollingNearTopWithoutInsetStays() {
        val scrollState = setUp(withInset = false)
        scrollTo(scrollState, 5)
        assertEquals(5, scrollState.value)
    }

    @Test
    fun scrollingFarFromTopWithInsetStays() {
        val scrollState = setUp(withInset = true)
        scrollTo(scrollState, 200)
        assertEquals(200, scrollState.value)
    }

    @Test
    fun scrollingIntoInsetRangeDoesNotSnapBackToCursor() {
        val scrollState = setUp(withInset = true)
        scrollTo(scrollState, 200)
        scrollTo(scrollState, 5)
        assertEquals(5, scrollState.value)
    }

    @Test
    fun gapShowsAtTopAndScrollsAway() {
        val scrollState = setUp(withInset = true)
        scrollTo(scrollState, 0)
        assertEquals(20.dp, composeRule.onNodeWithTag("field").getBoundsInRoot().top)
        scrollTo(scrollState, 200)
        assertEquals(0.dp, composeRule.onNodeWithTag("field").getBoundsInRoot().top)
    }
}
