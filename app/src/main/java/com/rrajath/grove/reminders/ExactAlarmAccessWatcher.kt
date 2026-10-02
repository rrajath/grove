package com.rrajath.grove.reminders

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Re-arms reminder alarms whenever exact-alarm access ("Alarms & reminders") has
 * changed since the last check. [AlarmScheduler.schedule] picks exact vs inexact
 * only at arm time, so without this a grant leaves every already-armed alarm
 * inexact (up to ~1 hour late), and a revoke (the OS stops the app and cancels
 * its exact alarms) leaves future reminders with no alarm at all.
 *
 * Checked on app start, on every return to the foreground, and from
 * `ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED` (see [ReminderBootReceiver]).
 * The persisted last-known value makes the three callers idempotent: whichever
 * runs first re-arms, the rest see no change. A null last-known value (first run
 * after install or update) counts as a change, so stale alarms are fixed once.
 *
 * Android side-effects sit behind lambdas so the logic is JVM-unit-testable.
 */
class ExactAlarmAccessWatcher(
    private val canScheduleExact: () -> Boolean,
    private val lastKnown: suspend () -> Boolean?,
    private val storeLastKnown: suspend (Boolean) -> Unit,
    private val rearm: suspend () -> Unit,
) {
    private val mutex = Mutex()

    /** Re-arms if access differs from the last-known value; returns whether it did. */
    suspend fun check(): Boolean = mutex.withLock {
        val granted = canScheduleExact()
        if (lastKnown() == granted) return@withLock false
        rearm()
        storeLastKnown(granted)
        true
    }
}
