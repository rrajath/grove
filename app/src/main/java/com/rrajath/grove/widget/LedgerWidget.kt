package com.rrajath.grove.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.ActionParameters
import androidx.glance.action.clickable
import androidx.glance.action.actionParametersOf
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontFamily
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.MainActivity
import com.rrajath.grove.data.toNoteMeta
import com.rrajath.grove.icon.AppIconManager
import com.rrajath.grove.ui.agenda.AgendaMeta
import com.rrajath.grove.ui.agenda.AgendaRow
import com.rrajath.grove.ui.agenda.agendaPriorityColor
import com.rrajath.grove.ui.agenda.metaColor
import com.rrajath.grove.ui.nav.Routes
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.groveColorsFor
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.headlineAtLine
import com.rrajath.grove.vault.AutoArchive
import com.rrajath.grove.vault.StateChangeResult
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Home-screen "Agenda · ledger" widget: `design/Grove.dc.html`'s Widget A variant,
 * re-grouped by day (per-widget spec) instead of priority. Each `provideGlance` call
 * is a one-shot read of the current vault/settings state — like [com.rrajath.grove.ui.reminders.RescheduleActivity],
 * not a long-lived reactive collector, since a Glance composition cannot be relied
 * on to keep running across the widget host process's own lifecycle. Callers that
 * change what the widget should show (mark-done, quick-add, sync completing) are
 * responsible for calling `LedgerWidget().updateAll(context)` themselves.
 */
class LedgerWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as GroveApplication
        val settings = app.settingsRepository.settings.first()
        val colors = groveColorsFor(settings.theme)
        val today = LocalDate.now()
        val windowDays = settings.agendaWidgetDaysAhead
        val openNotes = app.database.indexDao().plannedNotes().first()
            .map { it.toNoteMeta() }
            .filter { !it.isDoneKeyword }
        val sections = LedgerBuckets.build(openNotes, today, windowDays, settings)
        val todayCount = sections.firstOrNull { it.key.startsWith("Today") }?.count ?: 0
        val totalCount = sections.sumOf { it.count }
        val iconRes = AppIconManager.mipmapRes(settings.syncAppIconWithTheme, settings.theme)
        val backgroundColor = colors.surface.copy(alpha = 1f - settings.agendaWidgetTransparency)

        provideContent {
            LedgerContent(context, colors, backgroundColor, sections, todayCount, totalCount, windowDays, iconRes)
        }
    }
}

class LedgerWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = LedgerWidget()
}

@Composable
private fun LedgerContent(
    context: Context,
    colors: GroveColors,
    backgroundColor: Color,
    sections: List<LedgerBuckets.Section>,
    todayCount: Int,
    totalCount: Int,
    windowDays: Int,
    iconRes: Int,
) {
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .cornerRadius(22.dp)
            .background(ColorProvider(backgroundColor)),
    ) {
        HeaderRow(context, colors, todayCount, totalCount, windowDays, iconRes)
        if (sections.isEmpty()) {
            Box(
                modifier = GlanceModifier.fillMaxSize().padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing scheduled",
                    style = TextStyle(color = ColorProvider(colors.ink3), fontSize = 12.sp),
                )
            }
        } else {
            LazyColumn(modifier = GlanceModifier.fillMaxSize().padding(bottom = 10.dp)) {
                sections.forEach { section ->
                    item(itemId = section.key.hashCode().toLong()) {
                        SectionHeader(colors, section)
                    }
                    items(section.rows, itemId = { it.fileName.hashCode() * 31L + it.lineIndex }) { row ->
                        LedgerRow(context, colors, row)
                    }
                }
            }
        }
    }
}

