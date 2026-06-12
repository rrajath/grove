package com.rrajath.grove.ui.screens

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.SyncLogViewModel

/** Sync log (Settings → Sync → View Log, PRD §13). */
@Composable
fun SyncLogScreen(
    onBack: () -> Unit,
    viewModel: SyncLogViewModel = viewModel(factory = SyncLogViewModel.Factory),
) {
    val c = MaterialTheme.grove
    val entries by viewModel.entries.collectAsState()

    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    Text(
                        "Sync log",
                        style = MaterialTheme.typography.titleLarge, color = c.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            items(entries, key = { it.id }) { entry ->
                Column(Modifier.padding(vertical = 5.dp)) {
                    Text(
                        DateFormat.format("MM-dd HH:mm:ss", entry.timestamp).toString(),
                        fontFamily = PlexMono, fontSize = 10.5.sp, color = c.ink3,
                    )
                    Text(
                        entry.message,
                        fontFamily = PlexMono, fontSize = 12.5.sp, color = c.ink,
                    )
                }
            }
        }
    }
}
