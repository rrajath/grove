package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineEditingTest {

    /** Simulate typing Enter at [cursor] in [text] and running the helper. */
    private fun pressEnter(text: String, cursor: Int): TextEdit? {
        val typed = text.substring(0, cursor) + "\n" + text.substring(cursor)
        return LineEditing.continueListOnEnter(text, typed, cursor + 1)
    }

    @Test
    fun `dash item continues with a dash`() {
        val edit = pressEnter("- milk", 6)!!
        assertEquals("- milk\n- ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    @Test
    fun `plus item continues with a plus`() {
        val edit = pressEnter("+ eggs", 6)!!
        assertEquals("+ eggs\n+ ", edit.text)
    }

    @Test
    fun `indentation is preserved`() {
        val edit = pressEnter("  - nested", 10)!!
        assertEquals("  - nested\n  - ", edit.text)
    }

    @Test
    fun `numbered item increments`() {
        val edit = pressEnter("3. third", 8)!!
        assertEquals("3. third\n4. ", edit.text)
        val paren = pressEnter("1) first", 8)!!
        assertEquals("1) first\n2) ", paren.text)
    }

    @Test
    fun `enter on an empty item removes the bullet`() {
        val edit = pressEnter("- a\n- ", 6)!!
        assertEquals("- a\n", edit.text)
        assertEquals(4, edit.cursor)
    }

    @Test
    fun `enter on an empty numbered item removes the bullet`() {
        val edit = pressEnter("1. a\n2. ", 8)!!
        assertEquals("1. a\n", edit.text)
        assertEquals(5, edit.cursor)
    }

    @Test
    fun `enter mid-list keeps the following text after the new bullet`() {
        val edit = pressEnter("- one\n- three", 5)!!
        assertEquals("- one\n- \n- three", edit.text)
        assertEquals(8, edit.cursor)
    }

    // --- checklist items ---

    @Test
    fun `checklist item continues with a fresh unchecked box`() {
        val edit = pressEnter("- [ ] milk", 10)!!
        assertEquals("- [ ] milk\n- [ ] ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    @Test
    fun `checked item continues unchecked, not carrying the done state forward`() {
        val edit = pressEnter("- [X] milk", 10)!!
        assertEquals("- [X] milk\n- [ ] ", edit.text)
    }

    @Test
    fun `in-progress item continues unchecked`() {
        val edit = pressEnter("- [-] milk", 10)!!
        assertEquals("- [-] milk\n- [ ] ", edit.text)
    }

    @Test
    fun `numbered checklist item increments and keeps the checkbox`() {
        val edit = pressEnter("1. [ ] first", 12)!!
        assertEquals("1. [ ] first\n2. [ ] ", edit.text)
    }

    @Test
    fun `checklist indentation is preserved`() {
        val edit = pressEnter("  - [ ] nested", 14)!!
        assertEquals("  - [ ] nested\n  - [ ] ", edit.text)
    }

    @Test
    fun `enter on an empty checklist item removes it`() {
        val edit = pressEnter("- [ ] a\n- [ ] ", 14)!!
        assertEquals("- [ ] a\n", edit.text)
        assertEquals(8, edit.cursor)
    }

    @Test
    fun `enter on an empty checked item removes it`() {
        val edit = pressEnter("- [X] a\n- [X] ", 14)!!
        assertEquals("- [X] a\n", edit.text)
    }

    @Test
    fun `checklist indent preserves the box state`() {
        val edit = LineEditing.changeListIndent("- [ ] one\n- [X] two", 13, +1)!!
        assertEquals("- [ ] one\n  - [X] two", edit.text)
    }

    @Test
    fun `checklist outdent preserves the box state`() {
        val edit = LineEditing.changeListIndent("- [ ] one\n  - [-] two", 15, -1)!!
        assertEquals("- [ ] one\n- [-] two", edit.text)
    }

    @Test
    fun `indenting a numbered checklist item restarts numbering and keeps the box`() {
        val edit = LineEditing.changeListIndent("1. [ ] one\n2. [X] two", 17, +1)!!
        assertEquals("1. [ ] one\n  1. [X] two", edit.text)
    }

    @Test
    fun `non-list lines are untouched`() {
        assertNull(pressEnter("plain text", 10))
        assertNull(pressEnter("* heading", 9))
    }

    @Test
    fun `edits that are not a single newline are ignored`() {
        // paste of two chars
        assertNull(LineEditing.continueListOnEnter("- a", "- ab\n", 5))
        // deletion
        assertNull(LineEditing.continueListOnEnter("- ab", "- a", 3))
    }

    @Test
    fun `heading button starts a new heading from normal text`() {
        val edit = LineEditing.insertHeadingStar("some text", 9)
        assertEquals("some text\n* ", edit.text)
        assertEquals(edit.text.length, edit.cursor)
    }

    @Test
    fun `heading button demotes an empty heading`() {
        val once = LineEditing.insertHeadingStar("* ", 2)
        assertEquals("** ", once.text)
        assertEquals(3, once.cursor)
        val twice = LineEditing.insertHeadingStar(once.text, once.cursor)
        assertEquals("*** ", twice.text)
    }

    @Test
    fun `heading button demotes an empty heading without trailing space`() {
        val edit = LineEditing.insertHeadingStar("*", 1)
        assertEquals("** ", edit.text)
    }

    @Test
    fun `heading demote works on a middle line`() {
        val edit = LineEditing.insertHeadingStar("* top\n* \nbody", 8)
        assertEquals("* top\n** \nbody", edit.text)
        assertEquals(9, edit.cursor)
    }

    @Test
    fun `non-empty heading line inserts a new heading instead of demoting`() {
        val edit = LineEditing.insertHeadingStar("* title", 7)
        assertEquals("* title\n* ", edit.text)
    }

    // --- list indent buttons ---

    @Test
    fun `indent turns an item into a sub-list item`() {
        val edit = LineEditing.changeListIndent("- one\n- two", 9, +1)!!
        assertEquals("- one\n  - two", edit.text)
        assertEquals(11, edit.cursor)
    }

    @Test
    fun `outdent promotes a sub-list item`() {
        val edit = LineEditing.changeListIndent("- one\n  - two", 11, -1)!!
        assertEquals("- one\n- two", edit.text)
        assertEquals(9, edit.cursor)
    }

    @Test
    fun `indenting an ordered item restarts numbering at 1`() {
        val edit = LineEditing.changeListIndent("1. one\n2. two", 13, +1)!!
        assertEquals("1. one\n  1. two", edit.text)
    }

    @Test
    fun `indenting a paren-numbered item restarts at 1 keeping the suffix`() {
        val edit = LineEditing.changeListIndent("1) one\n3) three", 15, +1)!!
        assertEquals("1) one\n  1) three", edit.text)
    }

    @Test
    fun `outdenting an ordered sub-item keeps its number`() {
        val edit = LineEditing.changeListIndent("1. one\n  1. sub", 15, -1)!!
        assertEquals("1. one\n1. sub", edit.text)
    }

    @Test
    fun `outdent at column zero does nothing`() {
        assertNull(LineEditing.changeListIndent("- one", 5, -1))
    }

    @Test
    fun `indent on a non-list line does nothing`() {
        assertNull(LineEditing.changeListIndent("plain text", 5, +1))
        assertNull(LineEditing.changeListIndent("* heading", 5, +1))
    }

    @Test
    fun `outdent of a single-space indent removes just that space`() {
        val edit = LineEditing.changeListIndent(" - one", 6, -1)!!
        assertEquals("- one", edit.text)
        assertEquals(5, edit.cursor)
    }

    @Test
    fun `cursor at line start stays at line start on outdent`() {
        val edit = LineEditing.changeListIndent("- one\n  - two", 6, -1)!!
        assertEquals("- one\n- two", edit.text)
        assertEquals(6, edit.cursor)
    }

    @Test
    fun `indent only affects the cursor line`() {
        val edit = LineEditing.changeListIndent("- one\n- two\n- three", 8, +1)!!
        assertEquals("- one\n  - two\n- three", edit.text)
    }
}
