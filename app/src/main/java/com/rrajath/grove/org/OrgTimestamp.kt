package com.rrajath.grove.org

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

enum class RepeaterType(val marker: String) {
    /** `+`: shift by exactly one interval from the old date. */
    CUMULATIVE("+"),

    /** `++`: shift by intervals until the date lands in the future. */
    CATCH_UP("++"),

    /** `.+`: shift one interval from *today*. */
    FUTURE(".+");

    companion object {
        fun fromMarker(marker: String): RepeaterType? = entries.firstOrNull { it.marker == marker }
    }
}

data class Repeater(val type: RepeaterType, val value: Int, val unit: Char) {
    override fun toString(): String = "${type.marker}$value$unit"
}

/**
 * An org-mode timestamp: `<2025-04-30 Wed>`, `[2025-06-11 Wed 14:32]`,
 * `<2025-04-30 Wed 10:00-11:30 +1w -2d>` …
 *
 * Parsing is lenient about the day-name token; formatting always emits the
 * canonical English three-letter abbreviation (what Emacs writes by default).
 */
data class OrgTimestamp(
    val date: LocalDate,
    val time: LocalTime? = null,
    val endTime: LocalTime? = null,
    val active: Boolean = true,
    val repeater: Repeater? = null,
    /** Warning/delay cookie such as `-2d`, kept verbatim. */
    val warning: String? = null,
) {
    fun format(): String {
        val sb = StringBuilder()
        sb.append(if (active) '<' else '[')
        sb.append(date)
        sb.append(' ').append(dayAbbrev(date))
        if (time != null) {
            sb.append(' ').append(formatTime(time))
            if (endTime != null) sb.append('-').append(formatTime(endTime))
        }
        if (repeater != null) sb.append(' ').append(repeater)
        if (warning != null) sb.append(' ').append(warning)
        sb.append(if (active) '>' else ']')
        return sb.toString()
    }

    override fun toString(): String = format()

    /**
     * Reader-facing rendering of the same timestamp: `Jul 30`, `Jul 30 12:00`,
     * `Jul 30 12:00-13:30`, `Jul 30, 2027` for a date outside [today]'s year.
     *
     * Drops the org brackets and the day abbreviation (the month name already
     * reads as a date) but keeps a repeater and warning cookie verbatim: those
     * change what the timestamp *means*, so hiding them would lose information.
     * [format] remains the canonical on-disk form; this is display only.
     */
    fun formatHuman(today: LocalDate = LocalDate.now()): String {
        val sb = StringBuilder()
        sb.append(HUMAN_DATE.format(date))
        if (date.year != today.year) sb.append(", ").append(date.year)
        if (time != null) {
            sb.append(' ').append(formatTime(time))
            if (endTime != null) sb.append('-').append(formatTime(endTime))
        }
        if (repeater != null) sb.append(' ').append(repeater)
        if (warning != null) sb.append(' ').append(warning)
        return sb.toString()
    }

    companion object {
        // <2025-04-30 Wed 10:00-11:30 +1w -2d> with every part after the date optional
        private val PATTERN = Regex(
            """([<\[])(\d{4})-(\d{2})-(\d{2})(?:\s+([^\s\d>\]]+))?(?:\s+(\d{1,2}):(\d{2})(?:-(\d{1,2}):(\d{2}))?)?((?:\s+[.+]?\+\d+[hdwmy])?)((?:\s+-{1,2}\d+[hdwmy])?)\s*([>\]])""",
        )

        private val REPEATER = Regex("""([.+]?\+)(\d+)([hdwmy])""")

        /** `Jul 30`: the reader-facing date, see [formatHuman]. */
        private val HUMAN_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

        private fun formatTime(t: LocalTime): String =
            "%02d:%02d".format(t.hour, t.minute)

        fun dayAbbrev(date: LocalDate): String =
            date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)

        /** Parse the first timestamp in [text], or null. */
        fun parse(text: String): OrgTimestamp? = parseWithRange(text)?.first

        /** Parse the first timestamp in [text] returning it plus its char range. */
        fun parseWithRange(text: String): Pair<OrgTimestamp, IntRange>? {
            val m = PATTERN.find(text) ?: return null
            val (open, year, month, day) = m.destructured
            val close = m.groupValues[12]
            // Reject mismatched brackets like "<2025-01-01]"
            if ((open == "<") != (close == ">")) return null
            val date = try {
                LocalDate.of(year.toInt(), month.toInt(), day.toInt())
            } catch (e: java.time.DateTimeException) {
                return null
            }
            // Guarded the same way as the date above: the regex's `\d{1,2}:\d{2}`
            // happily matches out-of-range clock values like "25:00" or "10:99"
            // (typo'd by hand, or a >24h CLOCK duration cookie that looks like a
            // time), and LocalTime.of throws DateTimeException for those. Left
            // unguarded, that exception used to escape all the way out of
            // OrgTimestamp.parse into whichever caller triggered the lazy
            // NoteMeta.scheduledTs/deadlineTs evaluation first — most visibly the
            // agenda ledger widget's provideGlance, which has no caller-side
            // try/catch and has no visible "app crashed" UI to report it, so it
            // silently fell into the AppWidgetHost's generic "Can't show content"
            // fallback on every recomposition, surviving delete/re-add because
            // the bad timestamp lives in the vault, not the widget instance.
            val time = m.groupValues[6].takeIf { it.isNotEmpty() }?.let {
                try {
                    LocalTime.of(it.toInt(), m.groupValues[7].toInt())
                } catch (e: java.time.DateTimeException) {
                    return null
                }
            }
            val endTime = m.groupValues[8].takeIf { it.isNotEmpty() }?.let {
                try {
                    LocalTime.of(it.toInt(), m.groupValues[9].toInt())
                } catch (e: java.time.DateTimeException) {
                    return null
                }
            }
            val repeater = m.groupValues[10].trim().takeIf { it.isNotEmpty() }?.let { rep ->
                val repMatch = REPEATER.matchEntire(rep) ?: return@let null
                val type = RepeaterType.fromMarker(repMatch.groupValues[1]) ?: return@let null
                Repeater(type, repMatch.groupValues[2].toInt(), repMatch.groupValues[3][0])
            }
            val warning = m.groupValues[11].trim().takeIf { it.isNotEmpty() }
            return OrgTimestamp(
                date = date,
                time = time,
                endTime = endTime,
                active = open == "<",
                repeater = repeater,
                warning = warning,
            ) to (m.range)
        }
    }
}

