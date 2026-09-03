package com.rrajath.grove.org

/**
 * Block-level structure of a note body, for read-mode rendering.
 *
 * Each block (and each [ListItem]) carries the index of its first source line
 * *relative to the `bodyLines` list passed to [BlockParser.parse]*: read-mode
 * tap-to-edit position mapping uses this to translate a tapped rendered offset
 * back to a raw-file line + column (see ReadNoteScreen's offset mapping).
 */
sealed class OrgBlock {
    data class Paragraph(val lines: List<String>, val startLine: Int = 0) : OrgBlock()
    data class ListBlock(val items: List<ListItem>) : OrgBlock()

    /**
     * A `#+BEGIN_x … #+END_x` block, rendered as a collapsible drawer keyed by
     * [kind] (the upper-cased block type: `QUOTE`, `SRC`, `EXAMPLE`, `VERSE`,
     * `CENTER`, `LATEX`, a custom name, …).
     *
     * Also used for a run of standalone `#+KEYWORD:` lines that has no
     * `#+BEGIN`/`#+END` at all ([keywordRun] true): [kind] is `KEYWORDS` when the
     * run has two or more lines, otherwise the sole keyword's upper-cased name
     * (`CAPTION`, `NAME`, …). [contentLines] holds the raw keyword lines, verbatim.
     *
     * [language] is the `#+BEGIN_SRC <lang>` token when [kind] is `SRC`, else null.
     * [affiliated] holds any leading affiliated-keyword lines (`#+ATTR_*`,
     * `#+CAPTION:`, `#+NAME:`, `#+HEADER:`, `#+RESULTS:`) that sat immediately
     * above the `#+BEGIN` line, verbatim; they belong to this block and are shown
     * in its drawer. [contentLines] is everything between the markers.
     *
     * [startLine] is the body-relative index of the block's first line — the
     * first [affiliated] line when there is one, otherwise the `#+BEGIN` line
     * (or the first keyword line, for a [keywordRun]).
     */
    data class Block(
        val kind: String,
        val language: String?,
        val affiliated: List<String>,
        val contentLines: List<String>,
        val startLine: Int = 0,
        val keywordRun: Boolean = false,
    ) : OrgBlock()

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

    /** `#+BEGIN_<type>` (any type); group 1 is the type, group 2 the trailing text (SRC language / switches). */
    private val BEGIN = Regex("""^\s*#\+(?i:BEGIN_)(\S+)(.*)$""")

    /** `#+END_<type>`; group 1 is the type. */
    private val END = Regex("""^\s*#\+(?i:END_)(\S+)\s*$""")

    /**
     * Any standalone `#+KEYWORD:` line (`#+CAPTION:`, `#+NAME:`, `#+ATTR_HTML:`,
     * `#+TBLFM:`, a custom keyword, …) — anything that isn't a `#+BEGIN_`/`#+END_`
     * marker. A run of these directly above a `#+BEGIN` line folds into that
     * block's drawer; on its own it becomes its own collapsible block.
     */
    private val KEYWORD = Regex("""^\s*#\+(?!(?i:BEGIN_|END_))[A-Za-z][\w-]*:.*$""")

    /** The upper-cased keyword name of a [KEYWORD] line (`#+CAPTION: x` -> `CAPTION`). */
    private fun keywordName(line: String): String =
        Regex("""^\s*#\+([A-Za-z][\w-]*):""").find(line)?.groupValues?.get(1)?.uppercase() ?: "KEYWORD"

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

        /** Parse a `#+BEGIN_x … #+END_x` block that starts at [beginIndex]. */
        fun parseBlock(beginIndex: Int, affiliated: List<String>, blockStart: Int) {
            val m = BEGIN.find(bodyLines[beginIndex])!!
            val kind = m.groupValues[1].uppercase()
            val trailing = m.groupValues[2].trim()
            val language = if (kind == "SRC") {
                trailing.split(Regex("""\s+""")).firstOrNull()?.takeIf { it.isNotEmpty() }
            } else {
                null
            }
            var j = beginIndex + 1
            val content = mutableListOf<String>()
            while (j < bodyLines.size) {
                val end = END.find(bodyLines[j])
                if (end != null && end.groupValues[1].equals(kind, ignoreCase = true)) {
                    j++ // consume the END line
                    break
                }
                content.add(bodyLines[j])
                j++
            }
            blocks.add(OrgBlock.Block(kind, language, affiliated, content, blockStart))
            i = j
        }

        while (i < bodyLines.size) {
            val line = bodyLines[i]
            when {
                BEGIN.containsMatchIn(line) -> {
                    flushParagraph()
                    parseBlock(i, affiliated = emptyList(), blockStart = i)
                }

                KEYWORD.matches(line) -> {
                    // Collect the consecutive keyword run. If a `#+BEGIN` starts
                    // on the very next line, the run is that block's affiliated
                    // keywords and folds into its drawer. Otherwise the run is its
                    // own collapsible block: `KEYWORDS` for several lines, the
                    // keyword's own name for a one-off.
                    val runStart = i
                    val run = mutableListOf<String>()
                    var k = i
                    while (k < bodyLines.size && KEYWORD.matches(bodyLines[k])) {
                        run.add(bodyLines[k])
                        k++
                    }
                    flushParagraph()
                    if (k < bodyLines.size && BEGIN.containsMatchIn(bodyLines[k])) {
                        parseBlock(k, affiliated = run, blockStart = runStart)
                    } else {
                        val kind = if (run.size >= 2) "KEYWORDS" else keywordName(run[0])
                        blocks.add(
                            OrgBlock.Block(
                                kind = kind,
                                language = null,
                                affiliated = emptyList(),
                                contentLines = run,
                                startLine = runStart,
                                keywordRun = true,
                            )
                        )
                        i = k
                    }
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
