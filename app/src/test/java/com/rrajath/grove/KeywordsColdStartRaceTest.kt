package com.rrajath.grove

import com.rrajath.grove.org.OrgKeywords
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression test for the daily-digest mass-misfire bug: 446 tasks with a
 * custom `KILL` done-keyword were reported as still-due, because they were
 * indexed while [com.rrajath.grove.GroveApplication.keywords] was still
 * standing in on its `OrgKeywords.DEFAULT` seed instead of the user's
 * configured keyword string.
 *
 * Commits 343f65b/fd1dc85 ("Fix cold-start race that could index notebooks
 * with stale TODO keywords") guarded [com.rrajath.grove.GroveApplication]'s
 * sync-wiring launch with `settingsRepository.settings.first()` before
 * subscribing `fileStore.collect { syncManager.attach(it) }`. That guarantees
 * DataStore's Preferences are cached in memory, but it does NOT guarantee
 * that `keywords` - a *separate* `by lazy` `stateIn(appScope, Eagerly,
 * OrgKeywords.DEFAULT)` - has actually collected a real value yet: nothing
 * touches `keywords` before that point (`syncManager`'s constructor only
 * *stores* the `{ keywords.value }` closure; it doesn't call it), so the
 * closure's first-ever invocation - deep inside
 * [com.rrajath.grove.data.RoomNoteIndex.indexNotebook], with no suspension
 * point in between - can still observe the `DEFAULT` seed, regardless of how
 * much wall-clock time has elapsed by then.
 *
 * This reproduces that exact mechanism with a minimal harness mirroring
 * [com.rrajath.grove.GroveApplication]'s structure (a real
 * [com.rrajath.grove.GroveApplication] can't be unit-tested directly - it
 * needs an Android `Context`/DataStore/Room), under a fully manual
 * [kotlinx.coroutines.test.TestCoroutineScheduler] that never auto-advances,
 * i.e. the most adversarial scheduling `kotlinx-coroutines-test` can produce.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class KeywordsColdStartRaceTest {

    private val customConfig = "TODO NEXT | DONE CANCELLED KILL"
    private val expected = OrgKeywords.parse(customConfig)

    @Test
    fun `reading the closure before the stateIn collector has run yields the stale DEFAULT seed`() = runTest {
        // Sanity check: the harness only proves anything if the real config
        // actually differs from the seed, same as a KILL-keyword vault would.
        assertNotEquals(OrgKeywords.DEFAULT, expected)

        val settingsFlow = MutableStateFlow(customConfig)

        // Mirrors `GroveApplication.keywords: StateFlow<OrgKeywords> by lazy { ... }`.
        // `backgroundScope` (not `this`) because, like the real `appScope`,
        // this collector runs for the rest of the process's life and must not
        // make `runTest` wait for it to complete.
        val keywords = settingsFlow
            .map { OrgKeywords.parse(it) }
            .stateIn(backgroundScope, SharingStarted.Eagerly, OrgKeywords.DEFAULT)

        // Mirrors `syncManager`'s constructor storing `{ keywords.value }`
        // without calling it, and RoomNoteIndex.indexNotebook later invoking
        // that closure synchronously - with zero scheduler advancement in
        // between, exactly like the un-dispatched gap between `syncManager`
        // being built and its first sync firing.
        val closure = { keywords.value }

        assertEquals(OrgKeywords.DEFAULT, closure())
    }

    @Test
    fun `explicitly awaiting keywords before proceeding closes the race`() = runTest {
        val settingsFlow = MutableStateFlow(customConfig)
        val keywords = settingsFlow
            .map { OrgKeywords.parse(it) }
            .stateIn(backgroundScope, SharingStarted.Eagerly, OrgKeywords.DEFAULT)
        val closure = { keywords.value }

        // The fix applied in GroveApplication.onCreate: block on `keywords`
        // itself until it has actually emitted the value derived from the
        // settings already read, before anything downstream can invoke the
        // closure.
        var guardResolved = false
        val guard = launch {
            keywords.first { it == expected }
            guardResolved = true
        }

        // Before the scheduler runs anything, the race window is still open:
        // the guard hasn't resolved and the closure would still see DEFAULT.
        assertFalse(guardResolved)
        assertEquals(OrgKeywords.DEFAULT, closure())

        testScheduler.advanceUntilIdle()
        guard.join()

        assertTrue(guardResolved)
        assertEquals(expected, closure())
    }
}
