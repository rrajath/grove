package com.rrajath.grove.widget

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.glance.appwidget.updateAll
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.capture.CaptureInserter
import com.rrajath.grove.capture.TargetLocation
import com.rrajath.grove.org.DateShorthandParser
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.org.PlanningKind
import com.rrajath.grove.org.ShorthandParse
import com.rrajath.grove.settings.GroveSettings
import com.rrajath.grove.ui.components.PlanningDatesScreen
import com.rrajath.grove.ui.theme.GroveTheme
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.headlineAtLine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Host for the widget's "+" button: a small composer overlaid on whatever app the
 * user was in, reproducing `design/Grove.dc.html`'s widget quick-capture sheet
 * (`wCapOpen`) rather than the full multi-step Capture Picker/template flow.
 * Same one-shot-task pattern as [com.rrajath.grove.ui.reminders.RescheduleActivity]:
 * empty `taskAffinity`, finishes back to whatever was underneath, and the write
 * runs on [GroveApplication.appScope] so finishing early can't cancel it.
 */
class WidgetQuickAddActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Manages the IME inset itself (via Modifier.imePadding() below) rather
        // than leaning on windowSoftInputMode's window-resize behavior, which is
        // unreliable on a translucent/floating window like this one.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContent {
            val app = LocalContext.current.applicationContext as GroveApplication
            var theme by remember { mutableStateOf<GroveSettings?>(null) }
            LaunchedEffect(Unit) { theme = app.settingsRepository.settings.first() }
            theme?.let { s ->
                GroveTheme(theme = s.theme, fontSize = s.fontSize) {
                    QuickAddSheet(onDismiss = ::finish)
                }
            }
        }
    }

    companion object {
        fun intent(context: Context): Intent = Intent(context, WidgetQuickAddActivity::class.java)
    }
}

private enum class QuickAddDate(val label: String, val shorthand: String?) {
    TODAY("Today", "today"),
    TOMORROW("Tomorrow", "tomorrow"),
    WEEKEND("This weekend", "weekend"),
    NEXT_WEEK("Next week", "next week"),
    NONE("No date", null),
}

private val PRIORITIES: List<Char?> = listOf(null, 'A', 'B', 'C')

