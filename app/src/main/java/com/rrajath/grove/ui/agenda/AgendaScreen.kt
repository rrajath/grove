package com.rrajath.grove.ui.agenda

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.rrajath.grove.ui.theme.priorityColor
import com.rrajath.grove.ui.vault.NoteRef
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Upcoming/overdue agenda — a dedicated screen from the drawer's Agenda item,
 *  separate from Search (design decision: agenda answers "what's next", search
 *  answers "find a specific note"). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AgendaScreen(
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    viewModel: AgendaViewModel = viewModel(factory = AgendaViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val formatter = remember { DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.ENGLISH) }

    Scaffold(containerColor = c.bg) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.height(56.dp).fillMaxWidth().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconGlyph("←", onClick = onBack)
                Spacer(Modifier.width(4.dp))
                Text("Agenda", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 19.sp, color = c.ink)
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                LazyColumnAgenda(listState, state, formatter, onOpenNote)

                val nearBottom by remember {
                    derivedStateOf {
                        val info = listState.layoutInfo
                        val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
                        info.totalItemsCount > 0 && lastVisible >= info.totalItemsCount - 5
                    }
                }
                LaunchedEffect(nearBottom, state.days.size) {
                    if (nearBottom) viewModel.loadMoreDays()
                }

                ScrollJumpButtons(
                    listState = listState,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyColumnAgenda(
    listState: LazyListState,
    state: AgendaUiState,
    formatter: DateTimeFormatter,
    onOpenNote: (NoteRef) -> Unit,
) {
    val c = MaterialTheme.grove
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        if (state.overdue.isEmpty() && state.days.isEmpty()) {
            item {
                Column(Modifier.fillMaxWidth().padding(top = 52.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Nothing scheduled", fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = c.ink)
                    Text(
                        "Nothing overdue or due soon.",
                        fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink2,
                        modifier = Modifier.padding(top = 7.dp),
                    )
                }
            }
        }
        if (state.overdue.isNotEmpty()) {
            stickyHeader(key = "overdue-header") {
                AgendaSectionHeader("Overdue (${state.overdueCount})", color = c.red)
            }
            items(
                state.overdue,
                key = { "overdue-${it.fileName}@${it.lineIndex}" },
                contentType = { "result" },
            ) { result -> AgendaResultRow(result, onOpenNote) }
        }
        state.days.forEach { day ->
            stickyHeader(key = day.date.toString()) {
                AgendaSectionHeader(day.date.format(formatter), color = c.accent)
            }
            items(
                day.results,
                key = { "${day.date}-${it.fileName}@${it.lineIndex}" },
                contentType = { "result" },
            ) { result -> AgendaResultRow(result, onOpenNote) }
        }
    }
}

@Composable
private fun AgendaSectionHeader(text: String, color: Color) {
    val c = MaterialTheme.grove
    Text(
        text,
        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp, letterSpacing = 0.5.sp, color = color,
        modifier = Modifier
            .fillMaxWidth()
            .background(c.bg)
            .padding(top = 14.dp, bottom = 4.dp),
    )
}

@Composable
private fun AgendaResultRow(result: AgendaResult, onOpenNote: (NoteRef) -> Unit) {
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
            result.priority?.let { p ->
                Text(
                    "[#$p]",
                    fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                    fontSize = 11.sp, color = c.priorityColor(p[0]),
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
            Text(
                result.snippet.text,
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
