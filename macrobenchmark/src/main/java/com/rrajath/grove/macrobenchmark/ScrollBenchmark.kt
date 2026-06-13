package com.rrajath.grove.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Frame timing while scrolling the outline (heading tree). This is the screen
 * that exercises [OutlineNode]'s per-row work — inline tokenizing, swipe boxes,
 * `directChildren` — so it's where the rendering-side perf suggestions show up.
 *
 * PRECONDITION: the test device must already have a vault folder configured with
 * at least one notebook that has enough headings to scroll. See the "Seeding a
 * vault" section of the benchmarking instructions. If no notebook is present the
 * test fails fast with a clear message rather than reporting meaningless numbers.
 *
 * Compare `frameDurationCpuMs` P50/P90/P99 (and `frameOverrunMs` on API 31+)
 * before vs. after a change.
 */
@RunWith(AndroidJUnit4::class)
class ScrollBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun outlineScrollCompilationNone() = scroll(CompilationMode.None())

    @Test
    fun outlineScrollBaselineProfile() = scroll(CompilationMode.Partial())

    private fun scroll(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(FrameTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.WARM,
        compilationMode = mode,
        setupBlock = {
            pressHome()
            startActivityAndWait()
            // Land on the notebooks list, then open the first notebook.
            val notebooks = device.wait(Until.findObject(By.res("notebooks_list")), TIMEOUT_MS)
                ?: error(
                    "notebooks_list not found. Configure a vault folder with at least one " +
                        "notebook on the device before running ScrollBenchmark."
                )
            val firstRow = notebooks.children.firstOrNull()
                ?: error("Vault has no notebooks. Add a notebook before running ScrollBenchmark.")
            firstRow.click()
            device.wait(Until.findObject(By.res("outline_list")), TIMEOUT_MS)
                ?: error("outline_list not found after opening a notebook.")
        },
    ) {
        val list = device.findObject(By.res("outline_list"))
        // Keep the fling away from the screen edges (system gesture zones).
        list.setGestureMargin(device.displayWidth / 5)
        repeat(3) {
            list.fling(Direction.DOWN)
            device.waitForIdle()
        }
        list.fling(Direction.UP)
        device.waitForIdle()
    }

    companion object {
        private const val TIMEOUT_MS = 10_000L
    }
}
