package com.rrajath.grove.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

class ReminderDigestSchedulerTest {

    private val zone = ZoneId.of("UTC")
    private val defaultTime = LocalTime.of(9, 0)

    @Test
    fun `fires today when the default time hasn't passed yet`() {
        val now = LocalDateTime.of(2026, 7, 31, 8, 0)
        val expected = LocalDateTime.of(2026, 7, 31, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ReminderDigestScheduler.nextTriggerMillis(defaultTime, zone, now))
    }

    @Test
    fun `rolls to tomorrow once the default time has passed`() {
        val now = LocalDateTime.of(2026, 7, 31, 9, 30)
        val expected = LocalDateTime.of(2026, 8, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ReminderDigestScheduler.nextTriggerMillis(defaultTime, zone, now))
    }

    @Test
    fun `rolls to tomorrow exactly at the default time`() {
        val now = LocalDateTime.of(2026, 7, 31, 9, 0)
        val expected = LocalDateTime.of(2026, 8, 1, 9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, ReminderDigestScheduler.nextTriggerMillis(defaultTime, zone, now))
    }
}
