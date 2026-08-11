package com.rrajath.grove.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.org.DateShorthandParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.org.Repeater
import com.rrajath.grove.org.RepeaterType
import com.rrajath.grove.org.ShorthandParse
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * SCHEDULED and DEADLINE on one canvas ("Dates C" in `design/Grove.dc.html`).
 *
 * Both dates live on a single calendar: the scheduled day in blue, the deadline
 * in red, the lead time between them shaded; and calendar taps set whichever
 * section is expanded. Above the calendar is a shorthand box (`fri 10-11am ++1w`)
 * that patches the target timestamp; below it are the two expandable sections
 * carrying presets, time-of-day and the org repeater. Confirming commits *both*
 * timestamps at once, which is why [onConfirm] takes the pair: [focus] only
 * decides which section starts open, not what gets written.
 *
 * Presented as a full-window [Dialog] rather than a nav destination so the four
 * entry points (agenda swipe, outline row, metadata sheet, reminder reschedule)
 * keep their existing local state and ViewModel wiring.
 */
@Composable
fun PlanningDatesScreen(
    title: String,
    scheduled: OrgTimestamp?,
    deadline: OrgTimestamp?,
    focus: PlanningKind,
    onDismiss: () -> Unit,
    onConfirm: (scheduled: OrgTimestamp?, deadline: OrgTimestamp?) -> Unit,
) {
    val c = MaterialTheme.grove
    val today = remember { LocalDate.now() }

    // Every other note's SCHEDULED/DEADLINE day, so the calendar can mark days
    // that already have something on them (mirrors Emacs org-mode's scheduling
    // calendar) distinctly from this note's own dates, which get the full blue/
    // red fill instead.
    val app = LocalContext.current.applicationContext as GroveApplication
    val plannedNotes by app.database.indexDao().plannedNotes().collectAsState(initial = emptyList())
    val plannedDates = remember(plannedNotes) {
        plannedNotes.flatMapTo(mutableSetOf()) { note ->
            listOfNotNull(
                note.scheduled?.let { OrgTimestamp.parse(it)?.date },
                note.deadline?.let { OrgTimestamp.parse(it)?.date },
            )
        }
    }

    var sched by remember { mutableStateOf(scheduled) }
    var dead by remember { mutableStateOf(deadline) }
    var tab by remember { mutableStateOf(focus) }
    var month by remember {
        val start = (if (focus == PlanningKind.SCHEDULED) scheduled else deadline)?.date ?: today
        mutableStateOf(YearMonth.from(start))
    }
    var shorthand by remember { mutableStateOf("") }
    // True right after "Clear" is tapped, so the footer button can say "Clear
    // Dates" instead of its usual disabled-state text; cleared the moment
    // either date is set again.
    var showClearedLabel by remember { mutableStateOf(false) }
    LaunchedEffect(sched, dead) {
        if (sched != null || dead != null) showClearedLabel = false
    }

    val isSched = tab == PlanningKind.SCHEDULED
    val accent = if (isSched) c.blue else c.red
    val accentSoft = if (isSched) c.blueSoft else c.redSoft
    val active = if (isSched) sched else dead

    fun setActive(next: OrgTimestamp?) {
        if (isSched) sched = next else dead = next
    }

    fun patchActive(block: (OrgTimestamp) -> OrgTimestamp) {
        setActive(block(active ?: OrgTimestamp(today)))
    }

    val parse = remember(shorthand, today) { DateShorthandParser.parse(shorthand, today) }
    val parsedOk = parse as? ShorthandParse.Ok

    fun applyShorthand() {
        val sh = parsedOk?.value ?: return
        val kind = sh.target ?: tab
        val current = if (kind == PlanningKind.SCHEDULED) sched else dead
        var next = current ?: OrgTimestamp(sh.date ?: today)
        if (sh.date != null) next = next.copy(date = sh.date)
        if (sh.time != null) next = next.copy(time = sh.time, endTime = sh.endTime)
        if (sh.repeater != null) next = next.copy(repeater = sh.repeater)
        if (kind == PlanningKind.SCHEDULED) sched = next else dead = next
        tab = kind
        month = YearMonth.from(next.date)
        shorthand = ""
    }

    // Lead time is only meaningful with both dates set; negative means the work
    // is scheduled to start after it is already due.
    val leadDays = if (sched != null && dead != null) {
        ChronoUnit.DAYS.between(sched!!.date, dead!!.date).toInt()
    } else null
    val clash = leadDays != null && leadDays < 0
    val suggestion = if (dead != null && sched == null) dead!!.date.minusDays(3) else null

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        // This Dialog opens its own platform Window (decorFitsSystemWindows = false
        // to go edge-to-edge), separate from the Activity window GroveTheme's
        // SideEffect already styles — so the status/nav bar icon appearance has to
        // be re-asserted here too, or it falls back to the platform default (light
        // icons), unreadable over Grove's light-theme background.
        val dialogView = LocalView.current
        if (!dialogView.isInEditMode) {
            SideEffect {
                val window = (dialogView.parent as? DialogWindowProvider)?.window ?: return@SideEffect
                val controller = WindowCompat.getInsetsController(window, dialogView)
                controller.isAppearanceLightStatusBars = !c.isDark
                controller.isAppearanceLightNavigationBars = !c.isDark
            }
        }
        Surface(Modifier.fillMaxSize(), color = c.bg) {
            Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {

                // ---- header ----------------------------------------------------
                Row(
                    Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Close",
                            tint = c.ink,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Text(
                        title,
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 15.5.sp, color = c.ink,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "Clear",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 12.5.sp, color = c.ink2,
                        modifier = Modifier
                            .clip(RoundedCornerShape(9.dp))
                            .clickable { sched = null; dead = null; showClearedLabel = true }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    )
                }

                // ---- scrolling canvas ------------------------------------------
                Column(
                    Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 14.dp),
                ) {
                    ShorthandBox(
                        value = shorthand,
                        onValueChange = { shorthand = it },
                        accent = accent,
                        canApply = parsedOk != null,
                        onApply = ::applyShorthand,
                    )

                    if (parse != null) {
                        Text(
                            shorthandEcho(parse, today, tab),
                            fontFamily = PlexMono, fontSize = 11.5.sp,
                            color = if (parsedOk != null) c.green else c.red,
                            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 7.dp, bottom = 2.dp),
                        )
                    }

                    FlowRow(
                        Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        ShorthandHints.forEach { hint ->
                            Text(
                                hint,
                                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(c.surface2)
                                    .clickable {
                                        shorthand = if (shorthand.isEmpty()) hint else "$shorthand $hint"
                                    }
                                    .padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }

                    BothDatesCalendar(
                        month = month,
                        today = today,
                        scheduled = sched?.date,
                        deadline = dead?.date,
                        plannedDates = plannedDates,
                        activeLabel = if (isSched) "SCHEDULED" else "DEADLINE",
                        onPrev = { month = month.minusMonths(1) },
                        onNext = { month = month.plusMonths(1) },
                        onPick = { date -> patchActive { it.copy(date = date) } },
                    )

                    if (leadDays != null && !clash) {
                        Text(
                            if (leadDays == 0) "Starting the day it is due, no lead time."
                            else "$leadDays day${plural(leadDays)} of lead time before it is due.",
                            fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                            modifier = Modifier.padding(horizontal = 2.dp).padding(top = 9.dp),
                        )
                    }

                    if (clash) {
                        val late = abs(leadDays!!)
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(c.redSoft)
                                .border(1.dp, c.red, RoundedCornerShape(12.dp))
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            Text("!", fontFamily = PlexSans, fontSize = 14.sp, color = c.red)
                            Text(
                                "Scheduled $late day${plural(late)} after the deadline, " +
                                        "you would start it already late.",
                                fontFamily = PlexSans, fontSize = 12.sp, color = c.ink, lineHeight = 17.sp,
                            )
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    PlanningSection(
                        kind = PlanningKind.SCHEDULED,
                        expanded = isSched,
                        value = sched,
                        today = today,
                        showDurationChips = true,
                        onExpand = {
                            tab = PlanningKind.SCHEDULED
                            month = YearMonth.from(sched?.date ?: month.atDay(1))
                        },
                        onChange = { sched = it },
                    )
                    Spacer(Modifier.height(8.dp))
                    PlanningSection(
                        kind = PlanningKind.DEADLINE,
                        expanded = !isSched,
                        value = dead,
                        today = today,
                        showDurationChips = false,
                        onExpand = {
                            tab = PlanningKind.DEADLINE
                            month = YearMonth.from(dead?.date ?: month.atDay(1))
                        },
                        onChange = { dead = it },
                    )

                    if (suggestion != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .dashedBorder(c.line2, 12.dp)
                                .clickable { sched = OrgTimestamp(suggestion) }
                                .padding(horizontal = 11.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text("+", fontFamily = PlexSans, fontSize = 12.5.sp, color = c.accent)
                            Text(
                                "Start 3 days earlier, ${suggestion.format(HumanDate)}",
                                fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                            )
                        }
                    }
                }

                // ---- footer ----------------------------------------------------
                HorizontalDivider(color = c.line)
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(c.surface)
                        .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 14.dp),
                ) {
                    Column(Modifier.padding(horizontal = 2.dp).padding(top = 1.dp, bottom = 10.dp)) {
                        Text(
                            "SCHEDULED: " + (sched?.format() ?: "-"),
                            fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 20.sp,
                            color = if (sched != null) c.synTs else c.ink3,
                        )
                        Text(
                            "DEADLINE:  " + (dead?.format() ?: "-"),
                            fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 20.sp,
                            color = if (dead != null) c.red else c.ink3,
                        )
                    }
                    val canApplyDates = sched != null || dead != null
                    val footerLabel = when {
                        showClearedLabel -> "Clear Dates"
                        sched != null && dead != null -> "Apply Both Dates"
                        sched != null -> "Apply Scheduled Date"
                        dead != null -> "Apply Deadline"
                        else -> "Apply Both Dates"
                    }
                    Text(
                        footerLabel,
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.5.sp,
                        color = if (canApplyDates) c.accentInk else c.ink3,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(if (canApplyDates) c.accent else c.surface2)
                            .clickable(enabled = canApplyDates) { onConfirm(sched, dead) }
                            .padding(vertical = 13.dp),
                    )
                }
            }
        }
    }
}

