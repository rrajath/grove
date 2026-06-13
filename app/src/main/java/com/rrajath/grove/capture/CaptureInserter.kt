package com.rrajath.grove.capture

import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/**
 * Inserts an expanded capture entry into a document at a [TargetLocation].
 * Pure text-splice operations: every line not touched by the insertion is
 * preserved byte-for-byte.
 */
object CaptureInserter {

    data class Insertion(val newText: String, val insertedAtLine: Int)

    private val STARS = Regex("""^(\*+)\s+(.*)$""")

    fun insert(
        docText: String,
        location: TargetLocation,
        entry: String,
        today: LocalDate,
        keywords: OrgKeywords = OrgKeywords.DEFAULT,
    ): Insertion {
        return when (location) {
            is TargetLocation.TopOfFile -> {
                val doc = OrgParser.parse(docText, keywords)
                spliceEntry(docText, doc.preambleEnd, normalizeEntry(entry, 1))
            }

            is TargetLocation.BottomOfFile ->
                spliceEntry(docText, endOfFileLine(docText), normalizeEntry(entry, 1))

            is TargetLocation.UnderHeading -> {
                val doc = OrgParser.parse(docText, keywords)
                val target = location.customId?.let { doc.findByCustomId(it) }
                    ?: location.title?.let { doc.findByTitle(it) }
                    ?: throw CaptureTargetNotFound(
                        location.customId?.let { "No heading with CUSTOM_ID \"$it\"" }
                            ?: "No heading titled \"${location.title}\""
                    )
                val line = if (location.appendLast) subtreeEnd(doc, target) else firstChildLine(doc, target)
                spliceEntry(docText, line, normalizeEntry(entry, target.level + 1))
            }

            is TargetLocation.DatetreeDate, is TargetLocation.DatetreeDatetime ->
                insertDatetree(docText, entry, today, keywords)
        }
    }

    class CaptureTargetNotFound(message: String) : Exception(message)

    /** Heading depth an entry gets when inserted at [location], if statically known. */
    fun entryLevel(location: TargetLocation): Int? = when (location) {
        is TargetLocation.TopOfFile, is TargetLocation.BottomOfFile -> 1
        // Depends on the target heading's depth in the file; not known here.
        is TargetLocation.UnderHeading -> null
        // Year → month → day tree; entries sit under the day heading.
        is TargetLocation.DatetreeDate, is TargetLocation.DatetreeDatetime -> 4
    }

    /**
     * Make the capture editor WYSIWYG: prefix the expanded entry with the
     * stars its heading will get on insert, so the user types the heading
     * right after them. Skipped when the template already supplies a heading
     * (insertion re-levels it anyway) or the depth isn't statically known.
     */
    fun withHeadingStars(expanded: ExpandedTemplate, location: TargetLocation): ExpandedTemplate {
        val level = entryLevel(location) ?: return expanded
        if (STARS.matchEntire(expanded.text.substringBefore('\n')) != null) return expanded
        val stars = "*".repeat(level) + " "
        return ExpandedTemplate(stars + expanded.text, expanded.cursorOffset + stars.length)
    }

    // --- datetree ---

