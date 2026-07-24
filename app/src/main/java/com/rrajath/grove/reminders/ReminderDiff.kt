package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderEntity

/**
 * Result of diffing a file's previously stored reminders against what its
 * headlines currently call for.
 */
data class ReminderPlan(
    /** No longer wanted: cancel the alarm and drop the row. */
    val toCancel: List<ReminderEntity>,
    /** New or changed trigger time: (re)schedule the alarm and upsert the row. */
    val toSchedule: List<ReminderEntity>,
    /** Same key, same trigger time: leave the stored row and its alarm alone. */
    val unchanged: List<ReminderEntity>,
)

/** Pure diff logic (JVM-testable) behind reminder reconciliation. */
object ReminderDiff {

    fun diff(existing: List<ReminderEntity>, desired: List<ReminderEntity>): ReminderPlan {
        val desiredByKey = desired.associateBy { it.key }
        val existingByKey = existing.associateBy { it.key }

        val toCancel = existing.filter { it.key !in desiredByKey }
        val toSchedule = mutableListOf<ReminderEntity>()
        val unchanged = mutableListOf<ReminderEntity>()
        desired.forEach { d ->
            val e = existingByKey[d.key]
            if (e == null || e.triggerAtMillis != d.triggerAtMillis) {
                toSchedule.add(d)
            } else {
                unchanged.add(e)
            }
        }
        return ReminderPlan(toCancel, toSchedule, unchanged)
    }
}
