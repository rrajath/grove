package com.rrajath.grove.ui.agenda

import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.settings.GroveSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * Regression coverage for tag inheritance in the agenda: a heading's row must
 * show its own tags *and* every ancestor's tags (org-mode tag inheritance),
 * not just its own. [com.rrajath.grove.org.OrgParserTest] already locks in
 * `OrgDocument.inheritedTags` itself; this locks in that [AgendaViewModel.row]
 * actually renders that field rather than the headline's own-only tags.
 */
class AgendaViewModelRowTest {

    private val today = LocalDate.of(2025, 6, 11)

    /** Mirrors [com.rrajath.grove.data.NoteMetaMapping.toNoteMeta] without Room. */
    private fun note(
        title: String,
        tags: List<String>,
        inheritedTags: List<String>,
        scheduled: String? = null,
        keyword: String? = "TODO",
    ) = NoteMeta(
        fileName = "notes.org",
        lineIndex = 0,
        title = title,
        keyword = keyword,
        isDoneKeyword = false,
        priority = null,
        tags = tags,
        inheritedTags = inheritedTags,
        scheduled = scheduled,
        deadline = null,
        closed = null,
        createdAt = null,
        lastModified = 0L,
        searchText = title,
    )

    @Test
    fun `agenda row shows own tags plus every ancestor's tags`() {
        val text = """
            * Maintenance Projects              :maintenance:
            ** TODO Car Service Maintenance     :service:
            SCHEDULED: <2025-06-11 Wed>
        """.trimIndent()

        val doc = OrgParser.parse(text)
        val carService = doc.headlines.single { it.title == "Car Service Maintenance" }

        // Sanity check on the underlying computation this test relies on.
        assertEquals(listOf("service"), carService.tags)
        assertEquals(listOf("service", "maintenance"), doc.inheritedTags(carService))

        val meta = note(
            title = carService.title,
            tags = carService.tags,
            inheritedTags = doc.inheritedTags(carService),
            scheduled = "<2025-06-11 Wed>",
        )

        val row = AgendaViewModel.row(meta, today, showDate = false, p = GroveSettings())

        val tagChip = row.meta.single { it.tone == AgendaMetaTone.TAG }
        assertEquals(":service:maintenance:", tagChip.text)
    }

    @Test
    fun `agenda row for a top-level heading shows only its own tags`() {
        val meta = note(
            title = "Standalone task",
            tags = listOf("urgent"),
            inheritedTags = listOf("urgent"),
            scheduled = "<2025-06-11 Wed>",
        )

        val row = AgendaViewModel.row(meta, today, showDate = false, p = GroveSettings())

        val tagChip = row.meta.single { it.tone == AgendaMetaTone.TAG }
        assertEquals(":urgent:", tagChip.text)
    }

    @Test
    fun `an event row renders its own time range and no overdue styling`() {
        val meta = note(title = "Team offsite", tags = emptyList(), inheritedTags = emptyList())
        val ts = OrgTimestamp.parse("<2025-06-13 Fri 09:00-17:00>")!!

        val row = AgendaViewModel.row(
            meta, today, showDate = false, p = GroveSettings(),
            activeTs = ts, eventDay = LocalDate.of(2025, 6, 13),
        )

        assertEquals(ts, row.activeTs)
        assertTrue(row.meta.any { it.text == "09:00–17:00" })
        // Events never carry overdue / deadline styling.
        assertTrue(row.meta.none { it.tone == AgendaMetaTone.DANGER })
        assertNull(row.scheduledTs)
    }

    @Test
    fun `the event day chip shows only when the row is not under a day section`() {
        val meta = note(title = "Team offsite", tags = emptyList(), inheritedTags = emptyList())
        val ts = OrgTimestamp.parse("<2025-06-13 Fri>")!!

        // Under a day section (eventDay set): the header names the day, so no chip.
        val grouped = AgendaViewModel.row(
            meta, today, showDate = false, p = GroveSettings(),
            activeTs = ts, eventDay = LocalDate.of(2025, 6, 13),
        )
        assertTrue(grouped.meta.none { it.tone == AgendaMetaTone.EVENT })

        // No day section (eventDay null): the row carries the day itself.
        val loose = AgendaViewModel.row(meta, today, showDate = false, p = GroveSettings(), activeTs = ts)
        assertEquals("● Friday", loose.meta.single { it.tone == AgendaMetaTone.EVENT }.text)
    }

    @Test
    fun `isEvent is true for any keyword-less agenda heading`() {
        val ts = OrgTimestamp.parse("<2025-06-13 Fri>")!!
        val eventDay = LocalDate.of(2025, 6, 13)

        val event = AgendaViewModel.row(
            note("Team offsite", emptyList(), emptyList(), keyword = null),
            today, showDate = false, p = GroveSettings(), activeTs = ts, eventDay = eventDay,
        )
        assertTrue(event.isEvent)

        // Same heading, but it carries a keyword: still a task.
        val keyworded = AgendaViewModel.row(
            note("Review PR", emptyList(), emptyList(), keyword = "NEXT"),
            today, showDate = false, p = GroveSettings(), activeTs = ts, eventDay = eventDay,
        )
        assertEquals(false, keyworded.isEvent)

        // A scheduled heading with no keyword is also an event: nothing to complete.
        val scheduled = AgendaViewModel.row(
            note("Just scheduled", emptyList(), emptyList(), scheduled = "<2025-06-11 Wed>", keyword = null),
            today, showDate = false, p = GroveSettings(),
        )
        assertTrue(scheduled.isEvent)
    }
}
