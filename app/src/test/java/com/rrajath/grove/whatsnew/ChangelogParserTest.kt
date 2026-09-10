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
    fun `shippedReleases on the real CHANGELOG_md has no Unreleased and no repeated version`() {
        val text = File("../CHANGELOG.md").let { if (it.exists()) it else File("CHANGELOG.md") }.readText()
        val releases = ChangelogParser.shippedReleases(text)
        assertTrue("should produce at least one release", releases.isNotEmpty())
        assertTrue("capped at the screen limit", releases.size <= ChangelogParser.WHATS_NEW_SCREEN_LIMIT)
        assertTrue("Unreleased must be dropped", releases.none { it.title == "Unreleased" })
        assertTrue("every release must have a versionCode", releases.all { it.versionCode != null })
        assertEquals(
            "consecutive same-version headings (the seven 1.5.0 blocks) must be merged",
            releases.map { it.title }.zipWithNext().count { (a, b) -> a == b },
            0,
        )
        assertTrue("each release keeps at least one non-empty category", releases.all { r ->
            r.subsections.any { it.items.isNotEmpty() }
        })
        assertEquals("1.5.0", releases.first().title)
    }

    @Test
    fun `parses the project's real CHANGELOG_md without crashing and finds versions`() {
        // Regression against the parser silently drifting from the real file's format.
        val text = File("../CHANGELOG.md").let { if (it.exists()) it else File("CHANGELOG.md") }.readText()
        val versions = ChangelogParser.parse(text)
        assertTrue("should find at least one numbered version", versions.any { it.versionCode != null })
        assertTrue(
            "every subsection heading should be a real Keep-a-Changelog category (or \"\" for uncategorised bullets)",
            versions.flatMap { it.subsections }.all {
                it.heading in setOf("", "Added", "Changed", "Fixed", "Removed", "Deprecated", "Security")
            },
        )
    }

    @Test
    fun `bullets with no category heading are kept under an empty heading, not dropped`() {
        val text = """
            ## [1.3.0] - 2026-09-03

            - A plain uncategorised change
            - Another one that wraps onto a
              continuation line

            ## [1.2.0] - 2026-09-01

            ### Fixed
            - A categorised fix
        """.trimIndent()
        val versions = ChangelogParser.parse(text)
        assertEquals(listOf(""), versions[0].subsections.map { it.heading })
        assertEquals(
            listOf("A plain uncategorised change", "Another one that wraps onto a continuation line"),
            versions[0].subsections[0].items,
        )
    }

    @Test
    fun `parses the ISO date from a version heading and strips a build suffix`() {
        val text = """
            ## [1.3.0] - 2026-09-03

            - Change

            ## [1.0.3] - 2026-08-28 (build 316)

            ### Fixed
            - Old fix

            ## [Unreleased]

            - Pending
        """.trimIndent()
        val versions = ChangelogParser.parse(text).associateBy { it.title }
        assertEquals("2026-09-03", versions["1.3.0"]?.date)
        assertEquals("2026-08-28", versions["1.0.3"]?.date)
        assertEquals(null, versions["Unreleased"]?.date)
    }

    @Test
    fun `shippedReleases drops Unreleased and merges same-version headings`() {
        val text = """
            ## [Unreleased]

            - Not shipped yet

            ## [1.5.0] - 2026-09-09

            - Fixed: a CI-only thing

            ## [1.5.0] - 2026-09-07

            ### Added
            - A real feature

            ### Fixed
            - A real fix

            ## [1.4.0] - 2026-09-04

            ### Added
            - Older feature
        """.trimIndent()
        val releases = ChangelogParser.shippedReleases(text)
        assertEquals(listOf("1.5.0", "1.4.0"), releases.map { it.title })

        val v150 = releases[0]
        assertEquals("2026-09-09", v150.date)
        // Categories ordered Added, Fixed, then the uncategorised bullets last.
        assertEquals(listOf("Added", "Fixed", ""), v150.subsections.map { it.heading })
        assertEquals(listOf("A real feature"), v150.subsections[0].items)
        assertEquals(listOf("A real fix"), v150.subsections[1].items)
        assertEquals(listOf("Fixed: a CI-only thing"), v150.subsections[2].items)
    }
}
