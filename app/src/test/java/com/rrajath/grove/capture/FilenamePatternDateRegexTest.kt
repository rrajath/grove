package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class FilenamePatternDateRegexTest {

    @Test
    fun `parseDate reads a plain iso pattern`() {
        assertEquals(
            LocalDate.of(2026, 9, 23),
            FilenamePattern.parseDate("2026-09-23.org", "%<%Y-%m-%d>.org"),
        )
    }

    @Test
    fun `parseDate reads a compact pattern with a literal suffix`() {
        assertEquals(
            LocalDate.of(2026, 9, 23),
            FilenamePattern.parseDate("20260923-standup.org", "%<%Y%m%d>-standup.org"),
        )
    }

    @Test
    fun `parseDate rejects a non-matching file name`() {
        assertNull(FilenamePattern.parseDate("notes.org", "%<%Y-%m-%d>.org"))
    }

    @Test
    fun `parseDate rejects an impossible calendar date`() {
        // Feb 30 doesn't exist; the regex matches the digits but LocalDate.of must reject it.
        assertNull(FilenamePattern.parseDate("2026-02-30.org", "%<%Y-%m-%d>.org"))
    }

    @Test
    fun `toDateRegex is null when the pattern has no day token`() {
        assertNull(FilenamePattern.toDateRegex("%<%Y-%m>.org"))
        assertNull(FilenamePattern.toDateRegex("plain.org"))
    }

    @Test
    fun `toDateRegex escapes literal regex-special characters`() {
        val regex = FilenamePattern.toDateRegex("%<%Y-%m-%d>+notes.org")
        assertEquals(LocalDate.of(2026, 9, 23), FilenamePattern.parseDate("2026-09-23+notes.org", "%<%Y-%m-%d>+notes.org"))
        assertNull(regex?.find("2026-09-23Xnotes.org"))
    }

    @Test
    fun `toDateRegex is null when the pattern contains the slug token`() {
        assertNull(FilenamePattern.toDateRegex("%<%Y-%m-%d>-%(slug).org"))
    }

    @Test
    fun `compileDatePattern reuses one parser across many file names`() {
        val parser = FilenamePattern.compileDatePattern("%<%Y-%m-%d>.org")!!
        assertEquals(LocalDate.of(2026, 9, 23), parser.parse("2026-09-23.org"))
        assertEquals(LocalDate.of(2025, 1, 2), parser.parse("2025-01-02.org"))
        assertNull(parser.parse("notes.org"))
        assertNull(parser.parse("2026-02-30.org"))
        assertNull(FilenamePattern.compileDatePattern("%<%Y-%m>.org"))
    }
}
