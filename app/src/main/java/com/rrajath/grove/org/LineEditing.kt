package com.rrajath.grove.org

/** A text buffer plus cursor offset, the result of an editing helper. */
data class TextEdit(val text: String, val cursor: Int)

/**
 * Pure line-editing behaviors shared by the note editor and capture editor.
 * Operates on (text, cursor) so it stays JVM-testable without Compose types.
 */
object LineEditing {

    // Groups: 1 indent, 2 bullet, 3 spaces after bullet, 4 checkbox marker
    // ("[ ]"/"[x]"/"[X]"/"[-]", or absent), 5 spaces after the checkbox, 6 content.
    private val LIST_ITEM = Regex("""^(\s*)([-+]|\d+[.)])( +)(\[[ Xx-]\])?( *)(.*)$""")
    private val NUMBERED = Regex("""^(\d+)([.)])$""")
    private val EMPTY_HEADING = Regex("""^\*+ ?$""")

    /**
     * Org/markdown style list continuation. The caller (see `OrgInputTransformation`)
     * has already established, from the field's actual change list rather than a
     * whole-text diff, that this edit's only newline-adjacent change is a solitary
     * `"\n"` inserted right before [cursor] — so it doesn't matter whether an IME
     * also finalized/autocorrected a composing word earlier on the same line in the
     * same batch (a real Enter press right after a mid-line edit used to look, to a
     * naive whole-text length diff, like "more than one character changed" and get
     * silently dropped). Only the resulting line, read from [text], decides whether
     * to continue the list (`- `, `+ `, `3. `→`4. `, `- [ ] `→`- [ ] `) or, when the
     * item was empty, remove the dangling bullet instead. A continued checklist item
     * always restarts unchecked, regardless of the previous item's state. Returns
     * null when the line before the cursor isn't a list item.
     */
    fun continueListOnEnter(text: String, cursor: Int): TextEdit? {
        if (cursor < 1 || cursor > text.length || text[cursor - 1] != '\n') return null

        val lineStart = text.lastIndexOf('\n', cursor - 2) + 1
        val prevLine = text.substring(lineStart, cursor - 1)
        val item = LIST_ITEM.matchEntire(prevLine) ?: return null
        val (indent, bullet, _, checkbox, _, content) = item.destructured

        return if (content.isBlank()) {
            // Enter on an empty item ends the list: drop the bullet, no new line.
            TextEdit(text.substring(0, lineStart) + text.substring(cursor), lineStart)
        } else {
            val nextBullet = NUMBERED.matchEntire(bullet)
                ?.destructured
                ?.let { (n, suffix) -> "${n.toLong() + 1}$suffix" }
                ?: bullet
            val checkboxPart = if (checkbox.isNotEmpty()) "[ ] " else ""
            val insert = "$indent$nextBullet $checkboxPart"
            TextEdit(
                text.substring(0, cursor) + insert + text.substring(cursor),
                cursor + insert.length,
            )
        }
    }

    private const val INDENT_STEP = "  "

    /**
     * Toolbar indent buttons: shift the list item under the cursor one level
     * deeper ([delta] > 0) or shallower by [INDENT_STEP], turning it into a
     * sub-list item or promoting it back. Returns null when the cursor line
     * isn't a list item, or when outdenting an item already at column zero.
     */
    fun changeListIndent(text: String, cursor: Int, delta: Int): TextEdit? {
        val at = cursor.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', at - 1) + 1
        val lineEnd = text.indexOf('\n', at).let { if (it == -1) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        val item = LIST_ITEM.matchEntire(line) ?: return null
        val (indent, bullet, spaces, checkbox, checkboxSpace, content) = item.destructured
        val newLine = if (delta > 0) {
            // A fresh sub-list restarts numbering at 1 (unordered bullets unchanged).
            val newBullet = NUMBERED.matchEntire(bullet)
                ?.destructured?.let { (_, suffix) -> "1$suffix" }
                ?: bullet
            INDENT_STEP + indent + newBullet + spaces + checkbox + checkboxSpace + content
        } else {
            if (indent.isEmpty()) return null
            line.substring(minOf(INDENT_STEP.length, indent.length))
        }
        return TextEdit(
            text.substring(0, lineStart) + newLine + text.substring(lineEnd),
            (at + newLine.length - line.length).coerceAtLeast(lineStart),
        )
    }

    private val HEADING_FIRST_CHAR = Regex("""^\*+ [a-z]$""")

    /**
     * Auto-capitalize the first letter typed into an org heading. Returns null
     * when no capitalization is needed (not a heading line, char already uppercase,
     * or cursor not right after the first content character).
     *
     * Call this after [continueListOnEnter] in the text-change handler, passing
     * the old text (before the edit) and the new text + cursor from the IME.
     */
    fun capitalizeHeadingOnType(oldText: String, newText: String, cursor: Int): TextEdit? {
        if (newText.length != oldText.length + 1) return null
        if (cursor < 1 || cursor > newText.length) return null
        val newChar = newText[cursor - 1]
        if (!newChar.isLetter() || newChar.isUpperCase()) return null
        val lineStart = newText.lastIndexOf('\n', cursor - 2) + 1
        val lineUpToCursor = newText.substring(lineStart, cursor)
        if (!HEADING_FIRST_CHAR.matches(lineUpToCursor)) return null
        val capitalized = newText.substring(0, cursor - 1) + newChar.uppercaseChar() + newText.substring(cursor)
        return TextEdit(capitalized, cursor)
    }

    /**
     * Toolbar `*` button: on an empty heading line (`* `, `** `…) demote it by
     * one star; anywhere else start a new heading on the next line.
     */
    fun insertHeadingStar(text: String, cursor: Int): TextEdit {
        val at = cursor.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', at - 1) + 1
        val lineEnd = text.indexOf('\n', at).let { if (it == -1) text.length else it }
        val line = text.substring(lineStart, lineEnd)
        if (EMPTY_HEADING.matches(line)) {
            val newLine = "*".repeat(line.trimEnd().length + 1) + " "
            return TextEdit(
                text.substring(0, lineStart) + newLine + text.substring(lineEnd),
                lineStart + newLine.length,
            )
        }
        val snippet = "\n* "
        return TextEdit(
            text.substring(0, at) + snippet + text.substring(at),
            at + snippet.length,
        )
    }
}
