package com.rrajath.grove.vault

/**
 * Directory-walk ignore patterns: one glob per line, `#` comments.
 * `!`-prefixed lines are inert (negation is meaningless here — a directory
 * that's never descended into can't be re-included).
 */
class IgnorePatterns(rulesText: String) {

    private data class Pattern(val regex: Regex, val matchesPath: Boolean)

    private val patterns: List<Pattern> = rulesText.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
        .map { line -> Pattern(globToRegex(line), line.contains("/")) }

    fun isDirIgnored(name: String, path: String): Boolean = matches(name, path)

    fun isFileIgnored(name: String, path: String): Boolean = matches(name, path)

    private fun matches(name: String, path: String): Boolean =
        patterns.any { pattern -> pattern.regex.matches(if (pattern.matchesPath) path else name) }

    companion object {
        private fun globToRegex(glob: String): Regex {
            val sb = StringBuilder()
            for (ch in glob) {
                when (ch) {
                    '*' -> sb.append("[^/]*")
                    '?' -> sb.append("[^/]")
                    else -> sb.append(Regex.escape(ch.toString()))
                }
            }
            return Regex(sb.toString())
        }
    }
}
