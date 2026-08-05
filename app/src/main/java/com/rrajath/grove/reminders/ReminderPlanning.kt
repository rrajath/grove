package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.settings.ReminderLeadTime
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
     * reminder time", for date-only SCHEDULED/DEADLINE stamps). [leadTime]
     * pulls that instant earlier (Settings › Reminders › "Notify me"), but
     * only when [ts] carries its own time-of-day: date-only stamps don't fire
     * an individual notification at all (they bundle into the daily digest),
     * so shifting their trigger would only misplace which day's digest they
     * land in for no benefit.
     */
    fun triggerAtMillis(
        ts: OrgTimestamp,
        defaultReminderTime: LocalTime,
        zone: ZoneId = ZoneId.systemDefault(),
        leadTime: ReminderLeadTime = ReminderLeadTime.AT_TIME,
    ): Long {
        val time = ts.time ?: defaultReminderTime
        val base = LocalDateTime.of(ts.date, time).atZone(zone).toInstant().toEpochMilli()
        val offsetMillis = if (ts.time != null) leadTime.offsetMinutes * 60_000L else 0L
        return base - offsetMillis
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
        leadTime: ReminderLeadTime = ReminderLeadTime.AT_TIME,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<ReminderEntity> {
        if (!remindersEnabled) return emptyList()
        val result = mutableListOf<ReminderEntity>()
        doc.headlines.forEach { h ->
            if (h.keyword != null && doc.keywords.isDone(h.keyword)) return@forEach
            val path = ReminderKeys.headingPath(doc, h)
            h.planning.scheduled?.let { ts ->
                result.add(entity(fileName, path, h.title, h.level, PlanningType.SCHEDULED, ts, defaultReminderTime, leadTime, zone))
            }
            h.planning.deadline?.let { ts ->
                result.add(entity(fileName, path, h.title, h.level, PlanningType.DEADLINE, ts, defaultReminderTime, leadTime, zone))
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
        leadTime: ReminderLeadTime,
        zone: ZoneId,
    ): ReminderEntity {
        val key = ReminderKeys.reminderKey(fileName, headingPath, level, type)
        val hasExplicitTime = ts.time != null
        return ReminderEntity(
            key = key,
            fileName = fileName,
            headingPath = headingPath,
            headingTitle = headingTitle,
            headingLevel = level,
            planningType = type.storageKey,
            triggerAtMillis = triggerAtMillis(ts, defaultReminderTime, zone, leadTime),
            notificationId = ReminderKeys.notificationId(key),
            hasExplicitTime = hasExplicitTime,
            leadTime = (if (hasExplicitTime) leadTime else ReminderLeadTime.AT_TIME).storageKey,
        )
    }
}
