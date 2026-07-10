package com.rrajath.grove.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef
import java.time.format.DateTimeFormatter
import java.util.Locale

private val OPERATOR_CHIPS = listOf("t.TAG", "i.STATE", "s.PERIOD", "b.NOTEBOOK", "p.PRIORITY", "ad.DAYS")

/** Full-text + structured search with agenda grouping (design spec §9, PRD §5.5/§11). */
@Composable
fun SearchScreen(
    initialQuery: String?,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    var advancedOpen by remember { mutableStateOf(false) }
    var saveDialogOpen by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val listState = rememberLazyListState()

    LaunchedEffect(initialQuery) {
        if (!initialQuery.isNullOrBlank()) viewModel.submit(initialQuery)
        else focusRequester.requestFocus()
    }

    Scaffold(containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Search field row
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconGlyph("←", onClick = onBack)
                Row(
                    Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(13.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("⌕", fontFamily = PlexMono, fontSize = 14.sp, color = c.ink3)
                    Spacer(Modifier.width(8.dp))
                    BasicTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PlexSans, fontSize = 15.sp, color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.weight(1f).focusRequester(focusRequester),
                    )
                    if (state.query.isNotEmpty()) {
                        Text(
                            "×", fontFamily = PlexMono, fontSize = 15.sp, color = c.ink3,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { viewModel.onQueryChange("") }
                                .padding(4.dp),
                        )
                    }
                }
                if (state.query.isNotBlank()) {
                    IconGlyph("☆", onClick = { saveDialogOpen = true })
                }
            }

            // Meta row: advanced chip + result count
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (advancedOpen) c.accent else c.surface)
                        .border(1.dp, if (advancedOpen) c.accent else c.line, RoundedCornerShape(9.dp))
                        .clickable { advancedOpen = !advancedOpen }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "⚑ Advanced",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 12.sp,
                        color = if (advancedOpen) c.accentInk else c.ink2,
                    )
                }
                Spacer(Modifier.width(10.dp))
                if (state.results.isNotEmpty()) {
                    Text(
                        "${state.results.size} results across ${state.notebookCount} notebooks",
                        fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                    )
                }
            }

            if (advancedOpen) {
                AdvancedPanel(onChipTap = { op ->
                    viewModel.onQueryChange((state.query.trim() + " " + op.substringBefore('.') + ".").trim())
                })
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    state.query.isBlank() -> HistoryList(listState, state.history, onTap = viewModel::submit)
                    state.agenda != null -> AgendaList(listState, state.agenda!!, onOpenNote)
                    else -> ResultsList(listState, state.results, onOpenNote)
                }
                ScrollJumpButtons(
                    listState = listState,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(16.dp),
                )
            }
        }
    }

    if (saveDialogOpen) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { saveDialogOpen = false },
            containerColor = c.surface,
            title = {
                Text("Save search", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, color = c.ink)
            },
            text = {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("Name", color = c.ink3) },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.saveCurrentSearch(name)
                        saveDialogOpen = false
                    },
                    enabled = name.isNotBlank(),
                ) { Text("Save", color = c.accent, fontWeight = FontWeight.SemiBold) }
            },
            dismissButton = {
                TextButton(onClick = { saveDialogOpen = false }) { Text("Cancel", color = c.ink2) }
            },
        )
    }
}

@Composable
private fun AdvancedPanel(onChipTap: (String) -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(13.dp))
            .padding(13.dp),
    ) {
        Text(
            "Operators — space = AND · OR · prefix . = NOT · o.PROP sorts",
            fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            OPERATOR_CHIPS.take(3).forEach { OpChip(it, onChipTap); Spacer(Modifier.width(8.dp)) }
        }
        Spacer(Modifier.height(6.dp))
        Row {
            OPERATOR_CHIPS.drop(3).forEach { OpChip(it, onChipTap); Spacer(Modifier.width(8.dp)) }
        }
    }
}

@Composable
private fun OpChip(label: String, onTap: (String) -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(c.surface2)
            .clickable { onTap(label) }
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(label, fontFamily = PlexMono, fontSize = 11.sp, color = c.ink2)
    }
}

@Composable
private fun HistoryList(listState: LazyListState, history: List<String>, onTap: (String) -> Unit) {
    val c = MaterialTheme.grove
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
        if (history.isNotEmpty()) {
            items(history, key = { it }) { entry ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(9.dp))
                        .clickable { onTap(entry) }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("↻", fontFamily = PlexMono, fontSize = 12.sp, color = c.ink3)
                    Spacer(Modifier.width(10.dp))
                    Text(entry, fontFamily = PlexMono, fontSize = 13.5.sp, color = c.ink2)
                }
            }
        }
    }
}

@Composable
private fun ResultsList(listState: LazyListState, results: List<SearchResult>, onOpenNote: (NoteRef) -> Unit) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp)) {
        items(results, key = { "${it.fileName}@${it.lineIndex}" }, contentType = { "result" }) { result ->
            ResultRow(result, onOpenNote)
            HorizontalDivider(
                color = MaterialTheme.grove.line,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun AgendaList(listState: LazyListState, agenda: List<AgendaDay>, onOpenNote: (NoteRef) -> Unit) {
    val c = MaterialTheme.grove
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp)) {
        agenda.forEach { day ->
            item(key = day.date.toString(), contentType = "day-header") {
                Text(
                    day.date.format(formatter),
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp, letterSpacing = 0.5.sp, color = c.accent,
                    modifier = Modifier.padding(top = 14.dp, bottom = 4.dp),
                )
            }
            items(
                day.results,
                key = { "${day.date}-${it.fileName}@${it.lineIndex}" },
                contentType = { "result" },
            ) { result ->
                ResultRow(result, onOpenNote)
            }
        }
    }
}

@Composable
private fun ResultRow(result: SearchResult, onOpenNote: (NoteRef) -> Unit) {
    val c = MaterialTheme.grove
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable { onOpenNote(NoteRef(result.fileName, result.lineIndex)) }
            .padding(horizontal = 8.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            result.keyword?.let { kw ->
                Pill(
                    kw,
                    fg = if (result.isDone) c.green else c.amber,
                    bg = if (result.isDone) c.greenSoft else c.amberSoft,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(
                result.title,
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp, color = c.ink,
            )
        }
        if (result.snippet.text.isNotEmpty()) {
            val highlighted = remember(result.snippet, c) {
                buildAnnotatedString {
                    append(result.snippet.text)
                    result.snippet.highlight?.let { range ->
                        addStyle(
                            SpanStyle(color = c.amber, background = c.amberSoft, fontWeight = FontWeight.SemiBold),
                            range.first, (range.last + 1).coerceAtMost(result.snippet.text.length),
                        )
                    }
                }
            }
            Text(
                highlighted,
                fontFamily = PlexSans, fontSize = 13.5.sp, lineHeight = 1.5.em, color = c.ink2,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
        Text(
            result.breadcrumb,
            fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}
