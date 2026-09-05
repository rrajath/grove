package com.rrajath.grove.widget

import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.agenda.AgendaBuckets
import com.rrajath.grove.ui.agenda.AgendaMeta
import com.rrajath.grove.ui.agenda.AgendaMetaTone
import com.rrajath.grove.ui.agenda.AgendaRow
import com.rrajath.grove.ui.agenda.AgendaViewModel
import kotlinx.collections.immutable.toImmutableList
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
            rows = overdueNotes.map { widgetRow(it, today, showDate = true, settings) },
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
                rows = dayNotes.map { widgetRow(it, today, showDate = false, settings) },
            )
        }

        return listOfNotNull(overdueSection) + daySections
    }

    /**
     * Builds one widget row from [AgendaViewModel.row], then overrides its
     * tags/file/priority display with the widget's own settings — deliberately
     * independent of [GroveSettings.agendaShowTags] / [GroveSettings.agendaShowFile],
     * which govern the in-app Agenda screen instead (Settings § Agenda › Widget).
     */
    private fun widgetRow(m: NoteMeta, today: LocalDate, showDate: Boolean, settings: GroveSettings): AgendaRow {
        val rowSettings = settings.copy(agendaShowTags = settings.agendaWidgetShowTags, agendaShowFile = false)
        val row = AgendaViewModel.row(m, today, showDate, p = rowSettings)
        val meta = if (settings.agendaWidgetShowFileName) {
            (row.meta + AgendaMeta(contractFileName(m.fileName), AgendaMetaTone.MUTED)).toImmutableList()
        } else {
            row.meta
        }
        return row.copy(meta = meta, priority = row.priority.takeIf { settings.agendaWidgetShowPriority })
    }

    /**
     * Contracts a nested vault path to just its folders' initials, keeping the
     * file's own name whole: `"Work/Projects/meeting-notes.org"` →
     * `"W/P/meeting-notes.org"`. A top-level file (no `/`) is returned as-is.
     */
    fun contractFileName(fileName: String): String {
        val parts = fileName.split("/")
        if (parts.size <= 1) return fileName
        return parts.dropLast(1).joinToString("/") { it.take(1) } + "/" + parts.last()
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
