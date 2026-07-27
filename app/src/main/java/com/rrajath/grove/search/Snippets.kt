package com.rrajath.grove.search

/** Builds the result snippet with the matched-term range for highlighting. */
object Snippets {

    data class Snippet(val text: String, val highlight: IntRange?)

    private const val CONTEXT = 40
    private const val MAX_LEN = 120

    fun build(body: String, terms: List<String>): Snippet {
        if (body.isEmpty()) return Snippet("", null)
        for (term in terms) {
            val at = body.indexOf(term, ignoreCase = true)
            if (at >= 0) {
                val start = (at - CONTEXT).coerceAtLeast(0)
                val end = (at + term.length + CONTEXT * 2).coerceAtMost(body.length)
                val prefix = if (start > 0) "…" else ""
                val suffix = if (end < body.length) "…" else ""
                val text = prefix + body.substring(start, end) + suffix
                val hlStart = prefix.length + (at - start)
                return Snippet(text, hlStart until (hlStart + term.length))
            }
        }
        return Snippet(body.take(MAX_LEN) + if (body.length > MAX_LEN) "…" else "", null)
    }
}
