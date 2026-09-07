package com.rrajath.grove.ui.search

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.rrajath.grove.data.GroveDatabase
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.search.SearchRepository
import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.FakeSettingsRepository
import com.rrajath.grove.testing.FakeSyncTrigger
import com.rrajath.grove.testing.InMemoryGroveDatabase
import com.rrajath.grove.testing.OrgFixtures
import com.rrajath.grove.testing.TestVaultSeeder
import com.rrajath.grove.testing.support.MainDispatcherRule
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceTimeBy
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

/**
 * Layer-1 integration coverage for [SearchViewModel]: a query is debounced and
 * run against the real FTS-backed index, and clearing it returns to the blank
 * state.
 *
 * See internal/test-suite-01-integration-robolectric.md § Coverage matrix.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class SearchViewModelIntegrationTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val store = FakeFileStore(OrgFixtures.all)
    private val vaultFlow = MutableStateFlow<Vault?>(Vault(store))
    private val sync = FakeSyncTrigger()
    private val settings = FakeSettingsRepository()
    private val keywords = MutableStateFlow(OrgKeywords.DEFAULT)
    private val db: GroveDatabase =
        InMemoryGroveDatabase.create(queryCoroutineContext = mainDispatcherRule.dispatcher)
    private val searchRepository =
        SearchRepository(ApplicationProvider.getApplicationContext<Application>())

    private fun search() = SearchViewModel(
        vaultFlow = vaultFlow,
        sync = sync,
        searchRepository = searchRepository,
        database = db,
        keywordsFlow = keywords,
        settings = settings,
        dispatchers = mainDispatcherRule.appDispatchers,
    )

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `a query matches the note whose body contains the term`() = runTest {
        TestVaultSeeder.index(db, store)
        advanceUntilIdle()
        val vm = search()
        advanceUntilIdle()

        vm.onQueryChange("photosynthesis")
        advanceTimeBy(400) // clear the 300ms debounce
        advanceUntilIdle()

        val files = vm.state.value.groups.map { it.fileName }
        assertEquals(listOf("reading-list.org"), files)
        assertTrue(vm.state.value.resultCount >= 1)
    }

    @Test
    fun `clearing the query returns to the blank state`() = runTest {
        TestVaultSeeder.index(db, store)
        advanceUntilIdle()
        val vm = search()
        advanceUntilIdle()

        vm.onQueryChange("photosynthesis")
        advanceTimeBy(400)
        advanceUntilIdle()
        assertTrue(vm.state.value.groups.isNotEmpty())

        vm.onQueryChange("")
        advanceTimeBy(400)
        advanceUntilIdle()

        assertTrue(vm.state.value.isBlank)
        assertTrue(vm.state.value.groups.isEmpty())
    }
}
