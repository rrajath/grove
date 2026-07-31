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
    ) = ReminderEntity(
        key = key,
        fileName = "a.org",
        headingPath = key,
        headingTitle = key,
        headingLevel = 1,
        planningType = type.storageKey,
        triggerAtMillis = LocalDateTime.of(date, time).atZone(zone).toInstant().toEpochMilli(),
        notificationId = ReminderKeys.notificationId(key),
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
    fun `a heading scheduled and due today counts in both buckets`() {
        val reminders = listOf(
            entity("sched-today", today, PlanningType.SCHEDULED),
            entity("deadline-today", today, PlanningType.DEADLINE),
        )
        assertEquals(2, ReminderDigest.count(reminders, today, zone))
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
}
