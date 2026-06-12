package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef

/** Outline view per design spec §4 — collapsible heading tree, read-only in M2. */
@Composable
fun OutlineScreen(
    notebookId: String,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onCapture: () -> Unit,
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    LaunchedEffect(notebookId) { viewModel.load(notebookId) }

    // Collapsed headline line-indices (default: all expanded)
    var collapsed by remember(notebookId) { mutableStateOf(setOf<Int>()) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            notebookId,
                            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
                            fontSize = 17.sp, color = c.ink,
                        )
                        (state as? DocumentUiState.Loaded)?.let {
                            Text(
                                "${it.document.headlines.size} notes",
                                fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accent)
                    .clickable(onClick = onCapture),
                contentAlignment = Alignment.Center,
            ) {
                Text("+", fontFamily = PlexSans, fontSize = 26.sp, color = c.accentInk)
            }
        },
    ) { padding ->
        when (val s = state) {
            is DocumentUiState.Loading -> {}
            is DocumentUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.message, fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2)
            }

            is DocumentUiState.Loaded -> {
                val doc = s.document
                val visible = remember(doc, collapsed) { visibleHeadlines(doc, collapsed) }
                LazyColumn(
                    Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 10.dp),
                ) {
                    items(visible, key = { it.lineIndex }) { h ->
                        OutlineNode(
                            doc = doc,
                            headline = h,
                            isCollapsed = h.lineIndex in collapsed,
                            onToggle = {
                                collapsed = if (h.lineIndex in collapsed) collapsed - h.lineIndex
                                else collapsed + h.lineIndex
                            },
                            onOpen = { onOpenNote(NoteRef(notebookId, h.lineIndex)) },
                        )
                    }
                }
            }
        }
    }
}

private fun visibleHeadlines(doc: OrgDocument, collapsed: Set<Int>): List<OrgHeadline> {
    val result = mutableListOf<OrgHeadline>()
    var hideDeeperThan: Int? = null
    for (h in doc.headlines) {
        val hideLevel = hideDeeperThan
        if (hideLevel != null) {
            if (h.level > hideLevel) continue
            hideDeeperThan = null
        }
        result.add(h)
        if (h.lineIndex in collapsed) hideDeeperThan = h.level
    }
    return result
}

@Composable
private fun OutlineNode(
    doc: OrgDocument,
    headline: OrgHeadline,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
) {
    val c = MaterialTheme.grove
    val childCount = remember(doc, headline) { doc.directChildren(headline).size }
    val hasChildren = childCount > 0
    val isDone = headline.keyword != null && doc.keywords.isDone(headline.keyword)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onOpen)
            .padding(
                start = (22 * (headline.level - 1)).dp,
                top = 9.dp, bottom = 9.dp, end = 6.dp,
            ),
        verticalAlignment = Alignment.Top,
    ) {
        // Caret (tap target toggles fold)
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .clickable(enabled = hasChildren, onClick = onToggle),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                when {
                    !hasChildren -> "○"
                    isCollapsed -> "▸"
                    else -> "▾"
                },
                fontFamily = PlexMono, fontSize = if (hasChildren) 12.sp else 8.sp,
                color = c.ink3,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            "*".repeat(headline.level),
            fontFamily = PlexMono, fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp, color = c.synStar,
        )
        Spacer(Modifier.width(7.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                headline.keyword?.let { kw ->
                    val (fg, bg) = when {
                        doc.keywords.isDone(kw) -> c.green to c.greenSoft
                        kw == "IN-PROGRESS" -> c.blue to c.blueSoft
                        else -> c.amber to c.amberSoft
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(bg)
                            .padding(horizontal = 5.dp, vertical = 1.dp),
                    ) {
                        Text(
                            kw,
                            fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                            fontSize = 11.sp, color = fg,
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                }
                headline.priority?.let { p ->
                    Text(
                        "[#$p]",
                        fontFamily = PlexMono, fontWeight = FontWeight.Bold,
                        fontSize = 11.sp, color = c.red,
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(
                    headline.title,
                    fontFamily = PlexSans,
                    fontWeight = if (headline.level == 1) FontWeight.SemiBold else FontWeight.Medium,
                    fontSize = 14.5.sp,
                    color = if (isDone) c.ink3 else c.ink,
                    textDecoration = if (isDone) TextDecoration.LineThrough else null,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (isCollapsed && childCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text("… $childCount", fontFamily = PlexMono, fontSize = 11.sp, color = c.ink3)
                }
            }
            // Scheduled / deadline chips
            headline.planning.scheduled?.let { ts ->
                Text(
                    "◷ ${ts.format()}",
                    fontFamily = PlexMono, fontSize = 11.sp, color = c.blue,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            headline.planning.deadline?.let { ts ->
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.redSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "DEADLINE: ${ts.format()}",
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.red,
                    )
                }
            }
        }
        if (headline.tags.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                headline.tags.joinToString(":", prefix = ":", postfix = ":"),
                fontFamily = PlexMono, fontSize = 11.sp, color = c.synTag,
            )
        }
    }
}
