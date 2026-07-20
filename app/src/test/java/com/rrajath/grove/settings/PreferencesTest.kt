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

    @Test
    fun `checklist states round-trips and defaults to two`() {
        for (pref in ChecklistStates.entries) {
            assertEquals(pref, ChecklistStates.fromStorage(pref.storageKey))
        }
        assertEquals(ChecklistStates.TWO, ChecklistStates.fromStorage(null))
        assertEquals(ChecklistStates.TWO, ChecklistStates.fromStorage("four"))
    }

    @Test
    fun `checklist states marks are ordered open to done`() {
        assertEquals(listOf(' ', 'X'), ChecklistStates.TWO.marks)
        assertEquals(listOf(' ', '-', 'X'), ChecklistStates.THREE.marks)
    }
}