private val ShorthandHints = listOf("fri", "+2w", "aug 3", "10-11am", "++1w", "d: mon")

/** `› [ fri 10-11am ++1w ]  (Set)`: the free-text row above the calendar. */
@Composable
private fun ShorthandBox(
    value: String,
    onValueChange: (String) -> Unit,
    accent: Color,
    canApply: Boolean,
    onApply: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(c.bg)
            .border(1.dp, if (value.isNotEmpty()) accent else c.line, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 10.dp, top = 3.dp, bottom = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("›", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink3)
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(
                    "d: aug 5 ++1m",
                    fontFamily = PlexMono, fontSize = 14.sp, color = c.ink3,
                    modifier = Modifier.padding(vertical = 11.dp),
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontFamily = PlexMono, fontSize = 14.sp, color = c.ink,
                ),
                cursorBrush = SolidColor(accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onApply() }),
                modifier = Modifier.fillMaxWidth().padding(vertical = 11.dp),
            )
        }
        Text(
            "Set",
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
            color = if (canApply) c.surface else c.ink3,
            modifier = Modifier
                .clip(RoundedCornerShape(9.dp))
                .background(if (canApply) accent else c.surface2)
                .clickable(enabled = canApply, onClick = onApply)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}

/**
 * One month with both planning dates on it. The scheduled day is a solid blue
 * cell, the deadline a solid red one, and the days strictly between them carry a
 * soft accent band so the lead time reads at a glance.
 */
