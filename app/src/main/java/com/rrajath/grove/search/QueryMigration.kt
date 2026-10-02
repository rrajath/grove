package com.rrajath.grove.search

/**
 * Rewrites a query saved under Grove's original date semantics (syntax v1) so
 * it means the same thing under the Orgzly-compatible ones (v2), where a date
 * token without an operator takes the prefix's default op (`le` for s./d./cr.,
 * `eq` for c./a.) and every offset counts forward from today.
 *
 * | v1 token                                   | v2 rewrite        |
 * |--------------------------------------------|-------------------|
 * | `s./d./cr.` + today/now/tomorrow/yesterday | `X.eq.<day>`      |
 * | `a.Nd` (any event on or before)            | `a.le.Nd`         |
 * | `c./cr.Nd` (within the last N)             | `X.ge.-Nd`        |
 * | `c./cr.OP.Nd` (unsigned counted back)      | `X.OP.-Nd`        |
 * | lowercase/mixed `and`/`or` (were text)     | `"and"` / `"or"`  |
 *
 * Everything else already means the same in both. The rewrite never produces
 * a token it would rewrite again, so running it twice is harmless.
 */
object QueryMigration {

    fun toV2(query: String): String = TOKEN.replace(query) { migrateToken(it.value) }

    private fun migrateToken(token: String): String {
        if (token != "OR" && token != "AND" &&
            (token.equals("or", ignoreCase = true) || token.equals("and", ignoreCase = true))
        ) {
            return "\"$token\""
        }
        val m = PEELED.matchEntire(token) ?: return token
        val (open, neg, body, close) = m.destructured
        val dot = body.indexOf('.')
        if (dot !in 1..2) return token
        val prefix = body.substring(0, dot).lowercase()
        if (prefix !in DATE_PREFIXES) return token
        val value = body.substring(dot + 1)
        val rewritten = migrateDate(prefix, value) ?: return token
        return "$open$neg${body.substring(0, dot)}.$rewritten$close"
    }

    /** The v2 value for one v1 date value, or null when it already means the same. */
    private fun migrateDate(prefix: String, value: String): String? {
        val past = prefix == "c" || prefix == "cr"
        val opDot = value.indexOf('.')
        if (opDot > 0 && CompareOp.parse(value.substring(0, opDot)) != null) {
            val rest = value.substring(opDot + 1)
            return if (past && UNSIGNED.matches(rest)) "${value.substring(0, opDot)}.-$rest" else null
        }
        val lower = value.lowercase()
        return when {
            lower in SINGLE_DAYS -> if (prefix in setOf("s", "d", "cr")) "eq.$value" else null
            UNSIGNED.matches(lower) -> when {
                past -> "ge.-$value"
                prefix == "a" -> "le.$value"
                else -> null
            }
            else -> null
        }
    }

    private val TOKEN = Regex("""\S+""")
    private val PEELED = Regex("""^((?:\.?\()*)(\.?)([^()"]+)(\)*)$""")
    private val UNSIGNED = Regex("""\d+[dwm]""", RegexOption.IGNORE_CASE)
    private val SINGLE_DAYS = setOf("today", "now", "tomorrow", "yesterday")
    private val DATE_PREFIXES = setOf("s", "d", "a", "c", "cr")
}
