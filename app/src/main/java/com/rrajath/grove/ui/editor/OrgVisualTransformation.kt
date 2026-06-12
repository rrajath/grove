package com.rrajath.grove.ui.editor

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import com.rrajath.grove.org.InlineTokenizer
import com.rrajath.grove.org.InlineType
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.ui.theme.GroveColors

/**
 * Highlight-only syntax colouring for the raw org editor (design spec §6).
 * Text is never altered, so [OffsetMapping.Identity] applies — cursor math
 * stays trivial and fast.
 */
class OrgVisualTransformation(
    private val colors: GroveColors,
    private val keywords: OrgKeywords,
) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlight(text.text), OffsetMapping.Identity)

    fun highlight(raw: String): AnnotatedString = buildAnnotatedString {
        append(raw)
        var lineStart = 0
        for (line in raw.lineSequence()) {
            styleLine(line, lineStart)
            lineStart += line.length + 1
        }
    }

    private fun AnnotatedString.Builder.styleLine(line: String, start: Int) {
        val headline = HEADLINE.matchEntire(line)
        when {
            headline != null -> styleHeadline(line, start, headline)
            isPlanning(line) -> stylePlanning(line, start)
            isDrawerLine(line) -> addStyle(SpanStyle(color = colors.synProp), start, start + line.length)
            PROPERTY.matchEntire(line) != null -> styleProperty(line, start)
            else -> styleInline(line, start)
        }
    }

    private fun AnnotatedString.Builder.styleHeadline(line: String, start: Int, m: MatchResult) {
        val stars = m.groupValues[1]
        addStyle(
            SpanStyle(color = colors.synStar, fontWeight = FontWeight.SemiBold),
            start, start + stars.length,
        )
        var rest = m.groupValues[2]
        var offset = start + stars.length + 1

        val firstWord = rest.substringBefore(' ')
        if (firstWord in keywords.all) {
            val color = if (keywords.isDone(firstWord)) colors.synDone else colors.synTodo
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), offset, offset + firstWord.length)
            offset += firstWord.length
            rest = rest.drop(firstWord.length)
            val trimmed = rest.trimStart()
            offset += rest.length - trimmed.length
            rest = trimmed
        }
        PRIORITY.find(rest)?.let { p ->
            if (p.range.first == 0) {
                addStyle(SpanStyle(color = colors.red, fontWeight = FontWeight.Bold), offset, offset + p.value.length)
            }
        }
        TAGS_AT_END.find(line)?.let { tags ->
            addStyle(SpanStyle(color = colors.synTag), start + tags.range.first, start + tags.range.last + 1)
        }
        // Heading text itself: ink, semibold
        addStyle(
            SpanStyle(color = colors.ink, fontWeight = FontWeight.SemiBold),
            start + stars.length + 1,
            start + line.length,
        )
        // Re-apply keyword/priority/tags over the heading style
        if (firstWord in keywords.all) {
            val color = if (keywords.isDone(firstWord)) colors.synDone else colors.synTodo
            val kwStart = start + stars.length + 1
            addStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold), kwStart, kwStart + firstWord.length)
        }
        TAGS_AT_END.find(line)?.let { tags ->
            addStyle(
                SpanStyle(color = colors.synTag, fontWeight = FontWeight.Normal),
                start + tags.range.first, start + tags.range.last + 1,
            )
        }
    }

    private fun AnnotatedString.Builder.stylePlanning(line: String, start: Int) {
        addStyle(SpanStyle(color = colors.synKw), start, start + line.length)
        for (m in TIMESTAMP.findAll(line)) {
            addStyle(SpanStyle(color = colors.synTs), start + m.range.first, start + m.range.last + 1)
        }
        for (m in PLANNING_KW.findAll(line)) {
            addStyle(
                SpanStyle(color = colors.synKw, fontWeight = FontWeight.SemiBold),
                start + m.range.first, start + m.range.last + 1,
            )
        }
    }

    private fun AnnotatedString.Builder.styleProperty(line: String, start: Int) {
        val m = PROPERTY.matchEntire(line) ?: return
        val keyEnd = line.indexOf(':', line.indexOf(':') + 1) + 1
        addStyle(SpanStyle(color = colors.synKw), start, start + keyEnd)
        addStyle(SpanStyle(color = colors.synProp), start + keyEnd, start + line.length)
    }

    private fun AnnotatedString.Builder.styleInline(line: String, start: Int) {
        for (token in InlineTokenizer.tokenize(line)) {
            val s = start + token.range.first
            val e = start + token.range.last + 1
            when (token.type) {
                InlineType.TEXT -> {}
                InlineType.BOLD -> addStyle(SpanStyle(color = colors.synTag, fontWeight = FontWeight.SemiBold), s, e)
                InlineType.ITALIC -> addStyle(SpanStyle(color = colors.green, fontStyle = FontStyle.Italic), s, e)
                InlineType.UNDERLINE -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), s, e)
                InlineType.CODE -> addStyle(SpanStyle(color = colors.accent, background = colors.surface2), s, e)
                InlineType.VERBATIM -> addStyle(SpanStyle(color = colors.accent, background = colors.surface2), s, e)
                InlineType.LINK -> addStyle(
                    SpanStyle(color = colors.synLink, textDecoration = TextDecoration.Underline), s, e,
                )
                InlineType.TIMESTAMP -> addStyle(SpanStyle(color = colors.synTs), s, e)
            }
        }
    }

    private fun isPlanning(line: String): Boolean {
        val t = line.trimStart()
        return t.startsWith("SCHEDULED:") || t.startsWith("DEADLINE:") || t.startsWith("CLOSED:")
    }

    private fun isDrawerLine(line: String): Boolean {
        val t = line.trim()
        return t.equals(":PROPERTIES:", true) || t.equals(":END:", true)
    }

    companion object {
        private val HEADLINE = Regex("""^(\*+)\s+(.*)$""")
        private val PRIORITY = Regex("""\[#[A-Za-z]\]""")
        private val TAGS_AT_END = Regex("""\s+(:[A-Za-z0-9_@#%]+(?::[A-Za-z0-9_@#%]+)*:)\s*$""")
        private val TIMESTAMP = Regex("""[<\[][^>\]]*[>\]]""")
        private val PLANNING_KW = Regex("""(SCHEDULED|DEADLINE|CLOSED):""")
        private val PROPERTY = Regex("""^\s*:([^:\s]+):\s*(.*)$""")
    }
}
