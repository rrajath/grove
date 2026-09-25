package com.rrajath.grove.ui.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import com.rrajath.grove.ui.theme.grove
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexMono

/**
 * The editor suggestion strip's container: a full-width slot that is always exactly
 * one strip tall, whether or not [content] draws anything. Editors show it whenever
 * the keyboard is up, independent of which suggestion providers are enabled (today
 * only the Roam Features ones: [AutoLinkSuggestionStrip] and [RoamNodeSuggestionStrip]),
 * so the text above never jumps as chips or providers come and go.
 *
 * [content] is the one strip that applies right now, or nothing. Providers are
 * gated at their source (the view model / caller), never by this slot.
 */
@Composable
internal fun SuggestionSlot(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val line = MaterialTheme.grove.line
    // A hairline across the top marks the slot off from the text above it, so a tap
    // there (especially while it's empty) doesn't read as a tap on the text field.
    Box(
        modifier
            .fillMaxWidth()
            .drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), strokeWidth = 2f) },
    ) {
        SuggestionSlotSpacer()
        content()
    }
}

/**
 * An invisible stand-in exactly as tall as a suggestion strip (8dp strip padding +
 * 8dp chip padding around one 13sp mono line, top and bottom), so the slot keeps its
 * height while there are no chips. Sized from a real text line rather than a
 * hard-coded dp, so it tracks font scale.
 */
@Composable
private fun SuggestionSlotSpacer(modifier: Modifier = Modifier) {
    Text(
        " ",
        fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        modifier = modifier.alpha(0f).padding(vertical = 16.dp),
    )
}
