package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.ui.components.notebookIcon
import com.rrajath.grove.ui.components.searchIcon
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.util.pluralCount
import com.rrajath.grove.ui.vault.headlineAtLine
import kotlinx.coroutines.launch

/**
 * Full-height "insert a link" bottom sheet (toolbar `[[ ]]` long-press): a
 * notebook drill-down (design language of the refile picker) plus a live,
 * vault-wide search over [LinkPickerUiState.searchIndex]. Always opens at the
 * notebook list -- never the file being edited -- with the search field
 * focused and the keyboard already up.
 *
 * Tapping a heading either drills into it (it has sub-headings) or hands it
 * straight to [onConfirmHeading] (a leaf heading commits immediately, instead
 * of drilling into an empty "no sub-headings" screen first).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkPickerSheet(
    state: LinkPickerUiState,
    onQueryChange: (String) -> Unit,
    onPickNotebook: (String) -> Unit,
    onDrillInto: (Int) -> Unit,
    onBack: () -> Unit,
    onCancel: () -> Unit,
    onConfirmFileOrHeading: () -> Unit,
    onSelectSearchResult: suspend (LinkSearchItem) -> LinkPickerSearchOutcome,
    onConfirmHeading: (OrgDocument, String, OrgHeadline) -> Unit,
) {
    val c = MaterialTheme.grove
    val doc = state.pickedDoc
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    val crumb = when {
        doc == null -> "Choose a notebook, or search files and headings"
        state.path.isEmpty() -> "${state.pickedFile?.removeSuffix(".org")} › top level · or pick a heading"
        else -> (listOf(state.pickedFile?.removeSuffix(".org")) +
            state.path.map { doc.headlineAtLine(it)?.title ?: "?" })
            .joinToString(" › ")
    }

    fun selectSearchResult(item: LinkSearchItem) {
        scope.launch {
            val outcome = onSelectSearchResult(item)
            if (outcome is LinkPickerSearchOutcome.ReadyToConfirm) {
                onConfirmHeading(outcome.doc, outcome.fileName, outcome.heading)
            }
        }
    }

    fun selectBrowseHeading(h: OrgHeadline) {
        val d = doc ?: return
        val file = state.pickedFile ?: return
        if (d.hasDescendants(h)) onDrillInto(h.lineIndex) else onConfirmHeading(d, file, h)
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onCancel,
        sheetState = sheetState,
        containerColor = c.surface,
        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doc != null) {
                    Box(
                        Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(c.surface2)
                            .clickable(onClick = onBack),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("←", fontFamily = PlexSans, fontSize = 16.sp, color = c.ink)
                    }
                    Spacer(Modifier.width(10.dp))
                }
                Column {
                    Text(
                        "Insert a link",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp, color = c.ink,
                    )
                    Text(crumb, fontFamily = PlexSans, fontSize = 12.sp, color = c.ink2)
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(c.surface2)
                    .border(1.dp, c.accent, RoundedCornerShape(11.dp))
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(searchIcon(), contentDescription = null, tint = c.ink3, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(9.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = state.query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(fontFamily = PlexMono, fontSize = 13.5.sp, color = c.ink),
                        cursorBrush = SolidColor(c.accent),
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                    if (state.query.isEmpty()) {
                        Text(
                            "Search files and headings",
                            fontFamily = PlexSans, fontSize = 13.5.sp, color = c.ink3,
                        )
                    }
                }
                if (state.query.isNotEmpty()) {
                    Text(
                        "×", fontFamily = PlexMono, fontSize = 15.sp, color = c.ink3,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .clickable { onQueryChange("") }
                            .padding(4.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))

            val searching = state.query.isNotBlank()
            val results = remember(state.query, state.searchIndex) {
                if (searching) state.searchIndex?.let { filterLinkSearch(it, state.query) }.orEmpty() else emptyList()
            }
            if (searching) {
                Text(
                    "FILES & HEADINGS",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp, letterSpacing = 0.09.em, color = c.ink3,
                    modifier = Modifier.padding(start = 4.dp, bottom = 6.dp),
                )
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (searching) {
                    if (results.isEmpty()) {
                        item {
                            Text(
                                "No matches",
                                fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(
                        results,
                        key = { hit ->
                            when (hit) {
                                is LinkFileHit -> "file:${hit.fileName}"
                                is LinkHeadingHit -> "heading:${hit.fileName}:${hit.lineIndex}"
                            }
                        },
                    ) { hit ->
                        when (hit) {
                            is LinkFileHit -> LinkPickerRow(
                                icon = notebookIcon(),
                                title = hit.title,
                                crumb = "file",
                                meta = hit.noteCount.takeIf { it > 0 }?.let { pluralCount(it, "heading") },
                                onClick = { selectSearchResult(hit) },
                            )
                            is LinkHeadingHit -> LinkPickerRow(
                                glyph = "✳",
                                title = hit.title,
                                crumb = hit.crumb,
                                meta = "heading",
                                onClick = { selectSearchResult(hit) },
                            )
                        }
                    }
                } else if (doc == null) {
                    items(state.notebooks.orEmpty(), key = { it.fileName }) { nb ->
                        LinkPickerRow(
                            icon = notebookIcon(),
                            title = nb.fileName.removeSuffix(".org"),
                            meta = pluralCount(nb.noteCount, "heading"),
                            onClick = { onPickNotebook(nb.fileName) },
                        )
                    }
                } else {
                    val level = state.path.lastOrNull()?.let { doc.headlineAtLine(it) }
                    val rows = level?.let { doc.directChildren(it) }
                        ?: doc.headlines.filter { doc.parent(it) == null }
                    if (rows.isEmpty()) {
                        item {
                            Text(
                                "No sub-headings here",
                                fontFamily = PlexSans, fontSize = 13.sp, color = c.ink3,
                                modifier = Modifier.padding(vertical = 12.dp),
                            )
                        }
                    }
                    items(rows, key = { it.lineIndex }) { h ->
                        LinkPickerRow(
                            glyph = "✳",
                            title = h.title,
                            meta = doc.directChildren(h).size.takeIf { it > 0 }?.let { pluralCount(it, "heading") },
                            onClick = { selectBrowseHeading(h) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = c.line)
            Row(
                Modifier.padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(c.surface2)
                        .border(1.dp, c.line, RoundedCornerShape(12.dp))
                        .clickable(onClick = onCancel)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                ) {
                    Text(
                        "Cancel",
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = c.ink2,
                    )
                }
                val enabled = state.pickedFile != null
                val label = if (state.path.isEmpty() && doc != null) "Link to this file" else "Link to this heading"
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (enabled) c.accent else c.surface2)
                        .clickable(enabled = enabled, onClick = onConfirmFileOrHeading)
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp, color = if (enabled) c.accentInk else c.ink3,
                    )
                }
            }
        }
    }
}

@Composable
private fun LinkPickerRow(
    glyph: String? = null,
    icon: ImageVector? = null,
    title: String,
    crumb: String? = null,
    meta: String?,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
        } else {
            Text(glyph.orEmpty(), fontFamily = PlexMono, fontSize = 15.sp, color = c.accent)
        }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title, fontFamily = PlexMono, fontWeight = FontWeight.Medium,
                fontSize = 14.5.sp, color = c.ink,
            )
            crumb?.let {
                Text(it, fontFamily = PlexSans, fontSize = 11.sp, color = c.ink3)
            }
        }
        meta?.let {
            Spacer(Modifier.width(8.dp))
            Text(it, fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink3)
        }
    }
}
