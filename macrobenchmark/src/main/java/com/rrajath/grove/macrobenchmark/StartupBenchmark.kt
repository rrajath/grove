package com.rrajath.grove.macrobenchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Cold-start timing for Grove (time-to-initial-display / time-to-full-display).
 *
 * Run all three to see how much AOT compilation buys you — this is what a
 * Baseline Profile and R8 move. Compare `timeToInitialDisplayMs` across:
 *   - [startupCompilationNone]      — JIT only (worst case, fresh install)
 *   - [startupBaselineProfile]      — what a shipped Baseline Profile delivers
 *   - [startupFullCompilation]      — everything AOT-compiled (upper bound)
 */
@RunWith(AndroidJUnit4::class)
class StartupBenchmark {

    @get:Rule
    val rule = MacrobenchmarkRule()

    @Test
    fun startupCompilationNone() = startup(CompilationMode.None())

    @Test
    fun startupBaselineProfile() = startup(CompilationMode.Partial())

    @Test
    fun startupFullCompilation() = startup(CompilationMode.Full())

    private fun startup(mode: CompilationMode) = rule.measureRepeated(
        packageName = TARGET_PACKAGE,
        metrics = listOf(StartupTimingMetric()),
        iterations = 10,
        startupMode = StartupMode.COLD,
        compilationMode = mode,
    ) {
        pressHome()
        startActivityAndWait()
    }
}

internal const val TARGET_PACKAGE = "com.rrajath.grove"
