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
    /** Space-joined bare active timestamps in the note's own body (see NoteEntity). */
    val active: String? = null,
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
    val closedTime: LocalTime? get() = closedTs?.time
    val createdTime: LocalTime? get() = createdTs?.time

    /** Every parsed bare active timestamp in the note's body. */
    val activeTimestamps: List<OrgTimestamp> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        active?.let { OrgTimestamp.parseAll(it).filter { ts -> ts.active } } ?: emptyList()
    }

    /**
     * Every calendar day a bare active timestamp puts this note on: a single
     * stamp is one day, a ranged stamp is every day from start to end inclusive.
     */
    val activeDates: List<LocalDate> by lazy(LazyThreadSafetyMode.PUBLICATION) {
        activeTimestamps.flatMap { ts ->
            val end = ts.rangeEnd ?: ts.date
            generateSequence(ts.date) { d -> d.plusDays(1).takeIf { !it.isAfter(end) } }.toList()
        }
    }

    private fun tsOf(raw: String?) = lazy(LazyThreadSafetyMode.PUBLICATION) {
        raw?.let { OrgTimestamp.parse(it) }
    }
}

object QueryMatcher {

    fun matches(note: NoteMeta, query: SearchQuery, today: LocalDate): Boolean {
        if (!matchesAgendaWindow(note, query, today)) return false
        val expr = query.expr ?: return true
        return expr.matches { term -> matchesTerm(note, term, today) }
    }

    /**
     * The AND-groups [note] satisfies (empty when it doesn't match at all). A
     * query with no groups counts as one empty group, so a filter-only search
     * still reports why each note matched. Search uses this to decide which of
     * a note's lines to show as snippets. A query too large to flatten
     * ([SearchQuery.isFlatteningSkipped]) also reports one empty group on a
     * match, so its notes show as headings without text snippets.
     */
    fun satisfiedGroups(note: NoteMeta, query: SearchQuery, today: LocalDate): List<List<Term>> {
        if (!matchesAgendaWindow(note, query, today)) return emptyList()
        if (query.expr == null) return listOf(emptyList())
        if (query.isFlatteningSkipped) {
            return if (matches(note, query, today)) listOf(emptyList()) else emptyList()
        }
        return query.groups.filter { group -> group.all { term -> matchesTerm(note, term, today) } }
    }

    /**
     * `ad.N` (PRD §5.5): besides switching the results to a day-grouped agenda
     * view, it narrows to notes scheduled or with deadline within the next N
     * days (or overdue, same "on or before the pivot" rule [withinFuture] uses
     * for `s.`/`d.`). Applied independently of [SearchQuery.groups] so it still
     * filters when `ad.N` is the only token in the query.
     */
    private fun matchesAgendaWindow(note: NoteMeta, query: SearchQuery, today: LocalDate): Boolean {
        val days = query.agendaDays ?: return true
        val period = Period("${days}d")
        return withinFuture(note.scheduledDate, period, today) || withinFuture(note.deadlineDate, period, today)
    }

    /** [stateOrder] is the configured keyword sequence (active then done), used by `o.state`. */
    fun filter(
        notes: List<NoteMeta>,
        query: SearchQuery,
        today: LocalDate,
        stateOrder: List<String> = emptyList(),
    ): List<NoteMeta> = sort(notes.filter { matches(it, query, today) }, query, stateOrder)

