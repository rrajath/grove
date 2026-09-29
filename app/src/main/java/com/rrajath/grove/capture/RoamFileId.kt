package com.rrajath.grove.capture

import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser

/**
 * Every Roam node file gets a file-level `:PROPERTIES:` drawer with an `:ID:`,
 * added automatically at write time rather than by the template's own text.
 * Any `:ID:` a [CaptureTemplate.newFileTemplate] still carries is ignored in
 * favour of the generated one; its other drawer properties are kept.
 */
object RoamFileId {

    private val ID_LINE = Regex("""^\s*:ID:.*$""", RegexOption.IGNORE_CASE)

    /** Whether [newFileTemplate]'s leading file-level drawer defines an `:ID:` (which will be ignored). */
    fun templateDefinesId(newFileTemplate: String): Boolean {
        val doc = OrgParser.parse(newFileTemplate)
        val range = OrgMutations.fileDrawerRange(doc) ?: return false
        return doc.lines.subList(range.first + 1, range.last).any { ID_LINE.matches(it) }
    }

    /**
     * Splits [text]'s leading file-level drawer off: the drawer (its `:ID:`
     * lines dropped; "" when nothing else is left in it) and the rest of the
     * text. [cursorOffset] is re-based into the rest (clamped to its start
     * when it sat inside the drawer).
     */
    fun splitDrawer(text: String, cursorOffset: Int): Triple<String, String, Int> {
        val doc = OrgParser.parse(text)
        val range = OrgMutations.fileDrawerRange(doc) ?: return Triple("", text, cursorOffset)
        val drawerLines = doc.lines.subList(range.first, range.last + 1)
        val rest = doc.lines.drop(range.last + 1).joinToString("\n")
        val removed = drawerLines.sumOf { it.length + 1 }
        val props = drawerLines.subList(1, drawerLines.size - 1).filterNot { ID_LINE.matches(it) }
        val drawer = if (props.isEmpty()) "" else (listOf(drawerLines.first()) + props + drawerLines.last()).joinToString("\n")
        return Triple(drawer, rest, (cursorOffset - removed).coerceIn(0, rest.length))
    }

    /** [drawer] (from [splitDrawer]) back on top of [rest]. */
    fun joinDrawer(drawer: String, rest: String): String = if (drawer.isEmpty()) rest else "$drawer\n$rest"

    /**
     * [text] with its file-level `:ID:` set to [id]: any existing `:ID:` lines
     * in the leading drawer are replaced by one right after `:PROPERTIES:`, or
     * a new drawer holding just the `:ID:` is prepended when there is none.
     */
    fun withFileId(text: String, id: String): String {
        val doc = OrgParser.parse(text)
        val range = OrgMutations.fileDrawerRange(doc)
            ?: return ":PROPERTIES:\n:ID: $id\n:END:\n$text"
        val lines = doc.lines
        val props = lines.subList(range.first + 1, range.last).filterNot { ID_LINE.matches(it) }
        val drawer = listOf(lines[range.first], ":ID: $id") + props + lines[range.last]
        return (drawer + lines.drop(range.last + 1)).joinToString("\n")
    }

    /** [text] unchanged when it already has a file-level `:ID:`, else [withFileId]. */
    fun ensureFileId(text: String, id: String): String =
        if (OrgParser.parse(text).fileId.isNullOrBlank()) withFileId(text, id) else text
}
