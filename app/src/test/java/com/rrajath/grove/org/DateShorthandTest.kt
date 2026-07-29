package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class DateShorthandTest {

    // Wednesday, so "fri" is +2 days, "mon" is +5, "weekend" (Sat) is +3.
    private val today = LocalDate.of(2026, 7, 29)

    private fun ok(raw: String): DateShorthand {
        val parse = DateShorthandParser.parse(raw, today)
        assertTrue("expected '$raw' to parse, got $parse", parse is ShorthandParse.Ok)
        return (parse as ShorthandParse.Ok).value
    }

    private fun bad(raw: String) {
        assertEquals(ShorthandParse.Unrecognised, DateShorthandParser.parse(raw, today))
    }

    @Test
    fun `blank input parses to nothing at all`() {
        assertNull(DateShorthandParser.parse("", today))
        assertNull(DateShorthandParser.parse("   ", today))
    }

    @Test
    fun `gibberish is unrecognised`() {
        bad("hello there")
        bad("zzz")
    }

    @Test
    fun `weekday resolves forward and never to today`() {
        assertEquals(LocalDate.of(2026, 7, 31), ok("fri").date)
        assertEquals(LocalDate.of(2026, 8, 3), ok("monday").date)
        // Today is a Wednesday, so "wed" means next week's.
        assertEquals(LocalDate.of(2026, 8, 5), ok("wed").date)
    }

    @Test
    fun `relative words`() {
        assertEquals(today, ok("today").date)
        assertEquals(today.plusDays(1), ok("tomorrow").date)
        assertEquals(LocalDate.of(2026, 8, 1), ok("this weekend").date)
        assertEquals(LocalDate.of(2026, 8, 3), ok("next week").date)
        assertEquals(LocalDate.of(2026, 8, 29), ok("next month").date)
    }

    @Test
    fun `offsets`() {
        assertEquals(LocalDate.of(2026, 8, 12), ok("+2w").date)
        assertEquals(LocalDate.of(2026, 8, 3), ok("+5d").date)
        assertEquals(LocalDate.of(2026, 10, 29), ok("+3m").date)
    }

    @Test
    fun `iso date wins over everything else`() {
        assertEquals(LocalDate.of(2027, 1, 2), ok("2027-01-02").date)
    }

    @Test
    fun `month and day rolls to next year once past`() {
        assertEquals(LocalDate.of(2026, 8, 3), ok("aug 3").date)
        // July 1 has already gone by on July 29.
        assertEquals(LocalDate.of(2027, 7, 1), ok("jul 1").date)
    }

    @Test
    fun `time range shares a trailing meridiem`() {
        val sh = ok("10-11am")
        assertEquals(LocalTime.of(10, 0), sh.time)
        assertEquals(LocalTime.of(11, 0), sh.endTime)
    }

    @Test
    fun `time range with explicit meridiems`() {
        val sh = ok("11am-1pm")
        assertEquals(LocalTime.of(11, 0), sh.time)
        assertEquals(LocalTime.of(13, 0), sh.endTime)
    }

    @Test
    fun `bare clock time`() {
        assertEquals(LocalTime.of(9, 30), ok("9:30").time)
        assertEquals(LocalTime.of(21, 30), ok("9:30pm").time)
        assertEquals(LocalTime.of(0, 15), ok("12:15am").time)
        assertEquals(LocalTime.of(17, 0), ok("5pm").time)
    }

    @Test
    fun `repeat cookies`() {
        assertEquals(Repeater(RepeaterType.CATCH_UP, 1, 'w'), ok("++1w").repeater)
        assertEquals(Repeater(RepeaterType.FUTURE, 3, 'd'), ok(".+3d").repeater)
        assertEquals(Repeater(RepeaterType.CUMULATIVE, 2, 'm'), ok("every 2 months").repeater)
        assertEquals(Repeater(RepeaterType.CUMULATIVE, 1, 'w'), ok("every week").repeater)
    }

    @Test
    fun `target prefixes`() {
        assertEquals(PlanningKind.DEADLINE, ok("d: mon").target)
        assertEquals(PlanningKind.DEADLINE, ok("deadline: mon").target)
        assertEquals(PlanningKind.SCHEDULED, ok("s: mon").target)
        assertEquals(PlanningKind.SCHEDULED, ok("sched: mon").target)
        assertNull(ok("mon").target)
    }

    @Test
    fun `one line carries target, date, time range and repeater`() {
        val sh = ok("d: aug 3 10-11:30am ++1w")
        assertEquals(PlanningKind.DEADLINE, sh.target)
        assertEquals(LocalDate.of(2026, 8, 3), sh.date)
        assertEquals(LocalTime.of(10, 0), sh.time)
        assertEquals(LocalTime.of(11, 30), sh.endTime)
        assertEquals(Repeater(RepeaterType.CATCH_UP, 1, 'w'), sh.repeater)
    }

    @Test
    fun `a repeater alone is enough to be understood`() {
        val sh = ok("++1w")
        assertNull(sh.date)
        assertNull(sh.time)
        assertEquals(Repeater(RepeaterType.CATCH_UP, 1, 'w'), sh.repeater)
    }

    @Test
    fun `input is case insensitive`() {
        assertEquals(LocalDate.of(2026, 8, 3), ok("AUG 3").date)
        assertEquals(LocalDate.of(2026, 7, 31), ok("Fri").date)
    }

    @Test
    fun `impossible calendar dates do not crash`() {
        // 2026 is not a leap year, so Feb 29 must fall through, not throw.
        val parse = DateShorthandParser.parse("feb 29", today)
        assertTrue(parse == ShorthandParse.Unrecognised || parse is ShorthandParse.Ok)
        bad("2026-13-45")
    }
}
