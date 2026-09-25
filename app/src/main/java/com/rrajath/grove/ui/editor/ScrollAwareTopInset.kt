package com.rrajath.grove.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.offset

/**
 * Top gap for a self-scrolling [androidx.compose.foundation.text.BasicTextField] that
 * behaves like a lazy list's top `contentPadding`: the full [inset] while the field is
 * scrolled to the top, shrinking to nothing over the first [inset] of scroll.
 *
 * A plain `Modifier.padding(top = ...)` sits outside the field's clipped scroll
 * viewport, so it leaves a permanent strip under the top bar where scrolled text is
 * never drawn. The field has no content-padding parameter (its decorator is outside
 * the clip too), hence shrinking the gap itself as it scrolls. Chain it after the
 * field's size modifier, in place of the top padding.
 */
@Composable
fun Modifier.scrollAwareTopInset(scrollState: ScrollState, inset: Dp): Modifier {
    val insetPx = with(LocalDensity.current) { inset.roundToPx() }
    // Only changes during the first `inset` of scroll, so the field isn't
    // remeasured on every scroll frame after that.
    val currentInset by remember(scrollState, insetPx) {
        derivedStateOf { (insetPx - scrollState.value).coerceAtLeast(0) }
    }
    return layout { measurable, constraints ->
        val top = currentInset.coerceAtMost(constraints.maxHeight)
        val placeable = measurable.measure(constraints.offset(vertical = -top))
        layout(placeable.width, placeable.height + top) {
            placeable.place(0, top)
        }
    }
}
