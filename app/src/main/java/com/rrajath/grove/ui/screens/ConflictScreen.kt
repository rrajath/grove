package com.rrajath.grove.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.sync.ConflictResolution
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.ConflictUiState
import com.rrajath.grove.ui.vault.ConflictViewModel

/** Conflict picker (design spec §10) over a Syncthing .sync-conflict copy. */
@Composable
fun ConflictScreen(
    notebookId: String,
    onBack: () -> Unit,
    viewModel: ConflictViewModel = viewModel(factory = ConflictViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(notebookId) { viewModel.load(notebookId) }
    LaunchedEffect(state) { if (state is ConflictUiState.Resolved) onBack() }

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    Column(Modifier.padding(start = 4.dp)) {
                        Text(
                            "Resolve conflict",
                            style = MaterialTheme.typography.titleMedium, color = c.ink,
                        )
                        Text(notebookId, fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink2)
                    }
                },
            )
        },
    ) { padding ->
        when (val s = state) {
            is ConflictUiState.Loading, is ConflictUiState.Resolved -> {}
            is ConflictUiState.NoConflict -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text("No conflict on this notebook", fontFamily = PlexSans, color = c.ink2)
            }

            is ConflictUiState.Loaded -> Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
            ) {
                // Warning banner
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(13.dp))
                        .background(c.amberSoft)
                        .border(1.dp, c.amber, RoundedCornerShape(13.dp))
                        .padding(13.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(
                        Modifier.size(20.dp).clip(CircleShape).background(c.amber),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("!", fontFamily = PlexSans, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = c.surface)
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This file changed on two devices. Syncthing kept the newer " +
                                "version and saved the other as a conflict copy — pick what to keep.",
                        fontFamily = PlexSans, fontSize = 13.sp, lineHeight = 1.5.em, color = c.ink,
                    )
                }
                Spacer(Modifier.height(16.dp))

                DiffCard("CURRENT VERSION", s.currentText)
                Spacer(Modifier.height(12.dp))
                DiffCard("CONFLICT COPY · ${s.copyLabel}", s.copyText)
                Spacer(Modifier.height(18.dp))

                // Actions
                PrimaryButton("Keep both (merge under CONFLICT)") {
                    viewModel.resolve(s.fileName, ConflictResolution.KEEP_BOTH)
                }
                Spacer(Modifier.height(10.dp))
                Row {
                    SecondaryButton("Keep current", Modifier.weight(1f)) {
                        viewModel.resolve(s.fileName, ConflictResolution.KEEP_CURRENT)
                    }
                    Spacer(Modifier.width(10.dp))
                    SecondaryButton("Keep copy", Modifier.weight(1f)) {
                        viewModel.resolve(s.fileName, ConflictResolution.KEEP_CONFLICT_COPY)
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "Either way the .sync-conflict copy is removed afterwards.",
                    fontFamily = PlexMono, fontSize = 11.5.sp, color = c.ink3,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun DiffCard(label: String, text: String) {
    val c = MaterialTheme.grove
    Column {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp, letterSpacing = 0.8.sp, color = c.ink3,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 240.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(c.surface)
                .border(1.dp, c.line, RoundedCornerShape(13.dp))
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
        ) {
            Text(
                text.ifEmpty { "(empty file)" },
                fontFamily = PlexMono, fontSize = 12.5.sp, lineHeight = 1.7.em, color = c.ink,
            )
        }
    }
}

@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.accent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 14.5.sp, color = c.accentInk,
        )
    }
}

@Composable
private fun SecondaryButton(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val c = MaterialTheme.grove
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(13.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp, color = c.ink,
        )
    }
}