/**
 * Advance a repeating timestamp per org rules when its task is marked done.
 * Returns the timestamp unchanged if it has no repeater.
 */
fun OrgTimestamp.advanceRepeater(today: LocalDate): OrgTimestamp {
    val rep = repeater ?: return this
    fun LocalDate.plusUnit(n: Long): LocalDate = when (rep.unit) {
        'h' -> this // hour repeaters shift time, not date; date stays (time handled below)
        'd' -> plusDays(n)
        'w' -> plusWeeks(n)
        'm' -> plusMonths(n)
        'y' -> plusYears(n)
        else -> this
    }

    val n = rep.value.toLong()
    val newDate = when (rep.type) {
        RepeaterType.CUMULATIVE -> date.plusUnit(n)
        RepeaterType.CATCH_UP -> {
            var d = date.plusUnit(n)
            while (!d.isAfter(today)) d = d.plusUnit(n)
            d
        }
        RepeaterType.FUTURE -> today.plusUnit(n)
    }
    val newTime = if (rep.unit == 'h' && time != null) {
        val total = when (rep.type) {
            RepeaterType.CUMULATIVE, RepeaterType.CATCH_UP -> rep.value
            RepeaterType.FUTURE -> rep.value
        }
        time.plusHours(total.toLong())
    } else time
    return copy(date = newDate, time = newTime)
}
