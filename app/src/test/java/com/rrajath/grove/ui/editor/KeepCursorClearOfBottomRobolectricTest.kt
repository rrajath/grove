package com.rrajath.grove.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.testing.TestGroveApplication
import com.rrajath.grove.ui.theme.GroveDarkColors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Typing at the end of a long draft keeps the last line [EditorBottomMarginLines] clear
 * of the field's bottom edge, using the highlighter's display-only blank lines.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = TestGroveApplication::class, qualifiers = "w411dp-h891dp")
class KeepCursorClearOfBottomRobolectricTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val text = (1..80).joinToString("\n") { "Line $it of a long capture draft" }
    private val state = TextFieldState(text, TextRange(text.length))
    private lateinit var scrollState: ScrollState
    private var layout: TextLayoutResult? = null

    private fun setUp() {
        val focusRequester = FocusRequester()
        composeRule.setContent {
            scrollState = rememberScrollState()
            var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
            KeepCursorClearOfBottom(state, scrollState, { textLayout }, EditorBottomMarginLines)
            Box(Modifier.height(400.dp)) {
                BasicTextField(
                    state = state,
                    outputTransformation = remember {
                        OrgSyntaxHighlight(
                            GroveDarkColors,
                            OrgKeywords(listOf("TODO"), listOf("DONE")),
                            trailingBlankLines = EditorBottomMarginLines,
                        )
                    },
                    lineLimits = TextFieldLineLimits.MultiLine(),
                    scrollState = scrollState,
                    onTextLayout = { getResult ->
                        textLayout = getResult()
                        layout = textLayout
                    },
                    modifier = Modifier.fillMaxSize().focusRequester(focusRequester),
                )
            }
        }
        composeRule.runOnIdle { focusRequester.requestFocus() }
        composeRule.waitForIdle()
    }

    /** Gap between the cursor line's bottom and the viewport's bottom, in lines. */
    private fun linesClearOfBottom(): Float {
        val l = layout!!
        val line = l.getLineForOffset(state.selection.end)
        val lineHeight = l.getLineBottom(line) - l.getLineTop(line)
        val viewportBottom = scrollState.value + scrollState.viewportSize
        return (viewportBottom - l.getLineBottom(line)) / lineHeight
    }

    @Test
    fun typingAtTheEndLiftsTheLastLineOffTheBottomEdge() {
        setUp()
        composeRule.runOnIdle {
            state.edit {
                append(" and more")
                placeCursorAtEnd()
            }
        }
        composeRule.waitForIdle()
        assertEquals(scrollState.maxValue, scrollState.value)
        assertEquals(EditorBottomMarginLines.toFloat(), linesClearOfBottom(), 0.05f)
    }

    @Test
    fun blankLinesAreDisplayOnly() {
        setUp()
        composeRule.runOnIdle { state.edit { append("!") } }
        composeRule.waitForIdle()
        assertEquals("$text!", state.text.toString())
        assertTrue(layout!!.layoutInput.text.text.endsWith("!\n\n"))
    }

    @Test
    fun scrollingAloneNeverNudges() {
        setUp()
        composeRule.runOnIdle { runBlocking { scrollState.scrollTo(100) } }
        composeRule.waitForIdle()
        assertEquals(100, scrollState.value)
    }
}
