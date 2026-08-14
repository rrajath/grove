package com.rrajath.grove.whatsnew

/** One `### Added`/`### Fixed`/etc. block under a version heading. */
data class ChangelogSubsection(val heading: String, val items: List<String>)

/**
 * One `## [Unreleased]` or `## [1.0.0] - <date> (build <N>)` block. [buildNumber] is null for
 * Unreleased, otherwise the versionCode the release was cut at — the only value still guaranteed
 * unique and monotonically increasing across releases now that versionName is a manually-bumped
 * SemVer string that can repeat (see CHANGELOG.md's "Versioning" section). Entries archived before
 * that switch have no "(build N)" suffix; their title itself was "1.0.<versionCode>", so the
 * trailing segment is recovered as a fallback.
 */
data class ChangelogVersion(val title: String, val buildNumber: Int?, val subsections: List<ChangelogSubsection>)

/**
 * Parses `CHANGELOG.md`'s Keep-a-Changelog-style structure (see the file's own header comment)
 * into a What's New modal's data. Pure Kotlin, no Android imports, so it's JVM-testable directly
 * against the real file.
 */
object ChangelogParser {
    private val versionHeading = Regex("""^## \[(.+?)](?: - .+)?$""")
    private val buildSuffix = Regex("""\(build (\d+)\)\s*$""")
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
            title?.let { versions.add(ChangelogVersion(it, explicitBuild ?: legacyBuildNumber(it), subsections.toList())) }
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
     * Sections strictly newer than [lastSeenBuild] (a versionCode), in the file's existing
     * newest-first order, with empty sections (e.g. a just-cut, still-empty "Unreleased") dropped.
     * Returns everything when [lastSeenBuild] is null — the caller decides what null means for its
     * situation (e.g. suppress on a fresh install).
     */
    fun entriesSince(text: String, lastSeenBuild: Int?): List<ChangelogVersion> {
        val all = parse(text).filter { it.subsections.any { s -> s.items.isNotEmpty() } }
        lastSeenBuild ?: return all
        return all.takeWhile { it.buildNumber == null || it.buildNumber > lastSeenBuild }
    }

    // Pre-migration entries titled "1.0.<versionCode>" (see CHANGELOG.md's "Versioning" section):
    // the trailing segment was the versionCode itself, so it doubles as the build number.
    private fun legacyBuildNumber(title: String): Int? =
        if (title == "Unreleased") null else title.substringAfterLast('.').toIntOrNull()
}
