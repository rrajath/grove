package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Pure logic (no Android imports, JVM-testable) for the daily reminder digest
 * ("You have X tasks due today"), fired at the default reminder time.
 *
 * X must agree with what the Agenda screen shows for today (overdue + due
 * today), so this mirrors its rule: a heading belongs to exactly one day, its
 * SCHEDULED date if it has one, otherwise its DEADLINE. A heading carrying
 * both a SCHEDULED and a DEADLINE reminder is therefore collapsed to one task
 * here too, even though the reminders table tracks them as two distinct rows.
 */
object ReminderDigest {

    fun count(reminders: List<ReminderEntity>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int {
        fun dateOf(r: ReminderEntity): LocalDate =
            Instant.ofEpochMilli(r.triggerAtMillis).atZone(zone).toLocalDate()

        // One SCHEDULED and one DEADLINE row can both belong to the same heading;
        // collapse them to that heading's single anchor so this picks the same
        // anchor AgendaBuckets.whenDate would: a heading whose SCHEDULED carries
        // a time-of-day still anchors there, and a DEADLINE must not stand in for
        // it, or the heading gets counted here on a date it would never appear
        // under in the Agenda.
        val anchors = reminders
            .groupBy { Triple(it.fileName, it.headingPath, it.headingLevel) }
            .mapNotNull { (_, entries) ->
                val scheduled = entries.firstOrNull { it.planningType == PlanningType.SCHEDULED.storageKey }
                val deadline = entries.firstOrNull { it.planningType == PlanningType.DEADLINE.storageKey }
                scheduled ?: deadline
            }

        // Every overdue anchor plus every anchor due today, regardless of
        // whether it carries a specific time-of-day (see [ReminderEntity.hasExplicitTime]).
        // A timed anchor due today is counted here *and* still fires its own
        // "due now" notification later at its time (ReminderAlarmReceiver) -
        // that's intentional, not a double-count bug, so this total always
        // matches what the Agenda screen shows for today.
        return anchors.count { !dateOf(it).isAfter(today) }
    }
}
