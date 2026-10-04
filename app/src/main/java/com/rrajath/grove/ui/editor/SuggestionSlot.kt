package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Height of the editor suggestion strip ("1c Lead suggestion" in the Suggestion Strip prototype). */
internal val SuggestionSlotHeight = 50.dp

/**
 * The editor suggestion strip's container: a full-width slot that is always exactly
 * one strip tall, whether or not [content] draws anything. Editors show it whenever
 * the keyboard is up, independent of which suggestion providers are enabled, so the
 * text above never jumps as chips or providers come and go.
 *
 * [content] is the one strip that applies right now, or nothing. Providers are
 * gated at their source (the view model / caller), never by this slot.
 *
 * [trailing] (Capture's Save button) sits inside the strip at its end edge, so the
 * hairline runs the full width and the chips fade out just before it.
 */
@Composable
internal fun SuggestionSlot(
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val line = MaterialTheme.grove.line
    // A hairline across the top marks the slot off from the text above it, so a tap
    // there (especially while it's empty) doesn't read as a tap on the text field.
    // Opaque, so text the field slides down near its top (scrollAwareTopInset)
    // passes behind the slot instead of showing through it.
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = SuggestionSlotHeight)
            .background(MaterialTheme.grove.bg)
            .drawBehind { drawLine(line, Offset.Zero, Offset(size.width, 0f), strokeWidth = 2f) }
            .padding(end = if (trailing != null) 16.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) { content() }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
}

/**
 * One suggestion in the strip: an outlined, unfilled chip. [color] tints the outline
 * and label (`line` / `ink` by default; Roam-node chips pass their template colour).
 * [glyph] draws an optional leading marker.
 */
@Composable
internal fun SuggestionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.grove.line,
    labelColor: Color = MaterialTheme.grove.ink,
    glyph: (@Composable () -> Unit)? = null,
) {
    val shape = RoundedCornerShape(9.dp)
    Row(
        modifier
            .heightIn(min = 32.dp)
            .clip(shape)
            .border(1.dp, borderColor, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (glyph != null) {
            glyph()
            Spacer(Modifier.width(7.dp))
        }
        Text(
            label,
            fontFamily = PlexSans, fontWeight = FontWeight.Medium,
            fontSize = 13.5.sp, color = labelColor,
            maxLines = 1,
        )
    }
}
