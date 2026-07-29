package com.rrajath.grove.org

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** Which planning line a date belongs to. */
enum class PlanningKind { SCHEDULED, DEADLINE }

/**
 * One line of shorthand typed into the Dates screen's `›` box, already parsed:
 * `fri`, `+2w`, `aug 3 10-11am`, `d: mon ++1w`. Every field is optional — a bare
 * `10-11am` sets only the times, a bare `++1w` only the repeater — so applying a
 * [DateShorthand] patches the target timestamp rather than replacing it.
 *
 * Pure JVM (no Android types) so the grammar stays unit-testable.
 */
data class DateShorthand(
    /** Set only when the line carried an explicit `s:` / `d:` prefix. */
    val target: PlanningKind? = null,
    val date: LocalDate? = null,
    val time: LocalTime? = null,
    val endTime: LocalTime? = null,
    val repeater: Repeater? = null,
)

sealed interface ShorthandParse {
    /** At least one of date / time / repeater was recognised. */
    data class Ok(val value: DateShorthand) : ShorthandParse

    /** Non-blank input that yielded nothing usable. */
    data object Unrecognised : ShorthandParse
}

/**
 * Grammar for [DateShorthand]. Each rule consumes the span it matched (replacing
 * it with a space so word boundaries survive) and the next rule runs on what is
 * left, which is what lets one line carry a target, a date, a time range and a
 * repeater at once.
 *
 * Weekday and "weekend"/"next week" always resolve *forward*: `fri` typed on a
 * Friday means the Friday a week out, never today.
 */
object DateShorthandParser {

    private val DEADLINE_PREFIX = Regex("""^(?:deadline|dead|dl|d)\s*:\s*""")
    private val SCHEDULED_PREFIX = Regex("""^(?:scheduled|sched|s)\s*:\s*""")
    private val REPEAT_COOKIE = Regex("""(\+\+|\.\+)(\d+)\s*([dwmyh])""")
    private val REPEAT_WORDS = Regex("""every\s*(\d+)?\s*(day|week|month|year|hour|d|w|m|y|h)s?""")
    private val ISO_DATE = Regex("""\d{4}-\d{2}-\d{2}""")
    private val OFFSET = Regex("""(?:^|\s)\+(\d+)\s*([dwmy])""")
    private val TODAY = Regex("""today|tod\b""")
    private val TOMORROW = Regex("""tomorrow|tmr|tom\b""")
    private val WEEKEND = Regex("""(?:this\s*)?weekend""")
    private val NEXT_WEEK = Regex("""next\s*week""")
    private val NEXT_MONTH = Regex("""next\s*month""")
    private val WEEKDAY = Regex("""(mon|tues|tue|thurs|thur|thu|wed|fri|sat|sun)[a-z]*""")
    private val MONTH_DAY =
        Regex("""(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*\.?\s*(\d{1,2})""")
    private val TIME_RANGE =
        Regex("""(\d{1,2})(?::(\d{2}))?\s*(am|pm)?\s*(?:-|–|to)\s*(\d{1,2})(?::(\d{2}))?\s*(am|pm)?""")
    private val TIME_HM = Regex("""(\d{1,2}):(\d{2})\s*(am|pm)?""")
    private val TIME_AMPM = Regex("""(\d{1,2})\s*(am|pm)""")

    private val MONTHS =
        listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    private val WEEKDAYS = mapOf(
        "sun" to DayOfWeek.SUNDAY, "mon" to DayOfWeek.MONDAY, "tue" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "thu" to DayOfWeek.THURSDAY, "fri" to DayOfWeek.FRIDAY,
        "sat" to DayOfWeek.SATURDAY,
    )

