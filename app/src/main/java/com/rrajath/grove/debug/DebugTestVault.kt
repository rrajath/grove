package com.rrajath.grove.debug

import android.content.Context
import android.content.Intent
import com.rrajath.grove.BuildConfig
import com.rrajath.grove.vault.TestVaultHook
import java.io.File

/**
 * Debug-only activation of the directory-vault test hook, driven by extras on
 * `MainActivity`'s launch intent so Maestro (and, later, the benchmark module)
 * can start from a populated vault with no SAF picker.
 *
 * ```
 * adb shell am start -n com.rrajath.grove.debug/com.rrajath.grove.MainActivity \
 *   --ez grove_test_direct_vault true --ez grove_test_seed true
 * ```
 *
 * Every entry point is a no-op unless [BuildConfig.DEBUG]; the seeding code and
 * the `fixtures/` assets it reads only exist in the debug build (see
 * `copyDebugTestFixtures` in `app/build.gradle.kts`). See
 * internal/test-suite-03-e2e-maestro.md.
 */
object DebugTestVault {

    const val EXTRA_DIRECT_VAULT = "grove_test_direct_vault"
    const val EXTRA_SEED = "grove_test_seed"

    /** Directory name under the app's external files dir used as the test vault root. */
    private const val VAULT_DIR = "testvault"

    /** Called from `MainActivity.onCreate` before content is set. */
    fun applyFromLaunchIntent(context: Context, intent: Intent?) {
        if (!BuildConfig.DEBUG || intent == null) return
        if (!intent.getBooleanExtra(EXTRA_DIRECT_VAULT, false)) return

        val root = File(context.getExternalFilesDir(null), VAULT_DIR).apply { mkdirs() }
        if (intent.getBooleanExtra(EXTRA_SEED, false)) {
            seed(context, root)
        }
        TestVaultHook.useDirectory(root)
    }

    /** Wipe [root] and rewrite it from the debug APK's bundled `.org` fixtures. */
    private fun seed(context: Context, root: File) {
        root.listFiles()?.forEach { it.deleteRecursively() }
        val names = context.assets.list("fixtures").orEmpty().filter { it.endsWith(".org") }
        names.forEach { name ->
            context.assets.open("fixtures/$name").use { input ->
                File(root, name).outputStream().use { input.copyTo(it) }
            }
        }
    }
}
