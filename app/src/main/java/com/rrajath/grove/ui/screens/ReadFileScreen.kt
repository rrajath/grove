package com.rrajath.grove.ui.screens

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.data.FavoriteNote
import com.rrajath.grove.org.INTRO_LINE_INDEX
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.ui.components.CollapsibleKvSection
import com.rrajath.grove.ui.components.GroveToast
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.LinkedReferencesBar
import com.rrajath.grove.ui.components.LinkedReferencesSheet
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.newbadge.MarkNewFeatureSeen
import com.rrajath.grove.ui.newbadge.NewAnchors
import com.rrajath.grove.ui.theme.ContentFontScale
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.util.IntSetSaver
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef

/**
 * Read view for a whole `.org` file as one note: the file-level property drawer,
 * the preface, the heading-less intro and every heading in a single scroll. Reached
 * from the Outline overflow menu's "View file", or directly from the Notebooks list
 * when Settings § Roam Features' "Open roam files directly in read mode" is on and
 * the file is an org-roam file (has a file-level `:ID:`) under the line limit
 * (`WHOLE_FILE_LINE_LIMIT`).
 *
 * Deliberately has no `☰` metadata FAB: those actions are heading-scoped. The
 * Read/Edit toggle switches to the whole-file editor (`EditRegionScreen` with
 * `EditRegion.WHOLE_FILE`). Large files open with foldable headings folded, like
 * note Read mode. The Linked References bar (backlinks) shows at the bottom for
 * roam files when Settings § Roam Features' "Show backlinks" is on.
 */