@Composable
private fun HeaderRow(context: Context, colors: GroveColors, todayCount: Int, totalCount: Int, windowDays: Int, iconRes: Int) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            modifier = GlanceModifier
                .defaultWeight()
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                provider = ImageProvider(iconRes),
                contentDescription = null,
                modifier = GlanceModifier.size(22.dp).cornerRadius(7.dp),
            )
            Spacer(modifier = GlanceModifier.width(10.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    "Agenda",
                    style = TextStyle(color = ColorProvider(colors.ink), fontSize = 13.5.sp, fontWeight = FontWeight.Medium),
                )
                Text(
                    "$todayCount today · $totalCount in $windowDays days",
                    style = TextStyle(
                        color = ColorProvider(colors.ink2),
                        fontSize = 10.5.sp,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
        Box(
            modifier = GlanceModifier
                .size(30.dp)
                .cornerRadius(10.dp)
                .background(ColorProvider(colors.accent))
                .clickable(actionStartActivity(WidgetQuickAddActivity.intent(context))),
            contentAlignment = Alignment.Center,
        ) {
            Text("+", style = TextStyle(color = ColorProvider(colors.accentInk), fontSize = 17.sp, fontWeight = FontWeight.Medium))
        }
    }
}

@Composable
private fun SectionHeader(colors: GroveColors, section: LedgerBuckets.Section) {
    val isOverdue = section.key == "Overdue"
    val labelColor = if (isOverdue) colors.red else colors.ink2
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(start = 13.dp, end = 13.dp, top = 9.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            section.key.uppercase(),
            style = TextStyle(color = ColorProvider(labelColor), fontSize = 10.sp, fontWeight = FontWeight.Bold),
        )
        Spacer(modifier = GlanceModifier.width(7.dp))
        Text(
            section.count.toString(),
            style = TextStyle(color = ColorProvider(colors.ink3), fontSize = 10.sp, fontFamily = FontFamily.Monospace),
        )
        Spacer(modifier = GlanceModifier.width(7.dp))
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(1.dp)
                .background(ColorProvider(if (isOverdue) colors.red else colors.line)),
        ) {}
    }
}

/**
 * Approximate rendered line-height of the row's 13sp title text. Glance can't
 * report real text-layout metrics, so the circle and the keyword pill are each
 * centered inside a box of this fixed height, anchored to the top of the row —
 * i.e. the same band the title's first line occupies — rather than centered
 * against the title's full (possibly 2-line) height or the row's own top edge.
 */
private val LEDGER_LINE_HEIGHT = 18.dp

@Composable
private fun LedgerRow(context: Context, colors: GroveColors, row: AgendaRow) {
    val openIntent = Intent(Intent.ACTION_VIEW, noteUri(row))
        .setClass(context, MainActivity::class.java)
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        // Width folds in the 9dp gap that used to be a separate Spacer, so the
        // clickable region is wider than the visible ring without shifting the
        // title (the ring stays left-aligned at the same x it always rendered at).
        val gutterModifier = GlanceModifier.height(LEDGER_LINE_HEIGHT).width(23.dp)
        Box(
            modifier = if (row.keyword != null) {
                gutterModifier.clickable(
                    actionRunCallback<MarkDoneAction>(
                        actionParametersOf(
                            FILE_NAME_KEY to row.fileName,
                            LINE_INDEX_KEY to row.lineIndex,
                        ),
                    ),
                )
            } else {
                gutterModifier
            },
            contentAlignment = Alignment.CenterStart,
        ) {
            if (row.keyword != null) {
                // No border() modifier in Glance 1.1.1: the "hollow ring" is faked
                // by centering a smaller surface-colored circle over a light-grey one.
                Box(
                    modifier = GlanceModifier
                        .size(14.dp)
                        .cornerRadius(7.dp)
                        .background(ColorProvider(colors.line2)),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(modifier = GlanceModifier.size(12.dp).cornerRadius(6.dp).background(ColorProvider(colors.surface))) {}
                }
            }
        }
        Column(modifier = GlanceModifier.defaultWeight().clickable(actionStartActivity(openIntent))) {
            Row(verticalAlignment = Alignment.Top) {
                if (row.keyword != null) {
                    val (fg, bg) = stateChipColors(colors, row.keyword)
                    Box(
                        modifier = GlanceModifier.height(LEDGER_LINE_HEIGHT),
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            modifier = GlanceModifier.cornerRadius(4.dp).background(ColorProvider(bg))
                                .padding(horizontal = 5.dp, vertical = 2.dp),
                        ) {
                            Text(
                                row.keyword,
                                style = TextStyle(
                                    color = ColorProvider(fg),
                                    fontSize = 9.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                ),
                            )
                        }
                    }
                    Spacer(modifier = GlanceModifier.width(6.dp))
                }
                Text(
                    row.title,
                    maxLines = 2,
                    style = TextStyle(color = ColorProvider(colors.ink), fontSize = 13.sp, fontWeight = FontWeight.Medium),
                )
            }
            if (row.meta.isNotEmpty()) {
                Row(modifier = GlanceModifier.padding(top = 3.dp)) {
                    row.meta.forEachIndexed { index, m ->
                        if (index > 0) Spacer(modifier = GlanceModifier.width(8.dp))
                        MetaChip(colors, m)
                    }
                }
            }
        }
        if (row.priority != null) {
            Spacer(modifier = GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier.size(16.dp).cornerRadius(5.dp).background(ColorProvider(colors.surface2)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    row.priority,
                    style = TextStyle(
                        color = ColorProvider(colors.agendaPriorityColor(row.priority)),
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    ),
                )
            }
        }
    }
}

