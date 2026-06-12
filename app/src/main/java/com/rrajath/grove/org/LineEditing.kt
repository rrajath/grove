package com.rrajath.grove.org

/** A text buffer plus cursor offset, the result of an editing helper. */
data class TextEdit(val text: String, val cursor: Int)

/**
 * Pure line-editing behaviors shared by the note editor and capture editor.
 * Operates on (text, cursor) so it stays JVM-testable without Compose types.
 */
object LineEditing {

    private val LIST_ITEM = Regex("""^(\s*)([-+]|\d+[.)])( +)(.*)$""")
    private val NUMBERED = Regex("""^(\d+)([.)])$""")
    private val EMPTY_HEADING = Regex("""^\*+ ?$""")

    /**
     * Org/markdown style list continuation. Call after an edit that may have
     * been the Enter key: if [newText] is [oldText] with a single newline typed
     * at [cursor] and the line before it is a list item, either continue the
     * list (`- `, `+ `, `3. `→`4. `) or — when the item was empty — remove the
     * dangling bullet instead. Returns null when the edit wasn't that.
     */
    fun continueListOnEnter(oldText: String, newText: String, cursor: Int): TextEdit? {
        if (newText.length != oldText.length + 1) return null
        if (cursor < 1 || cursor > newText.length || newText[cursor - 1] != '\n') return null
        if (newText.substring(0, cursor - 1) + newText.substring(cursor) != oldText) return null

        val lineStart = newText.lastIndexOf('\n', cursor - 2) + 1
        val prevLine = newText.substring(lineStart, cursor - 1)
        val item = LIST_ITEM.matchEntire(prevLine) ?: return null
        val (indent, bullet, _, content) = item.destructured

        return if (content.isBlank()) {
            // Enter on an empty item ends the list: drop the bullet, no new line.
            TextEdit(newText.substring(0, lineStart) + newText.substring(cursor), lineStart)
        } else {
            val nextBullet = NUMBERED.matchEntire(bullet)
                ?.destructured
                ?.let { (n, suffix) -> "${n.toLong() + 1}$suffix" }
                ?: bullet
            val insert = "$indent$nextBullet "
            TextEdit(
                newText.substring(0, cursor) + insert + newText.substring(cursor),
                cursor + insert.length,
            )
        }
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
