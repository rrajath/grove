package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderDigestTest {

    private val zone = ZoneId.of("UTC")
    private val today = LocalDate.of(2026, 7, 31)

    private fun entity(
        key: String,
        date: LocalDate,
        type: PlanningType,
        time: java.time.LocalTime = java.time.LocalTime.of(9, 0),
        hasExplicitTime: Boolean = false,
        headingPath: String = key,
    ) = ReminderEntity(
        key = key,
        fileName = "a.org",
        headingPath = headingPath,
        headingTitle = headingPath,
        headingLevel = 1,
        planningType = type.storageKey,
        triggerAtMillis = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli(),
        notificationId = ReminderKeys.notificationId(key),
        hasExplicitTime = hasExplicitTime,
    )

    @Test
    fun `counts overdue plus scheduled-today plus deadline-today`() {
        val reminders = listOf(
            entity("overdue1", today.minusDays(2), PlanningType.SCHEDULED),
            entity("overdue2", today.minusDays(1), PlanningType.DEADLINE),
            entity("sched-today", today, PlanningType.SCHEDULED),
            entity("deadline-today", today, PlanningType.DEADLINE),
            entity("future", today.plusDays(3), PlanningType.SCHEDULED),
        )
        assertEquals(4, ReminderDigest.count(reminders, today, zone))
    }

    @Test
    fun `distinct headings scheduled and due today each count once`() {
        val reminders = listOf(
            entity("sched-today", today, PlanningType.SCHEDULED),
            entity("deadline-today", today, PlanningType.DEADLINE),
        )
        assertEquals(2, ReminderDigest.count(reminders, today, zone))
    }

    @Test
    fun `a single heading with both SCHEDULED and DEADLINE due today counts once, matching Agenda`() {
        val reminders = listOf(
            entity("task-sched", today, PlanningType.SCHEDULED, headingPath = "task"),
            entity("task-deadline", today, PlanningType.DEADLINE, headingPath = "task"),
        )
        assertEquals(1, ReminderDigest.count(reminders, today, zone))
    }

    @Test
    fun `a single heading overdue on both SCHEDULED and DEADLINE counts once`() {
        val reminders = listOf(
            entity("task-sched", today.minusDays(3), PlanningType.SCHEDULED, headingPath = "task"),
            entity("task-deadline", today.minusDays(1), PlanningType.DEADLINE, headingPath = "task"),
        )
        assertEquals(1, ReminderDigest.count(reminders, today, zone))
    }

    @Test
    fun `empty table counts zero`() {
        assertEquals(0, ReminderDigest.count(emptyList(), today, zone))
    }

    @Test
    fun `future-only reminders count zero`() {
        val reminders = listOf(entity("future", today.plusDays(1), PlanningType.SCHEDULED))
        assertEquals(0, ReminderDigest.count(reminders, today, zone))
    }

    @Test
    fun `reminders with an explicit time are excluded, they fire their own notification`() {
        val reminders = listOf(
            entity("timed-overdue", today.minusDays(1), PlanningType.SCHEDULED, hasExplicitTime = true),
            entity("timed-today", today, PlanningType.DEADLINE, hasExplicitTime = true),
            entity("date-only-today", today, PlanningType.SCHEDULED),
        )
        assertEquals(1, ReminderDigest.count(reminders, today, zone))
    }
}
