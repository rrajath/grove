package com.rrajath.grove.testing

import com.rrajath.grove.GroveApplication

/**
 * A [GroveApplication] whose [onCreate] is a no-op, for Robolectric unit tests
 * that need the real application type (its `by lazy` singletons, `contentResolver`,
 * `getString`) but not its process-start side effects — the appScope collectors,
 * `SyncManager` wiring, WorkManager scheduling, reminder reconciliation.
 *
 * Used via `@Config(application = TestGroveApplication::class)`.
 */
class TestGroveApplication : GroveApplication() {
    override fun onCreate() {
        // Deliberately does not call super.onCreate(): see class doc.
    }
}
