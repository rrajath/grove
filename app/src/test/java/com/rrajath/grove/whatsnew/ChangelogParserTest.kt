package com.rrajath.grove.whatsnew

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ChangelogParserTest {

    private val sample = """
        # Changelog

        Some prose explaining the format that must not be mistaken for a version.

        ## [Unreleased]

        ### Fixed
        - First unreleased fix that wraps onto a
          continuation line
        - Second unreleased fix

        ## [1.0.187] - 2026-08-04

        ### Added
        - Added thing

        ### Fixed
        - Fixed thing

        ## [1.0.179] - 2026-08-04

        ### Fixed
        - Older fixed thing
    """.trimIndent()

    @Test
    fun `parses version headings, subsections, and bullets`() {
        val versions = ChangelogParser.parse(sample)
        assertEquals(listOf("Unreleased", "1.0.187", "1.0.179"), versions.map { it.title })
        assertEquals(listOf(null, 187, 179), versions.map { it.versionNumber })

        val unreleased = versions[0]
        assertEquals(1, unreleased.subsections.size)
        assertEquals("Fixed", unreleased.subsections[0].heading)
        assertEquals(
            listOf("First unreleased fix that wraps onto a continuation line", "Second unreleased fix"),
            unreleased.subsections[0].items,
        )

        val v187 = versions[1]
        assertEquals(listOf("Added", "Fixed"), v187.subsections.map { it.heading })
    }

    @Test
    fun `entriesSince returns everything newer than the last seen version, stopping at it`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenVersion = "1.0.179", currentVersion = "1.0.187")
        assertEquals(listOf("Unreleased", "1.0.187"), result.map { it.title })
    }

    @Test
    fun `entriesSince tolerates a debug build suffix on the last seen version`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenVersion = "1.0.179-debug", currentVersion = "1.0.187")
        assertEquals(listOf("Unreleased", "1.0.187"), result.map { it.title })
    }

    @Test
    fun `entriesSince with a null last-seen version returns the full history`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenVersion = null, currentVersion = "1.0.187")
        assertEquals(listOf("Unreleased", "1.0.187", "1.0.179"), result.map { it.title })
    }

    @Test
    fun `entriesSince returns nothing once already caught up to the newest version`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenVersion = "1.0.187", currentVersion = "1.0.187")
        // "Unreleased" (versionNumber == null) is always newer than any seen numbered version.
        assertEquals(listOf("Unreleased"), result.map { it.title })
    }

    @Test
    fun `an empty Unreleased section right after a release cut is dropped, not shown as a blank entry`() {
        val text = """
            ## [Unreleased]

            ## [1.0.187] - 2026-08-04

            ### Fixed
            - Fixed thing
        """.trimIndent()
        val result = ChangelogParser.entriesSince(text, lastSeenVersion = "1.0.179", currentVersion = "1.0.187")
        assertEquals(listOf("1.0.187"), result.map { it.title })
    }

    @Test
    fun `parses the project's real CHANGELOG_md without crashing and finds versions`() {
        // Regression against the parser silently drifting from the real file's format.
        val text = File("../CHANGELOG.md").let { if (it.exists()) it else File("CHANGELOG.md") }.readText()
        val versions = ChangelogParser.parse(text)
        assertTrue("should find at least one numbered version", versions.any { it.versionNumber != null })
        assertTrue(
            "every subsection heading should be a real Keep-a-Changelog category",
            versions.flatMap { it.subsections }.all { it.heading in setOf("Added", "Changed", "Fixed", "Removed", "Deprecated", "Security") },
        )
    }
}