@Composable
private fun BothDatesCalendar(
    month: YearMonth,
    today: LocalDate,
    scheduled: LocalDate?,
    deadline: LocalDate?,
    plannedDates: Set<LocalDate>,
    activeLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPick: (LocalDate) -> Unit,
) {
    val c = MaterialTheme.grove
    val bandStart = if (scheduled != null && deadline != null && scheduled.isBefore(deadline)) scheduled else null
    val bandEnd = if (bandStart != null) deadline else null

    // Left/right swipe steps the month, same direction convention as the ‹/›
    // arrows: a leftward drag (negative total) reveals next month, rightward
    // reveals the previous one. Accumulated over the whole gesture rather than
    // acted on per-move so a single swipe only ever changes one month.
    val density = LocalDensity.current
    val swipeThresholdPx = remember(density) { with(density) { 56.dp.toPx() } }
    var dragAccum by remember { mutableStateOf(0f) }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 13.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .pointerInput(onPrev, onNext) {
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (dragAccum <= -swipeThresholdPx) onNext()
                        else if (dragAccum >= swipeThresholdPx) onPrev()
                        dragAccum = 0f
                    },
                    onDragCancel = { dragAccum = 0f },
                ) { change, dragAmount ->
                    change.consume()
                    dragAccum += dragAmount
                }
            }
            .padding(start = 11.dp, end = 11.dp, top = 11.dp, bottom = 12.dp),
    ) {
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                // Same direction convention as the swipe/arrows: moving to a later
                // month slides the new one in from the right as the old one exits
                // left; moving earlier reverses it.
                val forward = targetState.isAfter(initialState)
                (slideInHorizontally(tween(260)) { width -> if (forward) width else -width } + fadeIn(tween(260)))
                    .togetherWith(
                        slideOutHorizontally(tween(260)) { width -> if (forward) -width else width } + fadeOut(tween(260)),
                    )
            },
            label = "calendar-month",
        ) { animatedMonth ->
            Column {
                Row(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    MonthArrow("‹", onPrev)
                    Text(
                        animatedMonth.format(MonthLabel),
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 13.5.sp, color = c.ink, textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    MonthArrow("›", onNext)
                }

                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    listOf("S", "M", "T", "W", "T", "F", "S").forEach { d ->
                        Text(
                            d,
                            fontFamily = PlexSans, fontSize = 10.sp, color = c.ink3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f).padding(bottom = 4.dp),
                        )
                    }
                }

                // Sunday-first grid: DayOfWeek.value is Mon=1..Sun=7, so % 7 puts Sunday at 0.
                val leading = animatedMonth.atDay(1).dayOfWeek.value % 7
                val cells = List(leading) { null } + (1..animatedMonth.lengthOfMonth()).map { animatedMonth.atDay(it) }
                cells.chunked(7).forEach { week ->
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                today = today,
                                isScheduled = date != null && date == scheduled,
                                isDeadline = date != null && date == deadline,
                                inBand = date != null && bandStart != null && bandEnd != null &&
                                        date.isAfter(bandStart) && date.isBefore(bandEnd),
                                hasPlanned = date != null && date in plannedDates,
                                onPick = onPick,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            LegendSwatch(c.blue, "scheduled")
            LegendSwatch(c.red, "deadline")
            Text(
                "taps set $activeLabel",
                fontFamily = PlexMono, fontSize = 11.sp, color = c.ink2,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MonthArrow(glyph: String, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier.size(30.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = PlexSans, fontSize = 16.sp, color = c.ink2)
    }
}

@Composable
private fun LegendSwatch(color: Color, label: String) {
    val c = MaterialTheme.grove
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(11.dp).clip(RoundedCornerShape(4.dp)).background(color))
        Text(label, fontFamily = PlexSans, fontSize = 11.sp, color = c.ink2)
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    today: LocalDate,
    isScheduled: Boolean,
    isDeadline: Boolean,
    inBand: Boolean,
    hasPlanned: Boolean,
    onPick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    if (date == null) {
        Spacer(modifier.height(37.dp))
        return
    }
    val isToday = date == today
    val background = when {
        isDeadline -> c.red
        isScheduled -> c.blue
        inBand -> c.accentSoft
        else -> Color.Transparent
    }
    // Deadline wins over scheduled when both land on the same day: the harder
    // constraint is the one worth showing.
    val border = when {
        isDeadline -> c.red
        isScheduled -> c.blue
        isToday -> c.line2
        else -> Color.Transparent
    }
    // Some other note has a SCHEDULED/DEADLINE this day; this note's own dates
    // already get the full blue/red fill above, so this only ever applies to
    // days that fill doesn't already cover. Emacs-style: tint the day number
    // itself rather than a dot. No tint on today unless today itself is
    // scheduled/deadlined.
    val hasPlannedTint = hasPlanned && !isToday && !isScheduled && !isDeadline
    val ink = when {
        isScheduled || isDeadline -> c.surface
        hasPlannedTint -> c.violet
        else -> c.ink
    }

    Box(
        modifier
            .height(37.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(10.dp))
            .clickable { onPick(date) },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            date.dayOfMonth.toString(),
            fontFamily = PlexMono, fontSize = 13.sp, color = ink,
            fontWeight = when {
                isScheduled || isDeadline -> FontWeight.SemiBold
                hasPlannedTint -> FontWeight.Bold
                else -> FontWeight.Normal
            },
        )
    }
}

