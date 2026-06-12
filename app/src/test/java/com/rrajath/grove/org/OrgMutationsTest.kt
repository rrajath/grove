package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class OrgMutationsTest {

    private val doc = OrgParser.parse(
        """
        #+TITLE: Test

        * First :tag:
        body one

        ** TODO [#A] Child task  :work:
        SCHEDULED: <2025-06-09 Mon +1w>
        child body
        ** Second child
        * TODO Last
        DEADLINE: <2025-06-27 Fri>
        last body
        """.trimIndent() + "\n"
    )

    private fun h(title: String) = doc.headlines.first { it.title == title }

    @Test
    fun `setKeyword rewrites only the headline line`() {
        val result = OrgMutations.setKeyword(doc, h("First"), "TODO")
        assertTrue(result.contains("* TODO First  :tag:"))
        // everything else byte-identical
        assertEquals(
            doc.text.lines().filterIndexed { i, _ -> i != h("First").lineIndex },
            result.lines().filterIndexed { i, _ -> i != h("First").lineIndex },
        )
    }

    @Test
    fun `clearing keyword and priority`() {
        val child = h("Child task")
        val noKw = OrgParser.parse(OrgMutations.setKeyword(doc, child, null))
        assertNull(noKw.findByTitle("Child task")!!.keyword)
        assertEquals('A', noKw.findByTitle("Child task")!!.priority)

        val noPrio = OrgParser.parse(OrgMutations.setPriority(doc, child, null))
        assertNull(noPrio.findByTitle("Child task")!!.priority)
        assertEquals("TODO", noPrio.findByTitle("Child task")!!.keyword)
    }

    @Test
    fun `setTags adds and removes`() {
        val tagged = OrgParser.parse(OrgMutations.setTags(doc, h("Second child"), listOf("a", "b")))
        assertEquals(listOf("a", "b"), tagged.findByTitle("Second child")!!.tags)

        val untagged = OrgParser.parse(OrgMutations.setTags(doc, h("First"), emptyList()))
        assertEquals(emptyList<String>(), untagged.findByTitle("First")!!.tags)
    }

    @Test
    fun `setScheduled inserts planning line when missing`() {
        val ts = OrgTimestamp.parse("<2025-07-01 Tue>")!!
        val result = OrgParser.parse(OrgMutations.setScheduled(doc, h("First"), ts))
        assertEquals(ts, result.findByTitle("First")!!.planning.scheduled)
        // body unchanged
        assertTrue(result.text.contains("body one"))
    }

    @Test
    fun `setScheduled null removes the whole planning line when empty`() {
        val result = OrgMutations.setDeadline(doc, h("Last"), null)
        assertTrue(!result.contains("DEADLINE: <2025-06-27 Fri>"))
        assertTrue(result.contains("last body"))
    }

    @Test
    fun `setDeadline preserves existing scheduled`() {
        val child = h("Child task")
        val ts = OrgTimestamp.parse("<2025-08-01 Fri>")!!
        val result = OrgParser.parse(OrgMutations.setDeadline(doc, child, ts))
        val planning = result.findByTitle("Child task")!!.planning
        assertEquals(ts, planning.deadline)
        assertEquals("<2025-06-09 Mon +1w>", planning.scheduled!!.format())
    }

    @Test
    fun `markDone advances repeater and keeps keyword`() {
        val child = h("Child task")
        val result = OrgParser.parse(
            OrgMutations.markDone(doc, child, "DONE", LocalDateTime.of(2025, 6, 11, 14, 0))
        )
        val after = result.findByTitle("Child task")!!
        assertEquals("TODO", after.keyword)
        assertEquals("<2025-06-16 Mon +1w>", after.planning.scheduled!!.format())
        assertNull(after.planning.closed)
    }

    @Test
    fun `markDone without repeater sets keyword and CLOSED`() {
        val result = OrgParser.parse(
            OrgMutations.markDone(doc, h("Last"), "DONE", LocalDateTime.of(2025, 6, 11, 14, 30))
        )
        val after = result.findByTitle("Last")!!
        assertEquals("DONE", after.keyword)
        assertEquals("[2025-06-11 Wed 14:30]", after.planning.closed!!.format())
    }

    @Test
    fun `moveSubtree swaps siblings with bodies intact`() {
        val result = OrgMutations.moveSubtree(doc, h("Child task"), +1)!!
        val redoc = OrgParser.parse(result)
        val first = redoc.findByTitle("First")!!
        assertEquals(
            listOf("Second child", "Child task"),
            redoc.directChildren(first).map { it.title },
        )
        assertTrue(result.contains("child body"))
        // No-op at the edge
        assertNull(OrgMutations.moveSubtree(doc, h("Child task"), -1))
    }

    @Test
    fun `move top-level subtree`() {
        val result = OrgMutations.moveSubtree(doc, h("Last"), -1)!!
        val redoc = OrgParser.parse(result)
        assertTrue(
            redoc.headlines.first { it.level == 1 }.title == "Last" ||
                    redoc.headlines.indexOfFirst { it.title == "Last" } <
                    redoc.headlines.indexOfFirst { it.title == "First" }
        )
    }

    @Test
    fun `delete and replace subtree`() {
        val deleted = OrgParser.parse(OrgMutations.deleteSubtree(doc, h("Child task")))
        assertNull(deleted.findByTitle("Child task"))
        assertTrue(deleted.text.contains("Second child"))

        val replaced = OrgMutations.replaceSubtree(doc, h("Second child"), "** Renamed child\nnew body")
        assertTrue(replaced.contains("** Renamed child\nnew body"))
        assertTrue(!replaced.contains("** Second child"))
    }

    @Test
    fun `cut and paste releveles the subtree`() {
        val cut = OrgMutations.subtreeText(doc, h("Child task"))
        val without = OrgParser.parse(OrgMutations.deleteSubtree(doc, h("Child task")))
        val pasted = OrgParser.parse(
            OrgMutations.pasteUnder(without, without.findByTitle("Last")!!, cut)
        )
        val moved = pasted.findByTitle("Child task")!!
        assertEquals(2, moved.level)
        assertEquals("Last", pasted.parent(moved)!!.title)
        assertEquals("<2025-06-09 Mon +1w>", moved.planning.scheduled!!.format())
    }

    @Test
    fun `newChild appends with ID and CREATED drawer`() {
        val (text, line) = OrgMutations.newChild(
            doc, h("First"), "Fresh note",
            OrgMutations.NewNoteOptions(
                id = "uuid-1234",
                createdAt = LocalDateTime.of(2025, 6, 11, 9, 5),
            ),
        )
        val redoc = OrgParser.parse(text)
        val fresh = redoc.findByTitle("Fresh note")!!
        assertEquals(2, fresh.level)
        assertEquals(line, fresh.lineIndex)
        assertEquals("uuid-1234", fresh.id)
        assertEquals("[2025-06-11 Wed 9:05]", fresh.properties["CREATED"])
        assertEquals("First", redoc.parent(fresh)!!.title)
    }

    @Test
    fun `headlineLine formatting`() {
        assertEquals("** TODO [#A] Title  :a:b:", OrgMutations.headlineLine(2, "TODO", 'A', "Title", listOf("a", "b")))
        assertEquals("* Title", OrgMutations.headlineLine(1, null, null, "Title", emptyList()))
        assertEquals("* TODO", OrgMutations.headlineLine(1, "TODO", null, "", emptyList()))
    }
}
