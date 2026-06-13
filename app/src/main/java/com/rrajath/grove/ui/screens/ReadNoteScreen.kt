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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.core.net.toUri
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.org.BlockParser
import com.rrajath.grove.org.InlineTokenizer
import com.rrajath.grove.org.InlineType
import com.rrajath.grove.org.OrgBlock
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.PlexMono
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
                    NoteContent(
                        doc = doc,
                        headline = headline,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding)
                            // Double-tap anywhere in the note switches to edit mode.
                            .pointerInput(Unit) {
                                detectTapGestures(onDoubleTap = { onEdit() })
                            }
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 24.dp),
                        onOpenNote = onOpenNote,
                        fileName = noteRef.fileName,
                    )
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
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Column(modifier) {
        Spacer(Modifier.height(8.dp))

        // Tag chips
        val tags = doc.inheritedTags(headline)
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
        Text(
            headline.title,
            fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
            fontSize = 25.sp, color = c.ink, lineHeight = 1.3.em,
        )

        // Created / planning metadata
        headline.properties["CREATED"]?.let { created ->
            Spacer(Modifier.height(6.dp))
            Text("Created $created", fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink3)
        }
        headline.planning.scheduled?.let {
            Spacer(Modifier.height(6.dp))
            Text("◷ Scheduled ${it.format()}", fontFamily = PlexMono, fontSize = 12.5.sp, color = c.blue)
        }
        headline.planning.deadline?.let {
            Spacer(Modifier.height(6.dp))
            Text("DEADLINE ${it.format()}", fontFamily = PlexMono, fontSize = 12.5.sp, color = c.red)
        }
        Spacer(Modifier.height(16.dp))

        // Own body
        BodyBlocks(doc, doc.bodyOf(headline), fileName, onOpenNote)

        // Subtree rendered inline, headings sized by relative depth
        doc.subtree(headline).forEach { child ->
            Spacer(Modifier.height(20.dp))
            val rel = (child.level - headline.level).coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically) {
                child.keyword?.let { kw ->
                    val (fg, bg) = if (doc.keywords.isDone(kw)) c.green to c.greenSoft
                    else c.amber to c.amberSoft
                    Pill(kw, fg = fg, bg = bg)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    child.title,
                    fontFamily = PlexSerif, fontWeight = FontWeight.SemiBold,
                    fontSize = when (rel) {
                        1 -> 19.sp
                        2 -> 17.sp
                        else -> 16.sp
                    },
                    color = c.ink,
                )
            }
            Spacer(Modifier.height(8.dp))
            BodyBlocks(doc, doc.bodyOf(child), fileName, onOpenNote)
        }
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun BodyBlocks(
    doc: OrgDocument,
    bodyLines: List<String>,
    fileName: String,
    onOpenNote: (NoteRef) -> Unit,
) {
    val c = MaterialTheme.grove
    val context = LocalContext.current
    val blocks = remember(bodyLines) { BlockParser.parse(bodyLines) }

    fun openTarget(target: String) {
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

    blocks.forEach { block ->
        when (block) {
            is OrgBlock.Paragraph -> {
                Text(
                    annotateOrgText(block.lines.joinToString(" ") { it.trim() }, c, ::openTarget),
                    fontFamily = PlexSerif, fontSize = 16.sp,
                    lineHeight = 1.65.em, color = c.ink,
                )
                Spacer(Modifier.height(12.dp))
            }

            is OrgBlock.ListBlock -> {
                Column(Modifier.padding(start = 8.dp)) {
                    block.items.forEachIndexed { i, item ->
                        Row(Modifier.padding(vertical = 2.dp)) {
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
                            Text(
                                annotateOrgText(item.text, c, ::openTarget),
                                fontFamily = PlexSerif, fontSize = 16.sp,
                                lineHeight = 1.55.em, color = c.ink,
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
                        Text(line, fontFamily = PlexMono, fontSize = 13.sp, color = c.ink)
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
                        Text(line, fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink)
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

/** Render org inline markup into an AnnotatedString with tappable links. */
private fun annotateOrgText(
    text: String,
    c: GroveColors,
    onLink: (String) -> Unit,
): AnnotatedString = buildAnnotatedString {
    for (token in InlineTokenizer.tokenize(text)) {
        when (token.type) {
            InlineType.TEXT -> append(token.text)
            InlineType.BOLD -> withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(token.text) }
            InlineType.ITALIC -> withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(token.text) }
            InlineType.UNDERLINE -> withStyle(SpanStyle(textDecoration = TextDecoration.Underline)) { append(token.text) }
            InlineType.CODE, InlineType.VERBATIM -> withStyle(
                SpanStyle(fontFamily = PlexMono, fontSize = 13.5.sp, background = c.surface2)
            ) { append(token.text) }

            InlineType.TIMESTAMP -> withStyle(
                SpanStyle(fontFamily = PlexMono, fontSize = 13.5.sp, color = c.synTs)
            ) { append(token.text) }

            InlineType.LINK -> {
                val target = token.target ?: token.text
                withLink(
                    LinkAnnotation.Clickable(
                        tag = target,
                        styles = TextLinkStyles(
                            SpanStyle(color = c.synLink, textDecoration = TextDecoration.Underline)
                        ),
                    ) { onLink(target) }
                ) { append(token.text) }
            }
        }
    }
}
