package com.rrajath.grove.sync

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Collapses a burst of sync triggers into as few full passes as possible
 * (PERFORMANCE_AUDIT_2026-08-27 #3).
 *
 * Grove has several independent sync triggers — the 10s continuous-mode poll,
 * the WorkManager periodic worker, foreground/background lifecycle, a manual
 * save, a resolved conflict. Before this, each one launched its own coroutine
 * that serialized behind a mutex, so a save landing next to a poll tick paid the
 * full list+diff cost two or three times back to back.
 *
 * Here every trigger funnels through one [Channel] with a single consumer:
 * - A [Channel.CONFLATED] buffer keeps only the most recent request, so any
 *   number of triggers arriving while a pass runs coalesce into exactly one
 *   follow-up pass.
 * - A short [debounce] before each pass lets a cluster of triggers that arrive
 *   while idle (e.g. `onAppForeground` firing a sync and starting the poll in
 *   the same tick) settle into a single pass, the same way `SearchViewModel`
 *   debounces query input.
 *
 * The consumer lives for the life of [scope] (the app scope); [pass] is invoked
 * one at a time, never concurrently.
 */
class SyncCoalescer(
    scope: CoroutineScope,
    private val debounce: Duration = DEFAULT_DEBOUNCE,
    private val pass: suspend (reason: String) -> Unit,
) {
    private val requests = Channel<String>(Channel.CONFLATED)

    init {
        scope.launch {
            for (first in requests) {
                delay(debounce)
                // Drain anything that piled up during the debounce window so a
                // burst collapses into this one pass rather than trailing it.
                var reason = first
                while (true) {
                    reason = requests.tryReceive().getOrNull() ?: break
                }
                pass(reason)
            }
        }
    }

    /** Enqueue a sync. Never suspends; never fails. */
    fun request(reason: String) {
        requests.trySend(reason)
    }

    companion object {
        val DEFAULT_DEBOUNCE: Duration = 200.milliseconds
    }
}
