package com.rrajath.grove.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rrajath.grove.ui.components.BrandMarkGlyph
import com.rrajath.grove.ui.components.notebookIcon
import com.rrajath.grove.ui.theme.grove

/**
 * Horizontal suggestion strip docked above the keyboard while a 3+ character
 * word is being typed, offering `id:`-linkable files/headings that match it
 * (see `AutoLinkSuggest.kt`).
 * A chip whose title is short enough to show in full inserts on the first tap;
 * a longer, ellipsised one reveals its full title on the first tap and inserts
 * on the second.
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
        // The trailing chip dissolves into the strip's end edge rather than
        // being hard-clipped, which also signals the row scrolls.
        modifier.fadingTrailingEdge(SuggestionFadeWidth),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        contentPadding = SuggestionStripPadding,
    ) {
        items(suggestions, key = { it.key }) { suggestion ->
            val expanded = suggestion.key in expandedKeys
            val truncated = suggestion.title.length >= 10
            SuggestionChip(
                label = ellipsizeChipLabel(suggestion.title, expanded),
                onClick = {
                    if (truncated && !expanded) onToggleExpand(suggestion.key) else onPick(suggestion)
                },
                glyph = {
                    val tint = MaterialTheme.grove.accent
                    when (suggestion) {
                        is AutoLinkFileSuggestion ->
                            Icon(notebookIcon(), contentDescription = null, tint = tint, modifier = Modifier.size(13.dp))
                        is AutoLinkHeadingSuggestion ->
                            BrandMarkGlyph(size = 9.dp, color = tint)
                    }
                },
            )
        }
    }
}

/** Padding shared by every suggestion strip: a 10dp lead-in, and room to scroll the last chip clear of the fade. */
internal val SuggestionStripPadding = PaddingValues(start = 10.dp, end = 24.dp)

internal val SuggestionFadeWidth = 28.dp

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
