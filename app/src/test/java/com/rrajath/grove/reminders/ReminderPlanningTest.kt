package com.rrajath.grove.reminders

import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderPlanningTest {

    private val zone = ZoneId.of("UTC")
    private val nineAm = LocalTime.of(9, 0)

    @Test
    fun `timestamp with its own time uses that time, ignoring the default`() {
        val ts = OrgTimestamp(LocalDate.of(2026, 7, 24), time = LocalTime.of(14, 30))
        val expected = LocalDateTime.of(2026, 7, 24, 14, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ReminderPlanning.triggerAtMillis(ts, nineAm, zone))
    }

    @Test
    fun `date-only timestamp falls back to the default reminder time`() {
        val ts = OrgTimestamp(LocalDate.of(2026, 7, 24))
        val expected = LocalDateTime.of(2026, 7, 24, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ReminderPlanning.triggerAtMillis(ts, nineAm, zone))
    }

    @Test
    fun `a heading with both SCHEDULED and DEADLINE gets two independent reminders`() {
        val doc = OrgParser.parse("* TODO A\nSCHEDULED: <2026-07-24 Fri> DEADLINE: <2026-07-25 Sat>\n")
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = true, zone = zone)
        assertEquals(2, result.size)
        assertTrue(result.any { it.planningType == PlanningType.SCHEDULED.storageKey })
        assertTrue(result.any { it.planningType == PlanningType.DEADLINE.storageKey })
        assertTrue(result.all { it.headingTitle == "A" && it.fileName == "a.org" })
    }

    @Test
    fun `done headings are skipped`() {
        val doc = OrgParser.parse("* DONE A\nSCHEDULED: <2026-07-24 Fri>\n")
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = true, zone = zone)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `headings with no planning produce no reminders`() {
        val doc = OrgParser.parse("* TODO A\nJust a body line.\n")
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = true, zone = zone)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `disabled reminders yields nothing regardless of planning`() {
        val doc = OrgParser.parse("* TODO A\nSCHEDULED: <2026-07-24 Fri>\n")
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = false, zone = zone)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `custom done keywords are honored`() {
        val keywords = OrgKeywords.parse("TODO NEXT | DONE CANCELLED")
        val doc = OrgParser.parse("* CANCELLED A\nSCHEDULED: <2026-07-24 Fri>\n", keywords)
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = true, zone = zone)
        assertEquals(emptyList<Any>(), result)
    }

    @Test
    fun `hasExplicitTime reflects whether the timestamp carries a time-of-day`() {
        val doc = OrgParser.parse(
            "* TODO A\nSCHEDULED: <2026-07-24 Fri>\n" +
                "* TODO B\nSCHEDULED: <2026-07-24 Fri 14:30>\n"
        )
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = true, zone = zone)
        assertEquals(false, result.single { it.headingTitle == "A" }.hasExplicitTime)
        assertEquals(true, result.single { it.headingTitle == "B" }.hasExplicitTime)
    }

    @Test
    fun `nested headings key by their ancestor path`() {
        val doc = OrgParser.parse("* Project\n** TODO Sub task\nSCHEDULED: <2026-07-24 Fri>\n")
        val result = ReminderPlanning.desiredReminders("a.org", doc, nineAm, remindersEnabled = true, zone = zone)
        assertEquals(1, result.size)
        assertEquals(
            ReminderKeys.reminderKey("a.org", "Project/Sub task", 2, PlanningType.SCHEDULED),
            result.single().key,
        )
    }
}
