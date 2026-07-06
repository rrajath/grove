package com.rrajath.grove.ui.editor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class KeystrokeCounterTest {

    @Test
    fun `fires on the exact threshold`() {
        val counter = KeystrokeCounter(threshold = 20)
        val fired = (1..20).map { counter.tick() }
        assertEquals(List(19) { false } + true, fired)
    }

    @Test
    fun `resets after firing and fires again after another threshold`() {
        val counter = KeystrokeCounter(threshold = 3)
        assertFalse(counter.tick())
        assertFalse(counter.tick())
        assertTrue(counter.tick())
        assertFalse(counter.tick())
        assertFalse(counter.tick())
        assertTrue(counter.tick())
    }

    @Test
    fun `default threshold is 20`() {
        val counter = KeystrokeCounter()
        repeat(19) { assertFalse(counter.tick()) }
        assertTrue(counter.tick())
    }
}
