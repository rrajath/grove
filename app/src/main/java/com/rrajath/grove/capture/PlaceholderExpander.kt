package com.rrajath.grove.capture

import com.rrajath.grove.org.OrgTimestamp
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale

/** Everything dynamic a template expansion can draw on, injected for testability. */
data class CaptureContext(
    val now: LocalDateTime,
    val clipboard: String = "",
    val sharedText: String = "",
    val sharedUrl: String = "",
    /** Answers for `%^{prompt}` placeholders, keyed by prompt text. */
    val promptValues: Map<String, String> = emptyMap(),
    /**
     * Date-granularity target (e.g. datetree by date): `%U`/`%u` expand without
     * a time-of-day, same as `%T`/`%t`.
     */
    val dateOnly: Boolean = false,
)

data class ExpandedTemplate(
    val text: String,
    /** Char offset where the cursor should land (`%cursor` / `%?`), or end of text. */
    val cursorOffset: Int,
)

/** Expands org-capture style placeholders (PRD §7.3). */
object PlaceholderExpander {

    // Closing brace must be escaped: Android's ICU regex engine rejects a bare
    // `}` outside a character class (the JVM engine used in unit tests allows it).
    private val PROMPT = Regex("""%\^\{([^}]*)\}""")

    /** Prompts the UI must collect (in order, deduplicated) before expanding. */
    fun prompts(template: String): List<String> =
        PROMPT.findAll(template).map { it.groupValues[1] }.distinct().toList()

    fun expand(template: String, ctx: CaptureContext): ExpandedTemplate {
        val date = ctx.now.toLocalDate()
        val time = ctx.now.toLocalTime()
        val dateStamp = OrgTimestamp(date)
        val dateTimeStamp =
            if (ctx.dateOnly) dateStamp
            else OrgTimestamp(date, time = time.withSecond(0).withNano(0))
        val timeText = "%d:%02d".format(time.hour, time.minute)

        val sb = StringBuilder()
        var cursor: Int? = null
        var i = 0
        while (i < template.length) {
            val ch = template[i]
            if (ch != '%') {
                sb.append(ch)
                i++
                continue
            }
            val promptMatch = PROMPT.matchAt(template, i)
            if (promptMatch != null) {
                sb.append(ctx.promptValues[promptMatch.groupValues[1]] ?: "")
                i += promptMatch.value.length
                continue
            }
            val simple = listOf(
                // Longest first so %time wins over %t
                "%shared_text" to { ctx.sharedText },
                "%shared_url" to { ctx.sharedUrl },
                "%clipboard" to { ctx.clipboard },
                "%cursor" to null,
                "%month" to { date.month.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                "%date" to { date.toString() },
                "%time" to { timeText },
                "%year" to { date.year.toString() },
                "%day" to { date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH) },
                "%t" to { dateStamp.copy(active = false).format() },
                "%T" to { dateStamp.format() },
                "%u" to { dateTimeStamp.copy(active = false).format() },
                "%U" to { dateTimeStamp.format() },
                "%?" to null,
            ).firstOrNull { (key, _) -> template.startsWith(key, i) }

            if (simple == null) {
                sb.append(ch)
                i++
                continue
            }
            val (key, value) = simple
            if (value == null) {
                if (cursor == null) cursor = sb.length
            } else {
                sb.append(value())
            }
            i += key.length
        }
        return ExpandedTemplate(sb.toString(), cursor ?: sb.length)
    }
}
