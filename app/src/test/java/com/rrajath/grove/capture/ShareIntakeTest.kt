package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareIntakeTest {

    @Test
    fun `url uses the fetched page title as the link description`() {
        val note = ShareIntake.composeNote(
            SharedPayload(text = "", url = "https://example.com/post"),
            resolvedTitle = "A Great Post",
        )
        assertEquals("[[https://example.com/post][A Great Post]]", note.heading)
        assertNull(note.body)
    }

    @Test
    fun `url falls back to shared text then the url when no title resolved`() {
        val withSubject = ShareIntake.composeNote(
            SharedPayload(text = "Subject line", url = "https://example.com"),
            resolvedTitle = null,
        )
        assertEquals("[[https://example.com][Subject line]]", withSubject.heading)

        val bare = ShareIntake.composeNote(
            SharedPayload(text = "", url = "https://example.com"),
            resolvedTitle = null,
        )
        assertEquals("[[https://example.com][https://example.com]]", bare.heading)
    }

    @Test
    fun `square brackets in the title are neutralised so the link stays valid`() {
        val note = ShareIntake.composeNote(
            SharedPayload(text = "", url = "https://example.com"),
            resolvedTitle = "Guide [2026] to [things]",
        )
        assertEquals("[[https://example.com][Guide (2026) to (things)]]", note.heading)
    }

    @Test
    fun `long text becomes an empty heading with the text as body`() {
        val long = "x".repeat(ShareIntake.TEXT_HEADING_LIMIT + 1)
        val note = ShareIntake.composeNote(SharedPayload(text = long, url = ""), resolvedTitle = null)
        assertEquals("", note.heading)
        assertEquals(long, note.body)
    }

    @Test
    fun `short text becomes the heading`() {
        val note = ShareIntake.composeNote(
            SharedPayload(text = "Quick thought", url = ""),
            resolvedTitle = null,
        )
        assertEquals("Quick thought", note.heading)
        assertNull(note.body)
    }

    @Test
    fun `text at exactly the limit stays a heading`() {
        val exact = "y".repeat(ShareIntake.TEXT_HEADING_LIMIT)
        val note = ShareIntake.composeNote(SharedPayload(text = exact, url = ""), resolvedTitle = null)
        assertEquals(exact, note.heading)
        assertNull(note.body)
    }
}
