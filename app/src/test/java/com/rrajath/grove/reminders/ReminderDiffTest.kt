package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReminderDiffTest {

    private fun entity(
        key: String,
        triggerAtMillis: Long = 1_000L,
        fileName: String = "a.org",
        pendingPermission: Boolean = false,
        firedAt: Long? = null,
    ) = ReminderEntity(
        key = key,
        fileName = fileName,
        headingPath = key,
        headingTitle = key,
        headingLevel = 1,
        planningType = PlanningType.SCHEDULED.storageKey,
        triggerAtMillis = triggerAtMillis,
        notificationId = key.hashCode(),
        pendingPermission = pendingPermission,
        firedAt = firedAt,
    )

    @Test
    fun `brand new desired reminder is scheduled`() {
        val plan = ReminderDiff.diff(existing = emptyList(), desired = listOf(entity("a")))
        assertEquals(listOf("a"), plan.toSchedule.map { it.key })
        assertTrue(plan.toCancel.isEmpty())
        assertTrue(plan.unchanged.isEmpty())
    }

    @Test
    fun `reminder no longer desired is cancelled`() {
        val plan = ReminderDiff.diff(existing = listOf(entity("a")), desired = emptyList())
        assertEquals(listOf("a"), plan.toCancel.map { it.key })
        assertTrue(plan.toSchedule.isEmpty())
    }

    @Test
    fun `same key same trigger time is left unchanged`() {
        val existing = entity("a", triggerAtMillis = 500L, firedAt = 500L)
        val desired = entity("a", triggerAtMillis = 500L)
        val plan = ReminderDiff.diff(existing = listOf(existing), desired = listOf(desired))
        assertEquals(listOf(existing), plan.unchanged)
        assertTrue(plan.toSchedule.isEmpty())
        assertTrue(plan.toCancel.isEmpty())
        // The stored (fired) copy survives untouched, not silently replaced by the fresh copy.
        assertEquals(500L, plan.unchanged.single().firedAt)
    }

    @Test
    fun `same key different trigger time is rescheduled, not left alone`() {
        val existing = entity("a", triggerAtMillis = 500L)
        val desired = entity("a", triggerAtMillis = 999L)
        val plan = ReminderDiff.diff(existing = listOf(existing), desired = listOf(desired))
        assertEquals(listOf(999L), plan.toSchedule.map { it.triggerAtMillis })
        assertTrue(plan.unchanged.isEmpty())
        assertTrue(plan.toCancel.isEmpty())
    }

    @Test
    fun `a heading's SCHEDULED and DEADLINE reminders are independent in the diff`() {
        val scheduledKey = ReminderKeys.reminderKey("a.org", "A", 1, PlanningType.SCHEDULED)
        val deadlineKey = ReminderKeys.reminderKey("a.org", "A", 1, PlanningType.DEADLINE)
        val existing = listOf(entity(scheduledKey, triggerAtMillis = 100L))
        val desired = listOf(entity(scheduledKey, triggerAtMillis = 100L), entity(deadlineKey, triggerAtMillis = 200L))
        val plan = ReminderDiff.diff(existing, desired)
        assertEquals(listOf(deadlineKey), plan.toSchedule.map { it.key })
        assertEquals(listOf(scheduledKey), plan.unchanged.map { it.key })
    }

    @Test
    fun `mixed diff across many reminders`() {
        val existing = listOf(
            entity("keep", triggerAtMillis = 1L),
            entity("change", triggerAtMillis = 1L),
            entity("gone", triggerAtMillis = 1L),
        )
        val desired = listOf(
            entity("keep", triggerAtMillis = 1L),
            entity("change", triggerAtMillis = 2L),
            entity("new", triggerAtMillis = 1L),
        )
        val plan = ReminderDiff.diff(existing, desired)
        assertEquals(setOf("gone"), plan.toCancel.map { it.key }.toSet())
        assertEquals(setOf("change", "new"), plan.toSchedule.map { it.key }.toSet())
        assertEquals(setOf("keep"), plan.unchanged.map { it.key }.toSet())
    }
}
