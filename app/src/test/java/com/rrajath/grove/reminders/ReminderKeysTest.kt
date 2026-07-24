package com.rrajath.grove.reminders

import com.rrajath.grove.org.OrgParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReminderKeysTest {

    @Test
    fun `heading path is ancestor titles then own title`() {
        val doc = OrgParser.parse("* A\n** B\n*** C\n")
        val c = doc.headlines.first { it.title == "C" }
        assertEquals("A/B/C", ReminderKeys.headingPath(doc, c))
    }

    @Test
    fun `top level heading path is just its own title`() {
        val doc = OrgParser.parse("* A\n")
        val a = doc.headlines.first()
        assertEquals("A", ReminderKeys.headingPath(doc, a))
    }

    @Test
    fun `reminder key differs by planning type so a heading can have both`() {
        val scheduledKey = ReminderKeys.reminderKey("a.org", "A", 1, PlanningType.SCHEDULED)
        val deadlineKey = ReminderKeys.reminderKey("a.org", "A", 1, PlanningType.DEADLINE)
        assertNotEquals(scheduledKey, deadlineKey)
    }

    @Test
    fun `reminder key differs by file, level, or path`() {
        val base = ReminderKeys.reminderKey("a.org", "A", 1, PlanningType.SCHEDULED)
        assertNotEquals(base, ReminderKeys.reminderKey("b.org", "A", 1, PlanningType.SCHEDULED))
        assertNotEquals(base, ReminderKeys.reminderKey("a.org", "A", 2, PlanningType.SCHEDULED))
        assertNotEquals(base, ReminderKeys.reminderKey("a.org", "X/A", 1, PlanningType.SCHEDULED))
    }

    @Test
    fun `notificationId is stable and positive`() {
        val key = ReminderKeys.reminderKey("a.org", "A", 1, PlanningType.SCHEDULED)
        val id1 = ReminderKeys.notificationId(key)
        val id2 = ReminderKeys.notificationId(key)
        assertEquals(id1, id2)
        assert(id1 >= 0)
    }

    @Test
    fun `findHeadline relocates a heading by path and level`() {
        val doc = OrgParser.parse("* A\n** B\n* C\n")
        val found = ReminderKeys.findHeadline(doc, "A/B", 2)
        assertEquals("B", found?.title)
    }

    @Test
    fun `findHeadline returns null once the heading is renamed or relevels`() {
        val doc = OrgParser.parse("* A\n** B\n")
        assertNull(ReminderKeys.findHeadline(doc, "A/Renamed", 2))
        assertNull(ReminderKeys.findHeadline(doc, "A/B", 1))
    }
}
