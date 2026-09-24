package com.rrajath.grove.search

/** Builds the match-in-context snippets a full-text search result shows. */
object Snippets {

    /** Words of context kept on each side of a match. */
    const val CONTEXT_WORDS = 10

    /**
     * One snippet per cluster of matches in [line]: each match gets [radius]
     * words either side, and a later match that falls inside the current
     * window is shown there rather than opening a snippet of its own. Words are
     * whitespace-separated; the original spacing inside a window is kept, and
     * `…` marks a cut at either end.
     */
    fun windows(line: String, terms: List<String>, radius: Int = CONTEXT_WORDS): List<String> {
        val words = Regex("""\S+""").findAll(line).map { it.range }.toList()
        if (words.isEmpty()) return emptyList()
        val matchWords = highlightRanges(line, terms)
            .map { r -> words.indexOfFirst { r.first <= it.last && it.first <= r.first }.coerceAtLeast(0) }
            .distinct()
            .sorted()
        val out = mutableListOf<String>()
        var windowEnd = -1
        for (w in matchWords) {
            if (w <= windowEnd) continue
            val from = (w - radius).coerceAtLeast(0)
            windowEnd = (w + radius).coerceAtMost(words.lastIndex)
            val prefix = if (from > 0) "…" else ""
            val suffix = if (windowEnd < words.lastIndex) "…" else ""
            out += prefix + line.substring(words[from].first, words[windowEnd].last + 1) + suffix
        }
        return out
    }

    /** Every occurrence of every term within [text], case-insensitive. */
    fun highlightRanges(text: String, terms: List<String>): List<IntRange> = terms.flatMap { findRanges(text, it) }

    private fun findRanges(text: String, term: String): List<IntRange> {
        if (term.isEmpty()) return emptyList()
        val ranges = mutableListOf<IntRange>()
        var idx = 0
        while (true) {
            val found = text.indexOf(term, idx, ignoreCase = true)
            if (found < 0) break
            ranges += found until (found + term.length)
            idx = found + term.length
        }
        return ranges
    }
}
