package com.rrajath.grove.ui.screens

import com.rrajath.grove.org.OrgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the read view's subtree folding: [visibleReadRows] (which
 * headings mount) and [defaultReadCollapse] (what folds on open).
 */
class ReadViewFoldTest {

    private fun buildDoc(headingCount: Int): String = buildString {
        appendLine("* Root")
        var written = 0
        var section = 0
        while (written < headingCount) {
            section++
            appendLine("** Section $section")
            written++
            var leaf = 0
            while (written < headingCount && leaf < 4) {
                leaf++
                appendLine("*** Section $section item $leaf")
                appendLine("body for section $section item $leaf")
                written++
            }
        }
    }

    @Test
    fun `visibleReadRows hides everything under a folded heading`() {
        val doc = OrgParser.parse(
            """
            * Root
            ** A
            *** A1
            **** A1a
            ** B
            *** B1
            """.trimIndent(),
        )
        val root = doc.headlines.first { it.title == "Root" }
        val subtree = doc.subtree(root)
        val a = subtree.first { it.title == "A" }

        val visible = visibleReadRows(subtree, collapsed = setOf(a.lineIndex))
        val titles = visible.map { it.title }

        // A stays (it's the folded heading itself), its descendants vanish,
        // B and B1 (a different branch) are untouched.
        assertEquals(listOf("A", "B", "B1"), titles)
    }

    @Test
    fun `visibleReadRows with no collapse returns the whole subtree`() {
        val doc = OrgParser.parse("* Root\n** A\n*** A1\n** B")
        val root = doc.headlines.first()
        val subtree = doc.subtree(root)
        assertEquals(subtree, visibleReadRows(subtree, collapsed = emptySet()))
    }

    @Test
    fun `nested folds both take effect`() {
        val doc = OrgParser.parse(
            """
            * Root
            ** A
            *** A1
            **** A1a
            *** A2
            """.trimIndent(),
        )
        val root = doc.headlines.first()
        val subtree = doc.subtree(root)
        val a = subtree.first { it.title == "A" }
        val a1 = subtree.first { it.title == "A1" }

        // Folding A alone hides A1, A1a, A2.
        assertEquals(listOf("A"), visibleReadRows(subtree, setOf(a.lineIndex)).map { it.title })
        // Folding only A1 hides A1a but keeps A2.
        assertEquals(
            listOf("A", "A1", "A2"),
            visibleReadRows(subtree, setOf(a1.lineIndex)).map { it.title },
        )
    }

    @Test
    fun `small note opens fully expanded`() {
        val doc = OrgParser.parse(buildDoc(headingCount = 10))
        val root = doc.headlines.first()
        assertEquals(emptySet<Int>(), defaultReadCollapse(doc, doc.subtree(root)))
    }

    @Test
    fun `large note folds every heading that has descendants`() {
        val doc = OrgParser.parse(buildDoc(headingCount = LARGE_SUBTREE_THRESHOLD + 20))
        val root = doc.headlines.first()
        val subtree = doc.subtree(root)

        val folded = defaultReadCollapse(doc, subtree)
        assertTrue("expected a non-empty default fold", folded.isNotEmpty())

        // Exactly the headings with children are folded.
        val expected = subtree.filter { doc.hasDescendants(it) }.map { it.lineIndex }.toSet()
        assertEquals(expected, folded)

        // On open, only the note body + the one-level section list mounts.
        val visible = visibleReadRows(subtree, folded)
        assertTrue(visible.all { it.level <= 2 })
        assertTrue(visible.isNotEmpty())
    }
}
