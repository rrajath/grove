package com.rrajath.grove.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class PreferencesTest {

    @Test
    fun `theme preference round-trips through storage key`() {
        for (pref in ThemePreference.entries) {
            assertEquals(pref, ThemePreference.fromStorage(pref.storageKey))
        }
    }

    @Test
    fun `unknown or missing theme falls back to light`() {
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromStorage(null))
        assertEquals(ThemePreference.LIGHT, ThemePreference.fromStorage("sepia"))
    }

    @Test
    fun `font size round-trips and defaults to medium`() {
        for (pref in FontSizePreference.entries) {
            assertEquals(pref, FontSizePreference.fromStorage(pref.storageKey))
        }
        assertEquals(FontSizePreference.MEDIUM, FontSizePreference.fromStorage(null))
        assertEquals(FontSizePreference.MEDIUM, FontSizePreference.fromStorage("huge"))
    }

    @Test
    fun `font size scales are ordered`() {
        assert(FontSizePreference.SMALL.scale < FontSizePreference.MEDIUM.scale)
        assert(FontSizePreference.MEDIUM.scale < FontSizePreference.LARGE.scale)
    }

    @Test
    fun `note open mode round-trips and defaults to read`() {
        for (pref in NoteOpenMode.entries) {
            assertEquals(pref, NoteOpenMode.fromStorage(pref.storageKey))
        }
        assertEquals(NoteOpenMode.READ, NoteOpenMode.fromStorage(null))
    }
}
