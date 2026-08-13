package com.rrajath.grove.ui.screens.settings

import android.os.Process
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Reads this process's own recent logcat output for the "Recent error log" bug-report
 * attachment. No READ_LOGS permission needed: Android's log driver already restricts
 * `logcat` to entries tagged with the calling app's own UID, so filtering by this
 * process's PID surfaces only Grove's own log lines from the current session.
 * Returns null if the `logcat` binary can't be invoked (OEM restriction, etc.).
 */
internal suspend fun captureRecentErrorLog(maxLines: Int): List<String>? = withContext(Dispatchers.IO) {
    runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "brief", "--pid=${Process.myPid()}", "-t", maxLines.toString(),
        ).redirectErrorStream(true).start()
        val lines = BufferedReader(InputStreamReader(process.inputStream)).use { it.readLines() }
        process.waitFor()
        lines
    }.getOrNull()
}
