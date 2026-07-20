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
        val (result, newLine) = OrgMutations.moveSubtree(doc, h("Child task"), +1)!!
        val redoc = OrgParser.parse(result)
        val first = redoc.findByTitle("First")!!
        assertEquals(
            listOf("Second child", "Child task"),
            redoc.directChildren(first).map { it.title },
        )
        assertTrue(result.contains("child body"))
        assertEquals(redoc.findByTitle("Child task")!!.lineIndex, newLine)
        // No-op at the edge
        assertNull(OrgMutations.moveSubtree(doc, h("Child task"), -1))
    }

    @Test
    fun `move top-level subtree`() {
        val (result, newLine) = OrgMutations.moveSubtree(doc, h("Last"), -1)!!
        val redoc = OrgParser.parse(result)
        assertTrue(
            redoc.headlines.first { it.level == 1 }.title == "Last" ||
                    redoc.headlines.indexOfFirst { it.title == "Last" } <
                    redoc.headlines.indexOfFirst { it.title == "First" }
        )
        assertEquals(redoc.findByTitle("Last")!!.lineIndex, newLine)
    }

    @Test
    fun `moveSubtree down past a deep sibling subtree tracks the new line`() {
        val big = OrgParser.parse(
            """
            * A
            ** B
            body b
            *** B1
            deep body
            ** C
            body c
            """.trimIndent() + "\n"
        )
        val b = big.headlines.first { it.title == "B" }
        val (text, newLine) = OrgMutations.moveSubtree(big, b, +1)!!
        val redoc = OrgParser.parse(text)
        assertEquals(
            listOf("C", "B"),
            redoc.directChildren(redoc.findByTitle("A")!!).map { it.title },
        )
        assertEquals(redoc.findByTitle("B")!!.lineIndex, newLine)
        // B1 travelled with B, still its child
        assertEquals("B", redoc.parent(redoc.findByTitle("B1")!!)!!.title)
        // No-op moving down at the last sibling
        assertNull(OrgMutations.moveSubtree(redoc, redoc.findByTitle("B")!!, +1))
    }

    @Test
    fun `promoteSubtree shifts heading and descendants one level up`() {
        val text = OrgMutations.promoteSubtree(doc, h("Child task"))!!
        val redoc = OrgParser.parse(text)
        assertEquals(1, redoc.findByTitle("Child task")!!.level)
        // planning and body untouched
        assertTrue(text.contains("SCHEDULED: <2025-06-09 Mon +1w>"))
        assertTrue(text.contains("child body"))
        // siblings untouched
        assertEquals(2, redoc.findByTitle("Second child")!!.level)
    }

    @Test
    fun `promoteSubtree carries the whole subtree`() {
        val big = OrgParser.parse("* A\n** B\n*** B1\nbody\n** C\n")
        val text = OrgMutations.promoteSubtree(big, big.findByTitle("B")!!)!!
        val redoc = OrgParser.parse(text)
        assertEquals(1, redoc.findByTitle("B")!!.level)
        assertEquals(2, redoc.findByTitle("B1")!!.level)
        assertEquals(2, redoc.findByTitle("C")!!.level) // sibling stays put
    }

    @Test
    fun `promoteSubtree at level 1 returns null`() {
        assertNull(OrgMutations.promoteSubtree(doc, h("First")))
    }

    @Test
    fun `demoteSubtree shifts subtree deeper`() {
        val text = OrgMutations.demoteSubtree(doc, h("Second child"))!!
        val redoc = OrgParser.parse(text)
        val demoted = redoc.findByTitle("Second child")!!
        assertEquals(3, demoted.level)
        assertEquals("Child task", redoc.parent(demoted)!!.title)
    }

    @Test
    fun `demoteSubtree without previous sibling returns null`() {
        assertNull(OrgMutations.demoteSubtree(doc, h("Child task"))) // first child
        assertNull(OrgMutations.demoteSubtree(doc, h("First")))      // first top-level
    }

    @Test
    fun `demoteSubtree of last top-level nests under previous`() {
        val text = OrgMutations.demoteSubtree(doc, h("Last"))!!
        val redoc = OrgParser.parse(text)
        val demoted = redoc.findByTitle("Last")!!
        assertEquals(2, demoted.level)
        assertEquals("First", redoc.parent(demoted)!!.title)
        assertTrue(text.contains("DEADLINE: <2025-06-27 Fri>"))
    }

    @Test
    fun `refileInsert to top level appends releveled at end of file`() {
        val subtree = OrgMutations.subtreeText(doc, h("Child task"))
        val (text, line) = OrgMutations.refileInsert(doc, null, subtree)
        val redoc = OrgParser.parse(text)
        val moved = redoc.headlines.last()
        assertEquals("Child task", moved.title)
        assertEquals(1, moved.level)
        assertEquals(line, moved.lineIndex)
        assertTrue(text.contains("child body"))
    }

    @Test
    fun `refileInsert to top level of file without trailing newline`() {
        val dest = OrgParser.parse("* Only\nbody")
        val (text, line) = OrgMutations.refileInsert(dest, null, "** Moved\nstuff\n")
        val redoc = OrgParser.parse(text)
        val moved = redoc.findByTitle("Moved")!!
        assertEquals(1, moved.level)
        assertEquals(line, moved.lineIndex)
        assertEquals(listOf("Only", "Moved"), redoc.headlines.map { it.title })
    }

    @Test
    fun `refileInsert under nested heading relevels below target`() {
        val subtree = "* Moved\nbody m\n** Moved child\n"
        val (text, line) = OrgMutations.refileInsert(doc, h("Second child"), subtree)
        val redoc = OrgParser.parse(text)
        val moved = redoc.findByTitle("Moved")!!
        assertEquals(3, moved.level)
        assertEquals(4, redoc.findByTitle("Moved child")!!.level)
        assertEquals("Second child", redoc.parent(moved)!!.title)
        assertEquals(line, moved.lineIndex)
    }

    @Test
    fun `refileInsert into empty document`() {
        val empty = OrgParser.parse("")
        val (text, line) = OrgMutations.refileInsert(empty, null, "** Moved\nbody\n")
        assertEquals("* Moved\nbody\n", text)
        assertEquals(0, line)
    }

    @Test
    fun `refileWithinFile moves subtree under a later heading`() {
        val (text, line) = OrgMutations.refileWithinFile(doc, h("Child task"), h("Last").lineIndex)!!
        val redoc = OrgParser.parse(text)
        val moved = redoc.findByTitle("Child task")!!
        assertEquals("Last", redoc.parent(moved)!!.title)
        assertEquals(2, moved.level)
        assertEquals(line, moved.lineIndex)
        assertTrue(text.contains("child body"))
    }

    @Test
    fun `refileWithinFile moves subtree under an earlier heading`() {
        val (text, line) = OrgMutations.refileWithinFile(doc, h("Last"), h("Second child").lineIndex)!!
        val redoc = OrgParser.parse(text)
        val moved = redoc.findByTitle("Last")!!
        assertEquals("Second child", redoc.parent(moved)!!.title)
        assertEquals(3, moved.level)
        assertEquals(line, moved.lineIndex)
    }

    @Test
    fun `refileWithinFile to top level moves subtree to end`() {
        val (text, _) = OrgMutations.refileWithinFile(doc, h("Child task"), null)!!
        val redoc = OrgParser.parse(text)
        val moved = redoc.findByTitle("Child task")!!
        assertEquals(1, moved.level)
        assertNull(redoc.parent(moved))
        assertEquals("Child task", redoc.headlines.last().title)
    }

    @Test
    fun `refileWithinFile into own subtree returns null`() {
        assertNull(OrgMutations.refileWithinFile(doc, h("First"), h("Child task").lineIndex))
        assertNull(OrgMutations.refileWithinFile(doc, h("First"), h("First").lineIndex))
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
    fun `newTopLevel appends at end of file`() {
        val (text, line) = OrgMutations.newTopLevel(doc, "Brand new")
        val redoc = OrgParser.parse(text)
        val fresh = redoc.findByTitle("Brand new")!!
        assertEquals(1, fresh.level)
        assertEquals(line, fresh.lineIndex)
        assertEquals(redoc.headlines.last().title, "Brand new")
        assertTrue(text.startsWith(doc.text.trimEnd('\n')))
    }

    @Test
    fun `newTopLevel into empty document has no leading blank`() {
        val empty = OrgParser.parse("")
        val (text, line) = OrgMutations.newTopLevel(empty, "First note")
        assertEquals("* First note\n", text)
        assertEquals(0, line)
    }

    @Test
    fun `insertSiblingAbove lands right before the note at the same level`() {
        val (text, line) = OrgMutations.insertSiblingAbove(doc, h("Second child"), "New above")
        val redoc = OrgParser.parse(text)
        val fresh = redoc.findByTitle("New above")!!
        assertEquals(line, fresh.lineIndex)
        assertEquals(2, fresh.level)
        assertEquals("First", redoc.parent(fresh)!!.title)
        assertEquals(
            listOf("Child task", "New above", "Second child"),
            redoc.directChildren(redoc.findByTitle("First")!!).map { it.title },
        )
    }

    @Test
    fun `insertSiblingBelow lands right after the note's whole subtree`() {
        val (text, line) = OrgMutations.insertSiblingBelow(doc, h("Child task"), "New below")
        val redoc = OrgParser.parse(text)
        val fresh = redoc.findByTitle("New below")!!
        assertEquals(line, fresh.lineIndex)
        assertEquals(2, fresh.level)
        assertEquals(
            listOf("Child task", "New below", "Second child"),
            redoc.directChildren(redoc.findByTitle("First")!!).map { it.title },
        )
        // Child task's own body must stay intact, ahead of the new sibling.
        assertTrue(text.contains("child body"))
    }

    @Test
    fun `insertSiblingBelow at top level appends after the whole subtree`() {
        val (text, line) = OrgMutations.insertSiblingBelow(doc, h("First"), "New top-level")
        val redoc = OrgParser.parse(text)
        val fresh = redoc.findByTitle("New top-level")!!
        assertEquals(line, fresh.lineIndex)
        assertEquals(1, fresh.level)
        assertEquals(
            listOf("First", "New top-level", "Last"),
            redoc.headlines.filter { it.level == 1 }.map { it.title },
        )
    }

    @Test
    fun `headlineLine formatting`() {
        assertEquals("** TODO [#A] Title  :a:b:", OrgMutations.headlineLine(2, "TODO", 'A', "Title", listOf("a", "b")))
        assertEquals("* Title", OrgMutations.headlineLine(1, null, null, "Title", emptyList()))
        assertEquals("* TODO", OrgMutations.headlineLine(1, "TODO", null, "", emptyList()))
    }

    // --- toggleCheckbox ---

    private val checklistDoc = OrgParser.parse(
        """
        * Groceries
        - [ ] milk
        - [X] eggs
        - [-] bread
          - [ ] nested
        - plain item
        not a list line
        """.trimIndent() + "\n"
    )

    private val TWO_STATE = listOf(' ', 'X')
    private val THREE_STATE = listOf(' ', '-', 'X')

    @Test
    fun `two-state toggle cycles open to done and back`() {
        val line = checklistDoc.lines.indexOf("- [ ] milk")
        val once = OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE)!!
        assertTrue(once.lines()[line] == "- [X] milk")
        val redoc = OrgParser.parse(once)
        val twice = OrgMutations.toggleCheckbox(redoc, line, TWO_STATE)!!
        assertEquals("- [ ] milk", twice.lines()[line])
    }

    @Test
    fun `three-state toggle cycles open to in-progress to done and back`() {
        val line = checklistDoc.lines.indexOf("- [ ] milk")
        val step1 = OrgMutations.toggleCheckbox(checklistDoc, line, THREE_STATE)!!
        assertEquals("- [-] milk", step1.lines()[line])
        val step2 = OrgMutations.toggleCheckbox(OrgParser.parse(step1), line, THREE_STATE)!!
        assertEquals("- [X] milk", step2.lines()[line])
        val step3 = OrgMutations.toggleCheckbox(OrgParser.parse(step2), line, THREE_STATE)!!
        assertEquals("- [ ] milk", step3.lines()[line])
    }

    @Test
    fun `toggling a done item under the two-state config unchecks it`() {
        val line = checklistDoc.lines.indexOf("- [X] eggs")
        val result = OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE)!!
        assertEquals("- [ ] eggs", result.lines()[line])
    }

    @Test
    fun `a mark outside the configured states jumps to the first state`() {
        // Document has an in-progress box, but settings are two-state only.
        val line = checklistDoc.lines.indexOf("- [-] bread")
        val result = OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE)!!
        assertEquals("- [ ] bread", result.lines()[line])
    }

    @Test
    fun `lowercase x is treated as done`() {
        val doc = OrgParser.parse("- [x] task\n")
        val result = OrgMutations.toggleCheckbox(doc, 0, TWO_STATE)!!
        assertEquals("- [ ] task", result.lines()[0])
    }

    @Test
    fun `toggle preserves indent and trailing text`() {
        val line = checklistDoc.lines.indexOf("  - [ ] nested")
        val result = OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE)!!
        assertEquals("  - [X] nested", result.lines()[line])
    }

    @Test
    fun `toggle only rewrites the target line, everything else is byte-identical`() {
        val line = checklistDoc.lines.indexOf("- [ ] milk")
        val result = OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE)!!
        assertEquals(
            checklistDoc.lines.filterIndexed { i, _ -> i != line },
            result.lines().filterIndexed { i, _ -> i != line },
        )
    }

    @Test
    fun `non-checkbox list item returns null`() {
        val line = checklistDoc.lines.indexOf("- plain item")
        assertNull(OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE))
    }

    @Test
    fun `non-list line returns null`() {
        val line = checklistDoc.lines.indexOf("not a list line")
        assertNull(OrgMutations.toggleCheckbox(checklistDoc, line, TWO_STATE))
    }

    @Test
    fun `out of range line index returns null`() {
        assertNull(OrgMutations.toggleCheckbox(checklistDoc, -1, TWO_STATE))
        assertNull(OrgMutations.toggleCheckbox(checklistDoc, checklistDoc.lines.size, TWO_STATE))
    }
}
