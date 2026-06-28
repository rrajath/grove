package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockParserTest {

    @Test
    fun `paragraphs split on blank lines`() {
        val blocks = BlockParser.parse(listOf("one", "two", "", "three"))
        assertEquals(2, blocks.size)
        assertEquals(listOf("one", "two"), (blocks[0] as OrgBlock.Paragraph).lines)
        assertEquals(listOf("three"), (blocks[1] as OrgBlock.Paragraph).lines)
    }

    @Test
    fun `code block with language`() {
        val blocks = BlockParser.parse(
            listOf("before", "#+BEGIN_SRC kotlin", "val x = 1", "#+END_SRC", "after")
        )
        assertEquals(3, blocks.size)
        val code = blocks[1] as OrgBlock.CodeBlock
        assertEquals("kotlin", code.language)
        assertEquals(listOf("val x = 1"), code.lines)
    }

    @Test
    fun `example block and case-insensitive markers`() {
        val blocks = BlockParser.parse(listOf("#+begin_example", "raw", "#+end_example"))
        val code = blocks[0] as OrgBlock.CodeBlock
        assertNull(code.language)
        assertEquals(listOf("raw"), code.lines)
    }

    @Test
    fun `unterminated code block runs to end`() {
        val blocks = BlockParser.parse(listOf("#+BEGIN_SRC", "a", "b"))
        assertEquals(listOf("a", "b"), (blocks[0] as OrgBlock.CodeBlock).lines)
    }

    @Test
    fun `lists with checkboxes and ordering`() {
        val blocks = BlockParser.parse(
            listOf("- plain", "- [ ] open task", "- [X] done task", "1. first", "2) second")
        )
        val list = blocks[0] as OrgBlock.ListBlock
        assertEquals(5, list.items.size)
        assertNull(list.items[0].checkbox)
        assertEquals(' ', list.items[1].checkbox)
        assertEquals('X', list.items[2].checkbox)
        assertEquals(true, list.items[3].ordered)
        assertEquals("second", list.items[4].text)
    }

    @Test
    fun `nested list items carry correct indent levels`() {
        val blocks = BlockParser.parse(
            listOf(
                "- top",
                "  - nested",
                "    - deep",
                "  - nested again",
                "- top again",
            )
        )
        val list = blocks[0] as OrgBlock.ListBlock
        assertEquals(5, list.items.size)
        assertEquals(0, list.items[0].indent)
        assertEquals(2, list.items[1].indent)
        assertEquals(4, list.items[2].indent)
        assertEquals(2, list.items[3].indent)
        assertEquals(0, list.items[4].indent)
    }

    @Test
    fun `tables group into one block`() {
        val blocks = BlockParser.parse(listOf("| a | b |", "|---|---|", "| 1 | 2 |"))
        assertEquals(1, blocks.size)
        assertEquals(3, (blocks[0] as OrgBlock.Table).lines.size)
    }

    @Test
    fun `mixed content keeps order`() {
        val blocks = BlockParser.parse(
            listOf("text", "", "- item", "", "| t |", "#+BEGIN_SRC", "x", "#+END_SRC")
        )
        assertEquals(
            listOf(
                OrgBlock.Paragraph::class, OrgBlock.ListBlock::class,
                OrgBlock.Table::class, OrgBlock.CodeBlock::class,
            ),
            blocks.map { it::class },
        )
    }
}
