package com.rrajath.grove.ui.vault

import com.rrajath.grove.org.OrgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Regression coverage for the "favorites become inaccessible after an external edit"
 * bug: [NoteRef] round-tripping its optional customId, and [headlineFor] resolving by
 * that id instead of a raw line number that can drift once a `.org` file is edited
 * outside the app (Grove's primary edit path).
 */
class NoteRefTest {

    // --- encode/decode round trip ---

    @Test
    fun `encode omits the id suffix when customId is null`() {
        val ref = NoteRef("inbox.org", 3)
        assertEquals("inbox.org@3", ref.encode())
        assertEquals(ref, NoteRef.decode(ref.encode()))
    }

    @Test
    fun `encode appends the id suffix when customId is present`() {
        val ref = NoteRef("inbox.org", 3, "abc-123")
        assertEquals("inbox.org@3#abc-123", ref.encode())
        assertEquals(ref, NoteRef.decode(ref.encode()))
    }

    @Test
    fun `decode rejects malformed ids`() {
        assertNull(NoteRef.decode("no-at-sign"))
        assertNull(NoteRef.decode("inbox.org@notanumber"))
        assertNull(NoteRef.decode(""))
    }

    @Test
    fun `decode treats a trailing empty id suffix as no id`() {
        assertEquals(NoteRef("inbox.org", 3, null), NoteRef.decode("inbox.org@3#"))
    }

    // --- headlineFor resolution ---

    private val doc = OrgParser.parse(
        """
        |* First
        |:PROPERTIES:
        |:CUSTOM_ID: first-id
        |:END:
        |* Second
        |:PROPERTIES:
        |:ID: second-id
        |:END:
        |* Third
        |
        """.trimMargin(),
    )

    @Test
    fun `resolves by customId even when lineIndex has drifted`() {
        // Simulates an external edit that inserted lines above "Second", moving it off
        // the lineIndex a stale favorite/NoteRef would have recorded.
        val staleRef = NoteRef("notes.org", lineIndex = 999, customId = "second-id")
        val resolved = doc.headlineFor(staleRef)
        assertEquals("Second", resolved?.title)
    }

    @Test
    fun `resolves by CUSTOM_ID property specifically`() {
        val ref = NoteRef("notes.org", lineIndex = 999, customId = "first-id")
        assertEquals("First", doc.headlineFor(ref)?.title)
    }

    @Test
    fun `falls back to lineIndex when there is no customId`() {
        val third = doc.headlines.first { it.title == "Third" }
        val ref = NoteRef("notes.org", third.lineIndex, customId = null)
        assertEquals("Third", doc.headlineFor(ref)?.title)
    }

    @Test
    fun `falls back to lineIndex when the customId can't be found`() {
        val third = doc.headlines.first { it.title == "Third" }
        val ref = NoteRef("notes.org", third.lineIndex, customId = "no-such-id")
        assertEquals("Third", doc.headlineFor(ref)?.title)
    }

    @Test
    fun `returns null when neither the id nor the line resolve`() {
        val ref = NoteRef("notes.org", lineIndex = 999, customId = "no-such-id")
        assertNull(doc.headlineFor(ref))
    }
}
