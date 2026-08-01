package com.rrajath.grove.ui.reminders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.rrajath.grove.GroveApplication
import com.rrajath.grove.reminders.ReminderKeys
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.NoteRef

/**
 * Resolves a reminder notification's deep link (fileName + composite heading
 * key) into that heading's *current* line index: the vault must be opened
 * and parsed to know it, so this is a brief loading step before landing on
 * `ReadNoteScreen` via `Routes.note`.
 */
@Composable
fun ReminderResolveScreen(
    fileName: String,
    headingPath: String,
    level: Int,
    onResolved: (ref: NoteRef) -> Unit,
    onFailed: () -> Unit,
) {
    val c = MaterialTheme.grove
    val app = LocalContext.current.applicationContext as GroveApplication

    LaunchedEffect(fileName, headingPath, level) {
        val vault = app.vault.value
        val doc = vault?.open(fileName)
        val headline = doc?.let { ReminderKeys.findHeadline(it, headingPath, level) }
        if (vault == null || doc == null || headline == null) {
            onFailed()
        } else {
            onResolved(NoteRef(fileName, headline.lineIndex))
        }
    }

    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = c.accent)
    }
}