@Composable
private fun QuickAddSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as GroveApplication

    var keywords by remember { mutableStateOf(listOf("TODO")) }
    var notebooks by remember { mutableStateOf(emptyList<String>()) }
    var defaultNotebook by remember { mutableStateOf(GroveSettings.DEFAULT_SHARE_TARGET) }
    LaunchedEffect(Unit) {
        val settings = app.settingsRepository.settings.first()
        keywords = app.keywords.first().active.ifEmpty { listOf("TODO") }
        val vault = app.vault.filterNotNull().first()
        notebooks = vault.notebooks().map { it.fileName }.sorted()
        defaultNotebook = resolveShareTargetFile(settings)
    }

    var text by remember { mutableStateOf("") }
    var keyword by remember(keywords) { mutableStateOf(keywords.first()) }
    var priority by remember { mutableStateOf<Char?>(null) }
    // Exactly one of these two is "live" at a time: picking a preset clears
    // customDate, confirming the custom picker clears dateOption (null). Kept
    // separate rather than one eagerly-resolved OrgTimestamp so a preset like
    // "Today" still resolves at *send* time (see savedDate below), staying
    // correct even if the sheet is left open across midnight.
    var dateOption by remember { mutableStateOf<QuickAddDate?>(QuickAddDate.TODAY) }
    var customDate by remember { mutableStateOf<OrgTimestamp?>(null) }
    var dateMenuOpen by remember { mutableStateOf(false) }
    var customDateOpen by remember { mutableStateOf(false) }
    var notebook by remember(defaultNotebook) { mutableStateOf(defaultNotebook) }
    var notebookMenuOpen by remember { mutableStateOf(false) }
    // The Settings-configured share target is the default even when it isn't an
    // existing notebook yet (it gets created on send, same as sharing does); the
    // notebook chip's pop-out still needs it in its list even before that happens.
    val notebookChoices = remember(notebooks, defaultNotebook) { (listOf(defaultNotebook) + notebooks).distinct() }

    // Resolved lazily on every recomposition (not eagerly at pick time) so a
    // preset like "Today" still reflects the actual date whenever it's read,
    // staying correct even if the sheet is left open across midnight.
    val resolvedPresetDate = dateOption?.shorthand?.let { sh ->
        (DateShorthandParser.parse(sh, LocalDate.now()) as? ShorthandParse.Ok)?.value?.date
            ?.let { OrgTimestamp(it, active = true) }
    }
    val selectedDate = customDate ?: resolvedPresetDate
    val dateChipLabel = customDate?.formatHuman() ?: dateOption?.label ?: QuickAddDate.NONE.label

    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        keyboardController?.show()
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(onClick = onDismiss),
        )
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 10.dp)
                .padding(bottom = 14.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.grove.surface)
                .padding(13.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                KeywordChip(keyword) { keyword = keywords.next(keyword) }
                Spacer(Modifier.width(9.dp))
                BasicTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    textStyle = TextStyle(color = MaterialTheme.grove.ink, fontSize = 15.sp, fontFamily = PlexSans),
                    decorationBox = { inner ->
                        if (text.isEmpty()) {
                            Text("What needs doing?", color = MaterialTheme.grove.ink3, fontSize = 15.sp, fontFamily = PlexSans)
                        }
                        inner()
                    },
                )
            }
            Spacer(Modifier.height(11.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.grove.line))
            Spacer(Modifier.height(11.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box {
                    ComposerChip(dateChipLabel, active = dateOption != QuickAddDate.NONE || customDate != null) {
                        dateMenuOpen = true
                    }
                    DropdownMenu(
                        expanded = dateMenuOpen,
                        onDismissRequest = { dateMenuOpen = false },
                        containerColor = MaterialTheme.grove.surface,
                    ) {
                        QuickAddDate.entries.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.label, fontFamily = PlexSans, color = MaterialTheme.grove.ink) },
                                onClick = { dateOption = preset; customDate = null; dateMenuOpen = false },
                            )
                        }
                        HorizontalDivider(color = MaterialTheme.grove.line)
                        DropdownMenuItem(
                            text = { Text("Custom date…", fontFamily = PlexSans, color = MaterialTheme.grove.accent) },
                            onClick = { dateMenuOpen = false; customDateOpen = true },
                        )
                    }
                }
                Spacer(Modifier.width(7.dp))
                ComposerChip(priority?.let { "#$it" } ?: "Priority", active = priority != null) {
                    priority = PRIORITIES.next(priority)
                }
                Spacer(Modifier.width(7.dp))
                Box {
                    ComposerChip(notebook, active = false) { notebookMenuOpen = true }
                    DropdownMenu(
                        expanded = notebookMenuOpen,
                        onDismissRequest = { notebookMenuOpen = false },
                        containerColor = MaterialTheme.grove.surface,
                    ) {
                        notebookChoices.forEach { nb ->
                            DropdownMenuItem(
                                text = { Text(nb, fontFamily = PlexSans, color = MaterialTheme.grove.ink) },
                                onClick = { notebook = nb; notebookMenuOpen = false },
                            )
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                val canSend = text.isNotBlank()
                Box(
                    Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (canSend) MaterialTheme.grove.accent else MaterialTheme.grove.surface2)
                        .clickable(enabled = canSend) {
                            val savedText = text.trim()
                            val savedKeyword = keyword
                            val savedPriority = priority
                            val savedDate = selectedDate
                            val savedNotebook = notebook
                            app.appScope.launch {
                                submitQuickAdd(app, savedNotebook, savedKeyword, savedPriority, savedText, savedDate)
                            }
                            Toast.makeText(context, "Added to ${savedNotebook.removeSuffix(".org")}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        "↑",
                        color = if (canSend) MaterialTheme.grove.accentInk else MaterialTheme.grove.ink3,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Medium,
                        fontFamily = PlexSans,
                    )
                }
            }
        }
    }

    if (customDateOpen) {
        PlanningDatesScreen(
            title = text.ifBlank { "New task" },
            scheduled = selectedDate,
            deadline = null,
            focus = PlanningKind.SCHEDULED,
            onDismiss = { customDateOpen = false },
            // Quick-add has no deadline concept, so PlanningDatesScreen is reused
            // exactly as Reschedule uses it (both sections shown); the deadline
            // half of the result is simply never read.
            onConfirm = { sched, _ -> dateOption = null; customDate = sched; customDateOpen = false },
        )
    }
}

