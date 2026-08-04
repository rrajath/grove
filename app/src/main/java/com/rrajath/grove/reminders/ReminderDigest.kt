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

        // One SCHEDULED and one DEADLINE row can both belong to the same heading;
        // collapse them to that heading's single anchor *before* deciding whether
        // it belongs in the digest, from the full reminder set (not just the
        // date-only ones) so this picks the same anchor AgendaBuckets.whenDate
        // would: a heading whose SCHEDULED carries a time-of-day still anchors
        // there even though that row fires its own "due now" notification
        // instead of going in the digest - a date-only DEADLINE on the same
        // heading must not stand in for it, or the heading gets counted here on
        // a date it would never appear under in the Agenda.
        val anchors = reminders
            .groupBy { Triple(it.fileName, it.headingPath, it.headingLevel) }
            .mapNotNull { (_, entries) ->
                val scheduled = entries.firstOrNull { it.planningType == PlanningType.SCHEDULED.storageKey }
                val deadline = entries.firstOrNull { it.planningType == PlanningType.DEADLINE.storageKey }
                scheduled ?: deadline
            }

        // Reminders with an explicit time-of-day fire their own "due now" notification
        // (see ReminderReconciler/ReminderAlarmReceiver); only a date-only anchor belongs
        // in the digest, otherwise a timed task gets counted twice.
        return anchors.count { !it.hasExplicitTime && !dateOf(it).isAfter(today) }
    }
}