    private fun matchesTerm(note: NoteMeta, term: Term, today: LocalDate): Boolean {
        val result = when (val c = term.condition) {
            is Condition.Text ->
                note.searchText.contains(c.term, ignoreCase = true)

            is Condition.State ->
                if (c.state.equals("none", true)) note.keyword == null
                else note.keyword?.equals(c.state, ignoreCase = true) == true

            is Condition.StateType -> when (c.type) {
                Condition.StateType.Type.TODO -> note.keyword != null && !note.isDoneKeyword
                Condition.StateType.Type.DONE -> note.keyword != null && note.isDoneKeyword
                Condition.StateType.Type.NONE -> note.keyword == null
            }

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
            is Condition.Active -> anyActiveWithin(note, c.period, today)
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
        compared(date, period, today, past = false)?.let { return it }
        if (period.isNoDate) return date == null
        if (date == null) return false
        if (period.isOverdue) return date.isBefore(today)
        period.exactDate(today)?.let { return date == it }
        val pivot = period.pivot(today) ?: return false
        return !date.isAfter(pivot)
    }

    /** a.: any bare active timestamp whose day satisfies the window, mirroring
     *  [withinFuture] over the whole list (a ranged event contributes every day
     *  it spans via [NoteMeta.activeDates]). `a.overdue` matches an event that
     *  has fully passed (its last day is before today) on purpose: search is
     *  explicit, only the agenda hides overdue events. */
    private fun anyActiveWithin(note: NoteMeta, period: Period, today: LocalDate): Boolean {
        val dates = note.activeDates
        period.op?.let { op ->
            val target = period.compareTarget(today, past = false) ?: return false
            return dates.any { op.test(it, target) }
        }
        if (period.isNoDate) return dates.isEmpty()
        if (dates.isEmpty()) return false
        if (period.isOverdue) {
            return note.activeTimestamps.any { (it.rangeEnd ?: it.date).isBefore(today) }
        }
        period.exactDate(today)?.let { d -> return dates.any { it == d } }
        val pivot = period.pivot(today) ?: return false
        return dates.any { !it.isAfter(pivot) }
    }

    /** c./cr.: timestamp within [pastPivot, today] for a relative window;
     *  single-day tokens and "overdue" behave the same as for s./d. above. */
    private fun withinPast(date: LocalDate?, period: Period, today: LocalDate): Boolean {
        compared(date, period, today, past = true)?.let { return it }
        if (period.isNoDate) return date == null
        if (date == null) return false
        if (period.isOverdue) return date.isBefore(today)
        period.exactDate(today)?.let { return date == it }
        val pivot = period.pastPivot(today) ?: return false
        return !date.isBefore(pivot) && !date.isAfter(today)
    }

    /** `s.le.3d`-style comparison: null when [period] has no operator, false
     *  when the timestamp is missing or the token names no single day. */
    private fun compared(date: LocalDate?, period: Period, today: LocalDate, past: Boolean): Boolean? {
        val op = period.op ?: return null
        val target = period.compareTarget(today, past) ?: return false
        return date != null && op.test(date, target)
    }

    // --- ranking ---

    /**
     * `o.PROP` sorts when present (Orgzly's property set, `.o.PROP` reversed);
     * otherwise PRD §11 ranking: exact title > title contains > body match,
     * recency as tiebreaker. Notes missing the sorted property go last in
     * either direction. `o.state` follows [stateOrder], the configured keyword
     * sequence; an unconfigured keyword sorts after every configured one.
     */
    fun sort(notes: List<NoteMeta>, query: SearchQuery, stateOrder: List<String> = emptyList()): List<NoteMeta> {
        if (query.sortBy.isNotEmpty()) {
            val comparator = query.sortBy
                .map { comparatorFor(it, stateOrder) }
                .reduce { acc, next -> acc.then(next) }
            return notes.sortedWith(comparator)
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

    private fun comparatorFor(key: SortKey, stateOrder: List<String>): Comparator<NoteMeta> {
        val desc = key.descending
        return when (key.field) {
            SortField.NOTEBOOK -> nullsLast(desc) { it.fileName.lowercase() }
            SortField.TITLE -> nullsLast(desc) { it.title.lowercase() }
            SortField.SCHEDULED -> nullsLast(desc) { it.scheduledDate?.atTime(it.scheduledTime ?: LocalTime.MIN) }
            SortField.DEADLINE -> nullsLast(desc) { it.deadlineDate?.atTime(it.deadlineTime ?: LocalTime.MIN) }
            SortField.CLOSED -> nullsLast(desc) { it.closedDate?.atTime(it.closedTime ?: LocalTime.MIN) }
            SortField.CREATED -> nullsLast(desc) { it.createdDate?.atTime(it.createdTime ?: LocalTime.MIN) }
            // Orgzly: ascending uses a note's oldest event, descending its most recent.
            SortField.EVENT -> nullsLast(desc) { note ->
                val times = note.activeTimestamps.map { it.date.atTime(it.time ?: LocalTime.MIN) }
                if (desc) times.maxOrNull() else times.minOrNull()
            }
            SortField.PRIORITY -> nullsLast(desc) { it.priority?.uppercase() }
            SortField.STATE -> nullsLast(desc) { note ->
                note.keyword?.let { keyword ->
                    stateOrder.indexOfFirst { it.equals(keyword, ignoreCase = true) }
                        .takeIf { it >= 0 } ?: stateOrder.size
                }
            }
        }
    }

    private fun <T : Comparable<T>> nullsLast(descending: Boolean, selector: (NoteMeta) -> T?): Comparator<NoteMeta> =
        Comparator { a, b ->
            val x = selector(a)
            val y = selector(b)
            when {
                x == null && y == null -> 0
                x == null -> 1
                y == null -> -1
                descending -> y.compareTo(x)
                else -> x.compareTo(y)
            }
        }

}
