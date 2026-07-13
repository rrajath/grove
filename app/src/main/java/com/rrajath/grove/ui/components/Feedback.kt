package com.rrajath.grove.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.vault.OutlineSnack
import com.rrajath.grove.ui.vault.OutlineToast

/**
 * Bottom-center dark pill toast (design spec: ink bg, bg-color text, ~1.9s —
 * the ViewModel owns the timer). Render inside a full-size Box.
 */
@Composable
fun GroveToast(toast: OutlineToast?, modifier: Modifier = Modifier) {
    val c = MaterialTheme.grove
    // Keep the last message so the exit animation doesn't show an empty pill.
    var lastMessage by remember { mutableStateOf("") }
    toast?.let { lastMessage = it.message }
    AnimatedVisibility(
        visible = toast != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(c.ink)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            Text(
                lastMessage,
                fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                fontSize = 13.sp, color = c.bg,
            )
        }
    }
}

/**
 * Bottom undo snackbar for structural ops (design spec: ink bg, 12dp radius,
 * 14dp side margins, UNDO in bold accent, ~4.2s — ViewModel owns the timer).
 */
@Composable
fun GroveUndoSnackbar(
    snack: OutlineSnack?,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    var lastMessage by remember { mutableStateOf("") }
    snack?.let { lastMessage = it.message }
    AnimatedVisibility(
        visible = snack != null,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(c.ink)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                lastMessage,
                fontFamily = PlexSans, fontSize = 13.5.sp, color = c.bg,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                "UNDO",
                fontFamily = PlexSans, fontWeight = FontWeight.Bold,
                fontSize = 13.sp, color = c.accent,
                modifier = Modifier.clickable(onClick = onUndo),
            )
        }
    }
}
