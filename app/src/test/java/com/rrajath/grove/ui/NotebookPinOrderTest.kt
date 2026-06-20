package com.rrajath.grove.ui

import com.rrajath.grove.ui.vault.NotebookItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifies the pin-ordering logic applied in NotebooksViewModel:
 * pinned notebooks rise to the top in pin insertion order; unpinned
 * notebooks remain in alphabetical order below them.
 */
class NotebookPinOrderTest {

    private fun item(name: String, pinnedIndex: Int = -1) = NotebookItem(
        fileName = name,
        noteCount = 0,
        lastModified = 0L,
        hasConflict = false,
        pinnedIndex = pinnedIndex,
    )

    private fun sortedLike(items: List<NotebookItem>): List<NotebookItem> =
        items.sortedWith(
            compareBy<NotebookItem> { if (it.isPinned) it.pinnedIndex else Int.MAX_VALUE }
                .thenBy { it.fileName.lowercase() }
        )

    @Test
    fun `pinned items appear before unpinned items`() {
        val items = listOf(
            item("beta.org"),
            item("alpha.org", pinnedIndex = 0),
            item("gamma.org"),
        )
        val sorted = sortedLike(items)
        assertEquals("alpha.org", sorted[0].fileName)
        assertEquals("beta.org", sorted[1].fileName)
        assertEquals("gamma.org", sorted[2].fileName)
    }

    @Test
    fun `pinned items respect insertion order regardless of alphabet`() {
        val items = listOf(
            item("zebra.org", pinnedIndex = 0),
            item("apple.org", pinnedIndex = 1),
            item("mango.org", pinnedIndex = 2),
            item("notes.org"),
        )
        val sorted = sortedLike(items)
        assertEquals("zebra.org", sorted[0].fileName)
        assertEquals("apple.org", sorted[1].fileName)
        assertEquals("mango.org", sorted[2].fileName)
        assertEquals("notes.org", sorted[3].fileName)
    }

    @Test
    fun `unpinned items are sorted alphabetically case-insensitively`() {
        val items = listOf(
            item("Zebra.org"),
            item("apple.org"),
            item("Mango.org"),
        )
        val sorted = sortedLike(items)
        assertEquals("apple.org", sorted[0].fileName)
        assertEquals("Mango.org", sorted[1].fileName)
        assertEquals("Zebra.org", sorted[2].fileName)
    }

    @Test
    fun `isPinned reflects pinnedIndex correctly`() {
        assertTrue(item("a.org", pinnedIndex = 0).isPinned)
        assertTrue(item("b.org", pinnedIndex = 5).isPinned)
        assertFalse(item("c.org", pinnedIndex = -1).isPinned)
        assertFalse(item("d.org").isPinned)
    }

    @Test
    fun `all notebooks unpinned uses pure alphabetical order`() {
        val items = listOf(item("z.org"), item("a.org"), item("m.org"))
        val sorted = sortedLike(items)
        assertEquals(listOf("a.org", "m.org", "z.org"), sorted.map { it.fileName })
    }

    @Test
    fun `all notebooks pinned uses pin index order`() {
        val items = listOf(
            item("c.org", pinnedIndex = 2),
            item("a.org", pinnedIndex = 0),
            item("b.org", pinnedIndex = 1),
        )
        val sorted = sortedLike(items)
        assertEquals(listOf("a.org", "b.org", "c.org"), sorted.map { it.fileName })
    }
}
