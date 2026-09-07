package com.rrajath.grove.macrobenchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.rrajath.grove.macrobenchmark.BenchmarkVault.awaitNotebooks
import com.rrajath.grove.macrobenchmark.BenchmarkVault.withSeededVault
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold and warm app-startup timing into a populated Notebooks screen, at three
 * compilation modes so the baseline-profile win is visible:
 *
 *  - [CompilationMode.None]    — JIT only, worst case
 *  - [CompilationMode.Partial] with the packaged baseline profile (what ships)
 *  - [CompilationMode.Full]    — everything AOT, best case / upper bound
 *
 * Metrics: `timeToInitialDisplayMs` and (once MainActivity's Notebooks screen
 * calls `reportFullyDrawn`) `timeToFullDisplayMs`.
 *
 * Run: `./gradlew :macrobenchmark:connectedBenchmarkAndroidTest`
 * Results: macrobenchmark/build/outputs/connected_android_test_additional_output/
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test fun coldNone() = startup(StartupMode.COLD, CompilationMode.None())

    @Test fun coldBaselineProfile() =
        startup(StartupMode.COLD, CompilationMode.Partial(BaselineProfileMode.Require))

    @Test fun coldFull() = startup(StartupMode.COLD, CompilationMode.Full())

    @Test fun warm() = startup(StartupMode.WARM, CompilationMode.Partial())

    private fun startup(startupMode: StartupMode, compilationMode: CompilationMode) =
        rule.measureRepeated(
            packageName = BenchmarkVault.TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = compilationMode,
            startupMode = startupMode,
            iterations = 10,
            setupBlock = { pressHome() },
        ) {
            startActivityAndWait { it.withSeededVault() }
            device.awaitNotebooks()
        }
}
