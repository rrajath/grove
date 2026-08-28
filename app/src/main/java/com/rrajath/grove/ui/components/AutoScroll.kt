package com.rrajath.grove.ui.components

import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.isActive

/**
 * Positive when [y] (in the viewport's own local coordinates, i.e. *not*
 * adjusted for scroll offset) is within [edgePx] of the top or bottom edge of
 * a viewport [viewportHeightPx] tall: magnitude growing the further past the
 * edge, sign indicating direction (negative = scroll up/back). Zero anywhere
 * comfortably inside the viewport.
 */
private fun edgeUrgency(y: Float, viewportHeightPx: Int, edgePx: Float): Float {
    val overTop = edgePx - y
    val overBottom = y - (viewportHeightPx - edgePx)
    return when {
        overTop > 0f -> -overTop
        overBottom > 0f -> overBottom
        else -> 0f
    }
}

/**
 * Scrolls [scrollState] by an amount proportional to [urgency] (see
 * [edgeUrgency]), at [MutatePriority.UserInput] so this never gets silently
 * dropped by any lower-priority scroll animation running at the same time.
 *
 * Note that this priority also lets it *interrupt* an in-flight scroll drag,
 * which is why [autoScrollWhileSelecting] is careful to only call it for
 * gestures that are actually selecting text rather than scrolling.
 */
private suspend fun ScrollState.nudge(urgency: Float) {
    if (urgency == 0f) return
    scroll(MutatePriority.UserInput) { scrollBy(urgency * 0.6f) }
}

/**
 * Auto-scrolls [scrollState] while the user drags a text selection near the
 * top/bottom edge of this composable's viewport, so the selection can grow
 * past what is currently on screen.
 *
 * Only long-press-then-drag gestures qualify. The gesture is classified by how
 * it behaves during the platform long-press timeout: a press that stays put
 * that long and *then* moves is Compose's select-and-drag, while a press that
 * travels immediately is an ordinary scroll. Nudging an ordinary scroll would
 * be actively harmful: [nudge] runs at [MutatePriority.UserInput], the same
 * priority the scroll gesture itself holds, so it would cancel the drag the
 * user is in the middle of.
 *
 * This is *observational only* (listens at [PointerEventPass.Initial], never
 * consumes), so it never interferes with whatever gesture (text selection,
 * tapping a link) is already handling the touch.
 *
 * For the scrolled selection to actually keep growing, the `SelectionContainer`
 * must sit *outside* the scrolling content: Compose resolves a drag to a
 * character using coordinates local to that container, so a container that
 * scrolls along with the text would keep resolving the finger to the same
 * character no matter how far the content moved.
 *
 * Known limitation: once a selection *handle* (the round drag grip) has
 * appeared and the user grabs it for a fresh touch, Compose renders that
 * handle in its own [androidx.compose.ui.window.Popup] (a separate Android
 * window), so its drag events never reach this (or any) modifier on the
 * underlying content; this modifier can't help there. Text fields don't need
 * this modifier at all: `BasicTextField` with a `TextFieldState` implements
 * scroll-aware handle dragging itself, handles included.
 */
fun Modifier.autoScrollWhileSelecting(scrollState: ScrollState, edgeSize: Dp = 56.dp): Modifier = composed {
    val density = LocalDensity.current
    val edgePx = with(density) { edgeSize.toPx() }
    var viewportHeight by remember { mutableIntStateOf(0) }
    // Non-null only while a drag classified as a selection is in progress.
    var selectionPointerY by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(scrollState) {
        while (isActive) {
            val y = selectionPointerY
            if (y != null && viewportHeight > 0) {
                scrollState.nudge(edgeUrgency(y, viewportHeight, edgePx))
            }
            withFrameNanos { }
        }
    }

    this
        .onSizeChanged { size -> viewportHeight = size.height }
        .pointerInput(Unit) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                val longPressAt = down.uptimeMillis + viewConfiguration.longPressTimeoutMillis
                val slop = viewConfiguration.touchSlop
                // Travel before the long-press deadline marks the gesture as a
                // scroll; travel after it marks a settled press that has begun
                // dragging, i.e. an extending selection.
                var travelBefore = 0f
                var travelAfter = 0f
                var selecting = false

                var stillDown: Boolean
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                    val change = event.changes.firstOrNull()
                    if (change != null) {
                        val moved = change.positionChange().getDistance()
                        if (change.uptimeMillis < longPressAt) {
                            travelBefore += moved
                        } else if (travelBefore < slop) {
                            travelAfter += moved
                            if (travelAfter > slop) selecting = true
                        }
                        if (selecting) selectionPointerY = change.position.y
                    }
                    stillDown = event.changes.any { it.pressed }
                } while (stillDown)
                selectionPointerY = null
            }
        }
}
