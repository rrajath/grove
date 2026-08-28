package com.rrajath.grove.whatsnew

/** One `### Added`/`### Fixed`/etc. block under a version heading. */
data class ChangelogSubsection(val heading: String, val items: List<String>)

/**
 * One `## [Unreleased]` or `## [1.0.3] - <date>` block. [versionCode] is null for Unreleased,
 * otherwise the numeric versionCode the release shipped as: the version title parsed as
 * `MAJOR.MINOR.PATCH` into `MAJOR*10000 + MINOR*100 + PATCH` (so "1.2.3" -> 10203), matching
 * `BuildConfig.VERSION_CODE` (see `app/build.gradle.kts`).
 *
 * Two kinds of historical entry are handled specially:
 *  - Entries archived in mid-2026 carry a legacy `(build N)` suffix (N = the git-commit-count
 *    versionCode of that era); when present it is read verbatim and wins over the title.
 *  - Pre-1.0.0 entries titled `1.0.<commit-count>` parse the same way as any other version; their
 *    computed value is only ever compared against a last-seen versionCode for ordering, and for
 *    any realistic last-seen value it never changes which entries the What's New modal shows.
 */
data class ChangelogVersion(val title: String, val versionCode: Int?, val subsections: List<ChangelogSubsection>)

/**
 * Parses `CHANGELOG.md`'s Keep-a-Changelog-style structure (see the file's own header comment)
 * into a What's New modal's data. Pure Kotlin, no Android imports, so it's JVM-testable directly
 * against the real file.
 */
object ChangelogParser {
    private val versionHeading = Regex("""^## \[(.+?)](?: - .+)?$""")
    private val buildSuffix = Regex("""\(build (\d+)\)\s*$""")
    private val semver = Regex("""^(\d+)\.(\d+)\.(\d+)$""")
    private val subHeading = Regex("""^### (.+)$""")
    private val bulletStart = Regex("""^- (.+)$""")

    fun parse(text: String): List<ChangelogVersion> {
        val versions = mutableListOf<ChangelogVersion>()
        var title: String? = null
        var explicitBuild: Int? = null
        var subsections = mutableListOf<ChangelogSubsection>()
        var subHeadingText: String? = null
        var items = mutableListOf<StringBuilder>()

        fun flushSubsection() {
            subHeadingText?.let { subsections.add(ChangelogSubsection(it, items.map { i -> i.toString().trim() })) }
            subHeadingText = null
            items = mutableListOf()
        }
        fun flushVersion() {
            flushSubsection()
            title?.let { versions.add(ChangelogVersion(it, explicitBuild ?: versionCodeFromTitle(it), subsections.toList())) }
            title = null
            explicitBuild = null
            subsections = mutableListOf()
        }

        for (line in text.lines()) {
            val versionMatch = versionHeading.find(line)
            val subMatch = subHeading.find(line)
            val bulletMatch = bulletStart.find(line)
            when {
                versionMatch != null -> {
                    flushVersion()
                    title = versionMatch.groupValues[1]
                    explicitBuild = buildSuffix.find(line)?.groupValues?.get(1)?.toIntOrNull()
                }
                title == null -> Unit // prose before the first heading (format explainer, etc.)
                subMatch != null -> {
                    flushSubsection()
                    subHeadingText = subMatch.groupValues[1]
                }
                bulletMatch != null -> items.add(StringBuilder(bulletMatch.groupValues[1]))
                // Bullet continuation line (indented wrap); blank lines and anything before the
                // first bullet of a subsection are ignored.
                line.isNotBlank() && items.isNotEmpty() && (line.startsWith("  ") || line.startsWith("\t")) ->
                    items.last().append(' ').append(line.trim())
            }
        }
        flushVersion()
        return versions
    }

    /**
     * Sections strictly newer than [lastSeenCode] (a versionCode), in the file's existing
     * newest-first order, with empty sections (e.g. a just-cut, still-empty "Unreleased") dropped.
     * Returns everything when [lastSeenCode] is null — the caller decides what null means for its
     * situation (e.g. suppress on a fresh install).
     */
    fun entriesSince(text: String, lastSeenCode: Int?): List<ChangelogVersion> {
        val all = parse(text).filter { it.subsections.any { s -> s.items.isNotEmpty() } }
        lastSeenCode ?: return all
        return all.takeWhile { it.versionCode == null || it.versionCode > lastSeenCode }
    }

    // "MAJOR.MINOR.PATCH" -> MAJOR*10000 + MINOR*100 + PATCH, matching app/build.gradle.kts.
    // Null for anything that isn't a three-part numeric version (e.g. "Unreleased").
    private fun versionCodeFromTitle(title: String): Int? =
        semver.find(title)?.destructured?.let { (major, minor, patch) ->
            major.toInt() * 10000 + minor.toInt() * 100 + patch.toInt()
        }
}
