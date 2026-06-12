package com.rrajath.grove.org

/** Block-level structure of a note body, for read-mode rendering. */
sealed class OrgBlock {
    data class Paragraph(val lines: List<String>) : OrgBlock()
    data class ListBlock(val items: List<ListItem>) : OrgBlock()
    data class CodeBlock(val language: String?, val lines: List<String>) : OrgBlock()

    /** Org tables render as monospace plain text in v1 (PRD decision #4). */
    data class Table(val lines: List<String>) : OrgBlock()

    data class ListItem(val indent: Int, val ordered: Boolean, val text: String, val checkbox: Char?)
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

        fun flushParagraph(para: MutableList<String>) {
            if (para.isNotEmpty()) {
                blocks.add(OrgBlock.Paragraph(para.toList()))
                para.clear()
            }
        }

        val para = mutableListOf<String>()
        while (i < bodyLines.size) {
            val line = bodyLines[i]
            val srcMatch = BEGIN_SRC.find(line)
            val exampleMatch = if (srcMatch == null) BEGIN_EXAMPLE.find(line) else null
            when {
                srcMatch != null || exampleMatch != null -> {
                    flushParagraph(para)
                    val language = srcMatch?.groupValues?.get(1)?.takeIf { it.isNotEmpty() }
                    val endPattern = if (srcMatch != null) END_SRC else END_EXAMPLE
                    val code = mutableListOf<String>()
                    i++
                    while (i < bodyLines.size && !endPattern.containsMatchIn(bodyLines[i])) {
                        code.add(bodyLines[i])
                        i++
                    }
                    blocks.add(OrgBlock.CodeBlock(language, code))
                    i++ // skip END line (or run past EOF, fine)
                }

                line.trimStart().startsWith("|") -> {
                    flushParagraph(para)
                    val table = mutableListOf<String>()
                    while (i < bodyLines.size && bodyLines[i].trimStart().startsWith("|")) {
                        table.add(bodyLines[i])
                        i++
                    }
                    blocks.add(OrgBlock.Table(table))
                }

                UNORDERED.matches(line) || ORDERED.matches(line) -> {
                    flushParagraph(para)
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
                            )
                        )
                        i++
                    }
                    blocks.add(OrgBlock.ListBlock(items))
                }

                line.isBlank() -> {
                    flushParagraph(para)
                    i++
                }

                else -> {
                    para.add(line)
                    i++
                }
            }
        }
        flushParagraph(para)
        return blocks
    }
}
