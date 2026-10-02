package com.rrajath.grove.reminders

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExactAlarmAccessWatcherTest {

    private class Harness(var granted: Boolean, var stored: Boolean?) {
        var rearms = 0
        val watcher = ExactAlarmAccessWatcher(
            canScheduleExact = { granted },
            lastKnown = { stored },
            storeLastKnown = { stored = it },
            rearm = { rearms++ },
        )
    }

    @Test
    fun `first check after install or update rearms once and records access`() = runTest {
        val h = Harness(granted = false, stored = null)

        assertTrue(h.watcher.check())
        assertFalse(h.watcher.check())

        assertEquals(1, h.rearms)
        assertEquals(false, h.stored)
    }

    @Test
    fun `unchanged access does nothing`() = runTest {
        val h = Harness(granted = true, stored = true)

        assertFalse(h.watcher.check())

        assertEquals(0, h.rearms)
    }

    @Test
    fun `grant then revoke each rearm exactly once`() = runTest {
        val h = Harness(granted = false, stored = false)

        h.granted = true
        assertTrue(h.watcher.check())
        assertFalse("broadcast and foreground check both fire on a grant", h.watcher.check())
        assertEquals(1, h.rearms)
        assertEquals(true, h.stored)

        h.granted = false
        assertTrue(h.watcher.check())
        assertEquals(2, h.rearms)
        assertEquals(false, h.stored)
    }
}
