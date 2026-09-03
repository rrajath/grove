package com.rrajath.grove.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FilenameMatcherTest {

    private val vault = listOf(
        "journal.org",
        "work/planning-2025.org",
        "work/inbox.org",
        "recipes.org",
        "archive/journal-2019.org",
    )

    private fun hits(query: String) =
        FilenameMatcher.match(vault, QueryParser.parse(query))

    @Test
    fun `exact base-name match ranks EXACT`() {
        val hit = hits("journal").single { it.fileName == "journal.org" }
        assertEquals(FilenameMatcher.Quality.EXACT, hit.quality)
        assertEquals(listOf(0..6), hit.ranges)
    }

    @Test
    fun `prefix match ranks PREFIX`() {
        val hit = hits("plann").single()
        assertEquals("work/planning-2025.org", hit.fileName)
        assertEquals(FilenameMatcher.Quality.PREFIX, hit.quality)
    }

    @Test
    fun `folder segment match counts but highlights nothing`() {
        val hit = hits("work").single { it.fileName == "work/inbox.org" }
        assertEquals(FilenameMatcher.Quality.CONTAINS, hit.quality)
        assertTrue(hit.ranges.isEmpty())
    }

    @Test
    fun `substring inside base name ranks CONTAINS and highlights the span`() {
        val hit = hits("2025").single()
        assertEquals("work/planning-2025.org", hit.fileName)
        assertEquals(FilenameMatcher.Quality.CONTAINS, hit.quality)
        assertEquals(listOf(9..12), hit.ranges) // "planning-" is 9 chars
    }

    @Test
    fun `all AND terms must appear in the path`() {
        assertTrue(hits("journal recipes").isEmpty())
        assertEquals(
            listOf("archive/journal-2019.org"),
            hits("journal 2019").map { it.fileName },
        )
    }

    @Test
    fun `negated term excludes a path`() {
        val names = hits("journal .archive").map { it.fileName }
        assertEquals(listOf("journal.org"), names)
    }

    @Test
    fun `OR groups each contribute matches`() {
        val names = hits("recipes OR inbox").map { it.fileName }.toSet()
        assertEquals(setOf("recipes.org", "work/inbox.org"), names)
    }

    @Test
    fun `terms below the trigram floor never name-match`() {
        assertTrue(hits("wo").isEmpty())
    }

    @Test
    fun `a facet-only query yields no name matches`() {
        assertTrue(hits("i.TODO").isEmpty())
        assertTrue(hits("t.work").isEmpty())
    }

    @Test
    fun `facet conditions alongside text are ignored for filename matching`() {
        assertEquals(
            listOf("recipes.org"),
            hits("i.TODO recipes").map { it.fileName },
        )
    }

    @Test
    fun `case-insensitive`() {
        assertEquals("journal.org", hits("JOURNAL").single { it.fileName == "journal.org" }.fileName)
    }
}
