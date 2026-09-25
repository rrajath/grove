package com.rrajath.grove.ui.vault

import com.rrajath.grove.data.NoteEntity
import com.rrajath.grove.data.NoteOutlineRow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkedReferencesTest {

    private fun note(
        fileName: String,
        lineIndex: Int,
        title: String,
        body: String,
        orgId: String? = null,
        customId: String? = null,
    ) = NoteEntity(
        fileName = fileName,
        lineIndex = lineIndex,
        level = 1,
        title = title,
        keyword = null,
        priority = null,
        tags = "",
        inheritedTags = "",
        scheduled = null,
        deadline = null,
        closed = null,
        activeTimestamps = null,
        orgId = orgId,
        customId = customId,
        createdAt = null,
        body = body,
        isDone = false,
        lastModified = 0L,
    )

    @Test
    fun `a linked id reference is grouped by file and excludes the note's own body`() {
        val target = note("kyoto.org", 0, "Kyoto — Day 2", "Temple loop.", orgId = "ABC123")
        val referrer = note(
            "journal.org", 3, "Jun 11",
            "Dinner follows the plan in [[id:ABC123][Kyoto — Day 2]].",
        )
        val result = computeLinkedReferences(
            targetId = "ABC123",
            title = target.title,
            selfKey = target.fileName to target.lineIndex,
            linkCandidates = listOf(target, referrer),
            mentionCandidates = emptyList(),
            crumbs = emptyMap(),
        )
        assertEquals(1, result.linkedCount)
        assertEquals("journal.org", result.linkedByFile.single().fileName)
        assertEquals("id:ABC123", result.linkedByFile.single().hits.single().matchedTarget)
    }

    @Test
    fun `a coincidental id substring that isn't inside link brackets doesn't count`() {
        val referrer = note("journal.org", 3, "Note", "See id:ABC123 in the properties drawer, no brackets.")
        val result = computeLinkedReferences(
            targetId = "ABC123",
            title = "Kyoto",
            selfKey = "kyoto.org" to 0,
            linkCandidates = listOf(referrer),
            mentionCandidates = emptyList(),
            crumbs = emptyMap(),
        )
        assertTrue(result.linkedByFile.isEmpty())
    }

    @Test
    fun `an unlinked mention is excluded once the same note already counted as linked`() {
        val referrer = note(
            "journal.org", 3, "Note",
            "Mentions [[id:ABC123][Kyoto]] and later just says Kyoto again in passing.",
        )
        val result = computeLinkedReferences(
            targetId = "ABC123",
            title = "Kyoto",
            selfKey = "kyoto.org" to 0,
            linkCandidates = listOf(referrer),
            mentionCandidates = listOf(referrer),
            crumbs = emptyMap(),
        )
        assertEquals(1, result.linkedCount)
        assertEquals(0, result.unlinkedCount)
    }

    @Test
    fun `a plain-text title mention with no link is reported as unlinked`() {
        val referrer = note("journal.org", 3, "Note", "honestly kyoto day 2 was the best of the trip")
        val result = computeLinkedReferences(
            targetId = null,
            title = "kyoto day 2",
            selfKey = "kyoto.org" to 0,
            linkCandidates = emptyList(),
            mentionCandidates = listOf(referrer),
            crumbs = emptyMap(),
        )
        assertEquals(0, result.linkedCount)
        assertEquals(1, result.unlinkedCount)
        assertEquals(9 until 20, result.unlinked.single().plainTextRange)
    }

    @Test
    fun `own path crumb includes ancestors down to and including the row itself`() {
        val headings = listOf(
            NoteOutlineRow("trip.org", 0, level = 1, title = "Japan", orgId = null),
            NoteOutlineRow("trip.org", 1, level = 2, title = "Kyoto", orgId = null),
            NoteOutlineRow("trip.org", 2, level = 3, title = "Day 2", orgId = null),
            NoteOutlineRow("trip.org", 3, level = 2, title = "Osaka", orgId = null),
        )
        val crumbs = buildOwnPathCrumbs(headings)
        assertEquals("Japan › Kyoto › Day 2", crumbs["trip.org" to 2])
        assertEquals("Japan › Osaka", crumbs["trip.org" to 3])
    }

    @Test
    fun `crumbs built from only the hit files fill every hit`() {
        val linkedRow = note("journal.org", 1, "Jun 11", "See [[id:ABC123][Kyoto]].")
        val mentionRow = note("notes.org", 0, "Misc", "Kyoto was lovely.")
        val bare = computeLinkedReferences(
            targetId = "ABC123",
            title = "Kyoto",
            selfKey = "kyoto.org" to 0,
            linkCandidates = listOf(linkedRow),
            mentionCandidates = listOf(linkedRow, mentionRow),
            crumbs = emptyMap(),
        )
        assertEquals(listOf("journal.org", "notes.org"), hitFiles(bare))

        val outlines = listOf(
            NoteOutlineRow("journal.org", 0, level = 1, title = "June", orgId = null),
            NoteOutlineRow("journal.org", 1, level = 2, title = "Jun 11", orgId = null),
            NoteOutlineRow("notes.org", 0, level = 1, title = "Misc", orgId = null),
        )
        val result = withCrumbs(bare, buildOwnPathCrumbs(outlines))

        assertEquals("June › Jun 11", result.linkedByFile.single().hits.single().crumb)
        assertEquals("Misc", result.unlinked.single().crumb)
    }

    @Test
    fun `escapeLikeNeedle escapes percent underscore and backslash`() {
        assertEquals("50\\% off", escapeLikeNeedle("50% off"))
        assertEquals("a\\_b", escapeLikeNeedle("a_b"))
        assertEquals("a\\\\b", escapeLikeNeedle("a\\b"))
    }
}
