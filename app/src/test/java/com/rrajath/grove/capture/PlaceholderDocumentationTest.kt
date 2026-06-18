package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import java.time.LocalDateTime

/**
 * Verifies that every placeholder shown in the capture info dialog actually expands
 * to a non-trivial value, guarding against regressions in [PlaceholderExpander].
 */
class PlaceholderDocumentationTest {

    private val ctx = CaptureContext(
        now = LocalDateTime.of(2025, 6, 11, 14, 32, 0),
        clipboard = "clipboard text",
        sharedText = "shared text",
        sharedUrl = "https://example.com",
    )

    private fun expand(key: String) = PlaceholderExpander.expand(key, ctx).text

    @Test fun `%U expands to active datetime stamp`() {
        assertEquals("<2025-06-11 Wed 14:32>", expand("%U"))
    }

    @Test fun `%u expands to inactive datetime stamp`() {
        assertEquals("[2025-06-11 Wed 14:32]", expand("%u"))
    }

    @Test fun `%T expands to active date stamp`() {
        assertEquals("<2025-06-11 Wed>", expand("%T"))
    }

    @Test fun `%t expands to inactive date stamp`() {
        assertEquals("[2025-06-11 Wed]", expand("%t"))
    }

    @Test fun `%date expands to ISO date`() {
        assertEquals("2025-06-11", expand("%date"))
    }

    @Test fun `%time expands to HH colon MM`() {
        assertEquals("14:32", expand("%time"))
    }

    @Test fun `%day expands to day name`() {
        assertEquals("Wednesday", expand("%day"))
    }

    @Test fun `%month expands to month name`() {
        assertEquals("June", expand("%month"))
    }

    @Test fun `%year expands to 4-digit year`() {
        assertEquals("2025", expand("%year"))
    }

    @Test fun `%clipboard expands to clipboard text`() {
        assertEquals("clipboard text", expand("%clipboard"))
    }

    @Test fun `%shared_text expands to shared text`() {
        assertEquals("shared text", expand("%shared_text"))
    }

    @Test fun `%shared_url expands to shared URL`() {
        assertEquals("https://example.com", expand("%shared_url"))
    }

    @Test fun `%cursor produces empty text with cursor at 0`() {
        val result = PlaceholderExpander.expand("%cursor", ctx)
        assertEquals("", result.text)
        assertEquals(0, result.cursorOffset)
    }

    @Test fun `%? is an alias for %cursor`() {
        val cursor = PlaceholderExpander.expand("%cursor", ctx)
        val question = PlaceholderExpander.expand("%?", ctx)
        assertEquals(cursor.text, question.text)
        assertEquals(cursor.cursorOffset, question.cursorOffset)
    }

    @Test fun `%^{Prompt} is replaced by provided prompt value`() {
        val ctxWithPrompt = ctx.copy(promptValues = mapOf("Prompt" to "answer"))
        val result = PlaceholderExpander.expand("%^{Prompt}", ctxWithPrompt)
        assertEquals("answer", result.text)
    }

    @Test fun `none of the documented placeholders pass through verbatim`() {
        val passThrough = listOf(
            "%U", "%u", "%T", "%t", "%date", "%time", "%day", "%month", "%year",
            "%clipboard", "%shared_text", "%shared_url",
        ).filter { expand(it) == it }
        assertEquals("These placeholders should expand: $passThrough", emptyList<String>(), passThrough)
    }
}
