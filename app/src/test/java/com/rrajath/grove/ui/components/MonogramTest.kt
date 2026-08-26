package com.rrajath.grove.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonogramTest {

    @Test
    fun `letter is the uppercased first character`() {
        assertEquals("I", monogramLetter("inbox.org"))
        assertEquals("W", monogramLetter("work.org"))
        assertEquals("M", monogramLetter("My Reading List"))
    }

    @Test
    fun `letter trims leading whitespace`() {
        assertEquals("J", monogramLetter("  journal.org"))
    }

    @Test
    fun `blank source falls back to a bullet`() {
        assertEquals("•", monogramLetter(""))
        assertEquals("•", monogramLetter("   "))
    }

    @Test
    fun `leading emoji survives as one grapheme code point`() {
        // A single emoji code point (not a ZWJ sequence) is kept whole.
        assertEquals("🌲", monogramLetter("🌲 trees.org"))
    }

    @Test
    fun `hash palette key is stable and in range`() {
        val key = nameHashPaletteKey("work.org")
        assertEquals(key, nameHashPaletteKey("work.org"))
        assertTrue(key in MONOGRAM_PALETTE_KEYS)
    }
}
