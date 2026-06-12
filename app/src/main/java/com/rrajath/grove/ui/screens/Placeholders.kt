package com.rrajath.grove.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.components.GroveTopBar
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

// Placeholder screens for routes whose real implementations land in later
// milestones (outline/read: M2, capture: M3, conflict: M4, search: M6).

@Composable
fun OutlineScreen(notebookId: String, onBack: () -> Unit) {
    PlaceholderScreen(title = notebookId, subtitle = "Outline view arrives in M2", onBack = onBack)
}

@Composable
fun NoteScreen(noteId: String, mode: String, onBack: () -> Unit) {
    PlaceholderScreen(title = noteId, subtitle = "Note $mode mode arrives in M2", onBack = onBack)
}

@Composable
fun CaptureScreen(templateId: String?, onBack: () -> Unit) {
    PlaceholderScreen(title = "Capture", subtitle = "Capture templates arrive in M3", onBack = onBack)
}

@Composable
fun SearchScreen(onBack: () -> Unit) {
    PlaceholderScreen(title = "Search", subtitle = "Full-text search arrives in M6", onBack = onBack)
}

@Composable
fun ConflictScreen(notebookId: String, onBack: () -> Unit) {
    PlaceholderScreen(title = "Resolve conflict", subtitle = "Conflict picker arrives in M4", onBack = onBack)
}

@Composable
private fun PlaceholderScreen(title: String, subtitle: String, onBack: () -> Unit) {
    val c = MaterialTheme.grove
    Scaffold(
        containerColor = c.bg,
        topBar = {
            GroveTopBar(
                leading = { IconGlyph("←", onClick = onBack) },
                title = {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleLarge,
                        color = c.ink,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("✶", fontFamily = PlexMono, fontSize = 28.sp, color = c.ink3)
                Spacer(Modifier.height(10.dp))
                Text(
                    subtitle,
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 14.sp, color = c.ink2,
                )
            }
        }
    }
}
