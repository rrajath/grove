package com.rrajath.grove.search

import java.time.LocalDate

/**
 * Orgzly-compatible structured search (PRD §5.5).
 * Space = AND, `OR` = or (binds looser), `.` prefix = NOT,
 * `o.PROP` = sort, `ad.N` = agenda day-grouping.
 */
data class SearchQuery(
    /** OR of AND-groups. Empty = match everything. */
    val groups: List<List<Term>>,
    val sortBy: List<String> = emptyList(),
    val agendaDays: Int? = null,
) {
    val isEmpty: Boolean get() = groups.all { it.isEmpty() } && agendaDays == null

    /** Plain full-text terms (for FTS narrowing and match highlighting). */
    val textTerms: List<String>
        get() = groups.flatten().filter { !it.negated && it.condition is Condition.Text }
            .map { (it.condition as Condition.Text).term }
}

data class Term(val condition: Condition, val negated: Boolean)

sealed class Condition {
    data class Text(val term: String) : Condition()

    /** `i.STATE` — keyword match, case-insensitive; `i.none` = no keyword. */
    data class State(val state: String) : Condition()
    data class Notebook(val name: String) : Condition()
    data class Tag(val tag: String, val ownOnly: Boolean) : Condition()
    data class Priority(val priority: String) : Condition()
    data class Scheduled(val period: Period) : Condition()
    data class Deadline(val period: Period) : Condition()
    data class Closed(val period: Period) : Condition()
    data class Created(val period: Period) : Condition()
}

/** A relative time window token: today, tomorrow, yesterday, now, Nd/Nw/Nm. */
data class Period(val raw: String) {
    /**
     * Future pivot date for s./d. ("within period" = on or before pivot,
     * e.g. `s.3d` = scheduled in the next three days or overdue).
     */
    fun pivot(today: LocalDate): LocalDate? {
        val t = raw.lowercase()
        return when (t) {
            "today", "now" -> today
            "tomorrow" -> today.plusDays(1)
            "yesterday" -> today.minusDays(1)
            else -> {
                val m = RELATIVE.matchEntire(t) ?: return null
                val n = m.groupValues[1].toLong()
                when (m.groupValues[2]) {
                    "d" -> today.plusDays(n)
                    "w" -> today.plusWeeks(n)
                    "m" -> today.plusMonths(n)
                    else -> null
                }
            }
        }
    }

    /** Past pivot for c./cr. windows ([pivot, today]). */
    fun pastPivot(today: LocalDate): LocalDate? {
        val p = pivot(today) ?: return null
        val delta = java.time.temporal.ChronoUnit.DAYS.between(today, p)
        return today.minusDays(kotlin.math.abs(delta))
    }

    private companion object {
        val RELATIVE = Regex("""(\d+)([dwm])""")
    }
}

object QueryParser {

    fun parse(input: String): SearchQuery {
        val tokens = input.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val groups = mutableListOf<MutableList<Term>>(mutableListOf())
        val sortBy = mutableListOf<String>()
        var agendaDays: Int? = null

        for (token in tokens) {
            if (token.equals("OR", ignoreCase = false)) {
                groups.add(mutableListOf())
                continue
            }
            val negated = token.startsWith(".") && token.length > 1 && !token[1].isDigit()
            val body = if (negated) token.drop(1) else token

            val lower = body.lowercase()
            when {
                lower.startsWith("o.") -> sortBy.add(body.drop(2).lowercase())
                lower.startsWith("ad.") -> agendaDays = body.drop(3).toIntOrNull()
                else -> {
                    val condition = parseCondition(body)
                    groups.last().add(Term(condition, negated))
                }
            }
        }
        return SearchQuery(
            groups = groups.filter { it.isNotEmpty() },
            sortBy = sortBy,
            agendaDays = agendaDays,
        )
    }

    private fun parseCondition(body: String): Condition {
        val dot = body.indexOf('.')
        if (dot in 1..2) {
            val prefix = body.substring(0, dot).lowercase()
            val value = body.substring(dot + 1)
            if (value.isNotEmpty()) {
                when (prefix) {
                    "i" -> return Condition.State(value)
                    "b" -> return Condition.Notebook(value)
                    "t" -> return Condition.Tag(value, ownOnly = false)
                    "tn" -> return Condition.Tag(value, ownOnly = true)
                    "p" -> return Condition.Priority(value)
                    "s" -> return Condition.Scheduled(Period(value))
                    "d" -> return Condition.Deadline(Period(value))
                    "c" -> return Condition.Closed(Period(value))
                    "cr" -> return Condition.Created(Period(value))
                }
            }
        }
        return Condition.Text(body)
    }
}
