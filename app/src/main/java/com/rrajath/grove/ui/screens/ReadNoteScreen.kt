package com.rrajath.grove.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.BlockParser
import com.rrajath.grove.org.OrgBlock
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.components.annotateOrgInline
import com.rrajath.grove.ui.components.doubleTapToEdit
import com.rrajath.grove.ui.components.linkPressHandler
import com.rrajath.grove.ui.components.orgInlineLinks
import com.rrajath.grove.ui.components.ScrollJumpButtons
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.PlexSerif
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import com.rrajath.grove.ui.vault.headlineAtLine

/**
 * Read mode per design spec §5: rendered note (own body + subtree).
 * Edit mode is M5; the toggle shows a hint until then.
 */
@Composable
fun ReadNoteScreen(
    noteRef: NoteRef,
    onBack: () -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    /** Double-tap anywhere switches to edit mode. */
    onEdit: () -> Unit,
    viewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsState()
    // Reload whenever the screen comes back to the foreground (e.g. returning
    // from the editor) so saved edits show immediately.
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, noteRef.fileName) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.load(noteRef.fileName)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(noteRef.fileName) { viewModel.load(noteRef.fileName) }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {},
                actions = {
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        selectedIndex = 0,
                        onSelect = { if (it == 1) onEdit() },
                        modifier = Modifier.width(140.dp),
                    )
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is DocumentUiState.Loading -> {}
            is DocumentUiState.Error -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(s.message, color = c.ink2)
            }

            is DocumentUiState.Loaded -> {
                val doc = s.document
                val headline = doc.headlineAtLine(noteRef.lineIndex)
                if (headline == null) {
                    Box(
                        Modifier.fillMaxSize().padding(padding),
                        contentAlignment = Alignment.Center,
                    ) { Text("Note not found", color = c.ink2) }
                } else {
                    val listState = rememberLazyListState()
                    Box(Modifier.fillMaxSize().padding(padding)) {
                        NoteContent(
                            doc = doc,
                            headline = headline,
                            listState = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                // Fallback: double-tap on blank space (not over any
                                // rendered text run) still switches to edit mode. Taps
                                // that land on actual text are handled per-run below
                                // (doubleTapToEdit on each OrgText/line), which wins the
                                // gesture race against SelectionContainer's own
                                // double-tap-select-word — so in practice this outer
                                // catch-all only ever fires for empty margins.
                                .pointerInput(Unit) {
                                    detectTapGestures(onDoubleTap = { onEdit() })
                                },
                            onOpenNote = onOpenNote,
                            fileName = noteRef.fileName,
                            onEditAt = onEdit,
                        )
                        ScrollJumpButtons(
                            listState = listState,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NoteContent(
    doc: OrgDocument,
    headline: OrgHeadline,
    fileName: String,
    onOpenNote: (NoteRef) -> Unit,
    onEditAt: () -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    var boxCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var linkMenuState by remember { mutableStateOf<Pair<String, IntOffset>?>(null) }
    // Remembered so rows keep stable callbacks and can skip recomposition —
    // e.g. opening the link menu (state change below) must not re-render rows.
    val openLink: (String) -> Unit = remember(doc, fileName, context, onOpenNote) {
        { openOrgTarget(it, doc, fileName, context, onOpenNote) }
    }
    val onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit = remember {
        { target, textLocalPos, textCoords ->
            // Convert text-local position to outer-Box-local position
            boxCoords?.let {
                val boxLocalPos = it.localPositionOf(textCoords, textLocalPos)
                linkMenuState = target to IntOffset(boxLocalPos.x.toInt(), boxLocalPos.y.toInt())
            }
        }
    }

    // O(document) traversals, computed once per document instead of per
    // recomposition. Children are paired with their body lines up front so the
    // lazy items below stay cheap.
    val tags = remember(doc, headline) { doc.inheritedTags(headline) }
    val ownBody = remember(doc, headline) { doc.bodyOf(headline) }
    val children = remember(doc, headline) {
        doc.subtree(headline).map { it to doc.bodyOf(it) }
    }

    Box(
        Modifier.onGloballyPositioned { boxCoords = it }
    ) {
        LazyColumn(
            state = listState,
            modifier = modifier,
            contentPadding = PaddingValues(horizontal = 24.dp),
        ) {
            item(key = "header", contentType = "header") {
                SelectionContainer {
                    Column {
                        Spacer(Modifier.height(8.dp))

                        // Tag chips
                        if (tags.isNotEmpty()) {
                            Row {
                                tags.forEach { tag ->
                                    Pill(tag, fg = c.accent, bg = c.accentSoft)
                                    Spacer(Modifier.width(7.dp))
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }

                        // Title
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            headline.keyword?.let { kw ->
                                val (fg, bg) = if (doc.keywords.isDone(kw)) c.green to c.greenSoft
                                else c.amber to c.amberSoft
                                Pill(kw, fg = fg, bg = bg)
                                Spacer(Modifier.width(8.dp))
                            }
                        }
                        OrgText(
                            headline.title, onOpenLink = openLink, onLinkLongPress = onLinkLongPress,
                            onDoubleTapAt = onEditAt,
                            style = TextStyle(
                                fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
                                fontSize = 25.sp, color = c.ink, lineHeight = 1.3.em,
                            ),
                        )

                        // Created / planning metadata
                        headline.properties["CREATED"]?.let { created ->
                            Spacer(Modifier.height(6.dp))
                            Text("Created $created", fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink3)
                        }
                        headline.planning.scheduled?.let {
                            Spacer(Modifier.height(6.dp))
                            PlanningChip("SCHEDULED: ${it.format()}", fg = c.blue, bg = c.blueSoft)
                        }
                        headline.planning.deadline?.let {
                            Spacer(Modifier.height(6.dp))
                            PlanningChip("DEADLINE: ${it.format()}", fg = c.red, bg = c.redSoft)
                        }
                        Spacer(Modifier.height(16.dp))

                        // Own body
                        BodyBlocks(ownBody, openLink, onLinkLongPress, onEditAt)
                    }
                }
            }

            // Subtree rendered inline, headings sized by relative depth
            items(
                children,
                key = { (child, _) -> child.lineIndex },
                contentType = { "child" },
            ) { (child, body) ->
                SelectionContainer {
                    Column {
                        Spacer(Modifier.height(20.dp))
                        val rel = (child.level - headline.level).coerceAtLeast(1)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            child.keyword?.let { kw ->
                                val (fg, bg) = if (doc.keywords.isDone(kw)) c.green to c.greenSoft
                                else c.amber to c.amberSoft
                                Pill(kw, fg = fg, bg = bg)
                                Spacer(Modifier.width(8.dp))
                            }
                            OrgText(
                                child.title, onOpenLink = openLink, onLinkLongPress = onLinkLongPress,
                                onDoubleTapAt = onEditAt,
                                style = TextStyle(
                                    fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
                                    fontSize = when (rel) {
                                        1 -> 19.sp
                                        2 -> 17.sp
                                        else -> 16.sp
                                    },
                                    color = c.ink,
                                ),
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        BodyBlocks(body, openLink, onLinkLongPress, onEditAt)
                    }
                }
            }

            item(key = "footer", contentType = "footer") {
                Spacer(Modifier.height(40.dp))
            }
        }

        // Zero-size anchor Box at the press location; DropdownMenu anchors to it
        val (target, anchorOffset) = linkMenuState ?: (null to IntOffset.Zero)
        if (target != null && anchorOffset != IntOffset.Zero) {
            Box(
                Modifier.offset { anchorOffset }
            ) {
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

/** Text that renders org inline markup and hands link taps/long-presses to [onOpenLink]/[onLinkLongPress]. */
@Composable
private fun OrgText(
    text: String,
    onOpenLink: (String) -> Unit,
    onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    /** Double-tap anywhere in this run switches to edit mode. Word-selection
     * via long-press, and link taps, are unaffected — see [doubleTapToEdit]. */
    onDoubleTapAt: (() -> Unit)? = null,
) {
    val c = MaterialTheme.grove
    val annotated = remember(text, c, onOpenLink) { annotateOrgInline(text, c, onOpenLink) }
    val links = remember(text) { orgInlineLinks(text) }
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var textCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }

    // Wrapper to pass text's coordinates to the long-press handler
    val wrappedOnLongPress: (String, Offset) -> Unit = { target, textLocalPos ->
        textCoords?.let { onLinkLongPress(target, textLocalPos, it) }
    }

    Text(
        annotated,
        style = style,
        maxLines = maxLines,
        overflow = overflow,
        onTextLayout = { layout = it },
        modifier = modifier
            .onGloballyPositioned { textCoords = it }
            .doubleTapToEdit(
                layoutResult = { layout },
                links = links,
                enabled = onDoubleTapAt != null,
                onDoubleTap = { onDoubleTapAt?.invoke() },
            )
            .linkPressHandler(
                links = links,
                layoutResult = { layout },
                onTap = onOpenLink,
                onLongPress = wrappedOnLongPress,
            ),
    )
}

/** Copy link / Share link — the actions offered when long-pressing a link in read mode. */
@Composable
private fun LinkActionMenuItems(target: String, onDismiss: () -> Unit) {
    val c = MaterialTheme.grove
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    DropdownMenuItem(
        text = { Text("Copy link", fontFamily = PlexSans, fontSize = 14.sp, color = c.ink) },
        onClick = {
            clipboard.setText(AnnotatedString(target))
            onDismiss()
        },
    )
    DropdownMenuItem(
        text = { Text("Share link", fontFamily = PlexSans, fontSize = 14.sp, color = c.ink) },
        onClick = {
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, target)
            }
            context.startActivity(Intent.createChooser(intent, null))
            onDismiss()
        },
    )
}

/** A planning date shown as a soft-tinted chip (SCHEDULED blue, DEADLINE red). */
@Composable
private fun PlanningChip(text: String, fg: Color, bg: Color) {
    Box(
        Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text, fontFamily = PlexMono, fontSize = 12.5.sp, color = fg)
    }
}

@Composable
private fun BodyBlocks(
    bodyLines: List<String>,
    openTarget: (String) -> Unit,
    onLinkLongPress: (String, Offset, LayoutCoordinates) -> Unit,
    onEditAt: () -> Unit,
) {
    val c = MaterialTheme.grove
    val blocks = remember(bodyLines) { BlockParser.parse(bodyLines) }

    blocks.forEach { block ->
        when (block) {
            is OrgBlock.Paragraph -> {
                OrgText(
                    block.lines.joinToString(" ") { it.trim() },
                    onOpenLink = openTarget, onLinkLongPress = onLinkLongPress,
                    onDoubleTapAt = onEditAt,
                    style = TextStyle(fontFamily = PlexSerif, fontSize = 16.sp, lineHeight = 1.65.em, color = c.ink),
                )
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.ListBlock -> {
                Column(Modifier.padding(start = 8.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row(Modifier.padding(vertical = 2.dp).padding(start = (item.indent * 4).dp)) {
                            Text(
                                when {
                                    item.checkbox == 'X' || item.checkbox == 'x' -> "☑"
                                    item.checkbox == ' ' -> "☐"
                                    item.ordered -> "${i + 1}."
                                    else -> "•"
                                },
                                fontFamily = PlexSerif, fontSize = 16.sp, color = c.ink2,
                            )
                            Spacer(Modifier.width(10.dp))
                            OrgText(
                                item.text,
                                onOpenLink = openTarget, onLinkLongPress = onLinkLongPress,
                                onDoubleTapAt = onEditAt,
                                style = TextStyle(
                                    fontFamily = PlexSerif, fontSize = 16.sp,
                                    lineHeight = 1.55.em, color = c.ink,
                                ),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.CodeBlock -> {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface2)
                        .padding(12.dp),
                ) {
                    block.lines.forEach { line ->
                        PlainTappableLine(
                            line, fontFamily = PlexMono, fontSize = 13.sp, color = c.ink,
                            onDoubleTapAt = onEditAt,
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.Table -> {
                // v1 decision: tables as monospace plain text
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(c.surface)
                        .padding(10.dp),
                ) {
                    block.lines.forEach { line ->
                        PlainTappableLine(
                            line, fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink,
                            onDoubleTapAt = onEditAt,
                        )
                    }
                    Text(
                        "table rendering coming in v2",
                        fontFamily = PlexMono, fontSize = 10.5.sp, color = c.ink3,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** A single plain (non-org-markup) line — code/table content — that maps a
 * double-tap to edit mode at the tapped character. */
@Composable
private fun PlainTappableLine(
    line: String,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontSize: androidx.compose.ui.unit.TextUnit,
    color: Color,
    onDoubleTapAt: () -> Unit,
) {
    var layout by remember { mutableStateOf<TextLayoutResult?>(null) }
    Text(
        line,
        fontFamily = fontFamily, fontSize = fontSize, color = color,
        onTextLayout = { layout = it },
        modifier = Modifier.doubleTapToEdit(
            layoutResult = { layout },
            onDoubleTap = onDoubleTapAt,
        ),
    )
}

/** Resolve an org link target: internal id/custom-id jumps to the note, else opens externally. */
private fun openOrgTarget(
    target: String,
    doc: OrgDocument,
    fileName: String,
    context: android.content.Context,
    onOpenNote: (NoteRef) -> Unit,
) {
    when {
        target.startsWith("id:") ->
            doc.findById(target.removePrefix("id:"))
                ?.let { onOpenNote(NoteRef(fileName, it.lineIndex)) }

        target.startsWith("#") ->
            doc.findByCustomId(target.removePrefix("#"))
                ?.let { onOpenNote(NoteRef(fileName, it.lineIndex)) }

        else -> runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, target.toUri()))
        }
    }
}
