package com.rrajath.grove.dailies

import com.rrajath.grove.testing.FakeFileStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailiesRepositoryTest {

    @Test
    fun `resolveFileName joins directory and expanded pattern`() {
        val repo = DailiesRepository(FakeFileStore())
        assertEquals(
            "roam/daily/2026-09-23.org",
            repo.resolveFileName("roam/daily", "%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 23)),
        )
    }

    @Test
    fun `resolveFileName with a blank directory is vault-root relative`() {
        val repo = DailiesRepository(FakeFileStore())
        assertEquals(
            "2026-09-23.org",
            repo.resolveFileName("", "%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 23)),
        )
    }

    @Test
    fun `existsForDate reflects the store`() = runTest {
        val store = FakeFileStore(mapOf("roam/daily/2026-09-23.org" to "content"))
        val repo = DailiesRepository(store)
        assertTrue(repo.existsForDate("roam/daily", "%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 23)))
        assertFalse(repo.existsForDate("roam/daily", "%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 24)))
    }

    @Test
    fun `existingDates parses only files matching the pattern under the directory`() = runTest {
        val store = FakeFileStore(
            mapOf(
                "roam/daily/2026-09-21.org" to "",
                "roam/daily/2026-09-23.org" to "",
                "roam/daily/not-a-date.org" to "",
                "roam/work.org" to "",
            ),
        )
        val repo = DailiesRepository(store)
        assertEquals(
            listOf(LocalDate.of(2026, 9, 21), LocalDate.of(2026, 9, 23)),
            repo.existingDates("roam/daily", "%<%Y-%m-%d>.org"),
        )
    }

    @Test
    fun `existingDates on an empty or nonexistent directory is empty, not an error`() = runTest {
        val repo = DailiesRepository(FakeFileStore())
        assertEquals(emptyList<LocalDate>(), repo.existingDates("roam/daily", "%<%Y-%m-%d>.org"))
    }

    @Test
    fun `previousExistingDate finds the nearest earlier date via the sorted set`() = runTest {
        val store = FakeFileStore(
            mapOf("roam/daily/2026-09-21.org" to "", "roam/daily/2026-09-18.org" to ""),
        )
        val repo = DailiesRepository(store)
        assertEquals(
            LocalDate.of(2026, 9, 21),
            repo.previousExistingDate("roam/daily", "%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 23)),
        )
    }

    @Test
    fun `previousExistingDate falls back to the absolute previous day when nothing exists`() = runTest {
        val repo = DailiesRepository(FakeFileStore())
        assertEquals(
            LocalDate.of(2026, 9, 22),
            repo.previousExistingDate("roam/daily", "%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 23)),
        )
    }

    @Test
    fun `previousExistingDate falls back to a bounded day-by-day walk for an unparseable pattern`() = runTest {
        // No day token: existingDates can't reverse-parse this, so the walk must
        // still find an existing file by generating and checking candidates.
        val store = FakeFileStore(mapOf("roam/daily/standup-2026-09-18.org" to ""))
        val repo = DailiesRepository(store)
        assertEquals(
            LocalDate.of(2026, 9, 18),
            repo.previousExistingDate("roam/daily", "standup-%<%Y-%m-%d>.org", LocalDate.of(2026, 9, 23)),
        )
    }

    @Test
    fun `expandHeaderTemplate resolves date tokens against the given date, not wall-clock now`() {
        val repo = DailiesRepository(FakeFileStore())
        val expanded = repo.expandHeaderTemplate("#+title: %date\n%?", LocalDate.of(2020, 1, 5))
        assertEquals("#+title: 2020-01-05\n", expanded.text)
        assertEquals(expanded.text.length, expanded.cursorOffset)
    }
}
