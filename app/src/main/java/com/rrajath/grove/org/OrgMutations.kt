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
        /** Body text placed under the heading (after any properties drawer). */
        val body: String? = null,
    )

    // --- headline line edits ---

    fun setKeyword(doc: OrgDocument, h: OrgHeadline, keyword: String?): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, keyword, h.priority, h.title, h.tags))

    fun setPriority(doc: OrgDocument, h: OrgHeadline, priority: Char?): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, h.keyword, priority, h.title, h.tags))

    fun setTags(doc: OrgDocument, h: OrgHeadline, tags: List<String>): String =
        replaceLine(doc, h.lineIndex, headlineLine(h.level, h.keyword, h.priority, h.title, tags))

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

    private val CHECKBOX_LINE = Regex("""^(\s*(?:[-+]|\d+[.)])\s+)\[([ Xx-])\](.*)$""")

    /**
     * Read mode: tap-cycle a checklist item's box forward through [states] (in
     * order, wrapping around). [lineIndex] is absolute into [doc.lines] — the
     * caller resolves a [BlockParser.ListItem]'s body-relative `line` against
     * the owning headline's `bodyStart` first. A box whose current mark isn't
     * one of [states] (e.g. `[-]` on a file using the two-state config) jumps
     * to the first state rather than the one after it. Returns null when the
     * line isn't a checkbox list item.
     */
    fun toggleCheckbox(doc: OrgDocument, lineIndex: Int, states: List<Char>): String? {
        if (lineIndex !in doc.lines.indices) return null
        val m = CHECKBOX_LINE.matchEntire(doc.lines[lineIndex]) ?: return null
        val (prefix, mark, rest) = m.destructured
        val current = if (mark == "x") "X" else mark
        val idx = states.indexOf(current.first())
        val next = if (idx == -1) states.first() else states[(idx + 1) % states.size]
        val lines = doc.lines.toMutableList()
        lines[lineIndex] = "$prefix[$next]$rest"
        return lines.joinToString("\n")
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

    /**
     * Swap [h]'s subtree with its previous (delta=-1) or next (+1) sibling.
     * Returns the new text plus [h]'s new lineIndex, or null at an edge.
     */
    fun moveSubtree(doc: OrgDocument, h: OrgHeadline, delta: Int): Pair<String, Int>? {
        val siblings = siblingsOf(doc, h)
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
        val newLine = if (other.lineIndex < h.lineIndex) {
            first.lineIndex // moved up: h now starts where the previous sibling did
        } else {
            // moved down: everything between h's old start and the next
            // sibling's subtree end now sits in front of h
            first.lineIndex + (secondEnd - firstEnd)
        }
        return rebuilt.joinToString("\n") to newLine
    }

    /**
     * Shift [h] and its entire subtree one level shallower.
     * Returns null when [h] is already at level 1.
     */
    fun promoteSubtree(doc: OrgDocument, h: OrgHeadline): String? {
        if (h.level == 1) return null
        return shiftSubtreeLevels(doc, h, -1)
    }

    /**
     * Shift [h] and its entire subtree one level deeper. Returns null when
     * [h] has no previous same-level sibling to become its parent (demoting
     * would create an orphaned level jump).
     */
    fun demoteSubtree(doc: OrgDocument, h: OrgHeadline): String? {
        val siblings = siblingsOf(doc, h)
        val idx = siblings.indexOfFirst { it.lineIndex == h.lineIndex }
        if (idx <= 0) return null
        return shiftSubtreeLevels(doc, h, +1)
    }

    /**
     * Insert [subtree] into [doc], releveled: as the last child of [target],
     * or appended at the end of the file at level 1 when [target] is null.
     * Returns the new text plus the inserted root's lineIndex.
     */
    fun refileInsert(doc: OrgDocument, target: OrgHeadline?, subtree: String): Pair<String, Int> {
        val releveled = relevelSubtree(subtree.trimEnd('\n').split("\n"), (target?.level ?: 0) + 1)
        if (target == null && doc.lines.all { it.isEmpty() }) {
            return releveled.joinToString("\n") + "\n" to 0
        }
        val lines = doc.lines.toMutableList()
        var insertAt = target?.let { doc.subtreeEndLine(it) } ?: lines.size
        if (insertAt == lines.size && lines.isNotEmpty() && lines.last().isEmpty()) insertAt--
        lines.addAll(insertAt, releveled)
        return lines.joinToString("\n") to insertAt
    }

    /**
     * Refile [source]'s subtree within the same document, under the headline
     * starting at [targetLine] (or to the top level when null). One text
     * pipeline — the target's line is re-resolved after the delete so stale
     * indexes can't corrupt the file. Returns null when [targetLine] falls
     * inside [source]'s own subtree or no longer resolves to a headline.
     */
    fun refileWithinFile(doc: OrgDocument, source: OrgHeadline, targetLine: Int?): Pair<String, Int>? {
        val sourceEnd = doc.subtreeEndLine(source)
        if (targetLine != null && targetLine in source.lineIndex until sourceEnd) return null
        val subtree = subtreeText(doc, source)
        val redoc = OrgParser.parse(deleteSubtree(doc, source), doc.keywords)
        val target = targetLine?.let { line ->
            val adjusted = if (line > source.lineIndex) line - (sourceEnd - source.lineIndex) else line
            redoc.headlines.firstOrNull { it.lineIndex == adjusted } ?: return null
        }
        return refileInsert(redoc, target, subtree)
    }

    private fun siblingsOf(doc: OrgDocument, h: OrgHeadline): List<OrgHeadline> =
        doc.parent(h)?.let { doc.directChildren(it) }
            ?: doc.headlines.filter { it.level == h.level && doc.parent(it) == null }

    /**
     * Re-star [h]'s subtree by [delta] using the parsed headline positions,
     * so literal star lines inside blocks are never touched.
     */
    private fun shiftSubtreeLevels(doc: OrgDocument, h: OrgHeadline, delta: Int): String {
        val end = doc.subtreeEndLine(h)
        val headlineLines = doc.headlines
            .filter { it.lineIndex in h.lineIndex until end }
            .associateBy { it.lineIndex }
        val lines = doc.lines.mapIndexed { i, line ->
            val head = headlineLines[i] ?: return@mapIndexed line
            val m = STARS_LINE.matchEntire(line) ?: return@mapIndexed line
            "*".repeat((head.level + delta).coerceAtLeast(1)) + m.groupValues[2]
        }
        return lines.joinToString("\n")
    }

    /** Insert a new child note at the end of [h]'s subtree; returns text + new headline's line. */
    fun newChild(
        doc: OrgDocument,
        h: OrgHeadline,
        title: String,
        options: NewNoteOptions = NewNoteOptions(),
    ): Pair<String, Int> = insertNote(doc, doc.subtreeEndLine(h), h.level + 1, title, options)

    /** Append a new top-level note at the end of the file (outline FAB, PRD §5.3). */
    fun newTopLevel(
        doc: OrgDocument,
        title: String,
        options: NewNoteOptions = NewNoteOptions(),
    ): Pair<String, Int> = insertNote(doc, doc.lines.size, 1, title, options)

    /** Insert a new sibling note immediately above [h] (same level). */
    fun insertSiblingAbove(
        doc: OrgDocument,
        h: OrgHeadline,
        title: String = "",
        options: NewNoteOptions = NewNoteOptions(),
    ): Pair<String, Int> = insertNote(doc, h.lineIndex, h.level, title, options)

    /** Insert a new sibling note immediately below [h]'s whole subtree (same level). */
    fun insertSiblingBelow(
        doc: OrgDocument,
        h: OrgHeadline,
        title: String = "",
        options: NewNoteOptions = NewNoteOptions(),
    ): Pair<String, Int> = insertNote(doc, doc.subtreeEndLine(h), h.level, title, options)

    private fun insertNote(
        doc: OrgDocument,
        atLine: Int,
        level: Int,
        title: String,
        options: NewNoteOptions,
    ): Pair<String, Int> {
        val entry = buildList {
            // A blank-title note (created for immediate editing) needs the
            // trailing space so the cursor lands after "* " ready for typing.
            val header = headlineLine(level, options.keyword, null, title, emptyList())
            add(if (title.isEmpty() && options.keyword == null) "$header " else header)
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
            options.body?.takeIf { it.isNotBlank() }?.let { body ->
                body.trimEnd('\n').split("\n").forEach { add(it) }
            }
        }
        // An empty document is a single empty line — replace it instead of
        // stacking the note under a leading blank.
        if (doc.lines.all { it.isEmpty() }) {
            return entry.joinToString("\n") + "\n" to 0
        }
        val lines = doc.lines.toMutableList()
        var insertAt = atLine.coerceIn(0, lines.size)
        if (insertAt == lines.size && lines.isNotEmpty() && lines.last().isEmpty()) insertAt--
        lines.addAll(insertAt, entry)
        return lines.joinToString("\n") to insertAt
    }

    /** Paste a cut/copied subtree as the last child of [h], releveled. */
    fun pasteUnder(doc: OrgDocument, h: OrgHeadline, subtree: String): String {
        val releveled = relevelSubtree(subtree.trimEnd('\n').split("\n"), h.level + 1)
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

    private val STARS_PREFIX = Regex("""^(\*+)\s""")
    private val STARS_LINE = Regex("""^(\*+)(\s.*)$""")

    /** Re-star every headline line of a detached subtree so its root sits at [targetLevel]. */
    private fun relevelSubtree(subtreeLines: List<String>, targetLevel: Int): List<String> {
        val srcLevel = STARS_PREFIX.find(subtreeLines.first())?.groupValues?.get(1)?.length ?: 1
        val shift = targetLevel - srcLevel
        return subtreeLines.map { line ->
            val m = STARS_LINE.matchEntire(line)
            if (m != null) {
                val newLevel = (m.groupValues[1].length + shift).coerceAtLeast(1)
                "*".repeat(newLevel) + m.groupValues[2]
            } else line
        }
    }

    private fun replaceLine(doc: OrgDocument, lineIndex: Int, newLine: String): String {
        val lines = doc.lines.toMutableList()
        lines[lineIndex] = newLine
        return lines.joinToString("\n")
    }
}
