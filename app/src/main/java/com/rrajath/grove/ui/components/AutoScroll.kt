package com.rrajath.grove.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * Auto-scrolls [scrollState] while a pointer is held down near the top or
 * bottom edge of this composable. BasicTextField's own drag-to-select and
 * Compose's SelectionContainer don't scroll an ancestor Modifier.verticalScroll
 * on their own, so without this a selection drag that reaches the edge of the
 * viewport gets stuck instead of extending into off-screen content.
 *
 * Purely observational: it never consumes pointer events, so it doesn't
 * interfere with whatever gesture (selection, typing, tapping a link) is
 * already handling the touch.
 */
fun Modifier.autoScrollWhileDragging(scrollState: ScrollState, edgeSize: Dp = 56.dp): Modifier = composed {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeSize.toPx() }
    var viewportHeight by remember { mutableStateOf(0) }
    var pointerY by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(scrollState) {
        while (isActive) {
            val y = pointerY
            if (y != null && viewportHeight > 0) {
                val overTop = edgePx - y
                val overBottom = y - (viewportHeight - edgePx)
                val speed = when {
                    overTop > 0 -> -overTop
                    overBottom > 0 -> overBottom
                    else -> 0f
                }
                if (speed != 0f) scrollState.scrollBy(speed * 0.6f)
            }
            withFrameNanos { }
        }
    }

    this
        .onSizeChanged { size -> viewportHeight = size.height }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                var stillDown: Boolean
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    pointerY = change?.position?.y
                    stillDown = event.changes.any { it.pressed }
                } while (stillDown)
                pointerY = null
            }
        }
}
