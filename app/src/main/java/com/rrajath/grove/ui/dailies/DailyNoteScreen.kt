package com.rrajath.grove.ui.dailies

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.components.Pill
import com.rrajath.grove.ui.components.SegmentedControl
import com.rrajath.grove.ui.editor.EditRegion
import com.rrajath.grove.ui.editor.EditorViewModel
import com.rrajath.grove.ui.editor.WholeFileEditorBody
import com.rrajath.grove.ui.screens.FileContent
import com.rrajath.grove.ui.screens.IconGlyph
import com.rrajath.grove.ui.screens.ReadModeBreadcrumb
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.DocumentUiState
import com.rrajath.grove.ui.vault.DocumentViewModel
import com.rrajath.grove.ui.vault.NoteRef
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DailyNoteScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onNavigateDate: (LocalDate) -> Unit,
    onOpenNote: (NoteRef) -> Unit,
    onOpenOutline: (fileName: String) -> Unit,
    onOpenDatePicker: () -> Unit,
    showBacklinks: Boolean,
    showPreface: Boolean,
    showPropertyDrawers: Boolean,
    readModeFontSize: FontSizePreference,
    editModeFontSize: FontSizePreference,
    dailiesViewModel: DailiesViewModel = viewModel(factory = DailiesViewModel.Factory),
    documentViewModel: DocumentViewModel = viewModel(factory = DocumentViewModel.Factory),
    editorViewModel: EditorViewModel = viewModel(factory = EditorViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val haptic = LocalHapticFeedback.current
    val nav by dailiesViewModel.state.collectAsStateWithLifecycle()
    var mode by rememberSaveable(date) { mutableStateOf("read") }

    LaunchedEffect(date) { dailiesViewModel.load(date) }
    LaunchedEffect(nav?.fileName, nav?.exists) {
        val n = nav ?: return@LaunchedEffect
        if (n.exists) {
            documentViewModel.load(n.fileName)
            documentViewModel.loadLinkedReferences(n.fileName, com.rrajath.grove.org.INTRO_LINE_INDEX, null, "")
        }
    }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    androidx.compose.foundation.layout.Row(
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEE, MMM d")),
                            fontFamily = PlexSans, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                            fontSize = 17.sp, color = c.ink,
                        )
                        if (nav?.isToday == true) {
                            Box(Modifier.padding(start = 8.dp)) {
                                Pill(text = "TODAY", fg = c.accentInk, bg = c.accent)
                            }
                        }
                    }
                },
                subtitle = {
                    nav?.let { n ->
                        ReadModeBreadcrumb(
                            fileName = n.fileName,
                            path = emptyList(),
                            onOpenBreadcrumb = { onOpenOutline(n.fileName) },
                        )
                    }
                },
                actions = {
                    if (nav?.isToday == false) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .combinedClickable(
                                    onClick = { onNavigateDate(LocalDate.now()) },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        onOpenDatePicker()
                                    },
                                )
                                .padding(10.dp)
                                .testTag("dailies_today_button"),
                        ) {
                            androidx.compose.material3.Icon(Icons.Filled.CalendarToday, contentDescription = "Today", tint = c.ink2)
                        }
                    }
                    SegmentedControl(
                        options = listOf("Read", "Edit"),
                        optionIcons = listOf(Icons.Outlined.Visibility, Icons.Outlined.Edit),
                        selectedIndex = if (mode == "edit") 1 else 0,
                        onSelect = { mode = if (it == 1) "edit" else "read" },
                        modifier = Modifier.padding(end = 16.dp).width(IntrinsicSize.Min).testTag("read_edit_toggle"),
                    )
                },
            )
        },
    ) { padding ->
        val n = nav
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                n == null -> {}
                !n.exists && mode == "read" -> DailyEmptyState(
                    fileName = n.fileName,
                    onStartTyping = {
                        editorViewModel.loadNewWholeFile(
                            n.fileName,
                            "", // seeded below once the header template is expanded (Task 16 wires the real settings-backed template)
                            0,
                        )
                        mode = "edit"
                    },
                )
                mode == "read" -> {
                    val docState by documentViewModel.state.collectAsStateWithLifecycle()
                    (docState as? DocumentUiState.Loaded)?.let { loaded ->
                        FileContent(
                            doc = loaded.document,
                            fileName = n.fileName,
                            listState = androidx.compose.foundation.lazy.rememberLazyListState(),
                            showPreface = showPreface,
                            showPropertyDrawers = showPropertyDrawers,
                            favorites = emptyList(),
                            onEdit = { mode = "edit" },
                            onOpenLink = { target -> documentViewModel.openOrgLink(target, n.fileName, onOpenNote, onOpenOutline) },
                            onOpenDrawer = { _, _ -> },
                            onOpenBlock = {},
                            onOpenPreface = {},
                            onOpenFileProperties = {},
                            onToggleCheckbox = { _, _ -> },
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                else -> {
                    LaunchedEffect(n.fileName, n.exists) {
                        if (n.exists) editorViewModel.load(NoteRef(n.fileName, 0)) // placeholder load path — replaced below
                    }
                    // Edit-mode body wired fully in Task 16 alongside the prev/next
                    // pills and bottom bar; this task establishes the mode switch
                    // and the Read-mode + empty-day states only.
                }
            }
        }
    }
}

@Composable
private fun DailyEmptyState(fileName: String, onStartTyping: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .fillMaxSize()
            .clickable(onClick = onStartTyping)
            .padding(32.dp),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            "Nothing here yet. Tap to start typing and create $fileName. " +
                "Long-press the calendar icon to create a note for another date.",
            fontFamily = PlexSans, fontSize = 14.sp, color = c.ink2,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
