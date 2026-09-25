package com.rrajath.grove.ui.editor

import androidx.compose.ui.text.TextRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlockTemplateSuggestTest {

    /** [text] with `|` marking the caret: returns the trigger there, if any. */
    private fun triggerAt(text: String): BlockTrigger? {
        val caret = text.indexOf('|')
        return blockTemplateTriggerAt(text.removeRange(caret, caret + 1), TextRange(caret))
    }

    /** Expands the trigger at `|` and returns the result with `|` at the new caret. */
    private fun expand(text: String): String {
        val caret = text.indexOf('|')
        val plain = text.removeRange(caret, caret + 1)
        val trigger = blockTemplateTriggerAt(plain, TextRange(caret))!!
        val ins = expandBlockTemplate(plain, trigger)
        val out = plain.replaceRange(ins.start, ins.end, ins.replacement)
        return out.substring(0, ins.cursor) + "|" + out.substring(ins.cursor)
    }

    @Test
    fun `each shorthand maps to its template`() {
        assertEquals(BlockTemplate.QUOTE, triggerAt("<q|")?.template)
        assertEquals(BlockTemplate.EXAMPLE, triggerAt("<e|")?.template)
        assertEquals(BlockTemplate.SRC, triggerAt("<s|")?.template)
        assertEquals(TextRange(5, 7), triggerAt("text <q|")?.range)
    }

    @Test
    fun `no trigger for other letters, glued text, longer words or a selection`() {
        assertNull(triggerAt("<x|"))
        assertNull(triggerAt("<Q|"))
        assertNull(triggerAt("a<q|"))
        assertNull(triggerAt("<qu|"))
        assertNull(triggerAt("<q|x"))
        assertNull(triggerAt("<|q"))
        assertNull(blockTemplateTriggerAt("<q", TextRange(0, 2)))
    }

    @Test
    fun `trigger allowed with whitespace after the caret`() {
        assertEquals(BlockTemplate.QUOTE, triggerAt("<q| more")?.template)
        assertEquals(BlockTemplate.QUOTE, triggerAt("<q|\nnext")?.template)
    }

    @Test
    fun `no trigger in the preface`() {
        assertNull(triggerAt("#+title: <q|\n\nbody"))
        assertEquals(BlockTemplate.QUOTE, triggerAt("#+title: x\n\n<q|")?.template)
    }

    @Test
    fun `blank line is replaced by the block`() {
        assertEquals("* H\n#+BEGIN_QUOTE\n|\n#+END_QUOTE\nafter", expand("* H\n<q|\nafter"))
        assertEquals("#+BEGIN_EXAMPLE\n|\n#+END_EXAMPLE", expand("<e|"))
    }

    @Test
    fun `src caret lands after the begin line for the language`() {
        assertEquals("#+BEGIN_SRC |\n\n#+END_SRC", expand("<s|"))
    }

    @Test
    fun `existing text stays on its line and the block goes below`() {
        assertEquals("some text\n#+BEGIN_QUOTE\n|\n#+END_QUOTE", expand("some text <q|"))
        assertEquals("trailing\n#+BEGIN_QUOTE\n|\n#+END_QUOTE\nnext", expand("<q| trailing\nnext"))
        assertEquals("a b\n#+BEGIN_QUOTE\n|\n#+END_QUOTE", expand("a <q| b"))
        assertEquals("  - x\n  #+BEGIN_QUOTE\n  |\n  #+END_QUOTE", expand("  <q| - x"))
    }

    @Test
    fun `block matches the line indentation`() {
        assertEquals(
            "  - item\n  #+BEGIN_QUOTE\n  |\n  #+END_QUOTE",
            expand("  - item <q|"),
        )
        assertEquals("  #+BEGIN_SRC |\n  \n  #+END_SRC", expand("  <s|"))
    }
}
