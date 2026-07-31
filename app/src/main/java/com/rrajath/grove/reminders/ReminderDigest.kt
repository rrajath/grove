package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure logic (no Android imports, JVM-testable) for the daily reminder digest
 * — "You have X tasks due today" — that replaces individual notifications for
 * date-only reminders (see [ReminderEntity.hasExplicitTime]) fired at the
 * default reminder time.
 *
 * X sums three buckets rather than counting each heading once: overdue +
 * scheduled-today + deadline-today. A heading carrying both a SCHEDULED and a
 * DEADLINE landing on the same day is counted in both buckets, since the
 * reminders table already tracks them as two distinct entries.
 */
object ReminderDigest {

    fun count(reminders: List<ReminderEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        fun dateOf(r: ReminderEntity): LocalDate =
            Instant.ofEpochMilli(r.triggerAtMillis).atZone(zone).toLocalDate()

        val overdue = reminders.count { dateOf(it).isBefore(today) }
        val scheduledToday = reminders.count {
            it.planningType == PlanningType.SCHEDULED.storageKey && dateOf(it) == today
        }
        val deadlineToday = reminders.count {
            it.planningType == PlanningType.DEADLINE.storageKey && dateOf(it) == today
        }
        return overdue + scheduledToday + deadlineToday
    }
}
