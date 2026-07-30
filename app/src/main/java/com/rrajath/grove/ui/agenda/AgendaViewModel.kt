package com.rrajath.grove.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.data.toNoteMeta
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.search.NoteMeta
import com.rrajath.grove.settings.AgendaGrouping
import com.rrajath.grove.settings.AgendaStateFilter
import com.rrajath.grove.settings.AgendaSwipeAction
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.vault.OutlineSnack
import com.rrajath.grove.ui.vault.factory
import com.rrajath.grove.ui.vault.headlineAtLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/** Which theme color one meta chip on an agenda row renders in. */
enum class AgendaMetaTone { NORMAL, MUTED, DANGER, TAG }

/** One entry in a row's mono meta strip: the date, a `⚑` deadline, a time range, `↻` repeater, tags, or the file. */
data class AgendaMeta(val text: String, val tone: AgendaMetaTone)

data class AgendaRow(
    val fileName: String,
    val lineIndex: Int,
    val title: String,
    val keyword: String?,
    val isDone: Boolean,
    val priority: String?,
    val meta: List<AgendaMeta>,
    /** Prefills the Dates screen for the swipe-to-schedule/deadline actions. */
    val scheduledTs: OrgTimestamp?,
    val deadlineTs: OrgTimestamp?,
)

/** One "Group by" bucket: an uppercase key, its count, and its rows. */
data class AgendaGroup(val key: String, val count: Int, val rows: List<AgendaRow>)

/** The Today/Upcoming segmented control. Navigational, so it is not persisted. */
enum class AgendaTab { TODAY, UPCOMING }

data class AgendaUiState(
    /** "Wednesday" — the big header line. */
    val headerDay: String = "",
    /** "July 29" — the mono sub-line, followed by [todayCount]. */
    val headerDate: String = "",
    val todayCount: Int = 0,
    val tab: AgendaTab = AgendaTab.TODAY,
    val leversOpen: Boolean = false,
    val overdueOpen: Boolean = false,
    val overdue: List<AgendaRow> = emptyList(),
    val groups: List<AgendaGroup> = emptyList(),
    val grouping: AgendaGrouping = AgendaGrouping.DATE,
    val stateFilter: AgendaStateFilter = AgendaStateFilter.OPEN,
    val showTags: Boolean = true,
    val showFile: Boolean = false,
    val swipeLeftAction: AgendaSwipeAction = AgendaSwipeAction.MARK_DONE,
    val swipeRightAction: AgendaSwipeAction = AgendaSwipeAction.SET_SCHEDULED,
) {
    val overdueCount: Int get() = overdue.size
    val isEmpty: Boolean get() = overdue.isEmpty() && groups.isEmpty()
}

/**
 * The "Agenda A · focus" screen from `design/Grove.dc.html`: today's work first,
 * an overdue card above it, and a levers panel that re-buckets the same list by
 * date, priority, tag, or file.
 *
 * An item belongs to exactly one day — its SCHEDULED date if it has one, else
 * its DEADLINE — which is what makes the "Group by · Date" buckets disjoint. A
 * heading that has both shows on its scheduled day with a red `⚑ <date>` chip
 * announcing the deadline, rather than appearing twice.
 *
 * Unlike Search, Agenda also owns a mutation path: the row checkbox toggles
 * done, swipe gestures (configured in Settings § Agenda) set SCHEDULED/DEADLINE
 * or mark done, and the overdue card's "Move to today" rewrites every overdue
 * planning date at once. All of them are undoable while the snackbar is up.
 */
class AgendaViewModel(private val app: GroveApplication) : ViewModel() {

    private val _state = MutableStateFlow(AgendaUiState())
    val state: StateFlow<AgendaUiState> = _state

    /** Undo snackbar for the mutating actions (design spec: ~4.2s with an UNDO action). */
    private val _snack = MutableStateFlow<OutlineSnack?>(null)
    val snack: StateFlow<OutlineSnack?> = _snack

    private var matched: List<NoteMeta> = emptyList()
    private var windowDays = INITIAL_WINDOW_DAYS

    @Volatile
    private var prefs: GroveSettings = GroveSettings()

