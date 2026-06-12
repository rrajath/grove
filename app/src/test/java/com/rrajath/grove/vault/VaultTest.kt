package com.rrajath.grove.vault

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class VaultTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun vault(): Vault = Vault(JvmFileStore(tmp.root))

    @Test
    fun `lists only org files`() = runTest {
        tmp.newFile("a.org").writeText("* One\n** Sub\n*** Deeper\n* Two")
        tmp.newFile("b.org").writeText("* Single\n** Child")
        tmp.newFile("readme.txt").writeText("not org")

        val notebooks = vault().notebooks()
        assertEquals(listOf("a.org", "b.org"), notebooks.map { it.fileName })
        // Only top-level headings count as notes; subheadings belong to them.
        assertEquals(listOf(2, 1), notebooks.map { it.noteCount })
    }

    @Test
    fun `trashNotebook hides the file and survives an existing trash copy`() = runTest {
        tmp.newFile("n.org").writeText("* Note")
        tmp.newFile("n.org.trash").writeText("* Older deleted copy")

        val v = vault()
        assertTrue(v.trashNotebook("n.org"))
        assertEquals(emptyList<String>(), v.notebooks().map { it.fileName })
        assertTrue(tmp.root.resolve("n.org.trash-2").exists())
        assertEquals("* Older deleted copy", tmp.root.resolve("n.org.trash").readText())
    }

    @Test
    fun `applies orgzlyignore rules`() = runTest {
        tmp.newFile("keep.org").writeText("* K")
        tmp.newFile("archive.org").writeText("* A")
        tmp.newFile(".orgzlyignore").writeText("archive*")

        val notebooks = vault().notebooks()
        assertEquals(listOf("keep.org"), notebooks.map { it.fileName })
    }

    @Test
    fun `open parses the document`() = runTest {
        tmp.newFile("n.org").writeText("* Heading :tag:\nBody")
        val doc = vault().open("n.org")
        assertNotNull(doc)
        assertEquals("Heading", doc!!.headlines[0].title)
        assertNull(vault().open("missing.org"))
    }

    @Test
    fun `createNotebook appends org extension and rejects duplicates`() = runTest {
        val v = vault()
        assertTrue(v.createNotebook("inbox"))
        assertTrue(tmp.root.resolve("inbox.org").exists())
        assertFalse(v.createNotebook("inbox.org"))
    }

    @Test
    fun `save invalidates cache`() = runTest {
        tmp.newFile("n.org").writeText("* Old")
        val v = vault()
        assertEquals("Old", v.open("n.org")!!.headlines[0].title)
        v.save("n.org", "* New")
        assertEquals("New", v.open("n.org")!!.headlines[0].title)
    }

    @Test
    fun `display name strips extension`() {
        assertEquals("journal", Notebook("journal.org", 0, 0L).displayName)
    }
}
