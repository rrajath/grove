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
import androidx.compose.foundation.layout.height
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
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.settings.OutlineToggle
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.theme.starColor
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef

data class OutlineDisplayFlags(
    val tags: Boolean = true,
    val timestamps: Boolean = true,
    val keywords: Boolean = true,
)

/** Persist the collapsed line-index set across navigation (Set isn't saveable by default). */
private val IntSetSaver = listSaver<Set<Int>, Int>(save = { it.toList() }, restore = { it.toSet() })

/** Outline view per design spec §4 — collapsible heading tree with node ops. */
@Composable
fun OutlineScreen(
    notebookId: String,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onCreateNote: (NoteRef) -> Unit,
    onFavorite: (fileName: String, lineIndex: Int, title: String) -> Unit = { _, _, _ -> },
    displayFlags: OutlineDisplayFlags = OutlineDisplayFlags(),
    onToggleDisplay: (OutlineToggle, Boolean) -> Unit = { _, _ -> },
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    LaunchedEffect(notebookId) { viewModel.load(notebookId) }

    // Collapsed line-indices and scroll survive navigating into a note and back
    // (rememberSaveable persists across the destination leaving composition).
    var collapsed by rememberSaveable(notebookId, stateSaver = IntSetSaver) {
        mutableStateOf(setOf<Int>())
    }
    // "Show in context": narrow the view to one subtree (swipe-left in the spec)
    var narrowedTo by rememberSaveable(notebookId) { mutableStateOf<Int?>(null) }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    // A freshly opened notebook starts fully collapsed. Applied once per open
    // (the flag is saved alongside `collapsed`), so the user's later expanding
    // and collapsing is preserved across navigating into a note and back.
    var defaultCollapseApplied by rememberSaveable(notebookId) { mutableStateOf(false) }
    LaunchedEffect(state, defaultCollapseApplied) {
        if (!defaultCollapseApplied) {
            (state as? DocumentUiState.Loaded)?.let { loaded ->
                collapsed = loaded.document.headlines
                    .filter { loaded.document.hasDescendants(it) }
                    .map { it.lineIndex }
                    .toSet()
                defaultCollapseApplied = true
            }
        }
    }

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
                                "${it.document.headlines.count { h -> h.level == 1 }} notes",
                                fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink2,
                            )
                        }
                    }
                },
                actions = {
                    (state as? DocumentUiState.Loaded)?.let { loaded ->
                        val foldable = remember(loaded.document) {
                            loaded.document.headlines
                                .filter { loaded.document.hasDescendants(it) }
                                .map { it.lineIndex }
                                .toSet()
                        }
                        if (foldable.isNotEmpty()) {
                            // Mirrors the per-row carets: ▸▸ = all folded (tap to
                            // expand all), ▾▾ = expanded (tap to collapse all).
                            val allCollapsed = collapsed.containsAll(foldable)
                            IconGlyph(if (allCollapsed) "▸▸" else "▾▾", onClick = {
                                collapsed = if (allCollapsed) emptySet() else foldable
                            })
                        }
                    }
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
            // PRD §5.3: FAB adds a new top-level note to this notebook.
            Box(
                Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accent)
                    .clickable(onClick = {
                        viewModel.newTopLevelNote { line -> onCreateNote(NoteRef(notebookId, line)) }
                    }),
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
                if (doc.headlines.isEmpty()) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("✶", fontFamily = PlexMono, fontSize = 28.sp, color = c.ink3)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "This notebook is empty",
                                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                                fontSize = 15.sp, color = c.ink2,
                            )
                            Text(
                                "Tap + to write your first note",
                                fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
                            )
                        }
                    }
                    return@Scaffold
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
                    Box(Modifier.fillMaxSize()) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 10.dp)
                            .testTag("outline_list"),
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
                                    onNewChild = {
                                        viewModel.newChild(h) { line ->
                                            onCreateNote(NoteRef(notebookId, line))
                                        }
                                    },
                                    onInsertAbove = {
                                        viewModel.insertSiblingAbove(h) { line ->
                                            onCreateNote(NoteRef(notebookId, line))
                                        }
                                    },
                                    onInsertBelow = {
                                        viewModel.insertSiblingBelow(h) { line ->
                                            onCreateNote(NoteRef(notebookId, line))
                                        }
                                    },
                                    onCycleState = { viewModel.cycleState(h) },
                                    onMoveUp = { viewModel.moveUp(h) },
                                    onMoveDown = { viewModel.moveDown(h) },
                                    onCut = { viewModel.cutSubtree(h) },
                                    onCopy = { viewModel.copySubtree(h) },
                                    onPaste = if (viewModel.hasClipboard) ({ viewModel.pasteUnder(h) }) else null,
                                    onNarrow = { narrowedTo = h.lineIndex },
                                    onDelete = { viewModel.deleteSubtree(h) },
                                    onFavorite = { onFavorite(notebookId, h.lineIndex, h.title) },
                                ),
                            )
                        }
                    }
                    ScrollJumpButtons(
                        listState = listState,
                        // Stacked above the FAB (54.dp + its own padding) so the two
                        // never overlap.
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(bottom = 86.dp, end = 16.dp),
                    )
                    }
                }
            }
        }
    }

}

