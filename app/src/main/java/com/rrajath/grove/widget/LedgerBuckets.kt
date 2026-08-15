package com.rrajath.grove.widget

import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.agenda.AgendaBuckets
import com.rrajath.grove.ui.agenda.AgendaRow
import com.rrajath.grove.ui.agenda.AgendaViewModel
import java.time.LocalDate

/**
 * Pure bucketing for the "Widget A · ledger" home-screen widget
 * (`design/Grove.dc.html`'s `wGroups('a')`, adapted per the widget-specific spec:
 * grouped by day rather than priority, with priority-then-time ordering *within*
 * each day).
 *
 * Deliberately free of Glance/Android so it stays unit-testable on the JVM, the
 * same way [AgendaBuckets] is; the widget itself only renders what [build] returns.
 */
object LedgerBuckets {

    /** One widget section: a header key/count and its already-sorted rows. */
    data class Section(val key: String, val count: Int, val rows: List<AgendaRow>)

    /**
     * Overdue (unbounded, oldest-first — same as the Agenda screen) first, then
     * one section per day from [today] through [today] + [windowDays] - 1 that
     * actually has a task, in date order. A day with no tasks is omitted
     * entirely rather than rendered empty.
     */
    fun build(notes: List<NoteMeta>, today: LocalDate, windowDays: Int, settings: GroveSettings): List<Section> {
        val overdueNotes = AgendaBuckets.overdue(notes, today)
        val overdueSection = if (overdueNotes.isEmpty()) null else Section(
            key = "Overdue",
            count = overdueNotes.size,
            rows = overdueNotes.map { AgendaViewModel.row(it, today, showDate = true, p = settings) },
        )

        val horizon = today.plusDays((windowDays - 1).coerceAtLeast(0).toLong())
        val upcoming = notes.filter {
            val d = AgendaBuckets.whenDate(it)
            d != null && !d.isBefore(today) && !d.isAfter(horizon)
        }
        val days = upcoming.mapNotNull { AgendaBuckets.whenDate(it) }.distinct().sorted()
        val daySections = days.map { day ->
            val dayNotes = AgendaBuckets.onDay(upcoming, day).sortedWith(AgendaBuckets.BY_PRIORITY)
            Section(
                key = dayHeader(day, today),
                count = dayNotes.size,
                rows = dayNotes.map { AgendaViewModel.row(it, today, showDate = false, p = settings) },
            )
        }

        return listOfNotNull(overdueSection) + daySections
    }

    /**
     * Splits [rows] into what a widget should render (at most [max]) and how many
     * were cut. Pulled out of [LedgerWidget]'s composition so it stays testable on
     * the JVM: Glance's `LazyColumn` ships every item inline in a single RemoteViews
     * binder call (there's no out-of-process adapter like classic AppWidget
     * ListViews), which Android caps around 1MB, so an unbounded section can take
     * the whole widget down.
     */
    fun truncate(rows: List<AgendaRow>, max: Int): Pair<List<AgendaRow>, Int> {
        val visible = rows.take(max)
        return visible to (rows.size - visible.size)
    }

    /** "Today · Aug 5" / "Tomorrow · Aug 6" / plain "Aug 7" past that. */
    fun dayHeader(day: LocalDate, today: LocalDate): String {
        val short = day.format(AgendaBuckets.SHORT_DATE)
        return when (day) {
            today -> "Today · $short"
            today.plusDays(1) -> "Tomorrow · $short"
            else -> short
        }
    }
}
