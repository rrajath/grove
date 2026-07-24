package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgTimestamp
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * Pure logic (no Android imports, JVM-testable): which reminders a notebook's
 * headlines should have, and when each should fire.
 */
object ReminderPlanning {

    /**
     * The trigger instant for [ts]: its own time-of-day if it has one, else
     * [defaultReminderTime] on its date (Settings › Reminders › "Default
     * reminder time", for date-only SCHEDULED/DEADLINE stamps).
     */
    fun triggerAtMillis(ts: OrgTimestamp, defaultReminderTime: LocalTime, zone: ZoneId = ZoneId.systemDefault()): Long {
        val time = ts.time ?: defaultReminderTime
        return LocalDateTime.of(ts.date, time).atZone(zone).toInstant().toEpochMilli()
    }

    /**
     * Every reminder [fileName]'s headlines should have: one per non-done
     * heading's SCHEDULED and/or DEADLINE timestamp. Returns an empty list when
     * reminders are disabled, so callers can feed this straight into
     * [ReminderDiff] to cancel everything that previously existed for the file.
     */
    fun desiredReminders(
        fileName: String,
        doc: OrgDocument,
        defaultReminderTime: LocalTime,
        remindersEnabled: Boolean,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEntity> {
        if (!remindersEnabled) return emptyList()
        val result = mutableListOf<ReminderEntity>()
        doc.headlines.forEach { h ->
            if (h.keyword != null && doc.keywords.isDone(h.keyword)) return@forEach
            val path = ReminderKeys.headingPath(doc, h)
            h.planning.scheduled?.let { ts ->
                result.add(entity(fileName, path, h.title, h.level, PlanningType.SCHEDULED, ts, defaultReminderTime, zone))
            }
            h.planning.deadline?.let { ts ->
                result.add(entity(fileName, path, h.title, h.level, PlanningType.DEADLINE, ts, defaultReminderTime, zone))
            }
        }
        return result
    }

    private fun entity(
        fileName: String,
        headingPath: String,
        headingTitle: String,
        level: Int,
        type: PlanningType,
        ts: OrgTimestamp,
        defaultReminderTime: LocalTime,
        zone: ZoneId,
    ): ReminderEntity {
        val key = ReminderKeys.reminderKey(fileName, headingPath, level, type)
        return ReminderEntity(
            key = key,
            fileName = fileName,
            headingPath = headingPath,
            headingTitle = headingTitle,
            headingLevel = level,
            planningType = type.storageKey,
            triggerAtMillis = triggerAtMillis(ts, defaultReminderTime, zone),
            notificationId = ReminderKeys.notificationId(key),
        )
    }
}
