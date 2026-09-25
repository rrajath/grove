package com.rrajath.grove.ui.dailies

import com.rrajath.grove.dailies.DailyNoteLookup
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.FakeSettingsRepository
import com.rrajath.grove.testing.support.MainDispatcherRule
import com.rrajath.grove.vault.Vault
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

/** DailiesViewModel.noteSaved: only a save that creates the day re-lists the folder. */
class DailiesViewModelSaveTest {

    @get:Rule
    val main = MainDispatcherRule()

    private val day = LocalDate.of(2026, 9, 23)
    private val older = LocalDate.of(2026, 9, 10)
    private val external = LocalDate.of(2026, 9, 1)
    private val settings = FakeSettingsRepository(
        GroveSettings().copy(dailiesDirectory = "daily", dailiesFilenamePattern = "%<%Y-%m-%d>.org"),
    )

    private fun fixture(store: FakeFileStore): DailiesViewModel {
        val vault = MutableStateFlow<Vault?>(Vault(store, parseDispatcher = main.dispatcher))
        val lookup = DailyNoteLookup(vault, settings, CoroutineScope(main.dispatcher), main.dispatcher)
        return DailiesViewModel(vault, settings, main.appDispatchers, lookup)
    }

    @Test
    fun `re-saving an existing day does not re-list the folder`() = runTest(main.dispatcher) {
        val store = FakeFileStore(mapOf("daily/2026-09-23.org" to "", "daily/2026-09-10.org" to ""))
        val vm = fixture(store)
        vm.select(day)
        vm.refresh()
        advanceUntilIdle()

        // Appears outside the app; only a re-list would see it.
        store.write("daily/2026-09-01.org", "")
        store.write("daily/2026-09-23.org", "typed")
        vm.noteSaved(day, "daily/2026-09-23.org")
        advanceUntilIdle()

        assertEquals(setOf(older, day), vm.state.value!!.existingDates)
    }

    @Test
    fun `saving a brand-new day marks it existing and re-lists`() = runTest(main.dispatcher) {
        val store = FakeFileStore(mapOf("daily/2026-09-10.org" to ""))
        val vm = fixture(store)
        vm.select(day)
        vm.refresh()
        advanceUntilIdle()

        store.write("daily/2026-09-01.org", "")
        store.write("daily/2026-09-23.org", "first entry")
        vm.noteSaved(day, "daily/2026-09-23.org")
        assertTrue("in-place update lands before the re-list", vm.state.value!!.exists)
        advanceUntilIdle()

        assertEquals(setOf(external, older, day), vm.state.value!!.existingDates)
    }
}