@Composable
private fun KeywordChip(keyword: String, onClick: () -> Unit) {
    val (fg, bg) = when (keyword) {
        "NEXT" -> MaterialTheme.grove.green to MaterialTheme.grove.greenSoft
        "WAITING" -> MaterialTheme.grove.ink3 to MaterialTheme.grove.surface2
        else -> MaterialTheme.grove.synTodo to MaterialTheme.grove.amberSoft
    }
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 7.dp, vertical = 4.dp),
    ) {
        Text(keyword, color = fg, fontSize = 10.sp, fontWeight = FontWeight.Bold, fontFamily = PlexMono)
    }
}

@Composable
private fun ComposerChip(label: String, active: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(9.dp))
            .background(if (active) c.accentSoft else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(
            label,
            color = if (active) c.accent else c.ink2,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = PlexSans,
        )
    }
}

/**
 * The composer's default target file: Settings § Sharing's "Shared content target"
 * (`GroveSettings.shareTargetFile`), resolved exactly like [com.rrajath.grove.ui.AppViewModel.consumeSharedContent]
 * does for a shared link/text, not the first capture template's target — this composer
 * bypasses the template system entirely, so a template's target was never the right default.
 */
private fun resolveShareTargetFile(settings: GroveSettings): String {
    val target = settings.shareTargetFile.trim().ifBlank { GroveSettings.DEFAULT_SHARE_TARGET }
    return if (target.endsWith(".org")) target else "$target.org"
}

private fun <T> List<T>.next(current: T): T {
    val i = indexOf(current)
    return if (i == -1 || isEmpty()) current else this[(i + 1) % size]
}

/**
 * Appends a plain top-level heading to the bottom of [notebook] (creating it if
 * needed), then applies priority/scheduled the same way every other mutation
 * surface does ([OrgMutations.setPriority]/[OrgMutations.setScheduled]) so the
 * on-disk formatting is identical to a heading edited from the Outline or Agenda.
 */
private suspend fun submitQuickAdd(
    app: GroveApplication,
    notebook: String,
    keyword: String,
    priority: Char?,
    title: String,
    date: OrgTimestamp?,
) {
    if (title.isBlank()) return
    val vault = app.vault.filterNotNull().first()
    if (vault.open(notebook) == null) vault.createNotebook(notebook)
    val orgKeywords = OrgKeywords.parse(app.settingsRepository.settings.first().todoKeywords)
    val text = withContext(Dispatchers.Default) {
        val baseText = vault.open(notebook)?.text ?: ""
        val insertion = CaptureInserter.insert(
            docText = baseText,
            location = TargetLocation.BottomOfFile,
            entry = "$keyword $title",
            today = LocalDate.now(),
            keywords = orgKeywords,
        )
        var text = insertion.newText
        var doc = OrgParser.parse(text, orgKeywords)
        var headline = doc.headlineAtLine(insertion.insertedAtLine)
        if (priority != null && headline != null) {
            text = OrgMutations.setPriority(doc, headline, priority)
            doc = OrgParser.parse(text, orgKeywords)
            headline = doc.headlineAtLine(insertion.insertedAtLine)
        }
        if (date != null && headline != null) {
            text = OrgMutations.setScheduled(doc, headline, date)
        }
        text
    }
    vault.save(notebook, text)
    app.reindexNow(notebook, text)
    app.syncManager.requestSync("widget quick add")
    LedgerWidget().updateAll(app)
}
