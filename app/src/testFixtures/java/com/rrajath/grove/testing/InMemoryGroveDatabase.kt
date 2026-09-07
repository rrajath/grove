package com.rrajath.grove.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.rrajath.grove.data.GroveDatabase

/**
 * Builds a throwaway in-memory [GroveDatabase] wired through the production
 * [GroveDatabase.inMemory] path, so tests exercise the real FTS bootstrap and
 * driver rather than a hand-rolled copy.
 *
 * Works from both suites: the default [Context] is the Robolectric or
 * instrumented application. Callers own the instance and should
 * [GroveDatabase.close] it when done (a `@get:Rule TemporaryFolder`-style
 * teardown, or `@After`).
 *
 * See internal/test-suite-00-overview.md § Fakes to build (in-memory Room).
 */
object InMemoryGroveDatabase {
    fun create(
        context: Context = ApplicationProvider.getApplicationContext(),
    ): GroveDatabase = GroveDatabase.inMemory(context)
}
