package com.rrajath.grove.capture

import com.rrajath.grove.org.OrgParser
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/** Computes a Roam node's target filename from its pattern, and reads a draft's live title. */
object FilenamePattern {

    private val STRFTIME_BLOCK = Regex("""%<([^>]*)>""")
    private val STRFTIME_TOKEN = Regex("%[YmdHMS]")
    private val SLUG_TOKEN = "%(slug)"
    private val HEADLINE_START = Regex("""^\*+\s""")

    /** Expands `%<...>` strftime blocks (Y/m/d/H/M/S only) and `%(slug)` in [pattern]. */
    fun expand(pattern: String, now: LocalDateTime, slug: String): String =
        STRFTIME_BLOCK.replace(pattern) { expandStrftime(it.groupValues[1], now) }
            .replace(SLUG_TOKEN, slug)

    private fun expandStrftime(spec: String, now: LocalDateTime): String {
        val javaPattern = STRFTIME_TOKEN.replace(spec) { m ->
            when (m.value) {
                "%Y" -> "yyyy"
                "%m" -> "MM"
                "%d" -> "dd"
                "%H" -> "HH"
                "%M" -> "mm"
                "%S" -> "ss"
                else -> m.value
            }
        }
        return now.format(DateTimeFormatter.ofPattern(javaPattern))
    }

    /** Trims and collapses whitespace runs to `_`; no lowercasing/char-stripping. Blank title → blank slug. */
    fun slugFromTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return ""
        return trimmed.replace(Regex("""\s+"""), "_")
    }

    /**
     * The live `#+title:` value from preamble lines (before the first headline) in a
     * draft. Case-insensitive keyword, same scoping as [OrgParser]'s preamble scan.
     * Blank/missing title → null.
     */
    fun titleFromDraft(text: String): String? {
        for (line in text.split("\n")) {
            if (HEADLINE_START.find(line) != null) break
            val match = OrgParser.PREAMBLE_KEYWORD.matchEntire(line.trim()) ?: continue
            if (match.groupValues[1].equals("title", ignoreCase = true)) {
                return match.groupValues[2].trim().ifEmpty { null }
            }
        }
        return null
    }
}
