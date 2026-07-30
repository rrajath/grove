package com.rrajath.grove.ui.agenda

import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.settings.AgendaGrouping
import com.rrajath.grove.settings.AgendaStateFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * The agenda's day model, ported from the `QueryMatcher.agenda` tests when the
 * screen was rebuilt on the "Agenda A · focus" prototype. The rule changed:
 * a heading now lands on exactly one day (SCHEDULED, else DEADLINE) rather than
 * on every day either date touches.
 */
class AgendaBucketsTest {

    private val today = LocalDate.of(2025, 6, 11)

    private fun note(
        title: String,
        fileName: String = "notes.org",
        keyword: String? = null,
        done: Boolean = false,
        priority: String? = null,
        tags: List<String> = emptyList(),
        scheduled: String? = null,
        deadline: String? = null,
    ) = NoteMeta(
        fileName, 0, title, keyword, done, priority, tags, tags,
        scheduled, deadline, null, null, 0L, title,
    )

    // --- which day a heading lands on ---

    @Test
    fun `scheduled date wins over deadline when a heading has both`() {
        val both = note("Both", scheduled = "<2025-06-12 Thu>", deadline = "<2025-06-20 Fri>")
        assertEquals(LocalDate.of(2025, 6, 12), AgendaBuckets.whenDate(both))
    }

    @Test
    fun `a deadline-only heading lands on its deadline`() {
        val deadlineOnly = note("DeadlineOnly", deadline = "<2025-06-20 Fri>")
        assertEquals(LocalDate.of(2025, 6, 20), AgendaBuckets.whenDate(deadlineOnly))
    }

    @Test
    fun `a heading scheduled today with a past deadline is not overdue`() {
        // The old QueryMatcher.agenda called this overdue because *either* date
        // was past. The screen now anchors on the scheduled date only.
        val note = note("PastDeadline", scheduled = "<2025-06-11 Wed>", deadline = "<2025-06-01 Sun>")
        assertTrue(AgendaBuckets.overdue(listOf(note), today).isEmpty())
        assertEquals(listOf("PastDeadline"), AgendaBuckets.onDay(listOf(note), today).map { it.title })
    }

    // --- sections ---

    @Test
    fun `overdue is separate from today and upcoming`() {
        val overdue = note("Overdue", keyword = "TODO", scheduled = "<2025-06-01 Sun>")
        val todayNote = note("Today", deadline = "<2025-06-11 Wed>")
        val thursday = note("Thursday", scheduled = "<2025-06-12 Thu>")
        val nextWeek = note("NextWeek", scheduled = "<2025-06-25 Wed>")
        val all = listOf(overdue, todayNote, thursday, nextWeek)

        assertEquals(listOf("Overdue"), AgendaBuckets.overdue(all, today).map { it.title })
        assertEquals(listOf("Today"), AgendaBuckets.onDay(all, today).map { it.title })
        assertEquals(listOf("Thursday"), AgendaBuckets.upcoming(all, today, 7).map { it.title })
    }

    @Test
    fun `overdue sorted oldest first then priority then title`() {
        val recentB = note("RecentB", scheduled = "<2025-06-10 Tue>", priority = "B")
        val tiedA = note("TiedA", scheduled = "<2025-06-01 Sun>", priority = "A")
        val tiedB = note("TiedB", deadline = "<2025-06-01 Sun>", priority = "B")
        val tiedNone = note("TiedNone", scheduled = "<2025-06-01 Sun>")

        val ordered = AgendaBuckets.overdue(listOf(recentB, tiedA, tiedB, tiedNone), today)
        assertEquals(listOf("TiedA", "TiedB", "TiedNone", "RecentB"), ordered.map { it.title })
    }

    @Test
    fun `growing the day window extends the upcoming horizon`() {
        val soon = note("Soon", scheduled = "<2025-06-12 Thu>")
        val later = note("Later", scheduled = "<2025-06-20 Fri>")
        val all = listOf(soon, later)

        assertEquals(listOf("Soon"), AgendaBuckets.upcoming(all, today, 3).map { it.title })
        assertEquals(listOf("Soon", "Later"), AgendaBuckets.upcoming(all, today, 10).map { it.title })
    }

    // --- the "Show" levers ---

    @Test
    fun `Open admits anything not done-type including headings with no keyword`() {
        val open = note("Open", keyword = "TODO")
        val waiting = note("Waiting", keyword = "WAITING")
        val bare = note("Bare")
        val done = note("Done", keyword = "DONE", done = true)

        listOf(open, waiting, bare).forEach {
            assertTrue(it.title, AgendaBuckets.keep(it, AgendaStateFilter.Open))
        }
        assertFalse(AgendaBuckets.keep(done, AgendaStateFilter.Open))
    }

