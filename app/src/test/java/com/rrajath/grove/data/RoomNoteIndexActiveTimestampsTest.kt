package com.rrajath.grove.data

import android.app.Application
import com.rrajath.grove.testing.FakeFileStore
import com.rrajath.grove.testing.InMemoryGroveDatabase
import com.rrajath.grove.testing.TestVaultSeeder
import com.rrajath.grove.testing.support.MainDispatcherRule
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Layer-1 coverage that bare active timestamps survive the parse-to-index path:
 * [RoomNoteIndex] writes [NoteEntity.activeTimestamps], the `plannedNotes` SQL
 * narrowing lets an active-only heading through, and the row maps to a
 * [com.rrajath.grove.search.NoteMeta] whose `activeDates` expands a range.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [34])
class RoomNoteIndexActiveTimestampsTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val db = InMemoryGroveDatabase.create(queryCoroutineContext = mainDispatcherRule.dispatcher)

    private val vault = """
        #+TITLE: Events

        * Standup
        <2026-09-08 Tue>
        just an event, no keyword or planning

        * Conference trip
        <2026-09-14 Mon>--<2026-09-16 Wed>
        body

        * Plain note
        nothing dated here
    """.trimIndent() + "\n"

    private val store = FakeFileStore(mapOf("events.org" to vault))

    @After
    fun tearDown() = db.close()

    @Test
    fun `active-only headings are indexed and reach plannedNotes`() = runTest {
        TestVaultSeeder.index(db, store)

        val planned = db.indexDao().plannedNotes().first().associateBy { it.title }

        assertEquals("<2026-09-08 Tue>", planned["Standup"]!!.activeTimestamps)
        assertNull(planned["Standup"]!!.scheduled)
        assertEquals(
            "<2026-09-14 Mon>--<2026-09-16 Wed>",
            planned["Conference trip"]!!.activeTimestamps,
        )
        // A heading with no date at all never enters the planned set.
        assertTrue(planned["Plain note"] == null)
    }

    @Test
    fun `a ranged active timestamp maps to every covered day`() = runTest {
        TestVaultSeeder.index(db, store)

        val trip = db.indexDao().plannedNotes().first()
            .first { it.title == "Conference trip" }
            .toNoteMeta()

        assertEquals(
            listOf(
                LocalDate.of(2026, 9, 14),
                LocalDate.of(2026, 9, 15),
                LocalDate.of(2026, 9, 16),
            ),
            trip.activeDates,
        )
    }

    @Test
    fun `plannedActiveTimestamps returns the raw strings`() = runTest {
        TestVaultSeeder.index(db, store)

        assertEquals(
            setOf("<2026-09-08 Tue>", "<2026-09-14 Mon>--<2026-09-16 Wed>"),
            db.indexDao().plannedActiveTimestamps().first().toSet(),
        )
    }
}
