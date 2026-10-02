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

/**
 * Settings that change what a query means.
 * [stateOrder]: the configured keyword sequence (active then done), for `o.state`.
 * [defaultPriority]: org's default priority; `p.X` and `o.p` treat a note
 * with no priority as having it (null = no default).
 */
data class MatchOptions(
    val stateOrder: List<String> = emptyList(),
    val defaultPriority: String? = null,
)

object QueryMatcher {

    fun matches(note: NoteMeta, query: SearchQuery, today: LocalDate, options: MatchOptions = MatchOptions()): Boolean {
        if (!matchesAgendaWindow(note, query, today)) return false
        val expr = query.expr ?: return true
        return expr.matches { term -> matchesTerm(note, term, today, options) }
    }

    /**
     * The AND-groups [note] satisfies (empty when it doesn't match at all). A
     * query with no groups counts as one empty group, so a filter-only search
     * still reports why each note matched. Search uses this to decide which of
     * a note's lines to show as snippets. A query too large to flatten
     * ([SearchQuery.isFlatteningSkipped]) also reports one empty group on a
     * match, so its notes show as headings without text snippets.
     */
    fun satisfiedGroups(
        note: NoteMeta,
        query: SearchQuery,
        today: LocalDate,
        options: MatchOptions = MatchOptions(),
    ): List<List<Term>> {
        if (!matchesAgendaWindow(note, query, today)) return emptyList()
        if (query.expr == null) return listOf(emptyList())
        if (query.isFlatteningSkipped) {
            return if (matches(note, query, today, options)) listOf(emptyList()) else emptyList()
        }
        return query.groups.filter { group -> group.all { term -> matchesTerm(note, term, today, options) } }
    }

    /**
     * `ad.N` (PRD §5.5): besides switching the results to a day-grouped agenda
     * view, it narrows to notes scheduled or with deadline within the next N
     * days (or overdue: the same `le` rule a plain `s.Nd`/`d.Nd` uses).
     * Applied independently of [SearchQuery.groups] so it still
     * filters when `ad.N` is the only token in the query.
     */
    private fun matchesAgendaWindow(note: NoteMeta, query: SearchQuery, today: LocalDate): Boolean {
        val days = query.agendaDays ?: return true
        val period = Period("${days}d")
        return matchesDate(note.scheduledDate, period, today, CompareOp.LE) ||
            matchesDate(note.deadlineDate, period, today, CompareOp.LE)
    }

    fun filter(
        notes: List<NoteMeta>,
        query: SearchQuery,
        today: LocalDate,
        options: MatchOptions = MatchOptions(),
    ): List<NoteMeta> = sort(notes.filter { matches(it, query, today, options) }, query, options)

    private fun matchesTerm(note: NoteMeta, term: Term, today: LocalDate, options: MatchOptions): Boolean {
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

            is Condition.Priority -> {
                val effective = if (c.setOnly) note.priority else note.priority ?: options.defaultPriority
                effective?.equals(c.priority, ignoreCase = true) == true
            }

            // Orgzly's default operators when none is written: le for s./d./cr., eq for c./a.
            is Condition.Scheduled -> matchesDate(note.scheduledDate, c.period, today, CompareOp.LE)
            is Condition.Deadline -> matchesDate(note.deadlineDate, c.period, today, CompareOp.LE)
            is Condition.Active -> anyActiveMatches(note, c.period, today)
            is Condition.Closed -> matchesDate(note.closedDate, c.period, today, CompareOp.EQ)
            is Condition.Created -> matchesDate(note.createdDate, c.period, today, CompareOp.LE)
        }
        return result != term.negated
    }

    /**
     * [date] compared against the day [period] names, with its own op or
     * [defaultOp]. The op-less specials: `none` requires no date, `overdue`
     * requires one strictly before today. A token naming no day matches nothing.
     */
    private fun matchesDate(date: LocalDate?, period: Period, today: LocalDate, defaultOp: CompareOp): Boolean {
        if (period.isNoDate) return date == null
        if (date == null) return false
        if (period.isOverdue) return date.isBefore(today)
        val target = period.target(today) ?: return false
        return (period.op ?: defaultOp).test(date, target)
    }

    /** a./e.: any bare active timestamp day satisfying the comparison (default
     *  `eq`), a ranged event contributing every day it spans via
     *  [NoteMeta.activeDates]. `a.overdue` matches an event that has fully
     *  passed (its last day is before today) on purpose: search is explicit,
     *  only the agenda hides overdue events. */
    private fun anyActiveMatches(note: NoteMeta, period: Period, today: LocalDate): Boolean {
        val dates = note.activeDates
        if (period.isNoDate) return dates.isEmpty()
        if (period.isOverdue) {
            return note.activeTimestamps.any { (it.rangeEnd ?: it.date).isBefore(today) }
        }
        val target = period.target(today) ?: return false
        val op = period.op ?: CompareOp.EQ
        return dates.any { op.test(it, target) }
    }

    // --- ranking ---

    /**
     * `o.PROP` sorts when present (Orgzly's property set, `.o.PROP` reversed);
     * otherwise PRD §11 ranking: exact title > title contains > body match,
     * recency as tiebreaker. Notes missing the sorted property go last in
     * either direction. `o.state` follows [MatchOptions.stateOrder]; an
     * unconfigured keyword sorts after every configured one. `o.p` counts a
     * note with no priority as [MatchOptions.defaultPriority].
     */
    fun sort(notes: List<NoteMeta>, query: SearchQuery, options: MatchOptions = MatchOptions()): List<NoteMeta> {
        if (query.sortBy.isNotEmpty()) {
            val comparator = query.sortBy
                .map { comparatorFor(it, options) }
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

    private fun comparatorFor(key: SortKey, options: MatchOptions): Comparator<NoteMeta> {
        val stateOrder = options.stateOrder
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
            SortField.PRIORITY -> nullsLast(desc) { (it.priority ?: options.defaultPriority)?.uppercase() }
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
