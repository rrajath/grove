package com.rrajath.grove.testing.support

import com.rrajath.grove.AppDispatchers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Swaps `Dispatchers.Main` (what `viewModelScope` uses) for a single
 * [StandardTestDispatcher] for the duration of a test, so `viewModelScope`
 * coroutines only run when the test advances the scheduler
 * (`advanceUntilIdle()` / Turbine's `awaitItem()` inside `runTest`).
 *
 * [appDispatchers] points main/io/default at that same dispatcher, so a VM built
 * with it is fully deterministic. Pass it to the VM constructor under test.
 *
 * See internal/test-suite-01-integration-robolectric.md § Test conventions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule(
    val dispatcher: TestDispatcher = StandardTestDispatcher(),
) : TestWatcher() {

    val appDispatchers = AppDispatchers(main = dispatcher, io = dispatcher, default = dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
