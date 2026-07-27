package com.rrajath.grove.search

/** Builds the truncated result snippet a search result shows under its title. */
object Snippets {

    data class Snippet(val text: String)

    private const val CONTEXT = 40
    private const val MAX_LEN = 120

    fun build(body: String, terms: List<String>): Snippet {
        if (body.isEmpty()) return Snippet("")
        val anchor = terms.firstNotNullOfOrNull { term ->
            val at = body.indexOf(term, ignoreCase = true)
            if (at >= 0) at to term else null
        }
        if (anchor == null) {
            return Snippet(body.take(MAX_LEN) + if (body.length > MAX_LEN) "…" else "")
        }
        val (at, term) = anchor
        val start = (at - CONTEXT).coerceAtLeast(0)
        val end = (at + term.length + CONTEXT * 2).coerceAtMost(body.length)
        val prefix = if (start > 0) "…" else ""
        val suffix = if (end < body.length) "…" else ""
        return Snippet(prefix + body.substring(start, end) + suffix)
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
