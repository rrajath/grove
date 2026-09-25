package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.components.BrandMarkGlyph
import com.rrajath.grove.ui.components.notebookIcon
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove

/**
 * Prototype: horizontal chip strip docked above the keyboard while a 3+
 * character word is being typed, offering `id:`-linkable files/headings that
 * match it (see `AutoLinkSuggest.kt`). A chip whose title is short enough to
 * show in full inserts on the first tap; a longer, ellipsised one reveals its
 * full title on the first tap and inserts on the second.
 */
@Composable
fun AutoLinkSuggestionStrip(
    suggestions: List<AutoLinkSuggestion>,
    expandedKeys: Set<String>,
    onToggleExpand: (String) -> Unit,
    onPick: (AutoLinkSuggestion) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        // The trailing chip is hard-clipped at the row's end edge -- callers
        // keep that edge clear of their Save/menu FAB, so a clipped chip
        // there reads as sliced off. Dissolving it instead makes the strip
        // look like it fades into the FAB rather than stopping dead.
        modifier.fadingTrailingEdge(40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        items(suggestions, key = { it.key }) { suggestion ->
            val expanded = suggestion.key in expandedKeys
            val truncated = suggestion.title.length >= 10
            AutoLinkChip(
                suggestion = suggestion,
                label = ellipsizeChipLabel(suggestion.title, expanded),
                onClick = {
                    if (truncated && !expanded) onToggleExpand(suggestion.key) else onPick(suggestion)
                },
            )
        }
    }
}

@Composable
private fun AutoLinkChip(
    suggestion: AutoLinkSuggestion,
    label: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (suggestion) {
            is AutoLinkFileSuggestion ->
                Icon(notebookIcon(), contentDescription = null, tint = c.accent, modifier = Modifier.size(13.dp))
            is AutoLinkHeadingSuggestion ->
                BrandMarkGlyph(size = 9.dp, color = c.accent)
        }
        Spacer(Modifier.width(7.dp))
        Text(
            label,
            fontFamily = PlexMono, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, color = c.ink,
        )
    }
}

/**
 * An invisible stand-in exactly as tall as a suggestion strip (8dp strip padding +
 * 8dp chip padding around one 13sp mono line, top and bottom), so an editor can keep
 * the strip's slot reserved while there are no chips and the text above never jumps.
 * Sized from a real text line rather than a hard-coded dp, so it tracks font scale.
 */
@Composable
internal fun SuggestionSlotSpacer(modifier: Modifier = Modifier) {
    Text(
        " ",
        fontFamily = PlexMono, fontWeight = FontWeight.Medium, fontSize = 13.sp,
        modifier = modifier.alpha(0f).padding(vertical = 16.dp),
    )
}

/** Shared with [RoamNodeSuggestionStrip], the mutually-exclusive selection-triggered sibling strip. */
internal fun Modifier.fadingTrailingEdge(width: Dp) = this
    .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = width.toPx()
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Black, Color.Transparent),
                startX = size.width - fadePx,
                endX = size.width,
            ),
            blendMode = BlendMode.DstIn,
        )
    }
