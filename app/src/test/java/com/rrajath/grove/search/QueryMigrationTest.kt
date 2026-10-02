package com.rrajath.grove.search

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class QueryMigrationTest {

    private fun migrate(q: String) = QueryMigration.toV2(q)

    @Test
    fun `single-day s d cr tokens pin to eq`() {
        assertEquals("s.eq.today", migrate("s.today"))
        assertEquals("d.eq.tomorrow i.todo", migrate("d.tomorrow i.todo"))
        assertEquals("cr.eq.yesterday", migrate("cr.yesterday"))
        assertEquals("s.eq.now", migrate("s.now"))
    }

    @Test
    fun `c and a single days already default to eq`() {
        assertEquals("c.today a.today", migrate("c.today a.today"))
    }

    @Test
    fun `relative windows`() {
        assertEquals("s.3d d.1w", migrate("s.3d d.1w"))
        assertEquals("a.le.7d", migrate("a.7d"))
        assertEquals("c.ge.-1w", migrate("c.1w"))
        assertEquals("cr.ge.-2w", migrate("cr.2w"))
    }

    @Test
    fun `unsigned past comparisons get a minus`() {
        assertEquals("c.ge.-1w", migrate("c.ge.1w"))
        assertEquals("cr.lt.-1m", migrate("cr.lt.1m"))
        assertEquals("s.ge.3d", migrate("s.ge.3d"))
        assertEquals("c.eq.yesterday", migrate("c.eq.yesterday"))
    }

    @Test
    fun `negation, brackets and case are preserved`() {
        assertEquals(".s.eq.today", migrate(".s.today"))
        assertEquals("(S.eq.Today OR .(c.ge.-1w))", migrate("(S.Today OR .(c.1w))"))
    }

    @Test
    fun `lowercase keywords become quoted text`() {
        assertEquals("salt \"and\" pepper \"Or\" x OR y AND z", migrate("salt and pepper Or x OR y AND z"))
    }

    @Test
    fun `specials, other prefixes and text are untouched`() {
        val q = "s.overdue d.none a.nodate i.todo t.work p.A b.inbox ramen o.s ad.7 file.org"
        assertEquals(q, migrate(q))
    }

    @Test
    fun `running twice is harmless`() {
        val q = "s.today a.7d c.1w cr.ge.1m salt and pepper"
        assertEquals(migrate(q), migrate(migrate(q)))
    }

    @Test
    fun `a migrated query matches what the original matched`() {
        val today = LocalDate.of(2025, 6, 11)
        fun note(title: String, scheduled: String? = null, closed: String? = null, active: String? = null, created: String? = null) =
            NoteMeta("n.org", 0, title, null, false, null, emptyList(), emptyList(), scheduled, null, closed, active, created, 0L, title)
        val notes = listOf(
            note("pastSched", scheduled = "<2025-06-01 Sun>"),
            note("todaySched", scheduled = "<2025-06-11 Wed>"),
            note("closedRecent", closed = "[2025-06-09 Mon]"),
            note("closedOld", closed = "[2025-04-01 Tue]"),
            note("eventSoon", active = "<2025-06-14 Sat>"),
            note("eventFar", active = "<2025-08-01 Fri>"),
            note("createdRecent", created = "[2025-06-05 Thu]"),
        )
        // v1 meanings: s.today = exactly today; c.1w = closed in the last week;
        // a.7d = any event on or before a week out; cr.2w = created in the last 2 weeks.
        val expected = mapOf(
            "s.today" to listOf("todaySched"),
            "c.1w" to listOf("closedRecent"),
            "a.7d" to listOf("eventSoon"),
            "cr.2w" to listOf("createdRecent"),
        )
        for ((old, titles) in expected) {
            val got = QueryMatcher.filter(notes, QueryParser.parse(migrate(old)), today).map { it.title }
            assertEquals(old, titles, got)
        }
    }
}