@Composable
private fun MetaChip(colors: GroveColors, meta: AgendaMeta) {
    Text(
        meta.text,
        style = TextStyle(color = ColorProvider(colors.metaColor(meta.tone)), fontSize = 10.5.sp, fontFamily = FontFamily.Monospace),
    )
}

/** Foreground/background pair for the keyword pill, mirroring `AgendaScreen`'s `StateChip`. */
private fun stateChipColors(colors: GroveColors, keyword: String) = when (keyword) {
    "NEXT" -> colors.green to colors.greenSoft
    "WAITING" -> colors.ink3 to colors.surface2
    else -> colors.synTodo to colors.amberSoft
}

private fun noteUri(row: AgendaRow) = run {
    val noteId = Routes.encode(NoteRef(row.fileName, row.lineIndex).encode())
    "grove://note/$noteId?mode=read&isNew=false".toUri()
}

private val FILE_NAME_KEY = ActionParameters.Key<String>("fileName")
private val LINE_INDEX_KEY = ActionParameters.Key<Int>("lineIndex")

/**
 * The widget's circle tap: same "mark done" semantics as [com.rrajath.grove.ui.agenda.AgendaViewModel.toggleDone]
 * (recurring headings advance their repeater instead of gaining a done keyword, so they
 * are never auto-archived; a non-recurring done item is auto-archived when Settings has
 * it enabled) but with no undo — the row simply drops out of the widget's next render.
 */
class MarkDoneAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val fileName = parameters[FILE_NAME_KEY] ?: return
        val lineIndex = parameters[LINE_INDEX_KEY] ?: return
        val app = context.applicationContext as GroveApplication
        val vault = app.vault.value ?: return
        val doc = vault.open(fileName) ?: return
        val headline = doc.headlineAtLine(lineIndex) ?: return
        val keyword = headline.keyword ?: return
        if (doc.keywords.isDone(keyword)) return
        val doneKeyword = doc.keywords.done.firstOrNull() ?: return
        val settings = app.settingsRepository.settings.first()
        when (
            val result = AutoArchive.apply(vault, settings, doc, fileName, headline, doneKeyword, LocalDateTime.now())
        ) {
            is StateChangeResult.Plain -> {
                vault.save(fileName, result.text)
                app.reindexNow(fileName, result.text)
            }
            is StateChangeResult.Archived -> {
                vault.save(fileName, result.sourceText)
                app.reindexNow(fileName, result.sourceText)
                if (result.destFile != fileName) {
                    vault.save(result.destFile, result.destText)
                    app.reindexNow(result.destFile, result.destText)
                }
            }
        }
        app.syncManager.requestSync("ledger widget mark done")
        LedgerWidget().updateAll(context)
    }
}
