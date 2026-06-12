package com.rrajath.grove.org

import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Span edits on a document's text. Every function returns the new full text;
 * only the lines belonging to the edit are touched — the rest of the file is
 * preserved byte-for-byte (the lossless-parser invariant).
 */
object OrgMutations {

    data class NewNoteOptions(
        val keyword: String? = null,
        /** Adds an :ID: property (caller supplies the UUID — pure function). */
        val id: String? = null,
        /** Adds a :CREATED: inactive timestamp. */
        val createdAt: LocalDateTime? = null,
    )

    // --- headline line edits ---

    fun setKeyword(doc: OrgDocument, h: OrgHeadline, keyword: String?): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, keyword, h.priority, h.title, h.tags))

    fun setPriority(doc: OrgDocument, h: OrgHeadline, priority: Char?): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, h.keyword, priority, h.title, h.tags))

    fun setTags(doc: OrgDocument, h: OrgHeadline, tags: List<String>): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, h.keyword, h.priority, h.title, tags))

    fun setTitle(doc: OrgDocument, h: OrgHeadline, title: String): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, h.keyword, h.priority, title, h.tags))

    // --- planning edits ---

    fun setScheduled(doc: OrgDocument, h: OrgHeadline, ts: OrgTimestamp?): String =
        writePlanning(doc, h, h.planning.copy(scheduled = ts))

    fun setDeadline(doc: OrgDocument, h: OrgHeadline, ts: OrgTimestamp?): String =
        writePlanning(doc, h, h.planning.copy(deadline = ts))

    /**
     * Mark done per org rules: a repeating SCHEDULED/DEADLINE advances its date
     * and the keyword stays active; otherwise the keyword becomes [doneKeyword]
     * and a CLOSED stamp is added.
     */
    fun markDone(
        doc: OrgDocument,
        h: OrgHeadline,
        doneKeyword: String,
        now: LocalDateTime,
    ): String {
        val today = now.toLocalDate()
        val hasRepeater = h.planning.scheduled?.repeater != null || h.planning.deadline?.repeater != null
        return if (hasRepeater) {
            writePlanning(
                doc, h,
                h.planning.copy(
                    scheduled = h.planning.scheduled?.advanceRepeater(today),
                    deadline = h.planning.deadline?.advanceRepeater(today),
                ),
            )
        } else {
            val withClosed = writePlanning(
                doc, h,
                h.planning.copy(
                    closed = OrgTimestamp(
                        today,
                        time = now.toLocalTime().withSecond(0).withNano(0),
                        active = false,
                    )
                ),
            )
            val redoc = OrgParser.parse(withClosed, doc.keywords)
            val again = redoc.headlines.first { it.lineIndex == h.lineIndex }
            setKeyword(redoc, again, doneKeyword)
        }
    }

    // --- structural edits ---

    fun subtreeText(doc: OrgDocument, h: OrgHeadline): String =
        doc.lines.subList(h.lineIndex, doc.subtreeEndLine(h)).joinToString("\n")

    fun deleteSubtree(doc: OrgDocument, h: OrgHeadline): String {
        val lines = doc.lines.toMutableList()
        repeat(doc.subtreeEndLine(h) - h.lineIndex) { lines.removeAt(h.lineIndex) }
        return lines.joinToString("\n")
    }

    /** Replace [h]'s whole subtree with [newText] (the editor's save path). */
    fun replaceSubtree(doc: OrgDocument, h: OrgHeadline, newText: String): String {
        val lines = doc.lines.toMutableList()
        val end = doc.subtreeEndLine(h)
        repeat(end - h.lineIndex) { lines.removeAt(h.lineIndex) }
        lines.addAll(h.lineIndex, newText.trimEnd('\n').split("\n"))
        return lines.joinToString("\n")
    }

    /** Swap [h]'s subtree with its previous (delta=-1) or next (+1) sibling. */
    fun moveSubtree(doc: OrgDocument, h: OrgHeadline, delta: Int): String? {
        val siblings = doc.parent(h)?.let { doc.directChildren(it) }
            ?: doc.headlines.filter { it.level == h.level && doc.parent(it) == null }
        val idx = siblings.indexOfFirst { it.lineIndex == h.lineIndex }
        val other = siblings.getOrNull(idx + delta) ?: return null

        val (first, second) = if (other.lineIndex < h.lineIndex) other to h else h to other
        val lines = doc.lines
        val firstEnd = doc.subtreeEndLine(first)
        val secondEnd = doc.subtreeEndLine(second)
        val rebuilt = lines.subList(0, first.lineIndex) +
                lines.subList(second.lineIndex, secondEnd) +
                lines.subList(firstEnd, second.lineIndex) +
                lines.subList(first.lineIndex, firstEnd) +
                lines.subList(secondEnd, lines.size)
        return rebuilt.joinToString("\n")
    }

    /** Insert a new child note at the end of [h]'s subtree; returns text + new headline's line. */
    fun newChild(
        doc: OrgDocument,
        h: OrgHeadline,
        title: String,
        options: NewNoteOptions = NewNoteOptions(),
    ): Pair<String, Int> {
        val level = h.level + 1
        val entry = buildList {
            add(headlineLine(level, options.keyword, null, title, emptyList()))
            if (options.id != null || options.createdAt != null) {
                add(":PROPERTIES:")
                options.id?.let { add(":ID: $it") }
                options.createdAt?.let {
                    add(
                        ":CREATED: " + OrgTimestamp(
                            it.toLocalDate(),
                            time = it.toLocalTime().withSecond(0).withNano(0),
                            active = false,
                        ).format()
                    )
                }
                add(":END:")
            }
        }
        val at = doc.subtreeEndLine(h)
        val lines = doc.lines.toMutableList()
        var insertAt = at
        if (insertAt == lines.size && lines.isNotEmpty() && lines.last().isEmpty()) insertAt--
        lines.addAll(insertAt, entry)
        return lines.joinToString("\n") to insertAt
    }

    /** Paste a cut/copied subtree as the last child of [h], releveled. */
    fun pasteUnder(doc: OrgDocument, h: OrgHeadline, subtree: String): String {
        val subtreeLines = subtree.trimEnd('\n').split("\n")
        val srcLevel = Regex("""^(\*+)\s""").find(subtreeLines.first())?.groupValues?.get(1)?.length ?: 1
        val shift = (h.level + 1) - srcLevel
        val releveled = subtreeLines.map { line ->
            val m = Regex("""^(\*+)(\s.*)$""").matchEntire(line)
            if (m != null) {
                val newLevel = (m.groupValues[1].length + shift).coerceAtLeast(1)
                "*".repeat(newLevel) + m.groupValues[2]
            } else line
        }
        val lines = doc.lines.toMutableList()
        var insertAt = doc.subtreeEndLine(h)
        if (insertAt == lines.size && lines.isNotEmpty() && lines.last().isEmpty()) insertAt--
        lines.addAll(insertAt, releveled)
        return lines.joinToString("\n")
    }

    // --- helpers ---

    fun headlineLine(
        level: Int,
        keyword: String?,
        priority: Char?,
        title: String,
        tags: List<String>,
    ): String = buildString {
        append("*".repeat(level))
        keyword?.let { append(' ').append(it) }
        priority?.let { append(" [#").append(it).append(']') }
        if (title.isNotEmpty()) append(' ').append(title)
        if (tags.isNotEmpty()) {
            append("  ").append(tags.joinToString(":", prefix = ":", postfix = ":"))
        }
    }

    private fun planningLine(p: Planning): String? {
        val parts = buildList {
            p.scheduled?.let { add("SCHEDULED: ${it.format()}") }
            p.deadline?.let { add("DEADLINE: ${it.format()}") }
            p.closed?.let { add("CLOSED: ${it.format()}") }
        }
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    private fun writePlanning(doc: OrgDocument, h: OrgHeadline, planning: Planning): String {
        val lines = doc.lines.toMutableList()
        val planningLineIndex = h.lineIndex + 1
        val hadPlanning = planningLineIndex < doc.subtreeEndLine(h) &&
                planningLineIndex < lines.size &&
                isPlanningLine(lines[planningLineIndex])
        val newLine = planningLine(planning)
        when {
            hadPlanning && newLine != null -> lines[planningLineIndex] = newLine
            hadPlanning -> lines.removeAt(planningLineIndex)
            newLine != null -> lines.add(planningLineIndex, newLine)
        }
        return lines.joinToString("\n")
    }

    private fun isPlanningLine(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("SCHEDULED:") || t.startsWith("DEADLINE:") || t.startsWith("CLOSED:")
    }

    private fun replaceLine(doc: OrgDocument, lineIndex: Int, newLine: String): String {
        val lines = doc.lines.toMutableList()
        lines[lineIndex] = newLine
        return lines.joinToString("\n")
    }
}
