package com.rrajath.grove.org

enum class InlineType {
    TEXT, BOLD, ITALIC, UNDERLINE, CODE, VERBATIM, LINK, TIMESTAMP,
}

/**
 * One inline span of a tokenized line.
 *
 * @param range char range in the source string (includes markers/brackets)
 * @param text display text (markers stripped; link label or target)
 * @param target link target (for [InlineType.LINK])
 */
data class InlineToken(
    val range: IntRange,
    val type: InlineType,
    val text: String,
    val target: String? = null,
)

/**
 * One-way tokenizer for org inline markup — used for syntax highlighting and
 * read-mode rendering only; never serialized back (the raw text is retained).
 */
object InlineTokenizer {

    // [[target][label]] or [[target]]
    private val LINK = Regex("""\[\[([^\[\]]+)](?:\[([^\[\]]+)])?]""")

    // Bare URLs and other schemes org recognizes unbracketed
    private val BARE_URL = Regex("""(?:https?://|mailto:|tel:|sms:|geo:|file:)[^\s\[\]<>]+""")

    private val TIMESTAMP = Regex("""[<\[]\d{4}-\d{2}-\d{2}[^>\]\n]*[>\]]""")

    private fun emphasis(marker: Char): Regex {
        val m = Regex.escape(marker.toString())
        // org emphasis: marker at a boundary, content not starting/ending with whitespace
        return Regex("""(?<=^|[\s('"{>])$m([^\s$m](?:[^$m\n]*[^\s$m])?)$m(?=$|[\s.,:!?;)'"}\]<])""")
    }

    private val BOLD = emphasis('*')
    private val ITALIC = emphasis('/')
    private val UNDERLINE = emphasis('_')
    private val CODE_TILDE = emphasis('~')
    private val CODE_BACKTICK = emphasis('`')
    private val VERBATIM = emphasis('=')

    /** Tokenize [line] into non-overlapping spans covering the whole string. */
    fun tokenize(line: String): List<InlineToken> {
        val found = mutableListOf<InlineToken>()

        fun addAll(regex: Regex, build: (MatchResult) -> InlineToken?) {
            for (m in regex.findAll(line)) {
                if (found.none { it.range.overlaps(m.range) }) {
                    build(m)?.let { found.add(it) }
                }
            }
        }

        addAll(LINK) { m ->
            val target = m.groupValues[1]
            val label = m.groupValues[2].takeIf { it.isNotEmpty() }
            InlineToken(m.range, InlineType.LINK, label ?: target, target)
        }
        addAll(TIMESTAMP) { m ->
            OrgTimestamp.parse(m.value)?.let {
                InlineToken(m.range, InlineType.TIMESTAMP, m.value)
            }
        }
        addAll(BARE_URL) { m ->
            InlineToken(m.range, InlineType.LINK, m.value, m.value)
        }
        addAll(BOLD) { m -> InlineToken(m.range, InlineType.BOLD, m.groupValues[1]) }
        addAll(ITALIC) { m -> InlineToken(m.range, InlineType.ITALIC, m.groupValues[1]) }
        addAll(UNDERLINE) { m -> InlineToken(m.range, InlineType.UNDERLINE, m.groupValues[1]) }
        addAll(CODE_TILDE) { m -> InlineToken(m.range, InlineType.CODE, m.groupValues[1]) }
        addAll(CODE_BACKTICK) { m -> InlineToken(m.range, InlineType.CODE, m.groupValues[1]) }
        addAll(VERBATIM) { m -> InlineToken(m.range, InlineType.VERBATIM, m.groupValues[1]) }

        found.sortBy { it.range.first }

        // Fill gaps with plain text tokens
        val result = mutableListOf<InlineToken>()
        var pos = 0
        for (token in found) {
            if (token.range.first > pos) {
                result.add(
                    InlineToken(pos until token.range.first, InlineType.TEXT, line.substring(pos, token.range.first))
                )
            }
            result.add(token)
            pos = token.range.last + 1
        }
        if (pos < line.length) {
            result.add(InlineToken(pos until line.length, InlineType.TEXT, line.substring(pos)))
        }
        return result
    }

    private fun IntRange.overlaps(other: IntRange): Boolean =
        first <= other.last && other.first <= last
}
