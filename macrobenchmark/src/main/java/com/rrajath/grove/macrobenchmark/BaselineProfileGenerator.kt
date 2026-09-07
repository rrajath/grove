package com.rrajath.grove.macrobenchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import com.rrajath.grove.macrobenchmark.BenchmarkVault.awaitNotebooks
import com.rrajath.grove.macrobenchmark.BenchmarkVault.withSeededVault
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates the baseline profile that ships in the APK. It walks the critical
 * user journey — cold start into Notebooks, scroll, open a note, scroll its
 * outline, back — and records every class/method touched so ART can AOT-compile
 * them ahead of first run.
 *
 * Run: `./gradlew :app:generateBaselineProfile` (needs a connected device).
 * Output is merged into `app/src/main/baselineProfiles/` — commit it, and
 * regenerate on each release (see internal/grove-release-process notes).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = BenchmarkVault.TARGET_PACKAGE,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait { it.withSeededVault() }
        device.awaitNotebooks()

        device.findObject(By.res(BenchmarkVault.NOTEBOOKS_LIST)).fling(Direction.DOWN)
        device.waitForIdle()

        device.findObject(By.res(BenchmarkVault.NOTEBOOK_ROW)).click()
        device.wait(Until.hasObject(By.res(BenchmarkVault.OUTLINE_LIST)), BenchmarkVault.LAUNCH_TIMEOUT_MS)
        device.findObject(By.res(BenchmarkVault.OUTLINE_LIST)).fling(Direction.DOWN)
        device.waitForIdle()

        device.pressBack()
        device.awaitNotebooks()
    }
}
