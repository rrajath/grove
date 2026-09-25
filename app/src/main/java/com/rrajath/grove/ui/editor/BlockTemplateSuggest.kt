package com.rrajath.grove.ui.editor

import androidx.compose.ui.text.TextRange

/**
 * Org block templates offered as a suggestion chip when the matching `<x` shorthand
 * (after Emacs org-tempo) is typed: `<q` quote, `<e` example, `<s` src.
 */
enum class BlockTemplate(val trigger: Char, val keyword: String, val label: String) {
    QUOTE('q', "QUOTE", "quote"),
    EXAMPLE('e', "EXAMPLE", "example"),
    SRC('s', "SRC", "src"),
}

/** A typed `<x` shorthand and the range it occupies in the buffer. */
data class BlockTrigger(val template: BlockTemplate, val range: TextRange)

/** A [BlockTrigger] expanded: replace [start]..[end] with [replacement], caret at [cursor]. */
data class BlockInsertion(val start: Int, val end: Int, val replacement: String, val cursor: Int)

/**
 * The `<q` / `<e` / `<s` just typed at a collapsed cursor, or null. The `<` must
 * start the line or follow whitespace, and nothing but whitespace (or the end of
 * the buffer) may follow the cursor, so `a<q`, `<qu` and `<q|x` don't trigger.
 * Never fires in the preface ([isInPreface]), whose `#+KEY:` lines aren't body text.
 */
fun blockTemplateTriggerAt(text: CharSequence, selection: TextRange): BlockTrigger? {
    if (!selection.collapsed) return null
    val cursor = selection.start
    if (cursor < 2 || cursor > text.length) return null
    if (text[cursor - 2] != '<') return null
    val template = BlockTemplate.entries.firstOrNull { it.trigger == text[cursor - 1] } ?: return null
    if (cursor > 2 && !text[cursor - 3].isWhitespace()) return null
    if (cursor < text.length && !text[cursor].isWhitespace()) return null
    if (isInPreface(text, cursor)) return null
    return BlockTrigger(template, TextRange(cursor - 2, cursor))
}

/**
 * Expands [trigger] into its `#+BEGIN_x`/`#+END_x` block, indented like the line it
 * was typed on (so a block typed in a list item stays in that item). The shorthand
 * is removed; if that leaves the line blank the block takes its place, otherwise the
 * rest of the line is kept as is and the block starts on the next line. The caret
 * lands on the block's empty body line, except for src, where it lands after
 * `#+BEGIN_SRC ` so the language can be typed first.
 */
fun expandBlockTemplate(text: String, trigger: BlockTrigger): BlockInsertion {
    val start = trigger.range.min
    val end = trigger.range.max
    val lineStart = text.lastIndexOf('\n', start - 1) + 1
    val lineEnd = text.indexOf('\n', end).let { if (it == -1) text.length else it }
    val indent = text.substring(lineStart).takeWhile { it == ' ' || it == '\t' }
    // The line minus the shorthand and the whitespace around it: `a <q b` -> `a b`.
    val before = text.substring(lineStart, start).trimEnd()
    val after = text.substring(end, lineEnd).trim()
    val rest = when {
        after.isEmpty() -> before
        before.isBlank() -> indent + after
        else -> "$before $after"
    }
    val kw = trigger.template.keyword
    val begin = if (trigger.template == BlockTemplate.SRC) "$indent#+BEGIN_$kw " else "$indent#+BEGIN_$kw"
    val block = "$begin\n$indent\n$indent#+END_$kw"
    val prefix = if (rest.isBlank()) "" else "$rest\n"
    val caretInBlock = if (trigger.template == BlockTemplate.SRC) begin.length else begin.length + 1 + indent.length
    return BlockInsertion(lineStart, lineEnd, prefix + block, lineStart + prefix.length + caretInBlock)
}
