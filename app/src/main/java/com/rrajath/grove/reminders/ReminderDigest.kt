package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure logic (no Android imports, JVM-testable) for the daily reminder digest
 * ("You have X tasks due today") that replaces individual notifications for
 * date-only reminders (see [ReminderEntity.hasExplicitTime]) fired at the
 * default reminder time.
 *
 * X must agree with what the Agenda screen shows for the same set of tasks
 * (see `AgendaBuckets.whenDate`), so this mirrors its rule: a heading belongs
 * to exactly one day, its SCHEDULED date if it has one, otherwise its
 * DEADLINE. A heading carrying both a SCHEDULED and a DEADLINE reminder is
 * therefore collapsed to one task here too, even though the reminders table
 * tracks them as two distinct rows.
 */
object ReminderDigest {

    fun count(reminders: List<ReminderEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        fun dateOf(r: ReminderEntity): LocalDate =
            Instant.ofEpochMilli(r.triggerAtMillis).atZone(zone).toLocalDate()

        // Reminders with an explicit time-of-day fire their own "due now" notification
        // (see ReminderReconciler/ReminderAlarmReceiver); only date-only reminders belong
        // in the digest, otherwise a timed task gets counted twice.
        val dateOnly = reminders.filterNot { it.hasExplicitTime }

        // One SCHEDULED and one DEADLINE row can both belong to the same heading;
        // collapse them to that heading's single anchor date before counting.
        val anchorDates = dateOnly
            .groupBy { Triple(it.fileName, it.headingPath, it.headingLevel) }
            .mapNotNull { (_, entries) ->
                val scheduled = entries.firstOrNull { it.planningType == PlanningType.SCHEDULED.storageKey }
                val deadline = entries.firstOrNull { it.planningType == PlanningType.DEADLINE.storageKey }
                (scheduled ?: deadline)?.let(::dateOf)
            }

        return anchorDates.count { !it.isAfter(today) }
    }
}
