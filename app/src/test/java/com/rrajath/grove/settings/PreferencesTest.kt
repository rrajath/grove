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
    fun `notebook sort key round-trips and defaults to alphabetical`() {
        for (pref in NotebookSortKey.entries) {
            assertEquals(pref, NotebookSortKey.fromStorage(pref.storageKey))
        }
        assertEquals(NotebookSortKey.ALPHABETICAL, NotebookSortKey.fromStorage(null))
        assertEquals(NotebookSortKey.ALPHABETICAL, NotebookSortKey.fromStorage("size"))
    }

    @Test
    fun `note open mode round-trips and defaults to read`() {
        for (pref in NoteOpenMode.entries) {
            assertEquals(pref, NoteOpenMode.fromStorage(pref.storageKey))
        }
        assertEquals(NoteOpenMode.READ, NoteOpenMode.fromStorage(null))
    }

    @Test
    fun `new note cursor round-trips and defaults to body`() {
        for (pref in NewNoteCursor.entries) {
            assertEquals(pref, NewNoteCursor.fromStorage(pref.storageKey))
        }
        assertEquals(NewNoteCursor.BODY, NewNoteCursor.fromStorage(null))
        assertEquals(NewNoteCursor.BODY, NewNoteCursor.fromStorage("footer"))
    }
}
