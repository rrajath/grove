package com.rrajath.grove.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile by exercising startup and the main scroll path.
 *
 * This is OPTIONAL and separate from measuring: it produces the profile that
 * `CompilationMode.Partial()` (and a shipped app) compiles ahead of time.
 *
 * Run it with (API 33+ device/emulator):
 *   ./gradlew :macrobenchmark:connectedBenchmarkAndroidTest \
 *       -P android.testInstrumentationRunnerArguments.class=\
 *       com.rrajath.grove.macrobenchmark.BaselineProfileGenerator
 *
 * Pull the generated `baseline-prof.txt` from the test output and drop it at
 * `app/src/main/baseline-prof.txt` — AGP's ART-profile pipeline packages it and
 * profileinstaller installs it at first run. (The `androidx.baselineprofile`
 * Gradle plugin, which would automate this, does not yet support AGP 9 — it
 * fails with "not a supported android module".) See docs/benchmarking.md.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        // Exercise the notebooks -> outline -> scroll journey if a vault exists,
        // so those classes/methods land in the profile too. Safe to no-op when
        // no vault is configured (startup alone still gets profiled).
        device.wait(Until.findObject(By.res("notebooks_list")), 5_000)?.let { notebooks ->
            notebooks.children.firstOrNull()?.click()
            device.wait(Until.findObject(By.res("outline_list")), 5_000)
            device.findObject(By.res("outline_list"))?.fling(Direction.DOWN)
            device.waitForIdle()
        }
    }
}