    fun yearTitle(d: LocalDate) = d.year.toString()
    fun monthTitle(d: LocalDate): String =
        "%04d-%02d %s".format(d.year, d.monthValue, d.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH))
    fun dayTitle(d: LocalDate): String =
        "%04d-%02d-%02d %s".format(
            d.year, d.monthValue, d.dayOfMonth,
            d.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
        )

    private fun insertDatetree(
        docText: String,
        entry: String,
        today: LocalDate,
        keywords: OrgKeywords,
    ): Insertion {
        var text = docText
        var year = ensureDateChild(text, keywords, parent = null, level = 1, title = yearTitle(today), prefix = yearTitle(today))
        text = year.first
        var month = ensureDateChild(text, keywords, parent = year.second, level = 2, title = monthTitle(today), prefix = "%04d-%02d".format(today.year, today.monthValue))
        text = month.first
        val day = ensureDateChild(text, keywords, parent = month.second, level = 3, title = dayTitle(today), prefix = "%04d-%02d-%02d".format(today.year, today.monthValue, today.dayOfMonth))
        text = day.first

        val doc = OrgParser.parse(text, keywords)
        val dayHeadline = doc.headlines.first { it.lineIndex == day.second }
        return spliceEntry(text, subtreeEnd(doc, dayHeadline), normalizeEntry(entry, 4))
    }

    /**
     * Find or create a date-tree node. [parent] is the line index of the parent
     * headline (null = top level). Returns (newText, headlineLineIndex).
     */
    private fun ensureDateChild(
        text: String,
        keywords: OrgKeywords,
        parent: Int?,
        level: Int,
        title: String,
        prefix: String,
    ): Pair<String, Int> {
        val doc = OrgParser.parse(text, keywords)
        val siblings: List<OrgHeadline> = if (parent == null) {
            doc.headlines.filter { it.level == 1 }
        } else {
            val p = doc.headlines.first { it.lineIndex == parent }
            doc.directChildren(p)
        }

        siblings.firstOrNull { it.title == title }?.let { return text to it.lineIndex }

        // Chronological position among date-shaped siblings; others left in place.
        val dateSiblings = siblings.filter { looksLikeDate(it.title) }
        val insertBefore = dateSiblings.firstOrNull { it.title > prefix }
        val insertLine = when {
            insertBefore != null -> insertBefore.lineIndex
            dateSiblings.isNotEmpty() -> subtreeEnd(doc, dateSiblings.last())
            parent != null -> subtreeEnd(doc, doc.headlines.first { it.lineIndex == parent })
            else -> endOfFileLine(text)
        }
        val headlineLine = "*".repeat(level) + " " + title
        val spliced = spliceEntry(text, insertLine, listOf(headlineLine))
        return spliced.newText to spliced.insertedAtLine
    }

    private fun looksLikeDate(title: String): Boolean =
        Regex("""^\d{4}(-\d{2}){0,2}([ \t].*)?$""").matches(title)

    // --- splicing helpers ---

    /** First line after [h]'s own content, before its first child headline. */
    private fun firstChildLine(doc: OrgDocument, h: OrgHeadline): Int = h.contentEnd

    /** Line index just past [h]'s entire subtree. */
    private fun subtreeEnd(doc: OrgDocument, h: OrgHeadline): Int {
        val last = doc.subtree(h).lastOrNull() ?: h
        return last.contentEnd
    }

    /** Line index where bottom-of-file content goes (before a trailing-newline sentinel). */
    private fun endOfFileLine(text: String): Int {
        if (text.isEmpty()) return 0
        val lines = text.split("\n")
        return if (lines.last().isEmpty()) lines.size - 1 else lines.size
    }

    /**
     * Re-level the entry's first line to a headline at [level]; body lines are
     * kept verbatim. A first line without stars becomes the heading text.
     */
    private fun normalizeEntry(entry: String, level: Int): List<String> {
        val lines = entry.trimEnd('\n').split("\n")
        val first = lines.first()
        val m = STARS.matchEntire(first)
        val headingText = (m?.groupValues?.get(2) ?: first).trim()
        val heading = "*".repeat(level) + " " + headingText
        return listOf(heading) + lines.drop(1)
    }

    private fun spliceEntry(text: String, atLine: Int, entryLines: List<String>): Insertion {
        if (text.isEmpty()) {
            return Insertion(entryLines.joinToString("\n") + "\n", 0)
        }
        val lines = text.split("\n").toMutableList()
        var at = atLine.coerceIn(0, lines.size)
        // A trailing empty element is the file's final newline — insert before
        // it so the entry stays newline-terminated instead of creating a blank
        // line plus an unterminated last line.
        if (at == lines.size && lines.last().isEmpty()) at--
        lines.addAll(at, entryLines)
        return Insertion(lines.joinToString("\n"), at)
    }
}