    // Session-only view state; the levers themselves persist via settings.
    private var tab = AgendaTab.TODAY
    private var leversOpen = false
    private var overdueOpen = false

    @Volatile
    private var loadingMore = false

    private var eventId = 0L

    /** Pre-mutation text of every file one action touched — "Move to today" spans several. */
    private data class FileSnapshot(val fileName: String, val text: String)

    private var undoSnapshot: List<FileSnapshot> = emptyList()

    init {
        viewModelScope.launch {
            app.settingsRepository.settings.collect { settings ->
                prefs = settings
                recompute()
            }
        }
        viewModelScope.launch {
            // Only rows with a SCHEDULED or DEADLINE: the agenda buckets notes
            // purely by those dates, so an undated note can never surface here
            // and there is no reason to hold one in memory.
            app.database.indexDao().plannedNotes()
                .map { rows -> rows.map { it.toNoteMeta() } }
                .flowOn(Dispatchers.Default)
                .collect { notes ->
                    matched = notes
                    recompute()
                }
        }
    }

    // --- view state ---

    fun setTab(next: AgendaTab) {
        tab = next
        recompute()
    }

    fun toggleLevers() {
        leversOpen = !leversOpen
        recompute()
    }

    fun toggleOverdue() {
        overdueOpen = !overdueOpen
        recompute()
    }

    fun setGrouping(grouping: AgendaGrouping) = persist { it.setAgendaGrouping(grouping) }

    fun setStateFilter(filter: AgendaStateFilter) = persist { it.setAgendaStateFilter(filter) }

    fun setShowTags(show: Boolean) = persist { it.setAgendaShowTags(show) }

    fun setShowFile(show: Boolean) = persist { it.setAgendaShowFile(show) }

    private fun persist(block: suspend (com.rrajath.grove.settings.SettingsRepository) -> Unit) {
        viewModelScope.launch { block(app.settingsRepository) }
    }

    /** Grows the future-day window on scroll; overdue and today never repage. */
    fun loadMoreDays() {
        if (loadingMore || windowDays >= MAX_WINDOW_DAYS) return
        loadingMore = true
        windowDays = (windowDays + PAGE_SIZE).coerceAtMost(MAX_WINDOW_DAYS)
        viewModelScope.launch(Dispatchers.Default) {
            _state.value = buildState()
            loadingMore = false
        }
    }

    private fun recompute() {
        viewModelScope.launch(Dispatchers.Default) { _state.value = buildState() }
    }

    // --- list construction ---

