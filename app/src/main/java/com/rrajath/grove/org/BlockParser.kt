package com.rrajath.grove.org

/**
 * Block-level structure of a note body, for read-mode rendering.
 *
 * Each block (and each [ListItem]) carries the index of its first source line
 * *relative to the `bodyLines` list passed to [BlockParser.parse]* — read-mode
 * tap-to-edit position mapping uses this to translate a tapped rendered offset
 * back to a raw-file line + column (see ReadNoteScreen's offset mapping).
 */
sealed class OrgBlock {
    data class Paragraph(val lines: List<String>, val startLine: Int = 0) : OrgBlock()
    data class ListBlock(val items: List<ListItem>) : OrgBlock()
    data class CodeBlock(val language: String?, val lines: List<String>, val startLine: Int = 0) : OrgBlock()

    /** Org tables render as monospace plain text in v1 (PRD decision #4). */
    data class Table(val lines: List<String>, val startLine: Int = 0) : OrgBlock()

    data class ListItem(
        val indent: Int,
        val ordered: Boolean,
        val text: String,
        val checkbox: Char?,
        val line: Int = 0,
    )
}

object BlockParser {

    private val UNORDERED = Regex("""^(\s*)[-+]\s+(?:\[([ Xx-])\]\s+)?(.*)$""")
    private val ORDERED = Regex("""^(\s*)\d+[.)]\s+(?:\[([ Xx-])\]\s+)?(.*)$""")
    private val BEGIN_SRC = Regex("""^\s*#\+(?i:BEGIN_SRC)\s*(\S*)""")
    private val END_SRC = Regex("""^\s*#\+(?i:END_SRC)\s*$""")
    private val BEGIN_EXAMPLE = Regex("""^\s*#\+(?i:BEGIN_EXAMPLE)""")
    private val END_EXAMPLE = Regex("""^\s*#\+(?i:END_EXAMPLE)\s*$""")

    fun parse(bodyLines: List<String>): List<OrgBlock> {
        val blocks = mutableListOf<OrgBlock>()
        var i = 0

        val para = mutableListOf<String>()
        var paraStart = -1

        fun flushParagraph() {
            if (para.isNotEmpty()) {
                blocks.add(OrgBlock.Paragraph(para.toList(), paraStart))
                para.clear()
                paraStart = -1
            }
        }

        while (i < bodyLines.size) {
            val line = bodyLines[i]
            val srcMatch = BEGIN_SRC.find(line)
            val exampleMatch = if (srcMatch == null) BEGIN_EXAMPLE.find(line) else null
            when {
                srcMatch != null || exampleMatch != null -> {
                    flushParagraph()
                    val language = srcMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
                    val endPattern = if (srcMatch != null) END_SRC else END_EXAMPLE
                    val code = mutableListOf<String>()
                    i++
                    val codeStart = i
                    while (i < bodyLines.size && !endPattern.containsMatchIn(bodyLines[i])) {
                        code.add(bodyLines[i])
                        i++
                    }
                    blocks.add(OrgBlock.CodeBlock(language, code, codeStart))
                    i++ // skip END line (or run past EOF, fine)
                }

                line.trimStart().startsWith("|") -> {
                    flushParagraph()
                    val tableStart = i
                    val table = mutableListOf<String>()
                    while (i < bodyLines.size && bodyLines[i].trimStart().startsWith("|")) {
                        table.add(bodyLines[i])
                        i++
                    }
                    blocks.add(OrgBlock.Table(table, tableStart))
                }

                UNORDERED.matches(line) || ORDERED.matches(line) -> {
                    flushParagraph()
                    val items = mutableListOf<OrgBlock.ListItem>()
                    while (i < bodyLines.size) {
                        val m = UNORDERED.matchEntire(bodyLines[i])
                            ?: ORDERED.matchEntire(bodyLines[i])
                            ?: break
                        val ordered = ORDERED.matches(bodyLines[i])
                        items.add(
                            OrgBlock.ListItem(
                                indent = m.groupValues[1].length,
                                ordered = ordered,
                                text = m.groupValues[3],
                                checkbox = m.groupValues[2].firstOrNull(),
                                line = i,
                            )
                        )
                        i++
                    }
                    blocks.add(OrgBlock.ListBlock(items))
                }

                line.isBlank() -> {
                    flushParagraph()
                    i++
                }

                else -> {
                    if (para.isEmpty()) paraStart = i
                    para.add(line)
                    i++
                }
            }
        }
        flushParagraph()
        return blocks
    }
}