data class NodeOps(
    val onEdit: () -> Unit,
    val onNewChild: () -> Unit,
    val onInsertAbove: () -> Unit,
    val onInsertBelow: () -> Unit,
    val onCycleState: () -> Unit,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
    val onCut: () -> Unit,
    val onCopy: () -> Unit,
    val onPaste: (() -> Unit)?,
    val onNarrow: () -> Unit,
    val onDelete: () -> Unit,
    val onFavorite: () -> Unit = {},
)

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
    val hasChildren = remember(doc, headline) { doc.hasDescendants(headline) }
    // Only needed for the "… N" collapsed indicator below.
    val childCount = remember(doc, headline) { doc.directChildren(headline).size }
    val isDone = headline.keyword != null && doc.keywords.isDone(headline.keyword)
    var menuOpen by remember { mutableStateOf(false) }
    var insertMenuOpen by remember { mutableStateOf(false) }
    var insertMenuOffsetPx by remember { mutableStateOf(0 to 0) }
    // Tokenizing the title allocates a new AnnotatedString; rows recompose on
    // scroll and swipe, so keep it across recompositions.
    val titleAnnotated = remember(headline.title, c) { annotateOrgInline(headline.title, c) }

    // Swipe right = cycle state, swipe left = narrow to subtree (PRD §5.3).
    // A swipe is an action, not a dismiss. We react to targetValue (the moment
    // the swipe commits, before the row animates off-screen), run the action,
    // and snap the row home in a detached scope. Two traps avoided: vetoing via
    // confirmValueChange leaves the draggable stuck after one swipe; and
    // snapping inside this effect would self-cancel (the value change restarts
    // the effect mid-animation), stranding the row to the side.
    val swipeState = androidx.compose.material3.rememberSwipeToDismissBoxState()
    val currentOps by androidx.compose.runtime.rememberUpdatedState(ops)
    val swipeScope = androidx.compose.runtime.rememberCoroutineScope()
    LaunchedEffect(swipeState.targetValue) {
        when (swipeState.targetValue) {
            androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> currentOps.onCycleState()
            androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> currentOps.onNarrow()
            else -> return@LaunchedEffect
        }
        swipeScope.launch {
            swipeState.snapTo(androidx.compose.material3.SwipeToDismissBoxValue.Settled)
        }
    }

    Box {
        val density = LocalDensity.current
        NodeMenu(
            expanded = menuOpen,
            onDismiss = { menuOpen = false },
            onInsert = { insertMenuOpen = true },
            ops = ops,
            onInsertRowLayout = { widthPx, insertYPx -> insertMenuOffsetPx = widthPx to insertYPx },
        )
        InsertMenu(
            expanded = insertMenuOpen,
            onDismiss = { insertMenuOpen = false },
            onAction = {
                insertMenuOpen = false
                menuOpen = false
            },
            ops = ops,
            offset = with(density) {
                DpOffset(x = insertMenuOffsetPx.first.toDp(), y = insertMenuOffsetPx.second.toDp())
            },
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
            fontSize = 13.sp, color = c.starColor(headline.level),
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
                    titleAnnotated,
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
            // Body preview: first two non-empty lines, keeping the line breaks
            // so multi-line content stays readable.
            val preview = remember(doc, headline) {
                doc.bodyOf(headline)
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .take(2)
                    .joinToString("\n")
                    .take(200)
            }
            if (preview.isNotEmpty()) {
                Text(
                    preview,
                    fontFamily = PlexSans, fontSize = 12.5.sp, color = c.ink3,
                    maxLines = 2,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            // Scheduled / deadline chips
            headline.planning.scheduled?.takeIf { flags.timestamps }?.let { ts ->
                Box(
                    Modifier
                        .padding(top = 3.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(c.blueSoft)
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                ) {
                    Text(
                        "SCHEDULED: ${ts.format()}",
                        fontFamily = PlexMono, fontSize = 11.sp, color = c.blue,
                    )
                }
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
private fun NodeMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onInsert: () -> Unit,
    ops: NodeOps,
    // Reports this menu's measured width and the "Insert" row's vertical
    // position within it, in px, so the Insert sub-menu can be anchored
    // right beside it instead of using a guessed fixed offset.
    onInsertRowLayout: (widthPx: Int, insertYPx: Int) -> Unit = { _, _ -> },
) {
    val c = MaterialTheme.grove
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = c.surface,
    ) {
        var menuWidthPx by remember { mutableStateOf(0) }
        var insertRowYPx by remember { mutableStateOf(0) }
        LaunchedEffect(menuWidthPx, insertRowYPx) {
            onInsertRowLayout(menuWidthPx, insertRowYPx)
        }

        @Composable
        fun item(label: String, color: androidx.compose.ui.graphics.Color = c.ink, action: () -> Unit) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label, fontFamily = PlexSans, fontSize = 14.sp, color = color) },
                onClick = { onDismiss(); action() },
            )
        }
        Column(
            Modifier.onGloballyPositioned { menuWidthPx = it.size.width },
        ) {
            item("Edit", action = ops.onEdit)
            androidx.compose.material3.DropdownMenuItem(
                text = { Text("Insert ›", fontFamily = PlexSans, fontSize = 14.sp, color = c.ink) },
                onClick = onInsert,
                modifier = Modifier.onGloballyPositioned {
                    insertRowYPx = it.positionInParent().y.toInt()
                },
            )
            item("Cycle state", action = ops.onCycleState)
            item("Move up", action = ops.onMoveUp)
            item("Move down", action = ops.onMoveDown)
            item("Cut", action = ops.onCut)
            item("Copy", action = ops.onCopy)
            ops.onPaste?.let { item("Paste under", action = it) }
            item("Show in context", action = ops.onNarrow)
            item("Favorite", action = ops.onFavorite)
            item("Delete", color = c.red, action = ops.onDelete)
        }
    }
}

/** Sub-menu for the outline node's "Insert" action (design ask: below / above / sub-note). */
@Composable
private fun InsertMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onAction: () -> Unit,
    ops: NodeOps,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
) {
    val c = MaterialTheme.grove
    androidx.compose.material3.DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        offset = offset,
    ) {
        @Composable
        fun item(label: String, action: () -> Unit) {
            androidx.compose.material3.DropdownMenuItem(
                text = { Text(label, fontFamily = PlexSans, fontSize = 14.sp, color = c.ink) },
                onClick = { onAction(); action() },
            )
        }
        item("Insert below", action = ops.onInsertBelow)
        item("Insert above", action = ops.onInsertAbove)
        item("Insert sub-note", action = ops.onNewChild)
    }
}
