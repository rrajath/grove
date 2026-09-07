package com.rrajath.grove.debug

import android.content.Context
import android.content.Intent
import com.rrajath.grove.BuildConfig
import com.rrajath.grove.vault.TestVaultHook
import java.io.File

/**
 * Test-only activation of the directory-vault hook, driven by extras on
 * `MainActivity`'s launch intent so Maestro (Layer 3) and the `:macrobenchmark`
 * module (Layer 4) can start from a populated vault with no SAF picker and no
 * onboarding.
 *
 * ```
 * adb shell am start -n com.rrajath.grove.debug/com.rrajath.grove.MainActivity \
 *   --ez grove_test_direct_vault true --ez grove_test_seed true
 * ```
 *
 * Every entry point is a no-op unless [BuildConfig.TEST_HOOKS], which is on for
 * the `debug` and `benchmark` build types and off for `release` (R8 then
 * dead-strips this). The `.org` fixtures the small seed reads are only copied
 * into those two variants' assets (`copy{Debug,Benchmark}TestFixtures` in
 * `app/build.gradle.kts`). See internal/test-suite-03-e2e-maestro.md and
 * internal/test-suite-04-performance-macrobenchmark.md.
 */
object DebugTestVault {

    const val EXTRA_DIRECT_VAULT = "grove_test_direct_vault"
    const val EXTRA_SEED = "grove_test_seed"

    /**
     * Int extra: when > 0, seed a large *synthetic* vault instead of the bundled
     * fixtures — [size] small notebooks plus one [LARGE_OUTLINE_HEADINGS]-heading
     * outline, for the scroll benchmarks.
     */
    const val EXTRA_VAULT_SIZE = "grove_test_vault_size"

    private const val LARGE_OUTLINE_HEADINGS = 500

    /** Directory name under the app's external files dir used as the test vault root. */
    private const val VAULT_DIR = "testvault"

    /** Called from `MainActivity.onCreate` before content is set. */
    fun applyFromLaunchIntent(context: Context, intent: Intent?) {
        if (!BuildConfig.TEST_HOOKS || intent == null) return
        if (!intent.getBooleanExtra(EXTRA_DIRECT_VAULT, false)) return

        val root = File(context.getExternalFilesDir(null), VAULT_DIR).apply { mkdirs() }
        val size = intent.getIntExtra(EXTRA_VAULT_SIZE, 0)
        when {
            size > 0 -> seedLarge(root, size)
            intent.getBooleanExtra(EXTRA_SEED, false) -> seed(context, root)
        }
        TestVaultHook.useDirectory(root)
    }

    /** Wipe [root] and rewrite it from the debug/benchmark APK's bundled `.org` fixtures. */
    private fun seed(context: Context, root: File) {
        wipe(root)
        val names = context.assets.list("fixtures").orEmpty().filter { it.endsWith(".org") }
        names.forEach { name ->
            context.assets.open("fixtures/$name").use { input ->
                File(root, name).outputStream().use { input.copyTo(it) }
            }
        }
    }

    /**
     * Wipe [root] and fill it with generated content sized for the scroll
     * benchmarks: [notebooks] short files plus one long single-file outline.
     */
    private fun seedLarge(root: File, notebooks: Int) {
        wipe(root)
        File(root, "big-outline.org").writeText(
            buildString {
                append("#+TITLE: Big Outline\n\n")
                repeat(LARGE_OUTLINE_HEADINGS) { i ->
                    append("* Heading ${i + 1}\n")
                    append("  Body line for heading ${i + 1}.\n")
                }
            },
        )
        repeat(notebooks) { i ->
            File(root, "notebook-%03d.org".format(i + 1)).writeText(
                "#+TITLE: Notebook ${i + 1}\n\n* Item A\n  Note body.\n* Item B\n",
            )
        }
    }

    private fun wipe(root: File) {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }
}
