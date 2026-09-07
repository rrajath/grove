package com.rrajath.grove.ui.editor

import android.app.Application
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.FakeSettingsRepository
import com.rrajath.grove.testing.FakeSyncTrigger
import com.rrajath.grove.testing.InMemoryGroveDatabase
import com.rrajath.grove.testing.OrgFixtures
import com.rrajath.grove.testing.support.MainDispatcherRule
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Layer-1 integration coverage for [EditorViewModel]: load → edit → save on a
 * real in-memory Room DB (for the tag query) and an in-memory FileStore.
 *
 * See internal/test-suite-01-integration-robolectric.md § Coverage matrix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class EditorViewModelIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeFileStore(OrgFixtures.all)
    private val vault = Vault(store)
    private val vaultFlow = MutableStateFlow<Vault?>(vault)
    private val sync = FakeSyncTrigger()
    private val settings = FakeSettingsRepository()
    private val keywords = MutableStateFlow(OrgKeywords.DEFAULT)
    private val db: GroveDatabase =
        InMemoryGroveDatabase.create(queryCoroutineContext = mainDispatcherRule.dispatcher)

    private fun editor() = EditorViewModel(
        vaultFlow = vaultFlow,
        sync = sync,
        database = db,
        settings = settings,
        keywords = keywords,
        dispatchers = mainDispatcherRule.appDispatchers,
    )

    /** Line index of the first headline whose title starts with [prefix], in [file]. */
    private fun headlineLine(file: String, prefix: String): Int {
        val doc = OrgParser.parse(OrgFixtures.all.getValue(file))
        return doc.headlines.first { it.title.startsWith(prefix) }.lineIndex
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `load resolves a headline into the buffer and clears loading`() = runTest {
        val line = headlineLine("projects.org", "Ship v2")
        val vm = editor()

        vm.load(NoteRef("projects.org", line))
        advanceUntilIdle()

        val loaded = vm.state.value
        assertFalse(loaded.loading)
        assertEquals("projects.org", loaded.fileName)
        assertEquals(line, loaded.lineIndex)
        assertTrue(loaded.buffer.startsWith("* TODO Ship v2 release"))
        assertTrue(loaded.buffer.contains("** DONE Cut the changelog"))
        assertEquals(store.revisionOf("projects.org"), loaded.loadedRevision)
    }

    @Test
    fun `typing marks the buffer dirty then save writes it back and reindexes`() = runTest {
        val line = headlineLine("projects.org", "Ship v2")
        val vm = editor()
        vm.load(NoteRef("projects.org", line))
        advanceUntilIdle()

        val edited = vm.state.value.buffer.trimEnd() + "\n*** TODO Extra subtask\n"
        vm.onBufferChange(edited)
        assertTrue(vm.state.value.dirty)

        vm.save()
        advanceUntilIdle()

        assertFalse(vm.state.value.dirty)
        assertTrue(store.read("projects.org").contains("*** TODO Extra subtask"))
        assertEquals(1, sync.reindexCalls.size)
        assertEquals("projects.org", sync.reindexCalls.single().fileName)
    }

    @Test
    fun `save refuses a file changed underneath until forced`() = runTest {
        val line = headlineLine("projects.org", "Ship v2")
        val vm = editor()
        vm.load(NoteRef("projects.org", line))
        advanceUntilIdle()

        vm.onBufferChange(vm.state.value.buffer.trimEnd() + "\n*** NEXT Follow up\n")
        store.touch("projects.org") // external edit bumps the revision

        vm.save()
        advanceUntilIdle()
        assertTrue("stale file should block the save", vm.state.value.staleFile)
        assertTrue(vm.state.value.dirty)
        assertFalse(store.read("projects.org").contains("*** NEXT Follow up"))

        vm.save(force = true)
        advanceUntilIdle()
        assertFalse(vm.state.value.staleFile)
        assertFalse(vm.state.value.dirty)
        assertTrue(store.read("projects.org").contains("*** NEXT Follow up"))
    }

    @Test
    fun `loadRegion PREFACE loads only the file preface`() = runTest {
        val vm = editor()
        vm.loadRegion("projects.org", noteId = null, region = EditRegion.PREFACE)
        advanceUntilIdle()

        val s = vm.state.value
        assertFalse(s.loading)
        assertEquals(EditRegion.PREFACE, s.region)
        assertTrue(s.buffer.contains("#+TITLE: Projects"))
        assertFalse(s.buffer.contains("Ship v2 release"))
    }
}
