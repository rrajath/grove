package com.rrajath.grove.ui.capture

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rrajath.grove.capture.CaptureContext
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.capture.TargetLocation
import com.rrajath.grove.capture.TemplatesRepository
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.FakeSettingsRepository
import com.rrajath.grove.testing.FakeSyncTrigger
import com.rrajath.grove.testing.InMemoryGroveDatabase
import com.rrajath.grove.testing.OrgFixtures
import com.rrajath.grove.testing.support.MainDispatcherRule
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime

/**
 * Layer-1 integration coverage for [CaptureViewModel]: the save path writes the
 * entry into the target notebook and requests a sync; the guard rails
 * (blank text, no vault folder) surface as [SaveState.Failed].
 *
 * See internal/test-suite-01-integration-robolectric.md § Coverage matrix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class CaptureViewModelIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeFileStore(OrgFixtures.all)
    private val vaultFlow = MutableStateFlow<Vault?>(Vault(store))
    private val sync = FakeSyncTrigger()
    private val settings = FakeSettingsRepository(GroveSettings(vaultTreeUri = "content://vault/tree"))
    private val db: GroveDatabase =
        InMemoryGroveDatabase.create(queryCoroutineContext = mainDispatcherRule.dispatcher)
    private val templatesRepository =
        TemplatesRepository(ApplicationProvider.getApplicationContext<Application>())

    private val quickNote = CaptureTemplate(
        id = "t-quick",
        name = "Quick note",
        targetFile = "inbox.org",
        location = TargetLocation.BottomOfFile,
        template = "* %cursor",
    )

    private val context = CaptureContext(now = LocalDateTime.of(2026, 9, 6, 10, 0))

    private fun capture() = CaptureViewModel(
        templatesRepository = templatesRepository,
        database = db,
        sync = sync,
        vaultFlow = vaultFlow,
        settings = settings,
        dispatchers = mainDispatcherRule.appDispatchers,
    )

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `save inserts the entry into the target file and requests a sync`() = runTest {
        val vm = capture()

        vm.save(quickNote, "* A captured thought from the share sheet", context)
        advanceUntilIdle()

        assertEquals(SaveState.Saved, vm.saveState.value)
        assertTrue(
            store.read("inbox.org").contains("* A captured thought from the share sheet"),
        )
        assertEquals(listOf("capture saved"), sync.syncRequests)
    }

    @Test
    fun `save into a not-yet-existing notebook creates it`() = runTest {
        val vm = capture()
        val template = quickNote.copy(targetFile = "captures.org")

        vm.save(template, "* Brand new notebook entry", context)
        advanceUntilIdle()

        assertEquals(SaveState.Saved, vm.saveState.value)
        assertTrue(store.exists("captures.org"))
        assertTrue(store.read("captures.org").contains("* Brand new notebook entry"))
    }

    @Test
    fun `blank entry text fails without touching the vault`() = runTest {
        val vm = capture()
        val before = store.snapshot()

        vm.save(quickNote, "   ", context)
        advanceUntilIdle()

        assertEquals(SaveState.Failed("Nothing to save"), vm.saveState.value)
        assertEquals(before, store.snapshot())
        assertTrue(sync.syncRequests.isEmpty())
    }
}