@Composable
fun ReadFileScreen(
    fileName: String,
    onBack: () -> Unit,
    /** Read/Edit toggle → Edit, and every double-tap in the rendered file. */
    onEdit: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    /** Tapping the file name in the top bar, and `[[file:]]` links to other files. */
    onOpenOutline: (fileName: String) -> Unit,
    onOpenDrawer: (kind: String, ref: NoteRef) -> Unit = { _, _ -> },
    onOpenBlock: (fileName: String, line: Int) -> Unit = { _, _ -> },
    onOpenPreface: (fileName: String) -> Unit = {},
    onOpenFileProperties: (fileName: String) -> Unit = {},
    /** Settings toggle: show a collapsible section for the file-level `#+` keywords. */
    showPreface: Boolean = true,
    /** Settings toggle: show collapsible sections for `:PROPERTIES:`/`:LOGBOOK:` drawers. */
    showPropertyDrawers: Boolean = true,
    /**
     * Settings § Roam Features (experimental): show the Linked References bar
     * (backlinks). Only ever renders for a roam file (one with a file-level
     * `:ID:`, i.e. `document.fileId != null`) -- this toggle alone doesn't
     * force it on for a non-roam file.
     */
    showBacklinks: Boolean = false,
    /** Settings § Notes: font-size lever for the rendered file. App chrome is unaffected. */
    readModeFontSize: FontSizePreference = FontSizePreference.MEDIUM,
    /** Favorited headlines in this file, matched per-heading by customId, marked with a ★. */
    favorites: List<FavoriteNote> = emptyList(),
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    val toast by viewModel.toast.collectAsStateWithLifecycle()
    val loadedDoc = (state as? DocumentUiState.Loaded)?.document
    val isRoamFile = loadedDoc?.fileId != null
    val linkedReferences by viewModel.linkedReferences.collectAsStateWithLifecycle()
    var linkedRefsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(fileName, loadedDoc) {
        val doc = loadedDoc ?: return@LaunchedEffect
        val fileTitle = doc.preambleKeywords.firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }
            ?.second ?: fileName.removeSuffix(".org")
        viewModel.loadLinkedReferences(fileName, INTRO_LINE_INDEX, doc.fileId, fileTitle)
    }
    // Resolves a tapped org link (heading/id: → Read mode, whole file → outline,
    // external scheme → OS, unresolved → toast). See DocumentViewModel.openOrgLink.
    val onOpenLink: (String) -> Unit = remember(viewModel, onOpenNote, onOpenOutline, fileName) {
        { target -> viewModel.openOrgLink(target, fileName, onOpenNote, onOpenOutline) }
    }

    // Reload whenever the screen comes back to the foreground (e.g. returning
    // from the whole-file editor) so saved edits show immediately. The ON_RESUME
    // right after entering composition is skipped: the LaunchedEffect below
    // already loads on entry.
    val lifecycleOwner = LocalLifecycleOwner.current
    var seenFirstResume by remember(fileName) { mutableStateOf(false) }
    DisposableEffect(lifecycleOwner, fileName) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (seenFirstResume) viewModel.load(fileName) else seenFirstResume = true
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(fileName) { viewModel.load(fileName) }

    // Reaching this screen retires the NEW badge on the Outline's "View file" item.
    MarkNewFeatureSeen(NewAnchors.OUTLINE_VIEW_FILE)

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                actions = {
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        optionIcons = listOf(Icons.Outlined.Visibility, Icons.Outlined.Edit),
                        selectedIndex = 0,
                        onSelect = { if (it == 1) onEdit() },
                        // 16dp here + the top bar's own 8dp = the 24dp read gutter,
                        // so the toggle lines up with the body (see ReadNoteScreen).
                        modifier = Modifier.padding(end = 16.dp).width(140.dp).testTag("read_edit_toggle"),
                    )
                },
                subtitle = {
                    // No heading to crumb to -- just the file segment, like Read
                    // mode's intro breadcrumb; a tap opens the outline.
                    ReadModeBreadcrumb(
                        fileName = fileName,
                        path = emptyList(),
                        onOpenBreadcrumb = { onOpenOutline(fileName) },
                        modifier = Modifier.testTag("read_file_label"),
                    )
                },
            )
        },
        bottomBar = {
            if (showBacklinks && isRoamFile) {
                LinkedReferencesBar(
                    linkedCount = linkedReferences.linkedCount,
                    unlinkedCount = linkedReferences.unlinkedCount,
                    onClick = { linkedRefsOpen = true },
                )
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().testTag("read_file_screen")) {
            when (val s = state) {
                is DocumentUiState.Loading -> {}
                is DocumentUiState.Error -> Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(s.message, fontFamily = PlexSans, color = c.ink2)
                }

                is DocumentUiState.Loaded -> {
                    val doc = s.document
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        ContentFontScale(readModeFontSize) {
                            FileContent(
                                doc = doc,
                                fileName = fileName,
                                listState = listState,
                                showPreface = showPreface,
                                showPropertyDrawers = showPropertyDrawers,
                                favorites = favorites,
                                onEdit = onEdit,
                                onOpenLink = onOpenLink,
                                onOpenDrawer = onOpenDrawer,
                                onOpenBlock = { line -> onOpenBlock(fileName, line) },
                                onOpenPreface = onOpenPreface,
                                onOpenFileProperties = onOpenFileProperties,
                                onToggleCheckbox = { line, longPress ->
                                    if (longPress) viewModel.toggleChecklistProgress(line)
                                    else viewModel.toggleChecklistDone(line)
                                },
                                modifier = Modifier
                                    .fillMaxSize()
                                    // Fallback: a double-tap on blank margin (not over
                                    // any text run) still switches to edit mode.
                                    .pointerInput(Unit) {
                                        detectTapGestures(onDoubleTap = { onEdit() })
                                    },
                            )
                        }
                        ScrollJumpButtons(
                            listState = listState,
                            // 24dp end = the read gutter, matching the toggle above.
                            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 16.dp),
                        )
                    }
                }
            }
            GroveToast(
                toast = toast,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(padding)
                    .padding(bottom = 16.dp),
            )
        }
    }

    if (linkedRefsOpen) {
        val doc = loadedDoc
        val title = doc?.preambleKeywords?.firstOrNull { it.first.equals("#+TITLE:", ignoreCase = true) }
            ?.second ?: fileName.removeSuffix(".org")
        LinkedReferencesSheet(
            title = title,
            result = linkedReferences,
            onOpenReference = { refFileName, lineIndex, id ->
                linkedRefsOpen = false
                onOpenNote(NoteRef(refFileName, lineIndex, id))
            },
            onDismiss = { linkedRefsOpen = false },
        )
    }
}

/**
 * The file's sections in document order: file `:PROPERTIES:`, PREFACE, the intro
 * body, then one [ReadHeadingRow] per visible heading, sized by its absolute level.
 */
