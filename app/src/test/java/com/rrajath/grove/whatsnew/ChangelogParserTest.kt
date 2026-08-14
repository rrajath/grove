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

        ## [1.0.187] - 2026-08-04

        ### Added
        - Added thing

        ### Fixed
        - Fixed thing

        ## [1.0.179] - 2026-08-04

        ### Fixed
        - Older fixed thing
    """.trimIndent()

    // Post-migration entries carry an explicit "(build N)" suffix instead of encoding the
    // versionCode in the title itself (versionName is now manually bumped, see build.gradle.kts).
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
        assertEquals(listOf("Unreleased", "1.0.187", "1.0.179"), versions.map { it.title })
        assertEquals(listOf(null, 187, 179), versions.map { it.buildNumber })

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
    fun `parses an explicit build suffix instead of falling back to the title`() {
        val versions = ChangelogParser.parse(sampleWithBuildSuffix)
        assertEquals(listOf("Unreleased", "1.0.1", "1.0.0"), versions.map { it.title })
        // Not 1 and 0 (the titles' trailing segments) — the explicit "(build N)" wins.
        assertEquals(listOf(null, 300, 262), versions.map { it.buildNumber })
    }

    @Test
    fun `entriesSince returns everything newer than the last seen build, stopping at it`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenBuild = 179)
        assertEquals(listOf("Unreleased", "1.0.187"), result.map { it.title })
    }

    @Test
    fun `entriesSince with a null last-seen build returns the full history`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenBuild = null)
        assertEquals(listOf("Unreleased", "1.0.187", "1.0.179"), result.map { it.title })
    }

    @Test
    fun `entriesSince returns nothing once already caught up to the newest build`() {
        val result = ChangelogParser.entriesSince(sample, lastSeenBuild = 187)
        // "Unreleased" (buildNumber == null) is always newer than any seen numbered build.
        assertEquals(listOf("Unreleased"), result.map { it.title })
    }

    @Test
    fun `entriesSince still works when versionName repeats across releases`() {
        // Two releases both titled "1.0.0" (versionName not bumped between them) are only
        // distinguishable by their build number — this is exactly the case the switch to
        // versionCode-based tracking exists to handle.
        val text = """
            ## [Unreleased]

            ## [1.0.0] - 2026-08-11 (build 264)

            ### Fixed
            - Second 1.0.0 release

            ## [1.0.0] - 2026-08-04 (build 262)

            ### Fixed
            - First 1.0.0 release
        """.trimIndent()
        val result = ChangelogParser.entriesSince(text, lastSeenBuild = 262)
        assertEquals(1, result.size)
        assertEquals("Second 1.0.0 release", result[0].subsections[0].items[0])
    }

    @Test
    fun `an empty Unreleased section right after a release cut is dropped, not shown as a blank entry`() {
        val text = """
            ## [Unreleased]

            ## [1.0.187] - 2026-08-04

            ### Fixed
            - Fixed thing
        """.trimIndent()
        val result = ChangelogParser.entriesSince(text, lastSeenBuild = 179)
        assertEquals(listOf("1.0.187"), result.map { it.title })
    }

    @Test
    fun `parses the project's real CHANGELOG_md without crashing and finds versions`() {
        // Regression against the parser silently drifting from the real file's format.
        val text = File("../CHANGELOG.md").let { if (it.exists()) it else File("CHANGELOG.md") }.readText()
        val versions = ChangelogParser.parse(text)
        assertTrue("should find at least one numbered version", versions.any { it.buildNumber != null })
        assertTrue(
            "every subsection heading should be a real Keep-a-Changelog category",
            versions.flatMap { it.subsections }.all { it.heading in setOf("Added", "Changed", "Fixed", "Removed", "Deprecated", "Security") },
        )
    }
}
