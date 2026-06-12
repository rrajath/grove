package com.rrajath.grove.sync

import com.rrajath.grove.vault.JvmFileStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** In-memory NoteIndex fake recording engine interactions. */
private class FakeIndex : NoteIndex {
    val revisions = mutableMapOf<String, String>()
    val texts = mutableMapOf<String, String>()
    val conflicts = mutableMapOf<String, String?>()
    val indexedOrder = mutableListOf<String>()

    override suspend fun knownRevisions(): Map<String, String> = revisions.toMap()

    override suspend fun indexNotebook(
        fileName: String,
        revision: String,
        text: String,
        lastModified: Long,
        conflictFileName: String?,
    ) {
        revisions[fileName] = revision
        texts[fileName] = text
        conflicts[fileName] = conflictFileName
        indexedOrder.add(fileName)
    }

    override suspend fun setConflict(fileName: String, conflictFileName: String?) {
        conflicts[fileName] = conflictFileName
    }

    override suspend fun removeNotebook(fileName: String) {
        revisions.remove(fileName)
        texts.remove(fileName)
        conflicts.remove(fileName)
    }
}

class SyncEngineTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val index = FakeIndex()
    private var now = 1000L

    private fun engine() = SyncEngine(JvmFileStore(tmp.root), index) { now }

    @Test
    fun `first sync pulls everything`() = runTest {
        tmp.newFile("a.org").writeText("* A")
        tmp.newFile("b.org").writeText("* B")
        tmp.newFile("notes.txt").writeText("not org")

        val result = engine().sync()!!
        assertEquals(listOf("a.org", "b.org"), result.pulled.sorted())
        assertEquals("* A", index.texts["a.org"])
        assertNull(index.texts["notes.txt"])
        assertEquals(1000L, result.completedAt)
    }

    @Test
    fun `unchanged files are not re-pulled`() = runTest {
        tmp.newFile("a.org").writeText("* A")
        val e = engine()
        e.sync()
        index.indexedOrder.clear()

        val result = e.sync()!!
        assertEquals(emptyList<String>(), result.pulled)
        assertTrue(index.indexedOrder.isEmpty())
    }

    @Test
    fun `external change is detected by revision`() = runTest {
        val file = tmp.newFile("a.org").apply { writeText("* A") }
        val e = engine()
        e.sync()

        file.writeText("* A changed externally")
        file.setLastModified(file.lastModified() + 5000)
        val result = e.sync()!!
        assertEquals(listOf("a.org"), result.pulled)
        assertEquals("* A changed externally", index.texts["a.org"])
    }

    @Test
    fun `deleted files are removed from index`() = runTest {
        val file = tmp.newFile("a.org").apply { writeText("* A") }
        val e = engine()
        e.sync()

        file.delete()
        val result = e.sync()!!
        assertEquals(listOf("a.org"), result.removed)
        assertNull(index.texts["a.org"])
    }

    @Test
    fun `sync conflict copies are reported not indexed as notebooks`() = runTest {
        tmp.newFile("a.org").writeText("* current")
        tmp.newFile("a.sync-conflict-20250611-143200-DEVICE.org").writeText("* other")

        val result = engine().sync()!!
        assertEquals(mapOf("a.org" to "a.sync-conflict-20250611-143200-DEVICE.org"), result.conflicts)
        assertEquals(listOf("a.org"), result.pulled)
        assertEquals("a.sync-conflict-20250611-143200-DEVICE.org", index.conflicts["a.org"])
    }

    @Test
    fun `conflict marker clears once the copy disappears`() = runTest {
        tmp.newFile("a.org").writeText("* current")
        val copy = tmp.newFile("a.sync-conflict-20250611-143200-DEVICE.org").apply { writeText("* other") }
        val e = engine()
        e.sync()
        assertNotNull(index.conflicts["a.org"])

        copy.delete()
        e.sync()
        assertNull(index.conflicts["a.org"])
    }

    @Test
    fun `orgzlyignore is honored`() = runTest {
        tmp.newFile("keep.org").writeText("* K")
        tmp.newFile("archive.org").writeText("* A")
        tmp.newFile(".orgzlyignore").writeText("archive*")

        val result = engine().sync()!!
        assertEquals(listOf("keep.org"), result.pulled)
    }

    @Test
    fun `engine reports error state on store failure`() = runTest {
        val broken = object : com.rrajath.grove.vault.FileStore {
            override suspend fun list() = throw IllegalStateException("disk gone")
            override suspend fun read(name: String) = ""
            override suspend fun write(name: String, content: String) {}
            override suspend fun create(name: String) = false
            override suspend fun rename(oldName: String, newName: String) = false
            override suspend fun delete(name: String) = false
            override suspend fun exists(name: String) = false
        }
        val e = SyncEngine(broken, index) { now }
        assertNull(e.sync())
        assertTrue(e.state.value is SyncState.Error)
    }

    @Test
    fun `state ends in Done with result`() = runTest {
        tmp.newFile("a.org").writeText("* A")
        val e = engine()
        val result = e.sync()
        assertEquals(SyncState.Done(result!!), e.state.value)
    }
}
