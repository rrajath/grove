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
        val q = QueryParser.parse("s.today d.2d a.7d c.yesterday cr.1w i.todo b.inbox t.x tn.y p.a")
        val conditions = q.groups[0].map { it.condition }
        assertEquals(Condition.Scheduled(Period("today")), conditions[0])
        assertEquals(Condition.Deadline(Period("2d")), conditions[1])
        assertEquals(Condition.Active(Period("7d")), conditions[2])
        assertEquals(Condition.Closed(Period("yesterday")), conditions[3])
        assertEquals(Condition.Created(Period("1w")), conditions[4])
        assertEquals(Condition.State("todo"), conditions[5])
        assertEquals(Condition.Notebook("inbox"), conditions[6])
        assertEquals(Condition.Tag("x", ownOnly = false), conditions[7])
        assertEquals(Condition.Tag("y", ownOnly = true), conditions[8])
        assertEquals(Condition.Priority("a"), conditions[9])
    }

    @Test
    fun `sort and agenda tokens`() {
        val q = QueryParser.parse("i.todo o.scheduled o.priority ad.7")
        assertEquals(listOf(SortKey(SortField.SCHEDULED), SortKey(SortField.PRIORITY)), q.sortBy)
        assertEquals(7, q.agendaDays)
        assertEquals(1, q.groups.size)
    }

    // --- nesting ---

    private fun SearchQuery.groupStrings(): List<String> = groups.map { g ->
        g.joinToString(" ") { t -> (if (t.negated) "." else "") + t.condition.short() }
    }

    private fun Condition.short(): String = when (this) {
        is Condition.Text -> term
        is Condition.State -> "i.$state"
        is Condition.StateType -> "it.${type.name.lowercase()}"
        is Condition.Notebook -> "b.$name"
        is Condition.Tag -> "t.$tag"
        is Condition.Priority -> "p.$priority"
        is Condition.Scheduled -> "s.${period.raw}"
        is Condition.Closed -> "c.${period.op?.name?.lowercase()?.plus(".") ?: ""}${period.raw}"
        else -> toString()
    }

    @Test
    fun `brackets nest and distribute AND over OR`() {
        val q = QueryParser.parse("i.todo (s.today OR p.A)")
        assertEquals(listOf("i.todo s.today", "i.todo p.A"), q.groupStrings())
    }

    @Test
    fun `two levels of nesting with explicit AND, it and comparison`() {
        val q = QueryParser.parse("b.shopping t.tigros AND (it.todo OR (it.done AND c.eq.today)) o.p o.st o.t")
        assertEquals(
            listOf("b.shopping t.tigros it.todo", "b.shopping t.tigros it.done c.eq.today"),
            q.groupStrings(),
        )
        assertEquals(
            listOf(SortKey(SortField.PRIORITY), SortKey(SortField.STATE), SortKey(SortField.TITLE)),
            q.sortBy,
        )
    }

    @Test
    fun `brackets attached to words are split off`() {
        assertEquals(listOf("a b", "c"), QueryParser.parse("(a b)OR(c)").groupStrings())
    }

    @Test
    fun `negated group applies De Morgan`() {
        assertEquals(listOf(".t.work .t.home"), QueryParser.parse(".(t.work OR t.home)").groupStrings())
        assertEquals(listOf(".t.work", ".t.home"), QueryParser.parse(".(t.work t.home)").groupStrings())
        assertEquals(listOf("t.work"), QueryParser.parse(".(.t.work)").groupStrings())
    }

    @Test
    fun `unbalanced and empty brackets are lenient`() {
        assertEquals(listOf("a b", "a c"), QueryParser.parse("a (b OR c").groupStrings())
        assertEquals(listOf("a b"), QueryParser.parse("a) b)").groupStrings())
        assertEquals(listOf("a"), QueryParser.parse("a OR ()").groupStrings())
        assertTrue(QueryParser.parse("()").isEmpty)
        assertTrue(QueryParser.parse("( OR )").isEmpty)
    }

    @Test
    fun `lowercase or and and stay text`() {
        assertEquals(listOf("a or and b"), QueryParser.parse("a or and b").groupStrings())
    }

    @Test
    fun `oversized flattening keeps the tree but skips groups`() {
        val input = (1..7).joinToString(" ") { "(x$it OR y$it)" }
        val q = QueryParser.parse(input)
        assertTrue(q.isFlatteningSkipped)
        assertTrue(q.groups.isEmpty())
        assertFalse(q.isEmpty)
        assertEquals(14, q.textTerms.size)
    }

    // --- it., comparisons, sort keys ---

    @Test
    fun `it tokens parse, unknown values stay text`() {
        val q = QueryParser.parse("it.todo it.DONE it.none it.later")
        assertEquals(
            listOf(
                Condition.StateType(Condition.StateType.Type.TODO),
                Condition.StateType(Condition.StateType.Type.DONE),
                Condition.StateType(Condition.StateType.Type.NONE),
                Condition.Text("it.later"),
            ),
            q.groups[0].map { it.condition },
        )
    }

    @Test
    fun `comparison operators parse on every date prefix`() {
        val q = QueryParser.parse("s.le.today d.gt.3d a.eq.tomorrow c.ge.-1w cr.ne.yesterday s.today")
        assertEquals(
            listOf(
                Condition.Scheduled(Period("today", CompareOp.LE)),
                Condition.Deadline(Period("3d", CompareOp.GT)),
                Condition.Active(Period("tomorrow", CompareOp.EQ)),
                Condition.Closed(Period("-1w", CompareOp.GE)),
                Condition.Created(Period("yesterday", CompareOp.NE)),
                Condition.Scheduled(Period("today")),
            ),
            q.groups[0].map { it.condition },
        )
    }

    @Test
    fun `compare targets count forward or back`() {
        val today = LocalDate.of(2025, 6, 11)
        assertEquals(today.plusDays(3), Period("3d").compareTarget(today, past = false))
        assertEquals(today.minusDays(3), Period("3d").compareTarget(today, past = true))
        assertEquals(today.minusDays(3), Period("-3d").compareTarget(today, past = false))
        assertEquals(today.plusWeeks(1), Period("+1w").compareTarget(today, past = true))
        assertEquals(today, Period("today").compareTarget(today, past = true))
        assertNull(Period("overdue").compareTarget(today, past = false))
    }

    @Test
    fun `Orgzly sort aliases and reversed sort`() {
        val q = QueryParser.parse("o.book o.sched .o.dead o.e o.close o.cr o.pri .o.state o.title o.bogus")
        assertEquals(
            listOf(
                SortKey(SortField.NOTEBOOK),
                SortKey(SortField.SCHEDULED),
                SortKey(SortField.DEADLINE, descending = true),
                SortKey(SortField.EVENT),
                SortKey(SortField.CLOSED),
                SortKey(SortField.CREATED),
                SortKey(SortField.PRIORITY),
                SortKey(SortField.STATE, descending = true),
                SortKey(SortField.TITLE),
            ),
            q.sortBy,
        )
        assertTrue(q.groups.isEmpty())
    }

    @Test
    fun `sort and agenda modifiers inside brackets stay global`() {
        val q = QueryParser.parse("(o.t t.x) OR (t.y .o.p ad.3)")
        assertEquals(listOf("t.x", "t.y"), q.groupStrings())
        assertEquals(listOf(SortKey(SortField.TITLE), SortKey(SortField.PRIORITY, descending = true)), q.sortBy)
        assertEquals(3, q.agendaDays)
    }

    @Test
    fun `a group holding only modifiers contributes no branch`() {
        // Otherwise "t.x OR (o.p)" would gain an empty, match-everything group.
        assertEquals(listOf("t.x"), QueryParser.parse("t.x OR (o.p)").groupStrings())
    }

    @Test
    fun `deep nesting flattens correctly`() {
        val q = QueryParser.parse("a (b (c (d OR e)))")
        assertEquals(listOf("a b c d", "a b c e"), q.groupStrings())
    }

    @Test
    fun `odd inputs parse without crashing`() {
        assertTrue(QueryParser.parse("AND").isEmpty)
        assertTrue(QueryParser.parse("OR OR").isEmpty)
        assertTrue(QueryParser.parse(".(").isEmpty)
        assertTrue(QueryParser.parse(")))(((").isEmpty)
        assertEquals(listOf("a", "b"), QueryParser.parse("a AND OR b").groupStrings())
        assertEquals(listOf("a b"), QueryParser.parse("AND a AND b AND").groupStrings())
        // A lone "." is text, and ". (a)" (with a space) is not a group negation.
        assertEquals(listOf(". a"), QueryParser.parse(". (a)").groupStrings())
        // ".." before a bracket isn't the negation marker either.
        assertEquals(listOf(".. a"), QueryParser.parse("..(a)").groupStrings())
    }

    @Test
    fun `malformed comparisons fall back to plain periods`() {
        assertEquals(Condition.Scheduled(Period("eq.")), QueryParser.parse("s.eq.").groups[0][0].condition)
        assertEquals(Condition.Scheduled(Period("foo.today")), QueryParser.parse("s.foo.today").groups[0][0].condition)
        assertEquals(Condition.Scheduled(Period("today", CompareOp.LE)), QueryParser.parse("s.LE.today").groups[0][0].condition)
        // With an operator, none/nodate/overdue aren't the special window tokens.
        val p = Period.parse("eq.none")
        assertFalse(p.isNoDate)
        assertFalse(Period.parse("le.overdue").isOverdue)
    }

    @Test
    fun `text terms come from the tree without duplicates from distribution`() {
        val q = QueryParser.parse("milk (eggs OR bread) .cheese")
        assertEquals(listOf("milk", "eggs", "bread"), q.textTerms)
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

    @Test
    fun `nodate, overdue and exact-day tokens`() {
        val today = LocalDate.of(2025, 6, 11)
        assertTrue(Period("nodate").isNoDate)
        assertTrue(Period("none").isNoDate)
        assertFalse(Period("today").isNoDate)
        assertTrue(Period("overdue").isOverdue)
        assertFalse(Period("today").isOverdue)
        assertEquals(today, Period("today").exactDate(today))
        assertEquals(today.plusDays(1), Period("tomorrow").exactDate(today))
        assertEquals(today.minusDays(1), Period("yesterday").exactDate(today))
        assertNull(Period("3d").exactDate(today))
    }
}