    /** Returns null for blank input — the echo line stays hidden until you type. */
    fun parse(raw: String, today: LocalDate): ShorthandParse? {
        var s = raw.trim().lowercase()
        if (s.isEmpty()) return null

        var target: PlanningKind? = null
        DEADLINE_PREFIX.find(s)?.let { m ->
            target = PlanningKind.DEADLINE
            s = s.blankOut(m.range)
        }
        if (target == null) {
            SCHEDULED_PREFIX.find(s)?.let { m ->
                target = PlanningKind.SCHEDULED
                s = s.blankOut(m.range)
            }
        }

        var repeater: Repeater? = null
        REPEAT_COOKIE.find(s)?.let { m ->
            val type = RepeaterType.fromMarker(m.groupValues[1])
            if (type != null) {
                repeater = Repeater(type, m.groupValues[2].toInt(), m.groupValues[3][0])
                s = s.blankOut(m.range)
            }
        }
        if (repeater == null) {
            REPEAT_WORDS.find(s)?.let { m ->
                val n = m.groupValues[1].ifEmpty { "1" }.toInt()
                repeater = Repeater(RepeaterType.CUMULATIVE, n, m.groupValues[2][0])
                s = s.blankOut(m.range)
            }
        }

        var date: LocalDate? = null
        ISO_DATE.find(s)?.let { m ->
            runCatching { LocalDate.parse(m.value) }.getOrNull()?.let {
                date = it
                s = s.blankOut(m.range)
            }
        }
        if (date == null) {
            OFFSET.find(s)?.let { m ->
                date = today.plusUnit(m.groupValues[1].toLong(), m.groupValues[2][0])
                s = s.blankOut(m.range)
            }
        }
        if (date == null) {
            val rules = listOf<Pair<Regex, (MatchResult) -> LocalDate?>>(
                TODAY to { today },
                TOMORROW to { today.plusDays(1) },
                WEEKEND to { nextDayOfWeek(today, DayOfWeek.SATURDAY) },
                NEXT_WEEK to { nextDayOfWeek(today, DayOfWeek.MONDAY) },
                NEXT_MONTH to { today.plusMonths(1) },
                WEEKDAY to { m ->
                    WEEKDAYS[m.groupValues[1].take(3)]?.let { nextDayOfWeek(today, it) }
                },
                MONTH_DAY to { m -> monthDay(today, m.groupValues[1], m.groupValues[2].toInt()) },
            )
            for ((regex, resolve) in rules) {
                val m = regex.find(s) ?: continue
                val resolved = resolve(m) ?: continue
                date = resolved
                s = s.blankOut(m.range)
                break
            }
        }

        var time: LocalTime? = null
        var endTime: LocalTime? = null
        TIME_RANGE.find(s)?.let { m ->
            // A trailing am/pm applies to both ends: "10-11am" is 10:00–11:00.
            val shared = m.groupValues[6].ifEmpty { m.groupValues[3] }
            val start = makeTime(m.groupValues[1], m.groupValues[2], m.groupValues[3].ifEmpty { shared })
            if (start != null) {
                time = start
                endTime = makeTime(m.groupValues[4], m.groupValues[5], shared)
                s = s.blankOut(m.range)
            }
        }
        if (time == null) {
            TIME_HM.find(s)?.let { m ->
                makeTime(m.groupValues[1], m.groupValues[2], m.groupValues[3])?.let {
                    time = it
                    s = s.blankOut(m.range)
                }
            }
        }
        if (time == null) {
            TIME_AMPM.find(s)?.let { m ->
                makeTime(m.groupValues[1], "", m.groupValues[2])?.let {
                    time = it
                    s = s.blankOut(m.range)
                }
            }
        }

        if (date == null && time == null && repeater == null) return ShorthandParse.Unrecognised
        return ShorthandParse.Ok(DateShorthand(target, date, time, endTime, repeater))
    }

    private fun String.blankOut(range: IntRange) = replaceRange(range, " ")

    private fun LocalDate.plusUnit(n: Long, unit: Char): LocalDate = when (unit) {
        'w' -> plusWeeks(n)
        'm' -> plusMonths(n)
        'y' -> plusYears(n)
        else -> plusDays(n)
    }

    /** The next [target] strictly after [today] — never [today] itself. */
    private fun nextDayOfWeek(today: LocalDate, target: DayOfWeek): LocalDate {
        val delta = ((target.value - today.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
        return today.plusDays(delta.toLong())
    }

    /** `aug 3` means this year's Aug 3, or next year's if that day has passed. */
    private fun monthDay(today: LocalDate, monthName: String, day: Int): LocalDate? {
        val month = MONTHS.indexOf(monthName) + 1
        if (month == 0) return null
        fun of(year: Int) = runCatching { LocalDate.of(year, month, day) }.getOrNull()
        val thisYear = of(today.year)
        return if (thisYear != null && !thisYear.isBefore(today)) thisYear else of(today.year + 1)
    }

    private fun makeTime(hour: String, minute: String, meridiem: String): LocalTime? {
        var h = hour.toIntOrNull() ?: return null
        if (meridiem == "pm" && h < 12) h += 12
        if (meridiem == "am" && h == 12) h = 0
        val m = if (minute.isEmpty()) 0 else minute.toIntOrNull() ?: return null
        return runCatching { LocalTime.of(h, m) }.getOrNull()
    }
}
