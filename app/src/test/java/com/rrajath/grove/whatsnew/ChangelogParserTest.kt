package com.rrajath.grove.whatsnew

import org.junit.Assert.assertEquals
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

        ## [1.2.7] - 2026-08-04

        ### Added
        - Added thing

        ### Fixed
        - Fixed thing

        ## [1.2.1] - 2026-08-04

        ### Fixed
        - Older fixed thing
    """.trimIndent()

    // Entries archived in mid-2026 carry an explicit "(build N)" suffix (N = the git-commit-count
    // versionCode of that era). Kept as a regression guard: the suffix must still be read verbatim
    // and win over anything computed from the title.
    private val sampleWithBuildSuffix = """
        ## [Unreleased]

        ### Fixed
        - First unreleased fix

        ## [1.0.1] - 2026-08-10 (build 300)

        ### Added
        - Newest thing

        ## [1.0.0] - 2026-08-04 (build 262)

        ### Fixed
        - First 1.0.0 release
    """.trimIndent()

    @Test
    fun `parses version headings, subsections, and bullets`() {
        val versions = ChangelogParser.parse(sample)
        assertEquals(listOf("Unreleased", "1.2.7", "1.2.1"), versions.map { it.title })
        // versionCode = MAJOR*10000 + MINOR*100 + PATCH, computed from the title.
        assertEquals(listOf(null, 10207, 10201), versions.map { it.versionCode })

        val unreleased = versions[0]
        assertEquals(1, unreleased.subsections.size)
        assertEquals("Fixed", unreleased.subsections[0].heading)
        assertEquals(
            listOf("First unreleased fix that wraps onto a continuation line", "Second unreleased fix"),
            unreleased.subsections[0].items,
        )

        val v127 = versions[1]
        assertEquals(listOf("Added", "Fixed"), v127.subsections.map { it.heading })
    }

    @Test
    fun `parses an explicit build suffix instead of computing from the title`() {
        val versions = ChangelogParser.parse(sampleWithBuildSuffix)
        assertEquals(listOf("Unreleased", "1.0.1", "1.0.0"), versions.map { it.title })
        // Not 10001 and 10000 (computed from the titles) — the explicit "(build N)" wins.
        assertEquals(listOf(null, 300, 262), versions.map { it.versionCode })
    }

    @Test
    fun `entriesSince returns everything newer than the last seen code, stopping at it`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenCode = 10201)
        assertEquals(listOf("Unreleased", "1.2.7"), result.map { it.title })
    }

    @Test
    fun `entriesSince with a null last-seen code returns the full history`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenCode = null)
        assertEquals(listOf("Unreleased", "1.2.7", "1.2.1"), result.map { it.title })
    }

    @Test
    fun `entriesSince returns nothing once already caught up to the newest code`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenCode = 10207)
        // "Unreleased" (versionCode == null) is always newer than any seen numbered version.
        assertEquals(listOf("Unreleased"), result.map { it.title })
    }

    @Test
    fun `a legacy explicit build suffix still bounds entriesSince below a computed code`() {
        // The migration boundary in the real file: a device that last saw the "(build 304)"
        // release of 1.0.2 updates to a new scheme where VERSION_CODE is 10003. Everything above
        // the "(build 304)" entry is newer; that entry itself is the stopping point.
        val text = """
            ## [Unreleased]

            ## [1.0.3] - 2026-08-27

            ### Fixed
            - New scheme release

            ## [1.0.2] - 2026-08-26 (build 304)

            ### Fixed
            - Old scheme release
        """.trimIndent()
        val result = ChangelogParser.entriesSince(text, lastSeenCode = 304)
        assertEquals(listOf("1.0.3"), result.map { it.title })
    }

    @Test
    fun `an empty Unreleased section right after a release cut is dropped, not shown as a blank entry`() {
        val text = """
            ## [Unreleased]

            ## [1.2.7] - 2026-08-04

            ### Fixed
            - Fixed thing
        """.trimIndent()
        val result = ChangelogParser.entriesSince(text, lastSeenCode = 10201)
        assertEquals(listOf("1.2.7"), result.map { it.title })
    }

    @Test
    fun `parses the project's real CHANGELOG_md without crashing and finds versions`() {
        // Regression against the parser silently drifting from the real file's format.
        val text = File("../CHANGELOG.md").let { if (it.exists()) it else File("CHANGELOG.md") }.readText()
        val versions = ChangelogParser.parse(text)
        assertTrue("should find at least one numbered version", versions.any { it.versionCode != null })
        assertTrue(
            "every subsection heading should be a real Keep-a-Changelog category",
            versions.flatMap { it.subsections }.all { it.heading in setOf("Added", "Changed", "Fixed", "Removed", "Deprecated", "Security") },
        )
    }
}
