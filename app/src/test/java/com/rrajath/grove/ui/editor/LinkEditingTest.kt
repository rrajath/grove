package com.rrajath.grove.ui.editor

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.rrajath.grove.org.OrgLinkParser
import com.rrajath.grove.org.OrgLinkTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkEditingTest {

    // --- insertHttpsLink ---

    @Test
    fun `https link with no selection inserts scheme and description, cursor after scheme`() {
        val result = insertHttpsLink(TextFieldValue("see ", TextRange(4)))

        assertEquals("see [[https://][description]]", result.text)
        // "see [[" is 6 chars, "https://" is 8 → cursor at 14, right after the scheme.
        assertEquals(TextRange(14), result.selection)
    }

    @Test
    fun `https link consumes the selection as the description`() {
        // "my note" spans indices 4..11 of "See my note here".
        val result = insertHttpsLink(TextFieldValue("See my note here", TextRange(4, 11)))

        assertEquals("See [[https://][my note]] here", result.text)
        assertEquals(TextRange(14), result.selection)
    }

    @Test
    fun `https link handles a reversed selection`() {
        val result = insertHttpsLink(TextFieldValue("See my note here", TextRange(11, 4)))

        assertEquals("See [[https://][my note]] here", result.text)
        assertEquals(TextRange(14), result.selection)
    }

    // --- collapseDoubledScheme ---

    @Test
    fun `collapse folds a pasted https over the pre-inserted https`() {
        // Cursor was after "[[https://", user pasted "https://example.com".
        val text = "[[https://https://example.com][description]]"
        val cursor = "[[https://https://example.com".length
        val result = collapseDoubledScheme(text, cursor)!!

        assertEquals("[[https://example.com][description]]", result.text)
        assertEquals("[[https://example.com".length, result.cursor)
    }

    @Test
    fun `collapse lets a pasted http win over the pre-inserted https`() {
        val text = "[[https://http://insecure.example][x]]"
        val result = collapseDoubledScheme(text, text.indexOf(']'))!!

        assertEquals("[[http://insecure.example][x]]", result.text)
    }

    @Test
    fun `collapse folds https over a pasted https-prefixed url`() {
        val text = "[[http://https://example.com]]"
        val result = collapseDoubledScheme(text, 4)!!

        assertEquals("[[https://example.com]]", result.text)
        // Cursor sat inside the removed "http://" span → clamps to the scheme start.
        assertEquals(2, result.cursor)
    }

    @Test
    fun `collapse is a no-op for a single scheme`() {
        assertNull(collapseDoubledScheme("[[https://example.com][d]]", 10))
        assertNull(collapseDoubledScheme("plain text with https:// in it", 5))
    }

    // --- relativeOrgPath ---

    @Test
    fun `relative path within the same directory is just the filename`() {
        assertEquals("projects.org", relativeOrgPath("work/journal.org", "work/projects.org"))
    }

    @Test
    fun `relative path from the vault root into a subdirectory`() {
        assertEquals("work/projects.org", relativeOrgPath("inbox.org", "work/projects.org"))
    }

    @Test
    fun `relative path from a subdirectory to the vault root`() {
        assertEquals("../inbox.org", relativeOrgPath("work/acme.org", "inbox.org"))
        assertEquals("../../inbox.org", relativeOrgPath("work/clients/acme.org", "inbox.org"))
    }

    @Test
    fun `relative path across sibling directories`() {
        assertEquals("../notes/b.org", relativeOrgPath("work/a.org", "notes/b.org"))
    }

    @Test
    fun `relative path climbs out of a deeper directory`() {
        assertEquals("../shared.org", relativeOrgPath("work/clients/acme.org", "work/shared.org"))
    }

    // --- resilientHeadingTarget ---

    @Test
    fun `resilient target prefers the id over the custom id`() {
        assertEquals(
            HeadingLinkTarget.ById("abc-123"),
            resilientHeadingTarget(id = "abc-123", customId = "design", relPath = null),
        )
    }

    @Test
    fun `resilient target falls back to the custom id`() {
        assertEquals(
            HeadingLinkTarget.ByCustomId("design", relPath = "projects.org"),
            resilientHeadingTarget(id = null, customId = "design", relPath = "projects.org"),
        )
    }

    // --- formatHeadingLink ---

    @Test
    fun `format id link, no description`() {
        assertEquals("[[id:abc-123]]", formatHeadingLink(HeadingLinkTarget.ById("abc-123"), null))
    }

    @Test
    fun `format id link with a description`() {
        assertEquals(
            "[[id:abc-123][the design]]",
            formatHeadingLink(HeadingLinkTarget.ById("abc-123"), "the design"),
        )
    }

    @Test
    fun `format same-file custom id link`() {
        assertEquals(
            "[[#kyoto-day-2]]",
            formatHeadingLink(HeadingLinkTarget.ByCustomId("kyoto-day-2", relPath = null), null),
        )
    }

    @Test
    fun `format cross-file custom id link`() {
        assertEquals(
            "[[file:../trips/japan.org::#kyoto-day-2]]",
            formatHeadingLink(HeadingLinkTarget.ByCustomId("kyoto-day-2", "../trips/japan.org"), null),
        )
    }

    @Test
    fun `format same-file heading name link`() {
        assertEquals(
            "[[*Weekly Review][notes]]",
            formatHeadingLink(HeadingLinkTarget.ByName("Weekly Review", relPath = null), "notes"),
        )
    }

    @Test
    fun `format cross-file heading name link`() {
        assertEquals(
            "[[file:projects.org::*Design]]",
            formatHeadingLink(HeadingLinkTarget.ByName("Design", "projects.org"), null),
        )
    }

    // --- insertHeadingLink ---

    @Test
    fun `insert heading link splices over the selection and moves the cursor after it`() {
        val value = TextFieldValue("see here for details", TextRange(4, 8))
        val result = insertHeadingLink(value, "[[*Design]]")

        assertEquals("see [[*Design]] for details", result.text)
        assertEquals(TextRange(15), result.selection)
    }

    @Test
    fun `insert heading link at a collapsed cursor`() {
        val value = TextFieldValue("prefix ", TextRange(7))
        val result = insertHeadingLink(value, "[[id:x]]")

        assertEquals("prefix [[id:x]]", result.text)
        assertEquals(TextRange(15), result.selection)
    }

    // --- round-trips through OrgLinkParser ---

    /** Pull the target part out of a `[[target]]` / `[[target][desc]]` string. */
    private fun target(link: String, currentFile: String) =
        OrgLinkParser.parse(link.removePrefix("[[").substringBefore("]"), currentFile)

    @Test
    fun `emitted id link parses back to an Id target`() {
        val link = formatHeadingLink(HeadingLinkTarget.ById("abc-123"), null)
        assertEquals(OrgLinkTarget.Id("abc-123"), target(link, "notes.org"))
    }

    @Test
    fun `emitted same-file heading link parses back to a Heading target`() {
        val link = formatHeadingLink(HeadingLinkTarget.ByName("Weekly Review", relPath = null), null)
        assertEquals(OrgLinkTarget.Heading("Weekly Review"), target(link, "notes.org"))
    }

    @Test
    fun `emitted cross-file heading link resolves to the right file and heading`() {
        val rel = relativeOrgPath("work/a.org", "notes/b.org")
        val link = formatHeadingLink(HeadingLinkTarget.ByName("Some target", rel), null)
        assertEquals(
            OrgLinkTarget.FileHeading("notes/b.org", "Some target"),
            target(link, "work/a.org"),
        )
    }

    @Test
    fun `emitted cross-file custom id link resolves to the right file`() {
        val rel = relativeOrgPath("work/clients/acme.org", "work/projects.org")
        val link = formatHeadingLink(HeadingLinkTarget.ByCustomId("design", rel), null)
        assertEquals(
            OrgLinkTarget.FileCustomId("work/projects.org", "design"),
            target(link, "work/clients/acme.org"),
        )
    }
}
