package com.rrajath.grove.ui.editor

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoSaveTimestampTest {

    @Test
    fun `formats time as 24h HH-mm-ss`() {
        assertEquals("14:03:07", AutoSaveTimestamp.format(LocalTime.of(14, 3, 7)))
    }

    @Test
    fun `pads single-digit hour minute and second with zeros`() {
        assertEquals("00:05:09", AutoSaveTimestamp.format(LocalTime.of(0, 5, 9)))
    }

    @Test
    fun `formats midday and just-before-midnight times`() {
        assertEquals("12:00:00", AutoSaveTimestamp.format(LocalTime.NOON))
        assertEquals("23:59:59", AutoSaveTimestamp.format(LocalTime.of(23, 59, 59)))
    }
}
