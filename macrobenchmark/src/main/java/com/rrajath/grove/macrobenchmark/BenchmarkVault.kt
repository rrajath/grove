package com.rrajath.grove.macrobenchmark

import android.content.Intent
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until

/**
 * Shared setup for every benchmark in this module: which package to measure and
 * how to launch :app straight into a populated Notebooks screen with no SAF
 * folder picker and no onboarding.
 *
 * The `benchmark` build type of :app carries `BuildConfig.TEST_HOOKS = true`, so
 * `MainActivity` honours these launch-intent extras (see
 * `com.rrajath.grove.debug.DebugTestVault` in the app module):
 *
 *  - `grove_test_direct_vault` — point the vault at an app-owned directory
 *  - `grove_test_seed`         — (re)seed it from the bundled `.org` fixtures
 *  - `grove_test_vault_size`   — instead of the fixtures, generate N synthetic
 *                                notebooks plus one large outline, for scroll tests
 */
object BenchmarkVault {

    /** :app's `benchmark` variant installs under the release applicationId. */
    const val TARGET_PACKAGE = "com.rrajath.grove"

    const val EXTRA_DIRECT_VAULT = "grove_test_direct_vault"
    const val EXTRA_SEED = "grove_test_seed"
    const val EXTRA_VAULT_SIZE = "grove_test_vault_size"

    /** testTag → resource-id (MainActivity sets testTagsAsResourceId = true). */
    const val NOTEBOOKS_LIST = "notebooks_list"
    const val NOTEBOOK_ROW = "notebook_row"
    const val OUTLINE_LIST = "outline_list"

    const val LAUNCH_TIMEOUT_MS = 10_000L

    /**
     * Adds the extras that make MainActivity seed and open a directory vault.
     * Pass [vaultSize] > 0 for the large synthetic vault (scroll benchmarks);
     * 0 uses the small canonical fixture set (startup benchmark).
     */
    fun Intent.withSeededVault(vaultSize: Int = 0): Intent = apply {
        putExtra(EXTRA_DIRECT_VAULT, true)
        if (vaultSize > 0) putExtra(EXTRA_VAULT_SIZE, vaultSize) else putExtra(EXTRA_SEED, true)
    }

    /** Block until the Notebooks list is on screen (or time out). */
    fun UiDevice.awaitNotebooks() {
        wait(Until.hasObject(By.res(NOTEBOOKS_LIST)), LAUNCH_TIMEOUT_MS)
    }
}
