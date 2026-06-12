package com.rrajath.grove.vault

/**
 * `.orgzlyignore`-style rules: one glob per line, `#` comments, `!` negation.
 * Later rules win, mirroring gitignore semantics for flat file names.
 */
class IgnoreRules(rulesText: String) {

    private data class Rule(val regex: Regex, val negated: Boolean)

    private val rules: List<Rule> = rulesText.lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") }
        .map { line ->
            val negated = line.startsWith("!")
            val pattern = if (negated) line.drop(1) else line
            Rule(globToRegex(pattern), negated)
        }

    fun isIgnored(name: String): Boolean {
        var ignored = false
        for (rule in rules) {
            if (rule.regex.matches(name)) ignored = !rule.negated
        }
        return ignored
    }

    companion object {
        const val FILE_NAME = ".orgzlyignore"

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
