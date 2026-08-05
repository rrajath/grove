package com.rrajath.grove.whatsnew

/** One `### Added`/`### Fixed`/etc. block under a version heading. */
data class ChangelogSubsection(val heading: String, val items: List<String>)

/** One `## [Unreleased]` or `## [1.0.<N>] - <date>` block. [versionNumber] is null for Unreleased. */
data class ChangelogVersion(val title: String, val versionNumber: Int?, val subsections: List<ChangelogSubsection>)

/**
 * Parses `CHANGELOG.md`'s Keep-a-Changelog-style structure (see the file's own header comment)
 * into a What's New modal's data. Pure Kotlin, no Android imports, so it's JVM-testable directly
 * against the real file.
 */
object ChangelogParser {
    private val versionHeading = Regex("""^## \[(.+?)](?: - .+)?$""")
    private val subHeading = Regex("""^### (.+)$""")
    private val bulletStart = Regex("""^- (.+)$""")

    fun parse(text: String): List<ChangelogVersion> {
        val versions = mutableListOf<ChangelogVersion>()
        var title: String? = null
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
            title?.let { versions.add(ChangelogVersion(it, versionNumber(it), subsections.toList())) }
            title = null
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
     * Sections strictly newer than [lastSeenVersion] down through [currentVersion] (inclusive),
     * in the file's existing newest-first order, with empty sections (e.g. a just-cut, still-empty
     * "Unreleased") dropped. Both version strings tolerate a build suffix like "-debug"; only the
     * numeric commit-count segment is compared. Returns everything when [lastSeenVersion] is null
     * — the caller decides what null means for its situation (e.g. suppress on a fresh install).
     */
    fun entriesSince(text: String, lastSeenVersion: String?, currentVersion: String): List<ChangelogVersion> {
        val all = parse(text).filter { it.subsections.any { s -> s.items.isNotEmpty() } }
        val lastSeenN = lastSeenVersion?.let { versionNumber(stripBuildSuffix(it)) } ?: return all
        return all.takeWhile { it.versionNumber == null || it.versionNumber > lastSeenN }
    }

    private fun versionNumber(title: String): Int? =
        if (title == "Unreleased") null else title.substringAfterLast('.').toIntOrNull()

    private fun stripBuildSuffix(version: String): String = version.substringBefore('-')
}
