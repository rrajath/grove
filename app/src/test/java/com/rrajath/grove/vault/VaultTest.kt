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
        assertTrue(tmp.root.resolve("n-1.org.trash").exists())
        assertEquals("* Older deleted copy", tmp.root.resolve("n.org.trash").readText())
    }

    @Test
    fun `trashNotebook inserts the counter before the org extension on collision`() = runTest {
        tmp.newFile("foo.org").writeText("* First")
        val v = vault()
        assertTrue(v.trashNotebook("foo.org"))
        assertEquals("* First", tmp.root.resolve("foo.org.trash").readText())

        tmp.newFile("foo.org").writeText("* Second")
        assertTrue(v.trashNotebook("foo.org"))
        assertEquals("* Second", tmp.root.resolve("foo-1.org.trash").readText())

        tmp.newFile("foo.org").writeText("* Third")
        assertTrue(v.trashNotebook("foo.org"))
        assertEquals("* Third", tmp.root.resolve("foo-2.org.trash").readText())
    }

    @Test
    fun `trashNotebook counter goes before the org segment for nested paths`() = runTest {
        tmp.newFolder("sub", "dir")
        tmp.root.resolve("sub/dir/foo.org").writeText("* First")
        tmp.root.resolve("sub/dir/foo.org.trash").writeText("* Older")
        val v = vault()

        assertTrue(v.trashNotebook("sub/dir/foo.org"))
        assertTrue(tmp.root.resolve("sub/dir/foo-1.org.trash").exists())
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

    @Test
    fun `display name and dir are derived from a nested path`() {
        val nb = Notebook("projects/clients/acme.org", 0, 0L)
        assertEquals("acme", nb.displayName)
        assertEquals("projects/clients", nb.dir)
        assertEquals("", Notebook("root.org", 0, 0L).dir)
    }

    @Test
    fun `notebooks lists files from subdirectories by path`() = runTest {
        tmp.newFile("root.org").writeText("* R")
        tmp.newFolder("projects", "clients")
        tmp.root.resolve("projects/notes.org").writeText("* N\n* M")
        tmp.root.resolve("projects/clients/acme.org").writeText("* A")

        val notebooks = vault().notebooks()
        assertEquals(
            listOf("projects/clients/acme.org", "projects/notes.org", "root.org"),
            notebooks.map { it.fileName },
        )
        assertEquals(listOf(1, 2, 1), notebooks.map { it.noteCount })
    }

    @Test
    fun `createNotebook into a directory creates missing folders`() = runTest {
        val v = vault()
        assertTrue(v.createNotebook("tasks", dir = "work/2026"))
        assertTrue(tmp.root.resolve("work/2026/tasks.org").exists())
        assertFalse(v.createNotebook("tasks.org", dir = "work/2026"))
    }

    @Test
    fun `moveNotebook relocates the file keeping its name`() = runTest {
        tmp.newFile("acme.org").writeText("* A")
        val v = vault()

        assertEquals("clients/acme.org", v.moveNotebook("acme.org", "clients"))
        assertFalse(tmp.root.resolve("acme.org").exists())
        assertEquals("* A", tmp.root.resolve("clients/acme.org").readText())
        assertNull(v.moveNotebook("clients/acme.org", "clients"))
    }

    @Test
    fun `moveNotebook into a not-yet-existing nested folder creates the path`() = runTest {
        tmp.newFile("acme.org").writeText("* A")
        val v = vault()

        assertEquals("clients/2026/acme.org", v.moveNotebook("acme.org", "clients/2026"))
        assertTrue(tmp.root.resolve("clients/2026/acme.org").exists())
    }

    @Test
    fun `createNotebook honours a typed nested name under a directory`() = runTest {
        val v = vault()
        assertTrue(v.createNotebook("clients/acme.org", dir = "projects"))
        assertTrue(tmp.root.resolve("projects/clients/acme.org").exists())
    }

    @Test
    fun `renameFolder moves every descendant and creates the new directory`() = runTest {
        tmp.newFolder("projects", "clients")
        tmp.root.resolve("projects/grove.org").writeText("* G")
        tmp.root.resolve("projects/clients/acme.org").writeText("* A")
        val v = vault()

        assertEquals("work", v.renameFolder("projects", "work"))
        assertFalse(tmp.root.resolve("projects/grove.org").exists())
        assertEquals("* G", tmp.root.resolve("work/grove.org").readText())
        assertEquals("* A", tmp.root.resolve("work/clients/acme.org").readText())
        assertEquals(
            listOf("work/clients/acme.org", "work/grove.org"),
            v.notebooks().map { it.fileName },
        )
    }

    @Test
    fun `renameFolder keeps the parent and renames only the leaf`() = runTest {
        tmp.newFolder("area", "old")
        tmp.root.resolve("area/old/note.org").writeText("* N")
        val v = vault()

        assertEquals("area/new", v.renameFolder("area/old", "new"))
        assertEquals("* N", tmp.root.resolve("area/new/note.org").readText())
    }

    @Test
    fun `renameFolder is a no-op when the name is unchanged or the target exists`() = runTest {
        tmp.newFolder("a")
        tmp.root.resolve("a/one.org").writeText("* 1")
        tmp.newFolder("b")
        tmp.root.resolve("b/one.org").writeText("* 2")
        val v = vault()

        assertNull(v.renameFolder("a", "a"))
        assertNull(v.renameFolder("a", "b")) // b/one.org already exists
        assertTrue(tmp.root.resolve("a/one.org").exists())
    }

    @Test
    fun `trashFolder trashes every file and the folder drops out of the tree`() = runTest {
        tmp.newFolder("projects", "clients")
        tmp.root.resolve("projects/grove.org").writeText("* G")
        tmp.root.resolve("projects/clients/acme.org").writeText("* A")
        tmp.newFile("root.org").writeText("* R")
        val v = vault()

        assertEquals(2, v.trashFolder("projects"))
        assertEquals(listOf("root.org"), v.notebooks().map { it.fileName })
        assertTrue(tmp.root.resolve("projects/grove.org.trash").exists())
    }

    // --- case-insensitive name-collision guard (SAF/FAT sync targets) ---
    //
    // JvmFileStore inherits the host filesystem's casing behaviour (case-
    // insensitive on the default macOS/APFS setup, case-sensitive on Linux CI),
    // so these use a strictly case-sensitive in-memory store to prove Vault's
    // own guard rather than the OS's.

    /** In-memory, strictly case-sensitive [FileStore]. */
    private class CaseSensitiveStore : FileStore {
        private val files = linkedMapOf<String, String>()
        override suspend fun list(): List<FileEntry> =
            files.map { (name, body) -> FileEntry(name, 0L, body.length.toLong()) }
        override suspend fun read(name: String): String = files.getValue(name)
        override suspend fun write(name: String, content: String) { files[name] = content }
        override suspend fun create(name: String): Boolean {
            if (name in files) return false
            files[name] = ""
            return true
        }
        override suspend fun rename(oldName: String, newName: String): Boolean {
            if (oldName == newName || newName in files || oldName !in files) return false
            files[newName] = files.remove(oldName)!!
            return true
        }
        override suspend fun delete(name: String): Boolean = files.remove(name) != null
        override suspend fun exists(name: String): Boolean = name in files
    }

    @Test
    fun `createNotebook rejects a case-only duplicate`() = runTest {
        val v = Vault(CaseSensitiveStore())
        assertTrue(v.createNotebook("Work"))
        assertFalse(v.createNotebook("work"))
        assertFalse(v.createNotebook("WORK.org"))
    }

    @Test
    fun `renameNotebook rejects a rename onto an existing name case-insensitively`() = runTest {
        val v = Vault(CaseSensitiveStore())
        assertTrue(v.createNotebook("inbox"))
        assertTrue(v.createNotebook("archive"))
        assertFalse(v.renameNotebook("inbox.org", "Archive"))
        // A case-only self-rename is still allowed.
        assertTrue(v.renameNotebook("inbox.org", "Inbox"))
    }

    @Test
    fun `renameFolder rejects merging into an existing folder case-insensitively`() = runTest {
        val v = Vault(CaseSensitiveStore())
        assertTrue(v.createNotebook("one", dir = "Alpha"))
        assertTrue(v.createNotebook("two", dir = "Beta"))
        assertNull(v.renameFolder("Alpha", "Beta"))
        assertNull(v.renameFolder("Alpha", "beta"))
    }
}
