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
}
