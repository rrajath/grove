package com.rrajath.grove.search

import com.rrajath.grove.org.OrgTimestamp
import java.time.LocalDate
import java.time.LocalTime

/** Index-row view the matcher operates on (kept android/Room-free). */
data class NoteMeta(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    val keyword: String?,
    val isDoneKeyword: Boolean,
    val priority: String?,
    val tags: List<String>,
    val inheritedTags: List<String>,
    val scheduled: String?,
    val deadline: String?,
    val closed: String?,
    val createdAt: String?,
    val lastModified: Long,
    /** Heading + body text for plain-term matching. */
    val searchText: String,
) {
    // Parsed once per instance; matching, sorting, and the agenda view would
    // otherwise re-run the timestamp regex per comparison / per agenda day.
    private val scheduledTs by tsOf(scheduled)
    private val deadlineTs by tsOf(deadline)
    private val closedTs by tsOf(closed)
    private val createdTs by tsOf(createdAt)

    val scheduledDate: LocalDate? get() = scheduledTs?.date
    val deadlineDate: LocalDate? get() = deadlineTs?.date
    val closedDate: LocalDate? get() = closedTs?.date
    val createdDate: LocalDate? get() = createdTs?.date
    val scheduledTime: LocalTime? get() = scheduledTs?.time
    val deadlineTime: LocalTime? get() = deadlineTs?.time

    private fun tsOf(raw: String?) = lazy(LazyThreadSafetyMode.PUBLICATION) {
        raw?.let { OrgTimestamp.parse(it) }
    }
}

object QueryMatcher {

    fun matches(note: NoteMeta, query: SearchQuery, today: LocalDate): Boolean {
        if (query.groups.isEmpty()) return true
        return query.groups.any { group -> group.all { term -> matchesTerm(note, term, today) } }
    }

    fun filter(notes: List<NoteMeta>, query: SearchQuery, today: LocalDate): List<NoteMeta> =
        sort(notes.filter { matches(it, query, today) }, query)

    private fun matchesTerm(note: NoteMeta, term: Term, today: LocalDate): Boolean {
        val result = when (val c = term.condition) {
            is Condition.Text ->
                note.searchText.contains(c.term, ignoreCase = true)

            is Condition.State ->
                if (c.state.equals("none", true)) note.keyword == null
                else note.keyword?.equals(c.state, ignoreCase = true) == true

            is Condition.Notebook ->
                note.fileName.removeSuffix(".org").equals(c.name.removeSuffix(".org"), true)

            is Condition.Tag -> {
                val pool = if (c.ownOnly) note.tags else note.inheritedTags
                pool.any { it.contains(c.tag, ignoreCase = true) }
            }

            is Condition.Priority ->
                note.priority?.equals(c.priority, ignoreCase = true) == true

            is Condition.Scheduled -> withinFuture(note.scheduledDate, c.period, today)
            is Condition.Deadline -> withinFuture(note.deadlineDate, c.period, today)
            is Condition.Closed -> withinPast(note.closedDate, c.period, today)
            is Condition.Created -> withinPast(note.createdDate, c.period, today)
        }
        return result != term.negated
    }

    /** s./d.: on or before the period pivot for a relative window (e.g. `s.3d`
     *  = scheduled in the next three days or overdue); "today"/"tomorrow"/
     *  "yesterday" match that exact day instead of a window, and "overdue"
     *  matches anything strictly before today. */
    private fun withinFuture(date: LocalDate?, period: Period, today: LocalDate): Boolean {
        if (period.isNoDate) return date == null
        if (date == null) return false
        if (period.isOverdue) return date.isBefore(today)
        period.exactDate(today)?.let { return date == it }
        val pivot = period.pivot(today) ?: return false
        return !date.isAfter(pivot)
    }

    /** c./cr.: timestamp within [pastPivot, today] for a relative window;
     *  single-day tokens and "overdue" behave the same as for s./d. above. */
    private fun withinPast(date: LocalDate?, period: Period, today: LocalDate): Boolean {
        if (period.isNoDate) return date == null
        if (date == null) return false
        if (period.isOverdue) return date.isBefore(today)
        period.exactDate(today)?.let { return date == it }
        val pivot = period.pastPivot(today) ?: return false
        return !date.isBefore(pivot) && !date.isAfter(today)
    }

    // --- ranking ---

    /**
     * `o.PROP` sorts when present; otherwise PRD §11 ranking:
     * exact title > title contains > body match, recency as tiebreaker.
     */
    fun sort(notes: List<NoteMeta>, query: SearchQuery): List<NoteMeta> {
        if (query.sortBy.isNotEmpty()) {
            var comparator: Comparator<NoteMeta>? = null
            for (key in query.sortBy) {
                val next: Comparator<NoteMeta> = when (key) {
                    "priority", "p" -> compareBy { it.priority ?: "Z" }
                    "scheduled", "s" -> compareBy { it.scheduledDate ?: LocalDate.MAX }
                    "deadline", "d" -> compareBy { it.deadlineDate ?: LocalDate.MAX }
                    "created", "cr" -> compareBy { it.createdDate ?: LocalDate.MAX }
                    "title" -> compareBy { it.title.lowercase() }
                    "notebook", "b" -> compareBy { it.fileName.lowercase() }
                    else -> continue
                }
                comparator = comparator?.then(next) ?: next
            }
            if (comparator != null) return notes.sortedWith(comparator)
        }
        val terms = query.textTerms
        if (terms.isEmpty()) return notes
        return notes.sortedWith(
            compareBy<NoteMeta> { note ->
                when {
                    terms.any { note.title.equals(it, true) } -> 0
                    terms.any { note.title.contains(it, true) } -> 1
                    else -> 2
                }
            }.thenByDescending { it.lastModified }
        )
    }

}