    private fun buildState(): AgendaUiState {
        val today = LocalDate.now()
        val p = prefs
        val visible = matched.filter { keep(it, p.agendaStateFilter) }

        val todayItems = visible.filter { whenDate(it) == today }
        val overdueItems = visible
            .filter { whenDate(it)?.isBefore(today) == true }
            .sortedWith(
                compareBy<NoteMeta> { whenDate(it) ?: LocalDate.MAX }
                    .thenBy { it.priority ?: NO_PRIORITY_SORT_KEY }
                    .thenBy { it.title.lowercase() }
            )
        val horizon = today.plusDays((windowDays - 1).toLong())
        val futureItems = visible.filter {
            val d = whenDate(it)
            d != null && d.isAfter(today) && !d.isAfter(horizon)
        }

        val list = if (tab == AgendaTab.TODAY) todayItems else futureItems
        // Date grouping already puts the day in the section header; every other
        // grouping mixes days together, so the rows have to carry it themselves.
        val showDate = p.agendaGrouping != AgendaGrouping.DATE && tab != AgendaTab.TODAY

        return AgendaUiState(
            headerDay = today.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH),
            headerDate = today.format(HEADER_DATE),
            todayCount = todayItems.size,
            tab = tab,
            leversOpen = leversOpen,
            overdueOpen = overdueOpen,
            overdue = overdueItems.map { row(it, today, showDate = true, p = p) },
            groups = group(list, today, showDate, p),
            grouping = p.agendaGrouping,
            stateFilter = p.agendaStateFilter,
            showTags = p.agendaShowTags,
            showFile = p.agendaShowFile,
            swipeLeftAction = p.agendaSwipeLeftAction,
            swipeRightAction = p.agendaSwipeRightAction,
        )
    }

    private fun group(
        list: List<NoteMeta>,
        today: LocalDate,
        showDate: Boolean,
        p: GroveSettings,
    ): List<AgendaGroup> {
        fun bucket(key: String, notes: List<NoteMeta>, order: Comparator<NoteMeta>) =
            AgendaGroup(key, notes.size, notes.sortedWith(order).map { row(it, today, showDate, p) })

        return when (p.agendaGrouping) {
            AgendaGrouping.DATE ->
                if (tab == AgendaTab.TODAY) {
                    if (list.isEmpty()) emptyList()
                    else listOf(bucket("Scheduled today", list, BY_TIME))
                } else {
                    list.mapNotNull { whenDate(it) }.distinct().sorted().map { day ->
                        bucket(dayLabel(day, today), list.filter { whenDate(it) == day }, BY_TIME)
                    }
                }

            AgendaGrouping.PRIORITY ->
                PRIORITY_BUCKETS.mapNotNull { (key, label) ->
                    val notes = list.filter { it.priority == key }
                    if (notes.isEmpty()) null else bucket(label, notes, BY_TIME)
                }

            AgendaGrouping.TAG -> {
                val keys = LinkedHashSet<String>()
                list.forEach { keys += tagsOf(it) }
                keys.map { tag -> bucket(":$tag:", list.filter { tag in tagsOf(it) }, BY_PRIORITY) }
            }

            AgendaGrouping.FILE ->
                list.map { it.fileName }.distinct().map { file ->
                    bucket(file, list.filter { it.fileName == file }, BY_PRIORITY)
                }
        }
    }

    private fun row(m: NoteMeta, today: LocalDate, showDate: Boolean, p: GroveSettings): AgendaRow {
        val scheduledTs = m.scheduled?.let { OrgTimestamp.parse(it) }
        val deadlineTs = m.deadline?.let { OrgTimestamp.parse(it) }
        // The timestamp that decides which day the row lands on.
        val anchor = scheduledTs ?: deadlineTs
        val anchorDate = anchor?.date
        val overdue = anchorDate != null && anchorDate.isBefore(today)
        // Deadline-only: the row's own date *is* the deadline, so it reads red.
        val deadlineOnly = deadlineTs != null && scheduledTs == null

        val meta = buildList {
            if (showDate && anchorDate != null) {
                val late = ChronoUnit.DAYS.between(anchorDate, today)
                val text = (if (deadlineOnly) "⚑ " else "") +
                    if (overdue) "${anchorDate.format(SHORT_DATE)} · ${late}d late"
                    else dayLabel(anchorDate, today)
                add(AgendaMeta(text, if (overdue || deadlineOnly) AgendaMetaTone.DANGER else AgendaMetaTone.NORMAL))
            } else if (deadlineOnly) {
                add(AgendaMeta("⚑ due", AgendaMetaTone.DANGER))
            }
            anchor?.time?.let { start ->
                val range = start.format(CLOCK) + (anchor.endTime?.let { "–${it.format(CLOCK)}" } ?: "")
                add(AgendaMeta(range, AgendaMetaTone.NORMAL))
            }
            if (deadlineTs != null && scheduledTs != null) {
                add(AgendaMeta("⚑ ${deadlineTs.date.format(SHORT_DATE)}", AgendaMetaTone.DANGER))
            }
            (scheduledTs?.repeater ?: deadlineTs?.repeater)?.let {
                add(AgendaMeta("↻ $it", AgendaMetaTone.MUTED))
            }
            if (p.agendaShowTags) {
                m.tags.takeIf { it.isNotEmpty() }
                    ?.let { add(AgendaMeta(it.joinToString(":", ":", ":"), AgendaMetaTone.TAG)) }
            }
            if (p.agendaShowFile) add(AgendaMeta(m.fileName, AgendaMetaTone.MUTED))
        }

        return AgendaRow(
            fileName = m.fileName,
            lineIndex = m.lineIndex,
            title = m.title,
            keyword = m.keyword,
            isDone = m.isDoneKeyword,
            priority = m.priority,
            meta = meta,
            scheduledTs = scheduledTs,
            deadlineTs = deadlineTs,
        )
    }

    private fun keep(m: NoteMeta, filter: AgendaStateFilter) = when (filter) {
        AgendaStateFilter.ALL -> true
        AgendaStateFilter.NEXT -> !m.isDoneKeyword && m.keyword == NEXT_KEYWORD
        AgendaStateFilter.OPEN -> !m.isDoneKeyword && m.keyword != WAITING_KEYWORD
    }

    /** Tags a row can be grouped under; an untagged heading gets its own bucket. */
    private fun tagsOf(m: NoteMeta): List<String> = m.tags.ifEmpty { listOf(UNTAGGED) }

    // --- swipe-to-act mutations ---

    fun setScheduled(fileName: String, lineIndex: Int, ts: OrgTimestamp?) =
        mutatePlanning(fileName, lineIndex) { doc, h -> OrgMutations.setScheduled(doc, h, ts) }

    fun setDeadline(fileName: String, lineIndex: Int, ts: OrgTimestamp?) =
        mutatePlanning(fileName, lineIndex) { doc, h -> OrgMutations.setDeadline(doc, h, ts) }

    /** Both planning dates in one edit — what the Dates screen commits. */
    fun setPlanningDates(
        fileName: String,
        lineIndex: Int,
        scheduled: OrgTimestamp?,
        deadline: OrgTimestamp?,
    ) = mutatePlanning(fileName, lineIndex) { doc, h ->
        OrgMutations.setPlanningDates(doc, h, scheduled, deadline)
    }

    private fun mutatePlanning(fileName: String, lineIndex: Int, block: (OrgDocument, OrgHeadline) -> String) {
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val doc = vault.open(fileName) ?: return@launch
            val headline = doc.headlineAtLine(lineIndex) ?: return@launch
            val newText = withContext(Dispatchers.Default) { block(doc, headline) }
            vault.save(fileName, newText)
            app.syncManager.requestSync("agenda planning edit")
        }
    }

    /**
     * Swipe-to-done: sets the heading to the first configured done-type keyword
     * — never hardcoded "DONE", so a custom `todoKeywords` config is respected —
     * via org "mark done" semantics (`OrgMutations.markDone`: repeater advance
     * if the planning date repeats, else a CLOSED stamp).
     */
    fun markDone(fileName: String, lineIndex: Int) = toggleDone(fileName, lineIndex)

    /**
     * The row checkbox. Marks an open heading done, and reopens a done one —
     * reachable whenever the "Everything" filter is showing completed rows.
     */
    fun toggleDone(fileName: String, lineIndex: Int) {
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val doc = vault.open(fileName) ?: return@launch
            val headline = doc.headlineAtLine(lineIndex) ?: return@launch
            val reopening = headline.keyword in doc.keywords.done
            val newText = withContext(Dispatchers.Default) {
                if (reopening) {
                    OrgMutations.reopen(doc, headline, doc.keywords.active.firstOrNull())
                } else {
                    doc.keywords.done.firstOrNull()
                        ?.let { OrgMutations.markDone(doc, headline, it, LocalDateTime.now()) }
                }
            } ?: return@launch
            undoSnapshot = listOf(FileSnapshot(fileName, doc.text))
            vault.save(fileName, newText)
            app.syncManager.requestSync("agenda toggle done")
            showSnack(if (reopening) "Reopened" else "Marked done")
        }
    }

    /**
     * The overdue card's "Move to today": rewrites each overdue heading's
     * SCHEDULED to today, or its DEADLINE when that is the only date it has.
     * Time of day, repeater, and warning cookies all survive the move.
     *
     * Headings are rewritten highest-line-first within a file and the document
     * re-parsed between edits, since adding a planning line shifts every line
     * index below it.
     */
    fun moveOverdueToToday() {
        val rows = _state.value.overdue
        if (rows.isEmpty()) return
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            val today = LocalDate.now()
            val snapshots = mutableListOf<FileSnapshot>()
            var moved = 0

            for ((fileName, fileRows) in rows.groupBy { it.fileName }) {
                val doc = vault.open(fileName) ?: continue
                val original = doc.text
                var text = original
                withContext(Dispatchers.Default) {
                    for (r in fileRows.sortedByDescending { it.lineIndex }) {
                        val parsed = OrgParser.parse(text, doc.keywords)
                        val h = parsed.headlineAtLine(r.lineIndex) ?: continue
                        val scheduled = h.planning.scheduled
                        val deadline = h.planning.deadline
                        text = when {
                            scheduled != null && scheduled.date.isBefore(today) ->
                                OrgMutations.setScheduled(parsed, h, scheduled.copy(date = today))
                            scheduled == null && deadline != null && deadline.date.isBefore(today) ->
                                OrgMutations.setDeadline(parsed, h, deadline.copy(date = today))
                            else -> continue
                        }
                        moved++
                    }
                }
                if (text != original) {
                    snapshots += FileSnapshot(fileName, original)
                    vault.save(fileName, text)
                }
            }

            if (snapshots.isEmpty()) return@launch
            undoSnapshot = snapshots
            overdueOpen = false
            recompute()
            app.syncManager.requestSync("agenda move overdue")
            showSnack("Moved $moved to today")
        }
    }

    fun undo() {
        val snaps = undoSnapshot
        if (snaps.isEmpty()) return
        undoSnapshot = emptyList()
        _snack.value = null
        viewModelScope.launch {
            val vault = app.vault.value ?: return@launch
            snaps.forEach { vault.save(it.fileName, it.text) }
            app.syncManager.requestSync("agenda undo")
        }
    }

    private fun showSnack(message: String) {
        val s = OutlineSnack(message, ++eventId)
        _snack.value = s
        viewModelScope.launch {
            delay(4200)
            if (_snack.value?.id == s.id) _snack.value = null
        }
    }

    companion object {
        val Factory = factory { AgendaViewModel(it) }

        private const val INITIAL_WINDOW_DAYS = 14
        private const val PAGE_SIZE = 14

        // Safety cap: stops loadMoreDays() from growing forever if the user
        // scrolls through a long stretch with nothing scheduled.
        private const val MAX_WINDOW_DAYS = 366

        /** Sorts after "A"/"B"/"C", so unprioritised rows land at the bottom of a bucket. */
        private const val NO_PRIORITY_SORT_KEY = "Z"

        /**
         * The "Show" chips match these keyword names literally, per the design
         * prototype. A vault that doesn't use them still gets sensible
         * behaviour: "Open" becomes "everything not done", "Next only" empty.
         */
        private const val NEXT_KEYWORD = "NEXT"
        private const val WAITING_KEYWORD = "WAITING"

        private const val UNTAGGED = "untagged"

        private val PRIORITY_BUCKETS = listOf(
            "A" to "Priority A",
            "B" to "Priority B",
            "C" to "Priority C",
            null to "No priority",
        )

        private val HEADER_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM d", Locale.ENGLISH)
        private val SHORT_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)
        private val CLOCK: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

        /** The day a heading lands on: its SCHEDULED date, else its DEADLINE. */
        private fun whenDate(m: NoteMeta): LocalDate? = m.scheduledDate ?: m.deadlineDate

        /** Time on whichever timestamp [whenDate] chose; untimed rows sort last. */
        private fun timeOf(m: NoteMeta): LocalTime =
            (if (m.scheduledDate != null) m.scheduledTime else m.deadlineTime) ?: LocalTime.MAX

        private val BY_TIME: Comparator<NoteMeta> =
            compareBy<NoteMeta> { timeOf(it) }.thenBy { it.title.lowercase() }

        private val BY_PRIORITY: Comparator<NoteMeta> =
            compareBy<NoteMeta> { it.priority ?: NO_PRIORITY_SORT_KEY }
                .thenBy { timeOf(it) }
                .thenBy { it.title.lowercase() }

        /** "Today" / "Tomorrow" / "Yesterday" / "Friday" / "Friday, Aug 14" past a week out. */
        private fun dayLabel(date: LocalDate, today: LocalDate): String {
            val n = ChronoUnit.DAYS.between(today, date)
            if (n == 0L) return "Today"
            if (n == 1L) return "Tomorrow"
            if (n == -1L) return "Yesterday"
            val dow = date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            return if (abs(n) > 6) "$dow, ${date.format(SHORT_DATE)}" else dow
        }
    }
}