    @Test
    fun `a keyword filter pins exactly that keyword`() {
        val next = note("Next", keyword = "NEXT")
        val todo = note("Todo", keyword = "TODO")
        val filter = AgendaStateFilter.Keyword("NEXT")

        assertTrue(AgendaBuckets.keep(next, filter))
        assertFalse(AgendaBuckets.keep(todo, filter))
    }

    @Test
    fun `Everything admits done headings too`() {
        val done = note("Done", keyword = "DONE", done = true)
        assertTrue(AgendaBuckets.keep(done, AgendaStateFilter.All))
        assertFalse(AgendaBuckets.keep(done, AgendaStateFilter.Open))
    }

    // --- grouping ---

    @Test
    fun `date grouping on the Today tab is a single bucket sorted by time`() {
        val noTime = note("Alpha", scheduled = "<2025-06-11 Wed>")
        val at9 = note("Bravo", scheduled = "<2025-06-11 Wed 09:00>")
        val at14 = note("Charlie", deadline = "<2025-06-11 Wed 14:00>")

        val groups = AgendaBuckets.group(
            listOf(noTime, at9, at14), today, AgendaGrouping.DATE, isTodayTab = true,
        )
        assertEquals(1, groups.size)
        assertEquals("Scheduled today", groups[0].key)
        assertEquals(listOf("Bravo", "Charlie", "Alpha"), groups[0].notes.map { it.title })
    }

    @Test
    fun `date grouping on the Upcoming tab makes one bucket per day`() {
        val thursday = note("Thursday", scheduled = "<2025-06-12 Thu>")
        val friday = note("Friday", scheduled = "<2025-06-13 Fri>")

        val groups = AgendaBuckets.group(
            listOf(friday, thursday), today, AgendaGrouping.DATE, isTodayTab = false,
        )
        assertEquals(listOf("Tomorrow", "Friday"), groups.map { it.key })
    }

    @Test
    fun `priority grouping drops empty buckets and sorts within them`() {
        val a = note("Alpha", scheduled = "<2025-06-11 Wed>", priority = "A")
        val bLate = note("Bravo", scheduled = "<2025-06-11 Wed 14:00>", priority = "B")
        val bEarly = note("Charlie", scheduled = "<2025-06-11 Wed 08:00>", priority = "B")
        val none = note("Delta", scheduled = "<2025-06-11 Wed>")

        val groups = AgendaBuckets.group(
            listOf(a, bLate, bEarly, none), today, AgendaGrouping.PRIORITY, isTodayTab = true,
        )
        // No heading has priority C, so that bucket never appears.
        assertEquals(listOf("Priority A", "Priority B", "No priority"), groups.map { it.key })
        assertEquals(listOf("Charlie", "Bravo"), groups[1].notes.map { it.title })
    }

    @Test
    fun `tag grouping lists a heading under each of its tags and untagged otherwise`() {
        val multi = note("Multi", scheduled = "<2025-06-11 Wed>", tags = listOf("work", "urgent"))
        val bare = note("Bare", scheduled = "<2025-06-11 Wed>")

        val groups = AgendaBuckets.group(
            listOf(multi, bare), today, AgendaGrouping.TAG, isTodayTab = true,
        )
        assertEquals(listOf(":work:", ":urgent:", ":untagged:"), groups.map { it.key })
        assertEquals(listOf("Multi"), groups[0].notes.map { it.title })
        assertEquals(listOf("Bare"), groups[2].notes.map { it.title })
    }

    @Test
    fun `file grouping buckets by source file`() {
        val work = note("Work", fileName = "work.org", scheduled = "<2025-06-11 Wed>")
        val home = note("Home", fileName = "home.org", scheduled = "<2025-06-11 Wed>")

        val groups = AgendaBuckets.group(
            listOf(work, home), today, AgendaGrouping.FILE, isTodayTab = true,
        )
        assertEquals(listOf("work.org", "home.org"), groups.map { it.key })
    }

    // --- day labels ---

    @Test
    fun `day labels are relative near today and dated past a week out`() {
        assertEquals("Today", AgendaBuckets.dayLabel(today, today))
        assertEquals("Tomorrow", AgendaBuckets.dayLabel(today.plusDays(1), today))
        assertEquals("Yesterday", AgendaBuckets.dayLabel(today.minusDays(1), today))
        assertEquals("Sunday", AgendaBuckets.dayLabel(today.plusDays(4), today))
        assertEquals("Friday, Jun 20", AgendaBuckets.dayLabel(today.plusDays(9), today))
    }
}