/**
 * SCHEDULED or DEADLINE: a summary row that expands into presets, time-of-day and
 * the repeater editor. Only one section is expanded at a time; the expanded one
 * is also the target of calendar taps, which is what the legend's "taps set …"
 * spells out.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlanningSection(
    kind: PlanningKind,
    expanded: Boolean,
    value: OrgTimestamp?,
    today: LocalDate,
    showDurationChips: Boolean,
    onExpand: () -> Unit,
    onChange: (OrgTimestamp?) -> Unit,
) {
    val c = MaterialTheme.grove
    val isSched = kind == PlanningKind.SCHEDULED
    val accent = if (isSched) c.blue else c.red
    val accentSoft = if (isSched) c.blueSoft else c.redSoft
    val label = if (isSched) "SCHEDULED" else "DEADLINE"

    fun patch(block: (OrgTimestamp) -> OrgTimestamp) = onChange(block(value ?: OrgTimestamp(today)))

    Column {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(13.dp))
                .background(if (expanded) accentSoft else c.surface2)
                .border(1.dp, if (expanded) accent else c.line, RoundedCornerShape(13.dp))
                .clickable(onClick = onExpand)
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                if (isSched) "◷" else "⚑",
                fontFamily = PlexSans, fontSize = 15.sp, color = accent,
            )
            Column(Modifier.weight(1f)) {
                Text(
                    label,
                    fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                    fontSize = 10.sp, letterSpacing = 0.8.sp, color = accent,
                )
                Text(
                    summarize(value),
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, color = if (value != null) c.ink else c.ink3,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }

        if (!expanded) return@Column

        Column(Modifier.padding(horizontal = 2.dp).padding(top = 10.dp, bottom = 2.dp)) {
            FlowRow(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                datePresets(today).forEach { (name, date) ->
                    // "Today · Jul 29": the absolute date next to the relative name,
                    // so a preset never needs a second glance at the calendar.
                    Chip(
                        label = "$name · ${date.format(ShortDate)}",
                        selected = value?.date == date,
                        accent = accent,
                        onClick = { patch { it.copy(date = date) } },
                    )
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    timeLabel(value, c.ink, c.ink2),
                    fontFamily = PlexSans, fontSize = 13.5.sp,
                    modifier = Modifier.weight(1f),
                )
                GroveSwitch(on = value?.time != null, accent = accent) {
                    if (value?.time != null) patch { it.copy(time = null, endTime = null) }
                    else patch { it.copy(time = LocalTime.of(9, 0)) }
                }
            }

            if (value?.time != null) {
                Row(
                    Modifier.fillMaxWidth().padding(top = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TimeField(value.time, "09:00") { t ->
                        if (t == null) patch { it.copy(time = null, endTime = null) }
                        else patch { it.copy(time = t) }
                    }
                    Text("to", fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3)
                    TimeField(value.endTime, "-") { t -> patch { it.copy(endTime = t) } }
                    if (showDurationChips) {
                        Row(
                            Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                        ) {
                            listOf("30m" to 30L, "1h" to 60L, "2h" to 120L).forEach { (name, mins) ->
                                MiniChip(name) {
                                    val start = value.time ?: LocalTime.of(9, 0)
                                    patch { it.copy(time = start, endTime = start.plusMinutes(mins)) }
                                }
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 2.dp).padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "Repeat",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.5.sp, color = c.ink,
                    modifier = Modifier.weight(1f),
                )
                GroveSwitch(on = value?.repeater != null, accent = accent) {
                    if (value?.repeater != null) patch { it.copy(repeater = null) }
                    else patch { it.copy(repeater = Repeater(RepeaterType.CUMULATIVE, 1, 'w')) }
                }
            }

            value?.repeater?.let { rep ->
                RepeaterCard(
                    repeater = rep,
                    accent = accent,
                    accentSoft = accentSoft,
                    onChange = { next -> patch { it.copy(repeater = next) } },
                )
            }
        }
    }
}

/** "Every _week_, and if I am late, _keep the original rhythm_." plus the cookie. */
@Composable
private fun RepeaterCard(
    repeater: Repeater,
    accent: Color,
    accentSoft: Color,
    onChange: (Repeater) -> Unit,
) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .padding(12.dp),
    ) {
        // A single Text (rather than a FlowRow of separate Text composables) so the
        // whole sentence shares one text layout: the bold accent words then sit on
        // the exact same baseline as the plain words around them, and the trailing
        // "." can't drift onto its own mis-aligned box (which used to read as a
        // stray "·" floating above the line).
        val sentence = remember(repeater.value, repeater.unit, repeater.type, accent) {
            buildRepeaterSentence(intervalLabel(repeater), kindWords(repeater.type).lowercase(), accent)
        }
        var layoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
        Text(
            text = sentence.text,
            style = TextStyle(fontFamily = PlexSans, fontSize = 14.sp, color = c.ink, lineHeight = 26.sp),
            onTextLayout = { layoutResult = it },
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(sentence) {
                    detectTapGestures { tapOffset ->
                        val result = layoutResult ?: return@detectTapGestures
                        when (val position = result.getOffsetForPosition(tapOffset)) {
                            in sentence.intervalRange -> {
                                val units = listOf('d', 'w', 'm', 'y')
                                val next = units[(units.indexOf(repeater.unit).coerceAtLeast(0) + 1) % units.size]
                                onChange(repeater.copy(unit = next))
                            }
                            in sentence.kindRange -> {
                                val kinds = RepeaterType.entries
                                onChange(repeater.copy(type = kinds[(kinds.indexOf(repeater.type) + 1) % kinds.size]))
                            }
                            else -> Unit
                        }
                    }
                }
                .drawBehind {
                    val result = layoutResult ?: return@drawBehind
                    drawDashedUnderline(result, sentence.intervalRange, accent)
                    drawDashedUnderline(result, sentence.kindRange, accent)
                },
        )

        Row(
            Modifier.fillMaxWidth().padding(top = 10.dp),
            // Top-aligned: the cookie pill is a fixed-height single line, but
            // kindHint() can wrap to two lines, so centering the row would
            // float the pill below the hint's first line instead of flush
            // with it.
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Text(
                repeater.toString(),
                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
                color = accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(7.dp))
                    .background(accentSoft)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
            Text(
                kindHint(repeater),
                fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2, lineHeight = 16.sp,
                modifier = Modifier.weight(1f).padding(top = 3.dp),
            )
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            StepButton("−") { onChange(repeater.copy(value = (repeater.value - 1).coerceAtLeast(1))) }
            Text(
                repeater.value.toString(),
                fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 15.sp,
                color = c.ink, textAlign = TextAlign.Center,
                modifier = Modifier.width(22.dp),
            )
            StepButton("+") { onChange(repeater.copy(value = (repeater.value + 1).coerceAtMost(30))) }
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
            ) {
                listOf('d' to "Day", 'w' to "Week", 'm' to "Month", 'y' to "Year").forEach { (u, name) ->
                    Chip(
                        label = name,
                        selected = repeater.unit == u,
                        accent = accent,
                        onClick = { onChange(repeater.copy(unit = u)) },
                    )
                }
            }
        }
    }
}

