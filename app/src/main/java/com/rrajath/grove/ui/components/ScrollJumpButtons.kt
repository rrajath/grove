package com.rrajath.grove.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rrajath.grove.ui.theme.grove
import kotlinx.coroutines.launch

/**
 * A vertically-stacked pair of small round jump buttons (scroll-to-top above
 * scroll-to-bottom) anchored to the bottom-right of the screen. Operates on
 * the caller's own [ScrollState] — no new scroll state is created.
 *
 * The pair is hidden entirely when [scrollState] has no scrollable overflow
 * (content fits on screen). Each button additionally hides itself once
 * already at that edge (top button disappears at the top, bottom button
 * disappears at the bottom).
 *
 * Callers should place this inside a `Box` and align it with
 * `Modifier.align(Alignment.BottomEnd)`, adding extra bottom padding to
 * clear a FAB when the screen has one.
 */
@Composable
fun ScrollJumpButtons(
    scrollState: ScrollState,
    modifier: Modifier = Modifier,
) {
    // No overflow: nothing to jump to, hide the whole pair.
    if (scrollState.maxValue <= 0) return

    val scope = rememberCoroutineScope()
    val atTop = scrollState.value <= 0
    val atBottom = scrollState.value >= scrollState.maxValue

    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (!atTop) {
            ScrollJumpButton(
                icon = Icons.Default.KeyboardArrowUp,
                contentDescription = "Scroll to top",
                onClick = { scope.launch { scrollState.animateScrollTo(0) } },
            )
            Spacer(Modifier.height(8.dp))
        }
        if (!atBottom) {
            ScrollJumpButton(
                icon = Icons.Default.KeyboardArrowDown,
                contentDescription = "Scroll to bottom",
                onClick = { scope.launch { scrollState.animateScrollTo(scrollState.maxValue) } },
            )
        }
    }
}

@Composable
private fun ScrollJumpButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Box(
        Modifier
            .size(40.dp)
            .shadow(elevation = 3.dp, shape = CircleShape, clip = false)
            .clip(CircleShape)
            .background(c.surface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = c.ink2)
    }
}
