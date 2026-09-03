package com.rrajath.grove.search

/**
 * Matches a search query's plain-text terms against notebook file names /
 * vault-relative paths, so a file can be surfaced by its name even when none of
 * its notes contain the text (PRD §11).
 *
 * Content matching is untouched by this: it runs as a separate, additive pass in
 * [com.rrajath.grove.ui.search.SearchViewModel] and never feeds [QueryMatcher]
 * or the FTS candidate query, so the FTS-parity guarantee still holds.
 */
object FilenameMatcher {

    /** Strongest kind of match a single term scored against the base name.
     *  Ordinal order matters: it is the rank key for floating files. */
    enum class Quality { CONTAINS, PREFIX, EXACT }

    data class Hit(
        val fileName: String,
        val quality: Quality,
        /** Match offsets within the base name (last path segment), merged and
         *  sorted, for highlighting. Empty when only a folder segment matched. */
        val ranges: List<IntRange>,
    )

    /**
     * One [Hit] per file name that some OR-group's non-negated text terms all
     * appear in (as case-insensitive substrings of the full vault-relative
     * path), with no negated text term appearing in the path.
     *
     * Facet conditions (state, tag, dates, `b.`) are ignored here: a file is not
     * a note and cannot satisfy them, and the file row is an extra affordance,
     * not a note result. Notebook scoping from the Filters sheet is applied by
     * the caller instead.
     *
     * Terms shorter than [FtsQuery.MIN_TERM_CHARS] never name-match, mirroring
     * the floor the content search applies.
     */
    fun match(fileNames: Iterable<String>, query: SearchQuery): List<Hit> {
        val groups = query.groups
            .map { it.textTerms() }
            .filter { it.positives.isNotEmpty() }
        if (groups.isEmpty()) return emptyList()

        return fileNames.distinct().mapNotNull { path ->
            val lowerPath = path.lowercase()
            val base = path.substringAfterLast('/')
            groups.firstNotNullOfOrNull { g ->
                when {
                    g.negatives.any { lowerPath.contains(it) } -> null
                    !g.positives.all { lowerPath.contains(it) } -> null
                    else -> hitFor(path, base, g.positives)
                }
            }
        }
    }

    private data class GroupTerms(val positives: List<String>, val negatives: List<String>)

    private fun List<Term>.textTerms(): GroupTerms {
        val positives = mutableListOf<String>()
        val negatives = mutableListOf<String>()
        for (term in this) {
            val condition = term.condition
            if (condition !is Condition.Text) continue
            val value = condition.term.lowercase()
            if (value.codePointCount(0, value.length) < FtsQuery.MIN_TERM_CHARS) continue
            (if (term.negated) negatives else positives) += value
        }
        return GroupTerms(positives, negatives)
    }

    private fun hitFor(path: String, base: String, terms: List<String>): Hit {
        val lowerBase = base.lowercase()
        val baseStem = lowerBase.removeSuffix(".org")
        var quality = Quality.CONTAINS
        val ranges = mutableListOf<IntRange>()
        for (term in terms) {
            when {
                baseStem == term || lowerBase == term -> quality = maxOf(quality, Quality.EXACT)
                lowerBase.startsWith(term) -> quality = maxOf(quality, Quality.PREFIX)
            }
            var from = lowerBase.indexOf(term)
            while (from >= 0) {
                ranges += from until from + term.length
                from = lowerBase.indexOf(term, from + term.length)
            }
        }
        return Hit(path, quality, mergeRanges(ranges))
    }

    private fun mergeRanges(ranges: List<IntRange>): List<IntRange> {
        if (ranges.isEmpty()) return emptyList()
        val sorted = ranges.sortedBy { it.first }
        val merged = mutableListOf(sorted.first())
        for (range in sorted.drop(1)) {
            val last = merged.last()
            if (range.first <= last.last + 1) {
                merged[merged.lastIndex] = last.first..maxOf(last.last, range.last)
            } else {
                merged += range
            }
        }
        return merged
    }
}
