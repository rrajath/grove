package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun `src block with language`() {
        val blocks = BlockParser.parse(
            listOf("before", "#+BEGIN_SRC kotlin", "val x = 1", "#+END_SRC", "after")
        )
        assertEquals(3, blocks.size)
        val block = blocks[1] as OrgBlock.Block
        assertEquals("SRC", block.kind)
        assertEquals("kotlin", block.language)
        assertEquals(listOf("val x = 1"), block.contentLines)
        assertEquals(1, block.startLine)
    }

    @Test
    fun `example block and case-insensitive markers`() {
        val blocks = BlockParser.parse(listOf("#+begin_example", "raw", "#+end_example"))
        val block = blocks[0] as OrgBlock.Block
        assertEquals("EXAMPLE", block.kind)
        assertNull(block.language)
        assertEquals(listOf("raw"), block.contentLines)
    }

    @Test
    fun `quote block keeps its kind`() {
        val blocks = BlockParser.parse(listOf("#+begin_quote", "To be, or not to be.", "#+end_quote"))
        val block = blocks[0] as OrgBlock.Block
        assertEquals("QUOTE", block.kind)
        assertNull(block.language)
        assertEquals(listOf("To be, or not to be."), block.contentLines)
    }

    @Test
    fun `unterminated block runs to end`() {
        val blocks = BlockParser.parse(listOf("#+BEGIN_SRC", "a", "b"))
        assertEquals(listOf("a", "b"), (blocks[0] as OrgBlock.Block).contentLines)
    }

    @Test
    fun `mismatched end does not terminate a block`() {
        val blocks = BlockParser.parse(
            listOf("#+BEGIN_QUOTE", "one", "#+END_SRC", "two", "#+END_QUOTE")
        )
        assertEquals(1, blocks.size)
        assertEquals(listOf("one", "#+END_SRC", "two"), (blocks[0] as OrgBlock.Block).contentLines)
    }

    @Test
    fun `affiliated keywords directly above a block fold into it`() {
        val blocks = BlockParser.parse(
            listOf(
                "#+CAPTION: A snippet",
                "#+ATTR_LATEX: :width 0.8",
                "#+BEGIN_SRC python",
                "print('hi')",
                "#+END_SRC",
            )
        )
        assertEquals(1, blocks.size)
        val block = blocks[0] as OrgBlock.Block
        assertEquals("SRC", block.kind)
        assertEquals("python", block.language)
        assertEquals(listOf("#+CAPTION: A snippet", "#+ATTR_LATEX: :width 0.8"), block.affiliated)
        assertEquals(listOf("print('hi')"), block.contentLines)
        assertEquals(0, block.startLine)
    }

    @Test
    fun `standalone affiliated keyword stays paragraph text`() {
        val blocks = BlockParser.parse(listOf("#+ATTR_LATEX: :width 0.8", "some prose"))
        assertEquals(1, blocks.size)
        assertTrue(blocks[0] is OrgBlock.Paragraph)
        assertEquals(listOf("#+ATTR_LATEX: :width 0.8", "some prose"), (blocks[0] as OrgBlock.Paragraph).lines)
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
                OrgBlock.Table::class, OrgBlock.Block::class,
            ),
            blocks.map { it::class },
        )
    }
}
