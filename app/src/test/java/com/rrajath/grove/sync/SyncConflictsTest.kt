package com.rrajath.grove.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncConflictsTest {

    private val copy = "journal.sync-conflict-20250611-143200-ABCDEF7.org"

    @Test
    fun `recognizes syncthing conflict names`() {
        assertTrue(SyncConflicts.isConflictFile(copy))
        assertFalse(SyncConflicts.isConflictFile("journal.org"))
        assertFalse(SyncConflicts.isConflictFile("sync-conflict-notes.org"))
    }

    @Test
    fun `base name strips the conflict infix`() {
        assertEquals("journal.org", SyncConflicts.baseName(copy))
        assertEquals(null, SyncConflicts.baseName("journal.org"))
    }

    @Test
    fun `label formats the timestamp`() {
        assertEquals("2025-06-11 14:32", SyncConflicts.label(copy))
    }

    @Test
    fun `detect maps base to newest copy`() {
        val names = listOf(
            "journal.org",
            "journal.sync-conflict-20250610-090000-AAA.org",
            copy,
            "other.org",
        )
        assertEquals(mapOf("journal.org" to copy), SyncConflicts.detect(names))
    }

    @Test
    fun `keepBoth appends demoted copy under CONFLICT heading`() {
        val main = "* Heading\nbody\n"
        val conflict = "* Heading\nolder body\n** Child\n"
        val merged = ConflictResolver.keepBoth(main, conflict, "2025-06-11 14:32")
        val expected = """
            * Heading
            body
            * CONFLICT (sync copy from 2025-06-11 14:32)
            ** Heading
            older body
            *** Child
        """.trimIndent() + "\n"
        assertEquals(expected, merged)
        assertTrue(merged.startsWith(main))
    }

    @Test
    fun `keepBoth handles missing trailing newline`() {
        val merged = ConflictResolver.keepBoth("* A", "* B", "x")
        assertEquals("* A\n* CONFLICT (sync copy from x)\n** B\n", merged)
    }
}
