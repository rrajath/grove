package com.rrajath.grove.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class QueryParserTest {

    @Test
    fun `plain words are ANDed text terms`() {
        val q = QueryParser.parse("ramen kyoto")
        assertEquals(1, q.groups.size)
        assertEquals(
            listOf(Condition.Text("ramen"), Condition.Text("kyoto")),
            q.groups[0].map { it.condition },
        )
        assertTrue(q.groups[0].none { it.negated })
    }

    @Test
    fun `OR splits groups`() {
        val q = QueryParser.parse("t.work OR t.home")
        assertEquals(2, q.groups.size)
        assertEquals(Condition.Tag("work", false), q.groups[0][0].condition)
        assertEquals(Condition.Tag("home", false), q.groups[1][0].condition)
    }

    @Test
    fun `dot prefix negates`() {
        val q = QueryParser.parse(".i.done t.trip")
        assertTrue(q.groups[0][0].negated)
        assertEquals(Condition.State("done"), q.groups[0][0].condition)
        assertFalse(q.groups[0][1].negated)
    }

    @Test
    fun `all operator types parse`() {
        val q = QueryParser.parse("s.today d.2d c.yesterday cr.1w i.todo b.inbox t.x tn.y p.a")
        val conditions = q.groups[0].map { it.condition }
        assertEquals(Condition.Scheduled(Period("today")), conditions[0])
        assertEquals(Condition.Deadline(Period("2d")), conditions[1])
        assertEquals(Condition.Closed(Period("yesterday")), conditions[2])
        assertEquals(Condition.Created(Period("1w")), conditions[3])
        assertEquals(Condition.State("todo"), conditions[4])
        assertEquals(Condition.Notebook("inbox"), conditions[5])
        assertEquals(Condition.Tag("x", ownOnly = false), conditions[6])
        assertEquals(Condition.Tag("y", ownOnly = true), conditions[7])
        assertEquals(Condition.Priority("a"), conditions[8])
    }

    @Test
    fun `sort and agenda tokens`() {
        val q = QueryParser.parse("i.todo o.scheduled o.priority ad.7")
        assertEquals(listOf("scheduled", "priority"), q.sortBy)
        assertEquals(7, q.agendaDays)
        assertEquals(1, q.groups.size)
    }

    @Test
    fun `words containing dots stay text`() {
        val q = QueryParser.parse("file.org v1.2")
        assertEquals(
            listOf(Condition.Text("file.org"), Condition.Text("v1.2")),
            q.groups[0].map { it.condition },
        )
    }

    @Test
    fun `empty and blank queries`() {
        assertTrue(QueryParser.parse("").isEmpty)
        assertTrue(QueryParser.parse("   ").isEmpty)
    }

    @Test
    fun `period pivots`() {
        val today = LocalDate.of(2025, 6, 11)
        assertEquals(today, Period("today").pivot(today))
        assertEquals(today, Period("now").pivot(today))
        assertEquals(today.plusDays(1), Period("tomorrow").pivot(today))
        assertEquals(today.minusDays(1), Period("yesterday").pivot(today))
        assertEquals(today.plusDays(3), Period("3d").pivot(today))
        assertEquals(today.plusWeeks(2), Period("2w").pivot(today))
        assertEquals(today.plusMonths(1), Period("1m").pivot(today))
        assertNull(Period("nonsense").pivot(today))
    }

    @Test
    fun `past pivots mirror for closed and created`() {
        val today = LocalDate.of(2025, 6, 11)
        assertEquals(today, Period("today").pastPivot(today))
        assertEquals(today.minusDays(1), Period("yesterday").pastPivot(today))
        assertEquals(today.minusDays(7), Period("1w").pastPivot(today))
    }
}
