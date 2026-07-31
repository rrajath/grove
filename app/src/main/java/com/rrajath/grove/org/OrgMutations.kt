package com.rrajath.grove.org

import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.roundToInt

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
     * Write SCHEDULED and DEADLINE in one edit — what the Dates screen's "Apply
     * both dates" commits. Doing it as a single [writePlanning] keeps the two
     * timestamps on one planning line and makes the pair a single undo step,
     * which chaining [setScheduled] into [setDeadline] would not.
     */
    fun setPlanningDates(
        doc: OrgDocument,
        h: OrgHeadline,
        scheduled: OrgTimestamp?,
        deadline: OrgTimestamp?,
    ): String = writePlanning(doc, h, h.planning.copy(scheduled = scheduled, deadline = deadline))

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
            val advanced = writePlanning(
                doc, h,
                h.planning.copy(
                    scheduled = h.planning.scheduled?.advanceRepeater(today),
                    deadline = h.planning.deadline?.advanceRepeater(today),
                ),
            )
            // The keyword itself stays unchanged for a repeater (org semantics),
            // but the transition is still recorded — same as Emacs logging the
            // DONE note before reverting the state back to TODO.
            val doc1 = OrgParser.parse(advanced, doc.keywords)
            val h1 = doc1.headlines.first { it.lineIndex == h.lineIndex }
            val stamp = OrgTimestamp(today, time = now.toLocalTime().withSecond(0).withNano(0), active = false)
            val logged = appendLogbookEntry(doc1, h1, logStateChangeLine(doneKeyword, h1.keyword ?: "", stamp))
            val doc2 = OrgParser.parse(logged, doc.keywords)
            val h2 = doc2.headlines.first { it.lineIndex == h.lineIndex }
            upsertProperty(doc2, h2, "LAST_REPEAT", stamp.format())
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

    /**
     * Inverse of [markDone] for the non-repeating case: restore [activeKeyword]
     * and drop the CLOSED stamp, the way `org-todo` back to TODO does. A
     * repeating heading never reaches a done keyword in the first place, so
     * there is nothing to invert there.
     */
    fun reopen(doc: OrgDocument, h: OrgHeadline, activeKeyword: String?): String {
        val cleared = writePlanning(doc, h, h.planning.copy(closed = null))
        val redoc = OrgParser.parse(cleared, doc.keywords)
        val again = redoc.headlines.first { it.lineIndex == h.lineIndex }
        return setKeyword(redoc, again, activeKeyword)
    }

    /**
     * Single entry point for every "change this heading's TODO state" UI action
     * (metadata sheet chips, Outline/Search swipe state pickers): picking a
     * done-type [newKeyword] applies full "mark done" semantics, and leaving a
     * done-type keyword — including clearing it to no keyword at all — always
     * drops the stale CLOSED stamp via [reopen], the way `org-todo` does. A
     * plain keyword-to-keyword change with neither end done-type is a bare
     * [setKeyword].
     */
    fun changeKeyword(
        doc: OrgDocument,
        h: OrgHeadline,
        newKeyword: String?,
        keywords: OrgKeywords,
        now: LocalDateTime,
    ): String = when {
        newKeyword != null && keywords.isDone(newKeyword) -> markDone(doc, h, newKeyword, now)
        h.keyword != null && keywords.isDone(h.keyword) -> reopen(doc, h, newKeyword)
        else -> setKeyword(doc, h, newKeyword)
    }

    private val CHECKBOX_LINE = Regex("""^(\s*(?:[-+]|\d+[.)])\s+)\[([ Xx-])\](.*)$""")

    /**
     * Read mode: tap-cycle a checklist item's box forward through [states] (in
     * order, wrapping around). [lineIndex] is absolute into [doc.lines] — the
     * caller resolves a [BlockParser.ListItem]'s body-relative `line` against
     * the owning headline's `bodyStart` first. A box whose current mark isn't
     * one of [states] (e.g. `[-]` on a file using the two-state config) jumps
     * to the first state rather than the one after it. Returns null when the
     * line isn't a checkbox list item. Also refreshes the immediate parent's
     * statistics cookie and, if the parent is itself a checkbox item,
     * checks/unchecks it to match whether all of its direct children are now
     * done — see [updateParentCookie].
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
        updateParentCookie(lines, lineIndex)
        return lines.joinToString("\n")
    }

    private val LIST_ITEM_LINE = Regex("""^(\s*)(?:[-+]|\d+[.)])\s+(?:\[([ Xx-])\]\s+)?(.*)$""")
    private val FRACTION_COOKIE = Regex("""\[\d*/\d*\]""")
    private val PERCENT_COOKIE = Regex("""\[\d*%\]""")

    /**
     * Statistics cookies (org's `[/]`, `[x/y]`, `[%]`) on a parent item
     * summarize the checkbox state of its direct children — only refreshes a
     * cookie that's already present in the parent's text (org never invents
     * one) and only counts the shallowest indent level right under the
     * parent, so a nested sub-list with its own cookie isn't double-counted
     * into the outer one. Independently, when the parent is itself a
     * checkbox item, completing (or un-completing) all of its direct
     * children checks/unchecks the parent's own box too — same treatment as
     * any other checklist item finishing. This only ever touches the
     * immediate parent, not further ancestors.
     */
    private fun updateParentCookie(lines: MutableList<String>, toggledIndex: Int) {
        val toggled = LIST_ITEM_LINE.matchEntire(lines[toggledIndex]) ?: return
        val itemIndent = toggled.groupValues[1].length

        var parentIndex = -1
        for (i in toggledIndex - 1 downTo 0) {
            val m = LIST_ITEM_LINE.matchEntire(lines[i]) ?: break
            if (m.groupValues[1].length < itemIndent) {
                parentIndex = i
                break
            }
        }
        if (parentIndex == -1) return
        val parentMatch = LIST_ITEM_LINE.matchEntire(lines[parentIndex])!!
        val parentIndent = parentMatch.groupValues[1].length

        data class Child(val indent: Int, val checkbox: Char?)
        val below = mutableListOf<Child>()
        var i = parentIndex + 1
        while (i < lines.size) {
            val m = LIST_ITEM_LINE.matchEntire(lines[i]) ?: break
            val indent = m.groupValues[1].length
            if (indent <= parentIndent) break
            below.add(Child(indent, m.groupValues[2].firstOrNull()))
            i++
        }
        val directIndent = below.minOfOrNull { it.indent } ?: return
        val direct = below.filter { it.indent == directIndent && it.checkbox != null }
        if (direct.isEmpty()) return
        val total = direct.size
        val done = direct.count { it.checkbox == 'X' || it.checkbox == 'x' }

        val textRange = parentMatch.groups[3]!!.range
        val text = parentMatch.groupValues[3]
        val newText = FRACTION_COOKIE.find(text)?.let { text.replaceRange(it.range, "[$done/$total]") }
            ?: PERCENT_COOKIE.find(text)?.let {
                val pct = (done * 100.0 / total).roundToInt()
                text.replaceRange(it.range, "[$pct%]")
            }
        if (newText != null) {
            lines[parentIndex] = lines[parentIndex].replaceRange(textRange, newText)
        }

        val parentCheckbox = parentMatch.groups[2]
        if (parentCheckbox != null) {
            val newMark = if (done == total) "X" else " "
            lines[parentIndex] = lines[parentIndex].replaceRange(parentCheckbox.range, newMark)
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

    /** Emacs' `org-log-note-headings` state-change note: `- State %-12s from %-12s %t`. */
    private fun logStateChangeLine(newState: String, oldState: String, at: OrgTimestamp): String {
        fun pad(s: String) = "\"$s\"".padEnd(12)
        return "- State ${pad(newState)} from ${pad(oldState)} ${at.format()}"
    }

    /** First line right after any planning line — where the drawer run (if any) starts. */
    private fun drawerScanStart(doc: OrgDocument, h: OrgHeadline): Int {
        val planningLineIndex = h.lineIndex + 1
        val hasPlanning = planningLineIndex < doc.subtreeEndLine(h) &&
                planningLineIndex < doc.lines.size && isPlanningLine(doc.lines[planningLineIndex])
        return if (hasPlanning) planningLineIndex + 1 else h.lineIndex + 1
    }

    /**
     * Line index of [marker]'s own drawer-open line, if it appears in the
     * contiguous `:PROPERTIES:`/`:LOGBOOK:` run starting at [start] — mirrors
     * [OrgParser]'s drawer-scanning rules (drawers must immediately follow
     * the planning line, in either order, with no gap). Returns null if
     * [marker] isn't present or the run is interrupted before reaching it.
     */
    private fun findDrawerMarker(lines: List<String>, start: Int, end: Int, marker: String): Int? {
        var cursor = start
        while (cursor < end) {
            val trimmed = lines[cursor].trim().uppercase()
            if (trimmed == marker) return cursor
            if (trimmed != ":PROPERTIES:" && trimmed != ":LOGBOOK:") return null
            var i = cursor + 1
            while (i < end && !lines[i].trim().equals(":END:", ignoreCase = true)) i++
            if (i >= end) return null
            cursor = i + 1
        }
        return null
    }

    /**
     * Prepend [entry] to [h]'s `:LOGBOOK:` drawer (newest first, matching
     * Emacs' default `org-log-states-order-reversed`), creating the drawer
     * right after `:PROPERTIES:` (or at the top of the drawer run if there
     * isn't one) when it doesn't exist yet.
     */
    fun appendLogbookEntry(doc: OrgDocument, h: OrgHeadline, entry: String): String {
        val lines = doc.lines.toMutableList()
        val start = drawerScanStart(doc, h)
        val logbookMarker = findDrawerMarker(lines, start, h.bodyStart, ":LOGBOOK:")
        if (logbookMarker != null) {
            lines.add(logbookMarker + 1, entry)
        } else {
            val propertiesMarker = findDrawerMarker(lines, start, h.bodyStart, ":PROPERTIES:")
            val insertAt = if (propertiesMarker != null) {
                var end = propertiesMarker + 1
                while (!lines[end].trim().equals(":END:", ignoreCase = true)) end++
                end + 1
            } else start
            lines.addAll(insertAt, listOf(":LOGBOOK:", entry, ":END:"))
        }
        return lines.joinToString("\n")
    }

    /**
     * Add a free-text note to [h]'s `:LOGBOOK:` drawer (org's `C-c C-z`), creating
     * the drawer if it doesn't exist yet. Placed above the most recently added
     * note but below any other drawer entries (state-change lines, clock
     * entries, ...) — i.e. right before the first existing "Note taken on" line,
     * or at the very end of the drawer (before `:END:`) when there isn't one, so
     * a run of notes reads newest-first while staying a single contiguous block
     * after the drawer's history.
     */
    fun appendLogbookNote(doc: OrgDocument, h: OrgHeadline, note: String, at: OrgTimestamp): String {
        val entry = listOf("- Note taken on ${at.format()} \\\\") +
                note.trim('\n').split("\n").map { "  $it" }
        val lines = doc.lines.toMutableList()
        val start = drawerScanStart(doc, h)
        val logbookMarker = findDrawerMarker(lines, start, h.bodyStart, ":LOGBOOK:")
        if (logbookMarker != null) {
            var end = logbookMarker + 1
            while (!lines[end].trim().equals(":END:", ignoreCase = true)) end++
            val firstNoteLine = (logbookMarker + 1 until end)
                .firstOrNull { lines[it].trim().startsWith("- Note taken on", ignoreCase = true) }
            lines.addAll(firstNoteLine ?: end, entry)
        } else {
            val propertiesMarker = findDrawerMarker(lines, start, h.bodyStart, ":PROPERTIES:")
            val insertAt = if (propertiesMarker != null) {
                var end = propertiesMarker + 1
                while (!lines[end].trim().equals(":END:", ignoreCase = true)) end++
                end + 1
            } else start
            lines.addAll(insertAt, listOf(":LOGBOOK:") + entry + listOf(":END:"))
        }
        return lines.joinToString("\n")
    }

    private fun propertyKeyLine(key: String, line: String): Boolean {
        val m = Regex("""^\s*:([^:\s]+):""").find(line) ?: return false
        return m.groupValues[1].equals(key, ignoreCase = true)
    }

    /**
     * Set [key] to [value] in [h]'s `:PROPERTIES:` drawer, replacing an
     * existing entry for that key or inserting a new one, creating the
     * drawer at the top of the drawer run if it doesn't exist yet.
     */
    fun upsertProperty(doc: OrgDocument, h: OrgHeadline, key: String, value: String): String {
        val lines = doc.lines.toMutableList()
        val start = drawerScanStart(doc, h)
        val propertiesMarker = findDrawerMarker(lines, start, h.bodyStart, ":PROPERTIES:")
        val newLine = ":$key: $value"
        if (propertiesMarker != null) {
            var end = propertiesMarker + 1
            while (!lines[end].trim().equals(":END:", ignoreCase = true)) end++
            val existing = (propertiesMarker + 1 until end).firstOrNull { propertyKeyLine(key, lines[it]) }
            if (existing != null) lines[existing] = newLine else lines.add(end, newLine)
        } else {
            lines.addAll(start, listOf(":PROPERTIES:", newLine, ":END:"))
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
