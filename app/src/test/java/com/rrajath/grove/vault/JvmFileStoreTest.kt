package com.rrajath.grove.vault

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class JvmFileStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun store() = JvmFileStore(tmp.root)

    @Test
    fun `list returns vault-relative paths for nested files`() = runTest {
        tmp.newFile("root.org").writeText("* R")
        tmp.newFolder("projects", "clients")
        tmp.root.resolve("projects/notes.org").writeText("* N")
        tmp.root.resolve("projects/clients/acme.org").writeText("* A")

        assertEquals(
            listOf("projects/clients/acme.org", "projects/notes.org", "root.org"),
            store().list().map { it.name },
        )
    }

    @Test
    fun `list skips dot-directories`() = runTest {
        tmp.newFile("keep.org").writeText("* K")
        tmp.newFolder(".stversions")
        tmp.root.resolve(".stversions/keep~1.org").writeText("* old copy")
        tmp.newFolder(".git")
        tmp.root.resolve(".git/config").writeText("[core]")

        assertEquals(listOf("keep.org"), store().list().map { it.name })
    }

    @Test
    fun `create makes intermediate directories`() = runTest {
        assertTrue(store().create("a/b/c.org"))
        assertTrue(tmp.root.resolve("a/b/c.org").exists())
        assertFalse(store().create("a/b/c.org"))
    }

    @Test
    fun `rename moves a file across directories`() = runTest {
        val s = store()
        s.create("inbox.org")
        s.write("inbox.org", "* Task")

        assertTrue(s.rename("inbox.org", "archive/2026/inbox.org"))
        assertFalse(tmp.root.resolve("inbox.org").exists())
        assertEquals("* Task", tmp.root.resolve("archive/2026/inbox.org").readText())
    }

    @Test
    fun `rename refuses to overwrite an existing target`() = runTest {
        val s = store()
        s.create("a/one.org")
        s.create("b/two.org")
        assertFalse(s.rename("a/one.org", "b/two.org"))
        assertTrue(tmp.root.resolve("a/one.org").exists())
    }

    @Test
    fun `read write stat exists delete resolve by path`() = runTest {
        val s = store()
        s.create("dir/note.org")
        s.write("dir/note.org", "hello")

        assertEquals("hello", s.read("dir/note.org"))
        assertEquals("hello".length.toLong(), s.stat("dir/note.org")!!.size)
        assertTrue(s.exists("dir/note.org"))
        assertNull(s.stat("dir/missing.org"))

        assertTrue(s.delete("dir/note.org"))
        assertFalse(s.exists("dir/note.org"))
    }

    @Test
    fun `resolve rejects parent traversal`() = runTest {
        val ex = runCatching { store().read("../escape.org") }.exceptionOrNull()
        assertTrue(ex is IllegalArgumentException)
    }
}
