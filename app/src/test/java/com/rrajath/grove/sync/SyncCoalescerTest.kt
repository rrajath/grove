package com.rrajath.grove.sync

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalCoroutinesApi::class)
class SyncCoalescerTest {

    @Test
    fun `a burst of requests collapses into one pass`() = runTest {
        val reasons = mutableListOf<String>()
        val coalescer = SyncCoalescer(backgroundScope, debounce = 50.milliseconds) { r ->
            reasons.add(r)
        }

        repeat(20) { coalescer.request("burst-$it") }
        advanceTimeBy(1_000)
        runCurrent()

        assertEquals(listOf("burst-19"), reasons)
    }

    @Test
    fun `requests arriving while a pass runs coalesce into a single follow-up`() = runTest {
        val reasons = mutableListOf<String>()
        val gate = CompletableDeferred<Unit>()
        val coalescer = SyncCoalescer(backgroundScope, debounce = 10.milliseconds) { r ->
            reasons.add(r)
            if (reasons.size == 1) gate.await() // hold the first pass open
        }

        coalescer.request("first")
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("first"), reasons) // first pass is running, blocked

        // Several more triggers land while the first pass is still in flight.
        repeat(5) { coalescer.request("during-$it") }
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("first"), reasons) // nothing new started yet

        gate.complete(Unit)
        advanceTimeBy(100)
        runCurrent()
        assertEquals(listOf("first", "during-4"), reasons) // exactly one follow-up
    }

    @Test
    fun `spaced-out requests each run`() = runTest {
        val reasons = mutableListOf<String>()
        val coalescer = SyncCoalescer(backgroundScope, debounce = 10.milliseconds) { r ->
            reasons.add(r)
            delay(5)
        }

        coalescer.request("a")
        advanceTimeBy(500)
        runCurrent()
        coalescer.request("b")
        advanceTimeBy(500)
        runCurrent()

        assertEquals(listOf("a", "b"), reasons)
    }
}
