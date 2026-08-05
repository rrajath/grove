package com.rrajath.grove.reminders

import android.content.Context
import com.rrajath.grove.data.ReminderDao
import com.rrajath.grove.data.ReminderEntity
import com.rrajath.grove.org.OrgDocument
import java.time.LocalTime

/**
 * Android-side orchestration around [ReminderPlanning]/[ReminderDiff]: keeps
 * the `reminders` table and the actual `AlarmManager` alarms in sync.
 *
 * Two entry points, split by whether they need the vault:
 *  - [reconcileFile]/[reconcileAll] recompute desired reminders from a parsed
 *    [OrgDocument]: used right after a notebook (re)parses, or when
 *    reminders/the default reminder time setting changes.
 *  - [catchUpOverdue]/[rearmAll]/[reconcilePending] only touch the DB + alarms
 *    (no vault access), so they're cheap to call on app start, after a sync,
 *    and from `BOOT_COMPLETED`.
 */
class ReminderReconciler(
    private val context: Context?,
    private val dao: ReminderDao,
    private val clock: () -> Long = System::currentTimeMillis,
    // Android side-effects behind lambda seams so the scheduling logic in arm()/
    // applyPlan() is JVM-unit-testable with a fake dao and no Context. Production
    // uses the defaults (which delegate to the AlarmManager/notification objects).
    private val hasPermission: () -> Boolean = { AlarmScheduler.hasNotificationPermission(context!!) },
    // Date-only reminders (no explicit time-of-day) don't fire their own
    // notification; they're counted into the daily digest instead.
    private val notify: (ReminderEntity) -> Unit = { if (it.hasExplicitTime) ReminderNotification.show(context!!, it) },
    private val scheduleAlarm: (ReminderEntity) -> Unit = { AlarmScheduler.schedule(context!!, it) },
    private val cancelAlarm: (ReminderEntity) -> Unit = { AlarmScheduler.cancel(context!!, it) },
) {

    /** Recompute and apply the diff for one file's current headlines. */
    suspend fun reconcileFile(
        fileName: String,
        doc: OrgDocument,
        defaultReminderTime: LocalTime,
        remindersEnabled: Boolean,
    ) {
        val existing = dao.forFile(fileName)
        val desired = ReminderPlanning.desiredReminders(fileName, doc, defaultReminderTime, remindersEnabled)
        applyPlan(ReminderDiff.diff(existing, desired))
    }

    /** [reconcileFile] for every file in [documents] (fileName → parsed doc). */
    suspend fun reconcileAll(documents: Map<String, OrgDocument>, defaultReminderTime: LocalTime, remindersEnabled: Boolean) {
        documents.forEach { (fileName, doc) -> reconcileFile(fileName, doc, defaultReminderTime, remindersEnabled) }
    }

    /** Settings › Reminders toggled off: cancel every alarm and drop the table. */
    suspend fun disableAll() {
        dao.all().forEach { cancelAlarm(it) }
        dao.clearAll()
    }

    /**
     * Re-run scheduling for reminders that were waiting on a permission. Only
     * reminders whose trigger time is still in the future get scheduled; ones
     * that went overdue while permission was missing are settled silently
     * instead of firing as a flood of stale notifications the moment access is
     * granted (the user only wants notifications for what's ahead, not backlog).
     */
    suspend fun reconcilePending() {
        val now = clock()
        dao.pending().forEach { entity ->
            if (entity.triggerAtMillis <= now) {
                dao.upsert(entity.copy(pendingPermission = false, firedAt = now))
            } else {
                arm(entity)
            }
        }
    }

    /** DB-only safety net: fire anything overdue that hasn't shown its notification yet. */
    suspend fun catchUpOverdue() {
        dao.overdueUnfired(clock()).forEach { arm(it) }
    }

    /** `BOOT_COMPLETED`: AlarmManager alarms don't survive a reboot; re-arm everything stored. */
    suspend fun rearmAll() {
        dao.all().forEach { arm(it) }
    }

    private suspend fun applyPlan(plan: ReminderPlan) {
        plan.toCancel.forEach { entity ->
            cancelAlarm(entity)
            dao.delete(entity.key)
        }
        // notifyIfOverdue = false: a toSchedule entry is either brand new or just had its
        // trigger time changed by this reconcile (e.g. the user set a SCHEDULED/DEADLINE
        // to a past date/time). There's no prior period during which this exact trigger
        // was armed and missed, so unlike catchUpOverdue()/rearmAll() (genuine "the device
        // was off and this should have fired" catch-up), it must not fire a notification.
        plan.toSchedule.forEach { arm(it, notifyIfOverdue = false) }
        // plan.unchanged: leave the row and its alarm exactly as they are.
    }

    /**
     * Persist [entity] and either fire it now (already overdue) or schedule its
     * alarm, only if permitted; otherwise mark it pending so a permission
     * banner can prompt the user and [reconcilePending] can pick it up later.
     */
    private suspend fun arm(entity: ReminderEntity, notifyIfOverdue: Boolean = true) {
        val permitted = hasPermission()
        if (!permitted) {
            dao.upsert(entity.copy(pendingPermission = true, firedAt = null))
            return
        }
        if (entity.triggerAtMillis <= clock()) {
            // Always clear any previously-armed alarm for this reminder first: on a
            // reschedule-into-the-past the old future alarm shares this key's
            // PendingIntent and would otherwise still fire later (duplicate).
            cancelAlarm(entity)
            // Skip re-showing an already-fired reminder (e.g. BOOT_COMPLETED re-arming
            // a fired-but-not-completed reminder via rearmAll() → dao.all()). The
            // catchUpOverdue()/reconcilePending() paths pass entities with firedAt == null
            // by construction, so genuinely-unfired overdue reminders still fire.
            if (entity.firedAt != null) {
                dao.upsert(entity.copy(pendingPermission = false))
                return
            }
            dao.upsert(entity.copy(pendingPermission = false))
            if (notifyIfOverdue) notify(entity)
            dao.markFired(entity.key, clock())
        } else {
            dao.upsert(entity.copy(pendingPermission = false, firedAt = null))
            scheduleAlarm(entity)
        }
    }
}
