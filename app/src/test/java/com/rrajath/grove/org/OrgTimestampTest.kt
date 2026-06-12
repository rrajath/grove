package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class OrgTimestampTest {

    @Test
    fun `parses active date`() {
        val ts = OrgTimestamp.parse("<2025-04-30 Wed>")!!
        assertEquals(LocalDate.of(2025, 4, 30), ts.date)
        assertTrue(ts.active)
        assertNull(ts.time)
        assertNull(ts.repeater)
    }

    @Test
    fun `parses inactive datetime`() {
        val ts = OrgTimestamp.parse("[2025-06-11 Wed 14:32]")!!
        assertFalse(ts.active)
        assertEquals(LocalTime.of(14, 32), ts.time)
    }

    @Test
    fun `parses time range`() {
        val ts = OrgTimestamp.parse("<2025-05-01 Thu 10:00-11:30>")!!
        assertEquals(LocalTime.of(10, 0), ts.time)
        assertEquals(LocalTime.of(11, 30), ts.endTime)
    }

    @Test
    fun `parses repeaters of all types`() {
        assertEquals(
            Repeater(RepeaterType.CUMULATIVE, 1, 'w'),
            OrgTimestamp.parse("<2025-04-30 Wed +1w>")!!.repeater,
        )
        assertEquals(
            Repeater(RepeaterType.CATCH_UP, 2, 'd'),
            OrgTimestamp.parse("<2025-04-30 Wed ++2d>")!!.repeater,
        )
        assertEquals(
            Repeater(RepeaterType.FUTURE, 3, 'm'),
            OrgTimestamp.parse("<2025-04-30 Wed .+3m>")!!.repeater,
        )
    }

    @Test
    fun `parses repeater with warning`() {
        val ts = OrgTimestamp.parse("<2025-05-01 Thu 10:00-11:30 ++2d -1d>")!!
        assertEquals(Repeater(RepeaterType.CATCH_UP, 2, 'd'), ts.repeater)
        assertEquals("-1d", ts.warning)
    }

    @Test
    fun `parses without day name`() {
        val ts = OrgTimestamp.parse("<2025-04-30>")!!
        assertEquals(LocalDate.of(2025, 4, 30), ts.date)
    }

    @Test
    fun `rejects garbage and mismatched brackets`() {
        assertNull(OrgTimestamp.parse("no timestamp here"))
        assertNull(OrgTimestamp.parse("<2025-13-45 Xxx>"))
        assertNull(OrgTimestamp.parse("<2025-04-30 Wed]"))
    }

    @Test
    fun `format emits canonical org form`() {
        assertEquals("<2025-04-30 Wed>", OrgTimestamp.parse("<2025-04-30 Wed>")!!.format())
        assertEquals("[2025-06-11 Wed 14:32]", OrgTimestamp.parse("[2025-06-11 Wed 14:32]")!!.format())
        assertEquals(
            "<2025-05-01 Thu 10:00-11:30 ++2d -1d>",
            OrgTimestamp.parse("<2025-05-01 Thu 10:00-11:30 ++2d -1d>")!!.format(),
        )
        // Day name corrected to the actual weekday
        assertEquals("<2025-04-30 Wed>", OrgTimestamp.parse("<2025-04-30 Mon>")!!.format())
    }

    @Test
    fun `cumulative repeater advances by one interval`() {
        val ts = OrgTimestamp.parse("<2025-04-30 Wed +1w>")!!
        val advanced = ts.advanceRepeater(today = LocalDate.of(2025, 6, 1))
        assertEquals(LocalDate.of(2025, 5, 7), advanced.date)
    }

    @Test
    fun `catch-up repeater jumps past today`() {
        val ts = OrgTimestamp.parse("<2025-04-30 Wed ++1w>")!!
        val advanced = ts.advanceRepeater(today = LocalDate.of(2025, 6, 1))
        assertTrue(advanced.date.isAfter(LocalDate.of(2025, 6, 1)))
        // Stays on the same weekday
        assertEquals(ts.date.dayOfWeek, advanced.date.dayOfWeek)
        assertEquals(LocalDate.of(2025, 6, 4), advanced.date)
    }

    @Test
    fun `future repeater shifts from today`() {
        val ts = OrgTimestamp.parse("<2025-04-30 Wed .+2d>")!!
        val advanced = ts.advanceRepeater(today = LocalDate.of(2025, 6, 1))
        assertEquals(LocalDate.of(2025, 6, 3), advanced.date)
    }

    @Test
    fun `non-repeating timestamp is unchanged`() {
        val ts = OrgTimestamp.parse("<2025-04-30 Wed>")!!
        assertEquals(ts, ts.advanceRepeater(LocalDate.of(2025, 6, 1)))
    }

    @Test
    fun `parseWithRange reports the matched span`() {
        val text = "SCHEDULED: <2025-04-09 Wed> trailing"
        val (ts, range) = OrgTimestamp.parseWithRange(text)!!
        assertNotNull(ts)
        assertEquals("<2025-04-09 Wed>", text.substring(range))
    }
}
