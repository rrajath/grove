package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime

class ArchivePropertiesTest {

    private val now = LocalDateTime.of(2026, 9, 26, 14, 5, 37)

    private fun props(text: String, title: String, file: String = "work/tasks.org"): Map<String, String> {
        val doc = OrgParser.parse(text)
        val h = doc.headlines.first { it.title == title }
        return ArchiveProperties.of(doc, h, file, "/storage/emulated/0/org/$file", now).toMap()
    }

    @Test
    fun `records time, file, category and todo in Emacs order`() {
        val doc = OrgParser.parse("* TODO Task\n")
        val list = ArchiveProperties.of(doc, doc.headlines.first(), "work/tasks.org", "/storage/emulated/0/org/work/tasks.org", now)
        assertEquals(
            listOf(
                "ARCHIVE_TIME" to "2026-09-26 Sat 14:05",
                "ARCHIVE_FILE" to "/storage/emulated/0/org/work/tasks.org",
                "ARCHIVE_CATEGORY" to "tasks",
                "ARCHIVE_TODO" to "TODO",
            ),
            list,
        )
    }

    @Test
    fun `outline path joins ancestor titles and is omitted at top level`() {
        val text = "* Projects :work:\n** [#A] Grove\n*** DONE Ship it\n"
        assertEquals("Projects/Grove", props(text, "Ship it")["ARCHIVE_OLPATH"])
        assertEquals(null, props(text, "Projects")["ARCHIVE_OLPATH"])
    }

    @Test
    fun `todo is omitted for a keyword-less heading`() {
        assertEquals(null, props("* Plain note\n", "Plain note")["ARCHIVE_TODO"])
    }

    @Test
    fun `category prefers the nearest CATEGORY property`() {
        val text = "#+CATEGORY: fromkeyword\n* Parent\n:PROPERTIES:\n:CATEGORY: parentcat\n:END:\n** Child\n"
        assertEquals("parentcat", props(text, "Child")["ARCHIVE_CATEGORY"])
    }

    @Test
    fun `category falls back to the file-level drawer then the CATEGORY keyword`() {
        assertEquals(
            "drawercat",
            props(":PROPERTIES:\n:CATEGORY: drawercat\n:END:\n* Task\n", "Task")["ARCHIVE_CATEGORY"],
        )
        assertEquals("kwcat", props("#+CATEGORY: kwcat\n* Task\n", "Task")["ARCHIVE_CATEGORY"])
    }

    @Test
    fun `category falls back to the base file name without extension`() {
        assertEquals("inbox", props("* Task\n", "Task", file = "a/b/inbox.org")["ARCHIVE_CATEGORY"])
    }

    @Test
    fun `stamp creates a drawer after the planning line when none exists`() {
        val subtree = "* DONE Task\nCLOSED: [2026-09-26 Sat 14:00]\nBody\n** Child\n"
        val out = ArchiveProperties.stamp(subtree, OrgKeywords.DEFAULT, listOf("ARCHIVE_TIME" to "t", "ARCHIVE_FILE" to "f"))
        assertEquals(
            "* DONE Task\nCLOSED: [2026-09-26 Sat 14:00]\n:PROPERTIES:\n:ARCHIVE_TIME: t\n:ARCHIVE_FILE: f\n:END:\nBody\n** Child\n",
            out,
        )
    }

    @Test
    fun `stamp appends to an existing drawer and leaves child drawers alone`() {
        val subtree = "* Task\n:PROPERTIES:\n:ID: abc\n:END:\n** Child\n:PROPERTIES:\n:ID: def\n:END:"
        val out = ArchiveProperties.stamp(subtree, OrgKeywords.DEFAULT, listOf("ARCHIVE_TIME" to "t", "ARCHIVE_FILE" to "f"))
        assertEquals(
            "* Task\n:PROPERTIES:\n:ID: abc\n:ARCHIVE_TIME: t\n:ARCHIVE_FILE: f\n:END:\n** Child\n:PROPERTIES:\n:ID: def\n:END:",
            out,
        )
    }
}