/** The repeat sentence's text plus the char ranges of its two tappable words. */
private data class RepeaterSentence(
    val text: AnnotatedString,
    val intervalRange: IntRange,
    val kindRange: IntRange,
)

private fun buildRepeaterSentence(intervalText: String, kindText: String, accent: Color): RepeaterSentence {
    lateinit var intervalRange: IntRange
    lateinit var kindRange: IntRange
    val text = buildAnnotatedString {
        append("Every ")
        val intervalStart = length
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(intervalText) }
        intervalRange = intervalStart until length
        append(", and if I am late, ")
        val kindStart = length
        withStyle(SpanStyle(color = accent, fontWeight = FontWeight.SemiBold)) { append(kindText) }
        kindRange = kindStart until length
        append(".")
    }
    return RepeaterSentence(text, intervalRange, kindRange)
}

/**
 * Draws a dashed rule under [range] of [layout], anchored to that range's own
 * *font baseline* (not the line box's bottom): the 26sp line height that keeps
 * the sentence readable pads extra leading below the glyphs, which would leave
 * the rule floating well below the text if it were anchored to the box bottom
 * instead. Splits across lines in case the picked word ever wraps.
 */
private fun DrawScope.drawDashedUnderline(layout: TextLayoutResult, range: IntRange, color: Color) {
    if (range.isEmpty()) return
    val firstLine = layout.getLineForOffset(range.first)
    val lastLine = layout.getLineForOffset(range.last)
    for (line in firstLine..lastLine) {
        val start = maxOf(range.first, layout.getLineStart(line))
        val end = minOf(range.last + 1, layout.getLineEnd(line, visibleEnd = true))
        if (start >= end) continue
        val xStart = layout.getHorizontalPosition(start, usePrimaryDirection = true)
        val xEnd = layout.getHorizontalPosition(end, usePrimaryDirection = true)
        val y = layout.getLineBaseline(line) + 2.dp.toPx()
        drawLine(
            color = color,
            start = Offset(xStart, y),
            end = Offset(xEnd, y),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.5.dp.toPx()), 0f),
        )
    }
}

