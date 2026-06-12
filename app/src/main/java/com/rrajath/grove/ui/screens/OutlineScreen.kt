package com.rrajath.grove.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import com.rrajath.grove.settings.OutlineToggle
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef

data class OutlineDisplayFlags(
    val tags: Boolean = true,
    val timestamps: Boolean = true,
    val keywords: Boolean = true,
)

/** Outline view per design spec §4 — collapsible heading tree with node ops. */
@Composable
fun OutlineScreen(
    notebookId: String,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onCapture: () -> Unit,
    displayFlags: OutlineDisplayFlags = OutlineDisplayFlags(),
    onToggleDisplay: (OutlineToggle, Boolean) -> Unit = { _, _ -> },
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    LaunchedEffect(notebookId) { viewModel.load(notebookId) }

    // Collapsed headline line-indices (default: all expanded)
    var collapsed by remember(notebookId) { mutableStateOf(setOf<Int>()) }
    // "Show in context": narrow the view to one subtree (swipe-left in the spec)
    var narrowedTo by remember(notebookId) { mutableStateOf<Int?>(null) }
    var newChildFor by remember { mutableStateOf<OrgHeadline?>(null) }

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
                actions = {
                    var displayMenuOpen by remember { mutableStateOf(false) }
                    Box {
                        IconGlyph("⋮", onClick = { displayMenuOpen = true })
                        androidx.compose.material3.DropdownMenu(
                            expanded = displayMenuOpen,
                            onDismissRequest = { displayMenuOpen = false },
                            containerColor = c.surface,
                        ) {
                            @Composable
                            fun toggleItem(label: String, value: Boolean, toggle: OutlineToggle) {
                                androidx.compose.material3.DropdownMenuItem(
                                    text = {
                                        Text(
                                            (if (value) "✓ " else "   ") + label,
                                            fontFamily = PlexSans, fontSize = 14.sp, color = c.ink,
                                        )
                                    },
                                    onClick = { onToggleDisplay(toggle, !value) },
                                )
                            }
                            toggleItem("Show tags", displayFlags.tags, OutlineToggle.TAGS)
                            toggleItem("Show timestamps", displayFlags.timestamps, OutlineToggle.TIMESTAMPS)
                            toggleItem("Show keywords", displayFlags.keywords, OutlineToggle.KEYWORDS)
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
                val visible = remember(doc, collapsed, narrowedTo) {
                    val all = visibleHeadlines(doc, collapsed)
                    val narrowLine = narrowedTo
                    if (narrowLine == null) all
                    else {
                        val root = doc.headlines.firstOrNull { it.lineIndex == narrowLine }
                        if (root == null) all
                        else {
                            val subtreeLines = (doc.subtree(root) + root).map { it.lineIndex }.toSet()
                            all.filter { it.lineIndex in subtreeLines }
                        }
                    }
                }
                Column(Modifier.fillMaxSize().padding(padding)) {
                    if (narrowedTo != null) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { narrowedTo = null }
                                .padding(horizontal = 14.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                "◂ narrowed — tap to show all",
                                fontFamily = PlexMono, fontSize = 11.5.sp, color = c.accent,
                            )
                        }
                    }
                    LazyColumn(
                        Modifier
                            .fillMaxSize()
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
                                flags = displayFlags,
                                ops = NodeOps(
                                    onEdit = { onOpenNote(NoteRef(notebookId, h.lineIndex)) },
                                    onNewChild = { newChildFor = h },
                                    onCycleState = { viewModel.cycleState(h) },
                                    onMoveUp = { viewModel.moveUp(h) },
                                    onMoveDown = { viewModel.moveDown(h) },
                                    onCut = { viewModel.cutSubtree(h) },
                                    onCopy = { viewModel.copySubtree(h) },
                                    onPaste = if (viewModel.hasClipboard) ({ viewModel.pasteUnder(h) }) else null,
                                    onNarrow = { narrowedTo = h.lineIndex },
                                    onDelete = { viewModel.deleteSubtree(h) },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }

    newChildFor?.let { parent ->
        NewChildDialog(
            parentTitle = parent.title,
            onDismiss = { newChildFor = null },
            onCreate = { title ->
                viewModel.newChild(parent, title)
                newChildFor = null
            },
        )
    }
}

data class NodeOps(
    val onEdit: () -> Unit,
    val onNewChild: () -> Unit,
    val onCycleState: () -> Unit,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onCut: () -> Unit,
    val onCopy: () -> Unit,
    val onPaste: (() -> Unit)?,
    val onNarrow: () -> Unit,
    val onDelete: () -> Unit,
)

@Composable
private fun NewChildDialog(
    parentTitle: String,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit,
) {
    val c = MaterialTheme.grove
    var title by remember { mutableStateOf("") }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                "New note under \"$parentTitle\"",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = c.ink,
            )
        },
        text = {
            androidx.compose.material3.OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                singleLine = true,
                placeholder = { Text("Heading", color = c.ink3) },
            )
        },
        confirmButton = {
            androidx.compose.material3.TextButton(
                onClick = { if (title.isNotBlank()) onCreate(title.trim()) },
                enabled = title.isNotBlank(),
            ) { Text("Create", color = c.accent, fontWeight = FontWeight.SemiBold) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancel", color = c.ink2) }
        },
    )
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

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun OutlineNode(
    doc: OrgDocument,
    headline: OrgHeadline,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    ops: NodeOps,
    flags: OutlineDisplayFlags = OutlineDisplayFlags(),
) {
    val c = MaterialTheme.grove
    val childCount = remember(doc, headline) { doc.directChildren(headline).size }
    val hasChildren = childCount > 0
    val isDone = headline.keyword != null && doc.keywords.isDone(headline.keyword)
    var menuOpen by remember { mutableStateOf(false) }

    // Swipe right = cycle state, swipe left = narrow to subtree (PRD §5.3).
    val swipeState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> ops.onCycleState()
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> ops.onNarrow()
                else -> {}
            }
            false // always snap back; the swipe triggers an action, not a dismiss
        },
    )

    Box {
        NodeMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            ops = ops,
        )
    androidx.compose.material3.SwipeToDismissBox(
        state = swipeState,
        backgroundContent = {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("↻ state", fontFamily = PlexMono, fontSize = 11.sp, color = c.amber)
                Spacer(Modifier.weight(1f))
                Text("narrow ◂", fontFamily = PlexMono, fontSize = 11.sp, color = c.blue)
            }
        },
    ) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.bg)
            .combinedClickable(onClick = onOpen, onLongClick = { menuOpen = true })
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
                headline.keyword?.takeIf { flags.keywords }?.let { kw ->
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
            headline.planning.scheduled?.takeIf { flags.timestamps }?.let { ts ->
                Text(
                    "◷ ${ts.format()}",
                    fontFamily = PlexMono, fontSize = 11.sp, color = c.blue,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            headline.planning.deadline?.takeIf { flags.timestamps }?.let { ts ->
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
        if (flags.tags && headline.tags.isNotEmpty()) {
            Spacer(Modifier.width(6.dp))
            Text(
                headline.tags.joinToString(":", prefix = ":", postfix = ":"),
                fontFamily = PlexMono, fontSize = 11.sp, color = c.synTag,
            )
        }
    }
    }
    }
}

@Composable
private fun NodeMenu(expanded: Boolean, onDismiss: () -> Unit, ops: NodeOps) {
    val c = MaterialTheme.grove
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = c.surface,
    ) {
        @Composable
        fun item(label: String, color: androidx.compose.ui.graphics.Color = c.ink, action: () -> Unit) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label, fontFamily = PlexSans, fontSize = 14.sp, color = color) },
                onClick = { onDismiss(); action() },
            )
        }
        item("Edit", action = ops.onEdit)
        item("New sub-note", action = ops.onNewChild)
        item("Cycle state", action = ops.onCycleState)
        item("Move up", action = ops.onMoveUp)
        item("Move down", action = ops.onMoveDown)
        item("Cut", action = ops.onCut)
        item("Copy", action = ops.onCopy)
        ops.onPaste?.let { item("Paste under", action = it) }
        item("Show in context", action = ops.onNarrow)
        item("Delete", color = c.red, action = ops.onDelete)
    }
}
