package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArchiveLocationTest {

    @Test
    fun `parse strips leading dot-slash and defaults to org extension`() {
        assertEquals(ArchiveTarget("archive.org", emptyList()), ArchiveLocation.parse("./archive"))
        assertEquals(ArchiveTarget("archive.org", emptyList()), ArchiveLocation.parse("archive.org"))
    }

    @Test
    fun `parse reads a single heading target`() {
        assertEquals(
            ArchiveTarget("archive.org", listOf("Inbox")),
            ArchiveLocation.parse("./archive.org::* Inbox"),
        )
    }

    @Test
    fun `parse reads a nested heading path`() {
        assertEquals(
            ArchiveTarget("archive.org", listOf("Tasks", "Sub Task")),
            ArchiveLocation.parse("./archive.org::* Tasks/Sub Task"),
        )
    }

    @Test
    fun `parse returns null for a blank value`() {
        assertNull(ArchiveLocation.parse("   "))
    }

    @Test
    fun `resolve prefers a heading's own ARCHIVE property`() {
        val doc = OrgParser.parse(
            """
            #+ARCHIVE: ./file-level.org::* Top

            * Parent
            :PROPERTIES:
            :ARCHIVE: ./parent.org::* Parent Bucket
            :END:
            ** Child
            :PROPERTIES:
            :ARCHIVE: ./child.org::* Child Bucket
            :END:
            """.trimIndent() + "\n"
        )
        val child = doc.findByTitle("Child")!!
        assertEquals(ArchiveTarget("child.org", listOf("Child Bucket")), ArchiveLocation.resolve(doc, child))
    }

    @Test
    fun `resolve falls back to nearest ancestor then file level`() {
        val doc = OrgParser.parse(
            """
            #+ARCHIVE: ./file-level.org::* Top

            * Parent
            :PROPERTIES:
            :ARCHIVE: ./parent.org::* Parent Bucket
            :END:
            ** Child
            ** Grandchild sub
            *** Leaf
            """.trimIndent() + "\n"
        )
        val child = doc.findByTitle("Child")!!
        assertEquals(ArchiveTarget("parent.org", listOf("Parent Bucket")), ArchiveLocation.resolve(doc, child))

        val doc2 = OrgParser.parse(
            """
            #+ARCHIVE: ./file-level.org::* Top

            * Untagged parent
            ** Child
            """.trimIndent() + "\n"
        )
        val child2 = doc2.findByTitle("Child")!!
        assertEquals(ArchiveTarget("file-level.org", listOf("Top")), ArchiveLocation.resolve(doc2, child2))
    }

    @Test
    fun `resolve returns null when no ARCHIVE is set anywhere`() {
        val doc = OrgParser.parse("* Lone heading\n")
        assertNull(ArchiveLocation.resolve(doc, doc.findByTitle("Lone heading")!!))
    }

    @Test
    fun `findOrCreateHeadingPath reuses an existing heading`() {
        val doc = OrgParser.parse(
            """
            * Inbox
            ** Existing note
            """.trimIndent() + "\n"
        )
        val (result, target) = ArchiveLocation.findOrCreateHeadingPath(doc, listOf("Inbox"))
        assertEquals("Inbox", target?.title)
        // No mutation needed — same document instance/text.
        assertEquals(doc.text, result.text)
    }

    @Test
    fun `findOrCreateHeadingPath creates missing top-level and nested headings`() {
        val doc = OrgParser.parse("* Existing\n")
        val (result, target) = ArchiveLocation.findOrCreateHeadingPath(doc, listOf("Tasks", "Sub Task"))
        assertEquals("Sub Task", target?.title)
        val tasks = result.findByTitle("Tasks")!!
        val subTask = result.findByTitle("Sub Task")!!
        assertEquals(1, tasks.level)
        assertEquals(2, subTask.level)
        assertEquals(tasks.lineIndex, result.parent(subTask)?.lineIndex)
    }

    @Test
    fun `findOrCreateHeadingPath on an empty path resolves to top level`() {
        val doc = OrgParser.parse("* Existing\n")
        val (result, target) = ArchiveLocation.findOrCreateHeadingPath(doc, emptyList())
        assertNull(target)
        assertEquals(doc.text, result.text)
    }
}