@Composable
private fun StepButton(glyph: String, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(glyph, fontFamily = PlexSans, fontSize = 17.sp, color = c.ink2)
    }
}

/** Selectable preset/unit chip: filled in the section's accent when active. */
@Composable
private fun Chip(label: String, selected: Boolean, accent: Color, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Text(
        label,
        fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = 12.5.sp,
        color = if (selected) c.accentInk else c.ink2,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) accent else c.surface2)
            .border(1.dp, if (selected) Color.Transparent else c.line, RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}

/** Unselectable action chip (the 30m / 1h / 2h duration shortcuts). */
@Composable
private fun MiniChip(label: String, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Text(
        label,
        fontFamily = PlexMono, fontWeight = FontWeight.SemiBold, fontSize = 11.5.sp,
        color = c.ink2,
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(9.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/** 40×23 pill switch matching the prototype's toggles. */
@Composable
private fun GroveSwitch(on: Boolean, accent: Color, onToggle: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .size(width = 40.dp, height = 23.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (on) accent else c.surface3)
            .clickable(onClick = onToggle),
    ) {
        Box(
            Modifier
                .padding(start = if (on) 19.5.dp else 2.5.dp, top = 2.5.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(c.surface),
        )
    }
}

/** Free-text `HH:mm` field; the model only updates when the text parses. */
@Composable
private fun TimeField(value: LocalTime?, placeholder: String, onChange: (LocalTime?) -> Unit) {
    val c = MaterialTheme.grove
    var text by remember(value) { mutableStateOf(value?.format(ClockTime) ?: "") }
    Box(
        Modifier
            .width(74.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .padding(horizontal = 6.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (text.isEmpty()) {
            Text(placeholder, fontFamily = PlexMono, fontSize = 14.sp, color = c.ink3)
        }
        BasicTextField(
            value = text,
            onValueChange = { raw ->
                text = raw
                if (raw.isBlank()) onChange(null) else parseClock(raw)?.let(onChange)
            },
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(
                fontFamily = PlexMono, fontSize = 14.sp, color = c.ink,
                textAlign = TextAlign.Center,
            ),
            cursorBrush = SolidColor(c.accent),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** 1dp dashed rounded outline (the "Start 3 days earlier" suggestion row). */
private fun Modifier.dashedBorder(color: Color, radius: androidx.compose.ui.unit.Dp) = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius.toPx()),
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()), 0f),
        ),
    )
}

// ---- formatting helpers -------------------------------------------------------

private val HumanDate: DateTimeFormatter = DateTimeFormatter.ofPattern("EEE, MMM d")
private val ShortDate: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
private val MonthLabel: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM yyyy")
private val ClockTime: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

private val CLOCK = Regex("""^\s*(\d{1,2})\s*:?\s*(\d{2})\s*$""")

private fun parseClock(raw: String): LocalTime? {
    val m = CLOCK.find(raw) ?: return null
    return runCatching { LocalTime.of(m.groupValues[1].toInt(), m.groupValues[2].toInt()) }.getOrNull()
}

private fun plural(n: Int) = if (n == 1) "" else "s"

private fun datePresets(today: LocalDate): List<Pair<String, LocalDate>> {
    fun next(target: java.time.DayOfWeek): LocalDate {
        val delta = ((target.value - today.dayOfWeek.value + 7) % 7).let { if (it == 0) 7 else it }
        return today.plusDays(delta.toLong())
    }
    return listOf(
        "Today" to today,
        "Tomorrow" to today.plusDays(1),
        "This weekend" to next(java.time.DayOfWeek.SATURDAY),
        "Next week" to next(java.time.DayOfWeek.MONDAY),
    )
}

private fun summarize(ts: OrgTimestamp?): String {
    if (ts == null) return "Not set"
    val sb = StringBuilder(ts.date.format(HumanDate))
    ts.time?.let {
        sb.append(" · ").append(it.format(ClockTime))
        ts.endTime?.let { end -> sb.append('–').append(end.format(ClockTime)) }
    }
    ts.repeater?.let { sb.append(" · ").append(it) }
    return sb.toString()
}

/** "**Time** · All day": the label is semibold, the current value is not. */
private fun timeLabel(ts: OrgTimestamp?, ink: Color, ink2: Color): AnnotatedString {
    val value = ts?.time?.let { t ->
        t.format(ClockTime) + (ts.endTime?.let { " – ${it.format(ClockTime)}" } ?: "")
    } ?: "All day"
    return buildAnnotatedString {
        withStyle(SpanStyle(color = ink, fontWeight = FontWeight.SemiBold)) { append("Time") }
        withStyle(SpanStyle(color = ink2)) { append(" · $value") }
    }
}

private fun unitName(unit: Char, n: Int): String {
    val base = when (unit) {
        'd' -> "day"; 'w' -> "week"; 'm' -> "month"; 'y' -> "year"; 'h' -> "hour"; else -> "week"
    }
    return base + plural(n)
}

private fun intervalLabel(rep: Repeater): String =
    if (rep.value == 1) unitName(rep.unit, 1) else "${rep.value} ${unitName(rep.unit, rep.value)}"

private fun kindWords(type: RepeaterType): String = when (type) {
    RepeaterType.CUMULATIVE -> "Keep the original rhythm"
    RepeaterType.CATCH_UP -> "Skip the ones I missed"
    RepeaterType.FUTURE -> "Count from when I finish"
}

private fun kindHint(rep: Repeater): String {
    val interval = intervalLabel(rep).let { if (rep.value == 1) "one $it" else it }
    return when (rep.type) {
        RepeaterType.CUMULATIVE ->
            "Moves on by exactly $interval, so if you were late the new date can still be in the past."
        RepeaterType.CATCH_UP ->
            "Jumps forward in $interval steps until it lands after today. Missed rounds are dropped."
        RepeaterType.FUTURE ->
            "Next date is $interval after the day you actually mark it DONE."
    }
}

private fun relativeDay(date: LocalDate, today: LocalDate): String {
    val n = ChronoUnit.DAYS.between(today, date).toInt()
    return when {
        n == 0 -> "today"
        n == 1 -> "tomorrow"
        n == -1 -> "yesterday"
        n > 0 -> "in $n days"
        else -> "${abs(n)} days ago"
    }
}

private fun shorthandEcho(parse: ShorthandParse, today: LocalDate, activeTab: PlanningKind): String = when (parse) {
    is ShorthandParse.Unrecognised ->
        "Not understood, try “fri”, “+2w”, “aug 3 10-11am”"
    is ShorthandParse.Ok -> {
        val sh = parse.value
        // No explicit s:/d: prefix falls back to whichever section is active
        // (the same fallback applyShorthand() itself uses), so the echo always
        // states which field the line will actually be applied to.
        val target = sh.target ?: activeTab
        val arrow = "→ ${if (target == PlanningKind.DEADLINE) "DEADLINE" else "SCHEDULED"}"
        val segments = listOfNotNull(
            sh.date?.let { "${it.format(HumanDate)} · ${relativeDay(it, today)}" },
            sh.time?.let { t ->
                t.format(ClockTime) + (sh.endTime?.let { "–${it.format(ClockTime)}" } ?: "")
            },
            sh.repeater?.let { r -> "every ${intervalLabel(r)} $r" },
        )
        // The arrow segment is a destination label, not another fact to bullet
        // alongside the date/time/repeater: no "·" separator in front of it.
        if (segments.isEmpty()) arrow else segments.joinToString("  ·  ") + "  " + arrow
    }
}
