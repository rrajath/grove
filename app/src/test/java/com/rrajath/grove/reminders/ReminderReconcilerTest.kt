package com.rrajath.grove.reminders

import com.rrajath.grove.data.ReminderDao
import com.rrajath.grove.data.ReminderEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JVM regression tests for the scheduling logic in [ReminderReconciler.arm], with
 * the Android side-effects (notification/alarm) injected as recording lambdas and
 * an in-memory fake [ReminderDao]. Covers:
 *  - finding 2: boot re-arm must not re-fire an already-fired reminder;
 *  - finding 4: the overdue branch must always cancel any stale alarm first.
 */
class ReminderReconcilerTest {

    private val now = 10_000L

    private class FakeReminderDao : ReminderDao {
        val rows = LinkedHashMap<String, ReminderEntity>()
        override suspend fun forFile(fileName: String) = rows.values.filter { it.fileName == fileName }
        override suspend fun all() = rows.values.toList()
        override suspend fun get(key: String) = rows[key]
        override suspend fun pending() = rows.values.filter { it.pendingPermission }
        override suspend fun overdueUnfired(now: Long) =
            rows.values.filter { !it.pendingPermission && it.firedAt == null && it.triggerAtMillis <= now }
        override fun pendingCountFlow(now: Long): Flow<Int> =
            flowOf(rows.values.count { it.pendingPermission && it.triggerAtMillis > now })
        override suspend fun upsert(reminder: ReminderEntity) { rows[reminder.key] = reminder }
        override suspend fun markFired(key: String, firedAt: Long) {
            rows[key]?.let { rows[key] = it.copy(firedAt = firedAt) }
        }
        override suspend fun delete(key: String) { rows.remove(key) }
        override suspend fun clearAll() { rows.clear() }
    }

    private fun entity(
        key: String,
        triggerAtMillis: Long,
        firedAt: Long? = null,
        pendingPermission: Boolean = false,
    ) = ReminderEntity(
        key = key,
        fileName = "a.org",
        headingPath = key,
        headingTitle = key,
        headingLevel = 1,
        planningType = PlanningType.SCHEDULED.storageKey,
        triggerAtMillis = triggerAtMillis,
        notificationId = ReminderKeys.notificationId(key),
        pendingPermission = pendingPermission,
        firedAt = firedAt,
    )

    private class Recorder {
        val notified = mutableListOf<String>()
        val scheduled = mutableListOf<String>()
        val cancelled = mutableListOf<String>()
    }

    private fun reconciler(dao: ReminderDao, rec: Recorder) = ReminderReconciler(
        context = null,
        dao = dao,
        clock = { now },
        hasPermission = { true },
        notify = { rec.notified += it.key },
        scheduleAlarm = { rec.scheduled += it.key },
        cancelAlarm = { rec.cancelled += it.key },
    )

    @Test
    fun `boot rearm does not re-fire an already-fired overdue reminder`() = runTest {
        val dao = FakeReminderDao()
        dao.rows["k"] = entity("k", triggerAtMillis = 1_000L, firedAt = 1_500L)
        val rec = Recorder()

        reconciler(dao, rec).rearmAll()

        assertTrue("must not re-show a fired reminder", rec.notified.isEmpty())
        assertTrue("must not schedule an overdue reminder", rec.scheduled.isEmpty())
        assertEquals("firedAt must be preserved, not re-stamped", 1_500L, dao.rows["k"]!!.firedAt)
    }

    @Test
    fun `catch-up still fires a genuinely-unfired overdue reminder`() = runTest {
        val dao = FakeReminderDao()
        dao.rows["k"] = entity("k", triggerAtMillis = 1_000L, firedAt = null)
        val rec = Recorder()

        reconciler(dao, rec).catchUpOverdue()

        assertEquals(listOf("k"), rec.notified)
        assertEquals(now, dao.rows["k"]!!.firedAt)
    }

    @Test
    fun `overdue branch cancels any stale alarm before showing`() = runTest {
        // A reschedule-into-the-past reaches arm() with an unfired, now-overdue entity;
        // the previously-armed future alarm for this key must be cancelled so it can't
        // fire again later as a duplicate.
        val dao = FakeReminderDao()
        dao.rows["k"] = entity("k", triggerAtMillis = 1_000L, firedAt = null)
        val rec = Recorder()

        reconciler(dao, rec).catchUpOverdue()

        assertEquals(listOf("k"), rec.cancelled)
        assertEquals(listOf("k"), rec.notified)
    }

    @Test
    fun `future reminder is scheduled, not fired`() = runTest {
        val dao = FakeReminderDao()
        dao.rows["k"] = entity("k", triggerAtMillis = now + 5_000L)
        val rec = Recorder()

        reconciler(dao, rec).rearmAll()

        assertEquals(listOf("k"), rec.scheduled)
        assertTrue(rec.notified.isEmpty())
        assertNull(dao.rows["k"]!!.firedAt)
    }

    @Test
    fun `granting permission settles overdue-pending reminders instead of firing them`() = runTest {
        val dao = FakeReminderDao()
        dao.rows["overdue"] = entity("overdue", triggerAtMillis = now - 5_000L, pendingPermission = true)
        dao.rows["future"] = entity("future", triggerAtMillis = now + 5_000L, pendingPermission = true)
        val rec = Recorder()

        reconciler(dao, rec).reconcilePending()

        assertTrue("must not fire a notification for a reminder overdue before permission was granted", rec.notified.isEmpty())
        assertEquals("only the future reminder should be scheduled", listOf("future"), rec.scheduled)
        assertEquals("overdue reminder must be settled (no longer pending)", false, dao.rows["overdue"]!!.pendingPermission)
        assertEquals("overdue reminder must be marked handled so catch-up doesn't pick it up later", now, dao.rows["overdue"]!!.firedAt)
        assertEquals(false, dao.rows["future"]!!.pendingPermission)
    }
}