@Composable
private fun FileContent(
    doc: OrgDocument,
    fileName: String,
    listState: LazyListState,
    showPreface: Boolean,
    showPropertyDrawers: Boolean,
    favorites: List<FavoriteNote>,
    onEdit: () -> Unit,
    onOpenLink: (String) -> Unit,
    onOpenDrawer: (kind: String, ref: NoteRef) -> Unit,
    onOpenBlock: (Int) -> Unit,
    onOpenPreface: (fileName: String) -> Unit,
    onOpenFileProperties: (fileName: String) -> Unit,
    /** Toggle a checklist box: `longPress` false = tap (done), true = long-press (in-progress). */
    onToggleCheckbox: (line: Int, longPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    // Per-section drawer expand state, reset when the file changes; every
    // drawer starts collapsed, matching note Read mode.
    val collapsibleExpanded = remember(fileName) { mutableStateMapOf<String, Boolean>() }
    // A roam file's preface holds its title, so it's the one thing worth
    // seeing by default; a plain file's preface starts collapsed like every
    // other section.
    var prefaceExpanded by rememberSaveable(fileName) { mutableStateOf(doc.fileId != null) }
    var filePropsExpanded by rememberSaveable(fileName) { mutableStateOf(false) }

    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var linkMenuState by remember { mutableStateOf<Pair<String, IntOffset>?>(null) }
    val onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit = remember {
        { target, textLocalPos, textCoords ->
            boxCoords?.let {
                val boxLocalPos = it.localPositionOf(textCoords, textLocalPos)
                linkMenuState = target to IntOffset(boxLocalPos.x.toInt(), boxLocalPos.y.toInt())
            }
        }
    }

    // A file with a long heading list opens with its foldable headings folded,
    // matching note Read mode (defaultReadCollapse / LARGE_SUBTREE_THRESHOLD);
    // seeded in the initializer so the first frame already renders folded.
    var collapsed by rememberSaveable(fileName, stateSaver = IntSetSaver) {
        mutableStateOf(defaultReadCollapse(doc, doc.headlines))
    }
    val visibleRows = remember(doc, collapsed) { visibleReadRows(doc.headlines, collapsed) }
    val intro = remember(doc) { doc.introBody.toList() }
    // O(1) favorite lookup per row (see FavoriteNote.matches).
    val favoriteCustomIds = remember(favorites) { favorites.mapNotNull { it.customId }.toSet() }
    val favoriteLineIndices = remember(favorites) {
        favorites.filter { it.customId == null }.map { it.lineIndex }.toSet()
    }

    val hasPreface = showPreface && doc.preambleKeywords.isNotEmpty()
    val hasFileProps = showPropertyDrawers && doc.filePropertyDrawer.isNotEmpty()
    val isEmpty = doc.headlines.isEmpty() && !doc.hasIntro && !hasPreface && !hasFileProps

    Box(Modifier.onGloballyPositioned { boxCoords = it }) {
        if (isEmpty) {
            Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("This file is empty. Double-tap to start writing.", fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2)
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = modifier.testTag("read_file_scroll"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 16.dp, bottom = 48.dp),
            ) {
                if (hasFileProps) {
                    item(key = "fileprops") {
                        CollapsibleKvSection(
                            label = ":PROPERTIES:",
                            entries = doc.filePropertyDrawer,
                            expanded = filePropsExpanded,
                            onToggle = { filePropsExpanded = !filePropsExpanded },
                            onDoubleTap = { onOpenFileProperties(fileName) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (hasPreface) {
                    item(key = "preface") {
                        CollapsibleKvSection(
                            label = "PREFACE",
                            entries = doc.preambleKeywords,
                            expanded = prefaceExpanded,
                            onToggle = { prefaceExpanded = !prefaceExpanded },
                            onDoubleTap = { onOpenPreface(fileName) },
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
                if (doc.hasIntro) {
                    item(key = "intro") {
                        Column(Modifier.padding(top = 8.dp)) {
                            SelectionContainer {
                                Column {
                                    BodyBlocks(
                                        intro, doc.introStart, onToggleCheckbox,
                                        onOpenLink, onLinkLongPress, onEdit, onOpenBlock,
                                    )
                                }
                            }
                        }
                    }
                }
                items(visibleRows, key = { it.lineIndex }) { child ->
                    val childCollapsed = child.lineIndex in collapsed
                    ReadHeadingRow(
                        doc = doc,
                        child = child,
                        fileName = fileName,
                        // No enclosing note: the heading's own level sets its size.
                        relLevel = child.level,
                        isCollapsed = childCollapsed,
                        isFavorite = remember(child, favoriteCustomIds, favoriteLineIndices) {
                            val ident = child.customId ?: child.id
                            (ident != null && ident in favoriteCustomIds) || child.lineIndex in favoriteLineIndices
                        },
                        onToggleFold = {
                            collapsed = if (childCollapsed) collapsed - child.lineIndex
                            else collapsed + child.lineIndex
                        },
                        collapsibleExpanded = collapsibleExpanded,
                        showPropertyDrawers = showPropertyDrawers,
                        onOpenDrawer = onOpenDrawer,
                        // The whole-file editor has no per-heading caret target.
                        onEditAt = { onEdit() },
                        onToggleCheckbox = onToggleCheckbox,
                        onOpenLink = onOpenLink,
                        onLinkLongPress = onLinkLongPress,
                        onOpenBlock = onOpenBlock,
                    )
                }
                item(key = "bottom-spacer") { Spacer(Modifier.height(40.dp)) }
            }
        }

        // Zero-size anchor Box at the press location; DropdownMenu anchors to it.
        val (target, anchorOffset) = linkMenuState ?: (null to IntOffset.Zero)
        if (target != null && anchorOffset != IntOffset.Zero) {
            Box(Modifier.offset { anchorOffset }) {
                DropdownMenu(
                    expanded = true,
                    onDismissRequest = { linkMenuState = null },
                    containerColor = c.surface,
                ) {
                    LinkActionMenuItems(target, onDismiss = { linkMenuState = null })
                }
            }
        }
    }
}
