package com.rrajath.grove.ui.vault

import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.data.FavoritesRepository
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.settings.SettingsRepository
import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.FakeSyncTrigger
import com.rrajath.grove.testing.InMemoryGroveDatabase
import com.rrajath.grove.testing.OrgFixtures
import com.rrajath.grove.testing.TestGroveApplication
import com.rrajath.grove.testing.TestVaultSeeder
import com.rrajath.grove.testing.support.MainDispatcherRule
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Layer-1 integration coverage for [DocumentViewModel]: load/error, org-link
 * resolution across every target form, the structural outline mutations
 * (move / promote / demote / delete) and their single-step undo, note creation,
 * the metadata edits (state / priority / tags / planning / logbook), the
 * heading-less-intro promote, favorites (with the `:CUSTOM_ID:` it forces), the
 * open-editor pending-buffer splice, and cross-file refile.
 *
 * The 27 link forms also have Maestro end-to-end coverage (flow 05); this pins
 * the resolution at the VM level against the Room index + vault.
 *
 * See internal/test-suite-01-integration-robolectric.md § Coverage matrix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = TestGroveApplication::class, sdk = [34])
class DocumentViewModelIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val app = ApplicationProvider.getApplicationContext<GroveApplication>()

    private val introFile = """
        #+TITLE: Intro fixture

        Heading-less prose that lives before any heading.

        * TODO A real heading
        Body of the heading.
    """.trimIndent() + "\n"

    private val store = FakeFileStore(OrgFixtures.all + mapOf("intro.org" to introFile))
    private val vault = Vault(store)
    private val vaultFlow = MutableStateFlow<Vault?>(vault)
    private val keywords = MutableStateFlow(OrgKeywords.DEFAULT)
    private val sync = FakeSyncTrigger()
    private val db: GroveDatabase =
        InMemoryGroveDatabase.create(queryCoroutineContext = mainDispatcherRule.dispatcher)
    private val repoScope = CoroutineScope(mainDispatcherRule.dispatcher)
    private val settingsRepository = SettingsRepository(app, repoScope)
    private val favoritesRepository = FavoritesRepository(app)

    private val liveVms = mutableListOf<DocumentViewModel>()

    private fun documentVm() = DocumentViewModel(
        vaultFlow = vaultFlow,
        keywordsFlow = keywords,
        sync = sync,
        database = db,
        settingsRepository = settingsRepository,
        favoritesRepository = favoritesRepository,
        dispatchers = mainDispatcherRule.appDispatchers,
        app = app,
    ).also { liveVms += it }

    @After
    fun tearDown() {
        liveVms.forEach { it.viewModelScope.cancel() }
        repoScope.cancel()
        db.close()
    }

    // --- helpers -----------------------------------------------------------

    private fun loadedDoc(vm: DocumentViewModel): OrgDocument =
        (vm.state.value as DocumentUiState.Loaded).document

    private fun headline(vm: DocumentViewModel, titlePrefix: String): OrgHeadline =
        loadedDoc(vm).headlines.first { it.title.startsWith(titlePrefix) }

    private fun TestScope.loaded(fileName: String): DocumentViewModel {
        val vm = documentVm()
        vm.load(fileName)
        advanceUntilIdle()
        return vm
    }

    /** Record every resolved link so a test can assert what navigation fired. */
    private class LinkSpy {
        val notes = mutableListOf<NoteRef>()
        val outlines = mutableListOf<String>()
    }

    private fun DocumentViewModel.follow(rawTarget: String, currentFile: String, spy: LinkSpy) =
        openOrgLink(rawTarget, currentFile, onOpenNote = { spy.notes += it }, onOpenOutline = { spy.outlines += it })

    private suspend fun TestScope.settleUntil(timeoutMs: Long = 5_000, condition: suspend () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            advanceUntilIdle()
            if (condition()) return
            @Suppress("BlockingMethodInNonBlockingContext")
            Thread.sleep(20)
        }
        advanceUntilIdle()
        if (!condition()) throw AssertionError("condition not met within ${timeoutMs}ms")
    }

    // --- load ------------------------------------------------------------

    @Test
    fun `load with no vault configured surfaces an error`() = runTest {
        vaultFlow.value = null
        val vm = documentVm()
        vm.load("inbox.org")
        advanceUntilIdle()

        assertTrue(vm.state.value is DocumentUiState.Error)
    }

    @Test
    fun `load of an unknown file surfaces a not-found error`() = runTest {
        val vm = loaded("does-not-exist.org")
        val state = vm.state.value as DocumentUiState.Error
        assertTrue(state.message.contains("does-not-exist.org"))
    }

    @Test
    fun `load parses the file into the Loaded state`() = runTest {
        val vm = loaded("projects.org")
        val state = vm.state.value as DocumentUiState.Loaded
        assertEquals("projects.org", state.fileName)
        assertTrue(state.document.headlines.any { it.title == "Ship v2 release" })
    }

    // --- org link resolution -------------------------------------------

    @Test
    fun `star and fuzzy heading links in the same file resolve to that heading`() = runTest {
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("*Hub Target Star", "links-hub.org", spy)
        vm.follow("Hub Target Star", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals(2, spy.notes.size)
        assertTrue(spy.notes.all { it.fileName == "links-hub.org" })
    }

    @Test
    fun `custom-id and heading-id links in the same file resolve to that heading`() = runTest {
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("#hub-custom", "links-hub.org", spy)
        vm.follow("id:hub-heading-0000000000", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals(2, spy.notes.size)
    }

    @Test
    fun `a file-level id link opens that file's outline`() = runTest {
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("id:hub-file-0000000000", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals(listOf("links-hub.org"), spy.outlines)
    }

    @Test
    fun `a cross-file heading-id link resolves through the index`() = runTest {
        TestVaultSeeder.index(db, store)
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("id:far-heading-0000000000", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals(1, spy.notes.size)
        assertEquals("links-far.org", spy.notes.single().fileName)
    }

    @Test
    fun `a bare cross-file link opens the other file's outline`() = runTest {
        TestVaultSeeder.index(db, store)
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("file:links-far.org", "links-hub.org", spy)
        vm.follow("links-far.org", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals(listOf("links-far.org", "links-far.org"), spy.outlines)
    }

    @Test
    fun `a file link with a heading search resolves to that heading`() = runTest {
        TestVaultSeeder.index(db, store)
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("file:links-far.org::*Far Heading", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals("links-far.org", spy.notes.single().fileName)
    }

    @Test
    fun `a file link whose heading is gone falls back to the file outline`() = runTest {
        TestVaultSeeder.index(db, store)
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("file:links-far.org::*No Such Heading", "links-hub.org", spy)
        advanceUntilIdle()

        assertEquals(listOf("links-far.org"), spy.outlines)
        assertTrue(spy.notes.isEmpty())
    }

    @Test
    fun `an external link is handed to the OS`() = runTest {
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()

        vm.follow("https://example.com/grove", "links-hub.org", spy)
        advanceUntilIdle()

        val started = Shadows.shadowOf(app).nextStartedActivity
        assertNotNull(started)
        assertEquals("https://example.com/grove", started.dataString)
        assertTrue(spy.notes.isEmpty() && spy.outlines.isEmpty())
    }

    @Test
    fun `an unresolved link shows a toast and navigates nowhere`() = runTest {
        val vm = loaded("links-hub.org")
        val spy = LinkSpy()
        val toasts = mutableListOf<OutlineToast?>()
        backgroundScope.launch { vm.toast.toList(toasts) }

        vm.follow("id:nonexistent-0000000000", "links-hub.org", spy)
        advanceUntilIdle()

        assertTrue(spy.notes.isEmpty() && spy.outlines.isEmpty())
        assertTrue(toasts.any { it?.message?.contains("Couldn't find") == true })
    }

    // --- structural outline edits + undo -------------------------------

    @Test
    fun `moveDown reorders the subtree and persists it`() = runTest {
        val vm = loaded("projects.org")
        val ship = headline(vm, "Ship v2 release")

        vm.moveDown(ship)
        advanceUntilIdle()

        val text = store.read("projects.org")
        assertTrue("Backlog should now precede Ship v2", text.indexOf("Backlog") < text.indexOf("Ship v2 release"))
        assertTrue(sync.syncRequests.isNotEmpty())
    }

    @Test
    fun `moveUp at the top of the file is blocked and writes nothing`() = runTest {
        val vm = loaded("projects.org")
        val before = store.read("projects.org")
        val toasts = mutableListOf<OutlineToast?>()
        backgroundScope.launch { vm.toast.toList(toasts) }

        vm.moveUp(headline(vm, "Ship v2 release"))
        advanceUntilIdle()

        assertEquals(before, store.read("projects.org"))
        assertTrue(toasts.any { it?.message == "Can't move further" })
    }

    @Test
    fun `promote of a top-level heading is blocked`() = runTest {
        val vm = loaded("projects.org")
        val before = store.read("projects.org")
        val toasts = mutableListOf<OutlineToast?>()
        backgroundScope.launch { vm.toast.toList(toasts) }

        vm.promote(headline(vm, "Ship v2 release"))
        advanceUntilIdle()

        assertEquals(before, store.read("projects.org"))
        assertTrue(toasts.any { it?.message == "Already top level" })
    }

    @Test
    fun `demote then promote round-trips a heading's level`() = runTest {
        val vm = loaded("projects.org")

        vm.demote(headline(vm, "Backlog"))
        advanceUntilIdle()
        assertEquals(2, OrgParser.parse(store.read("projects.org")).headlines.first { it.title == "Backlog" }.level)

        vm.promote(headline(vm, "Backlog"))
        advanceUntilIdle()
        assertEquals(1, OrgParser.parse(store.read("projects.org")).headlines.first { it.title == "Backlog" }.level)
    }

    @Test
    fun `deleteNote removes the subtree and undo restores the file`() = runTest {
        val vm = loaded("projects.org")
        val before = store.read("projects.org")

        vm.deleteNote(headline(vm, "Backlog"))
        advanceUntilIdle()
        val afterDelete = store.read("projects.org")
        assertFalse(afterDelete.contains("Backlog"))
        assertFalse("a child of the deleted subtree", afterDelete.contains("Dark mode polish"))

        vm.undo()
        advanceUntilIdle()

        assertEquals(before, store.read("projects.org"))
        assertTrue(sync.syncRequests.contains("undo"))
    }

    // --- note creation ------------------------------------------------

    @Test
    fun `newChild inserts a blank child and reports its line`() = runTest {
        val vm = loaded("projects.org")
        var createdLine: Int? = null

        vm.newChild(headline(vm, "Backlog")) { createdLine = it }
        advanceUntilIdle()

        assertNotNull(createdLine)
        val doc = OrgParser.parse(store.read("projects.org"))
        val child = doc.headlines.first { it.lineIndex == createdLine }
        assertEquals(2, child.level)
        assertTrue(sync.syncRequests.contains("note added"))
    }

    @Test
    fun `newTopLevelNote appends a level-1 heading to the file`() = runTest {
        val vm = loaded("projects.org")
        val countBefore = loadedDoc(vm).headlines.count { it.level == 1 }
        var createdLine: Int? = null

        vm.newTopLevelNote { createdLine = it }
        advanceUntilIdle()

        assertNotNull(createdLine)
        val doc = OrgParser.parse(store.read("projects.org"))
        assertEquals(countBefore + 1, doc.headlines.count { it.level == 1 })
    }

    // --- metadata edits ---------------------------------------------

    @Test
    fun `setState to a done keyword writes it to the file`() = runTest {
        val vm = loaded("projects.org")

        vm.setState(headline(vm, "Ship v2 release"), "DONE")
        advanceUntilIdle()

        assertTrue(store.read("projects.org").contains("DONE Ship v2 release"))
        assertTrue(sync.syncRequests.isNotEmpty())
    }

    @Test
    fun `setPriority writes a priority cookie to the heading`() = runTest {
        val vm = loaded("projects.org")

        vm.setPriority(headline(vm, "Ship v2 release"), 'A')
        advanceUntilIdle()

        assertTrue(store.read("projects.org").contains("[#A]"))
    }

    @Test
    fun `setTags rewrites the heading's tag list`() = runTest {
        val vm = loaded("projects.org")

        vm.setTags(headline(vm, "Ship v2 release"), listOf("release", "urgent"))
        advanceUntilIdle()

        assertTrue(store.read("projects.org").lineSequence().first { it.contains("Ship v2 release") }
            .contains(":release:urgent:"))
    }

    @Test
    fun `setPlanningDates writes the SCHEDULED stamp`() = runTest {
        val vm = loaded("projects.org")
        val when_ = LocalDate.now().plusDays(2)

        vm.setPlanningDates(headline(vm, "Backlog"), OrgTimestamp(when_), null)
        advanceUntilIdle()

        assertTrue(store.read("projects.org").contains("SCHEDULED: <$when_"))
    }

    @Test
    fun `addNote appends a timestamped entry to the heading's logbook`() = runTest {
        val vm = loaded("projects.org")

        vm.addNote(headline(vm, "Ship v2 release"), "Waiting on legal sign-off")
        advanceUntilIdle()

        val text = store.read("projects.org")
        assertTrue(text.contains(":LOGBOOK:"))
        assertTrue(text.contains("Waiting on legal sign-off"))
    }

    // --- heading-less intro --------------------------------------------

    @Test
    fun `withIntroHeading wraps the intro prose in a new heading and publishes its line`() = runTest {
        val vm = loaded("intro.org")

        vm.withIntroHeading(describe = "") { doc, h -> com.rrajath.grove.org.OrgMutations.setPriority(doc, h, 'B') }
        advanceUntilIdle()

        assertNotNull(vm.introPromotedLine.value)
        val text = store.read("intro.org")
        assertTrue("a heading was added with the mutation applied", text.contains("[#B]"))
        val headingLine = text.lineSequence().indexOfFirst { it.startsWith("* ") }
        val proseLine = text.lineSequence().indexOfFirst { it.contains("Heading-less prose that lives before any heading.") }
        assertTrue("the prose is now under the new heading", headingLine in 0 until proseLine)
        assertTrue(sync.syncRequests.contains("intro promoted to heading"))
    }

    // --- favorites (and the CUSTOM_ID they force) ---------------------

    @Test
    fun `ensureCustomId writes a CUSTOM_ID onto a heading that has none`() = runTest {
        val vm = loaded("projects.org")
        var resolved: String? = null

        vm.ensureCustomId(headline(vm, "Backlog")) { resolved = it }
        advanceUntilIdle()

        assertNotNull(resolved)
        assertTrue(store.read("projects.org").contains(":CUSTOM_ID: $resolved"))
    }

    @Test
    fun `toggleFavorite adds the heading to favorites with a stable id`() = runTest {
        val vm = loaded("projects.org")

        vm.toggleFavorite(headline(vm, "Backlog"))
        settleUntil { favoritesRepository.favorites.first().any { it.title == "Backlog" } }

        val fav = favoritesRepository.favorites.first().first { it.title == "Backlog" }
        assertEquals("projects.org", fav.fileName)
        assertNotNull("favorite should carry the CUSTOM_ID it forced", fav.customId)
    }

    @Test
    fun `toggleIntroFavorite favorites the heading-less intro without adding a heading`() = runTest {
        val vm = loaded("intro.org")
        val before = store.read("intro.org")

        vm.toggleIntroFavorite()
        settleUntil { favoritesRepository.favorites.first().any { it.fileName == "intro.org" } }

        assertEquals("the file must be untouched", before, store.read("intro.org"))
        val fav = favoritesRepository.favorites.first().first { it.fileName == "intro.org" }
        assertEquals(com.rrajath.grove.org.INTRO_LINE_INDEX, fav.lineIndex)
    }

    // --- open-editor pending-buffer splice --------------------------

    @Test
    fun `a read-mode edit folds into the open editor's buffer instead of hitting disk`() = runTest {
        val vm = loaded("projects.org")
        val ship = headline(vm, "Ship v2 release")
        val subtree = com.rrajath.grove.org.OrgMutations.subtreeText(loadedDoc(vm), ship)
        val diskBefore = store.read("projects.org")

        var folded: String? = null
        vm.setPendingEdit(
            PendingEdit("projects.org", ship.lineIndex, subtree),
            onBufferChanged = { folded = it },
        )

        vm.setPriority(ship, 'C')
        advanceUntilIdle()

        assertEquals("disk must be untouched while the editor holds the subtree", diskBefore, store.read("projects.org"))
        assertNotNull(folded)
        assertTrue(folded!!.contains("[#C]"))
        assertTrue("no sync for a buffered edit", sync.syncRequests.isEmpty())
    }

    // --- refile ------------------------------------------------------

    @Test
    fun `refile moves a subtree into another file, updating both`() = runTest {
        TestVaultSeeder.index(db, store)
        val vm = loaded("projects.org")

        vm.startRefile(headline(vm, "Backlog"))
        advanceUntilIdle()
        vm.refilePickNotebook("reading-list.org")
        advanceUntilIdle()
        vm.refileConfirm()
        advanceUntilIdle()

        assertFalse("source file loses the subtree", store.read("projects.org").contains("Backlog"))
        val dest = store.read("reading-list.org")
        assertTrue("dest file gains the subtree", dest.contains("Backlog"))
        assertTrue("a child came along", dest.contains("Dark mode polish"))
        assertTrue(sync.syncRequests.contains("refile"))
    }

    @Test
    fun `refileCancel drops the picker without touching any file`() = runTest {
        val vm = loaded("projects.org")
        val before = store.snapshot()

        vm.startRefile(headline(vm, "Backlog"))
        advanceUntilIdle()
        vm.refileCancel()
        advanceUntilIdle()

        assertNull(vm.refile.value)
        assertEquals(before, store.snapshot())
    }
}
