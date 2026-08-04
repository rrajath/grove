package com.rrajath.grove.ui.agenda

import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.settings.GroveSettings
import org.junit.Assert.assertEquals
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
    ) = NoteMeta(
        fileName = "notes.org",
        lineIndex = 0,
        title = title,
        keyword = "TODO",
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
}
