package com.rrajath.grove.ui.components

import androidx.compose.foundation.MutatePriority
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
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.isActive

/**
 * Positive when [y] (in the viewport's own local coordinates, i.e. *not*
 * adjusted for scroll offset) is within [edgePx] of the top or bottom edge of
 * a viewport [viewportHeightPx] tall — magnitude growing the further past the
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
 * dropped by (and always wins a tug-of-war against) any lower-priority
 * scroll animation — e.g. a "bring cursor into view" `animateScrollTo` firing
 * at the same time as an active drag.
 */
private suspend fun ScrollState.nudge(urgency: Float) {
    if (urgency == 0f) return
    scroll(MutatePriority.UserInput) { scrollBy(urgency * 0.6f) }
}

/**
 * Auto-scrolls [scrollState] while a pointer is held down and dragged near
 * the top/bottom edge of this composable's viewport. Handles the common case
 * of a drag that starts and stays within this window — e.g. long-press then
 * drag to extend a text selection, before any selection handle has appeared.
 *
 * This is *observational only* (listens at [PointerEventPass.Initial], never
 * consumes), so it never interferes with whatever gesture — text selection,
 * BasicTextField editing, tapping a link — is already handling the touch.
 *
 * Known limitation: once a selection *handle* (the round drag grip) has
 * appeared and the user grabs it for a fresh touch, Compose renders that
 * handle in its own [androidx.compose.ui.window.Popup] — a separate Android
 * window — so its drag events never reach this (or any) modifier on the
 * underlying content; this modifier can't help there. For BasicTextField,
 * [chaseSelectionEdge] fixes that case too, by watching the selection's value
 * instead of raw touches. See that function's kdoc.
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
                scrollState.nudge(edgeUrgency(y, viewportHeight, edgePx))
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

/**
 * Continuously auto-scrolls [scrollState] to follow a text selection's active
 * edge as it nears the top/bottom of the viewport — including while the user
 * is dragging a selection *handle*, whose touch events [autoScrollWhileDragging]
 * can't see (they land in a separate Popup window; see its kdoc).
 *
 * Unlike raw pointer tracking, this is driven purely by the selection's
 * current value, which BasicTextField/SelectionContainer keep updating live
 * as the user drags — including handle drags — regardless of which window
 * the touch itself is delivered to. [edgeYProvider] should return the current
 * viewport-local Y (already adjusted for [scrollState]'s current offset — see
 * call site) of whichever selection edge is more "urgently" past a viewport
 * edge, or null when there's no active/measurable selection.
 *
 * Practical note: like standard Android text selection, this needs the
 * selection value to keep changing (i.e. the finger to keep moving, even
 * slightly) to keep scrolling — a perfectly motionless finger parked past the
 * edge won't continue to scroll on its own, matching native `EditText`
 * behavior (which is likewise driven by `ACTION_MOVE`, not a timer).
 */
suspend fun chaseSelectionEdge(
    scrollState: ScrollState,
    edgePx: Float,
    viewportHeightPx: () -> Int,
    edgeYProvider: () -> Float?,
) {
    while (coroutineContext.isActive) {
        val h = viewportHeightPx()
        val y = edgeYProvider()
        if (y != null && h > 0) {
            scrollState.nudge(edgeUrgency(y, h, edgePx))
        }
        withFrameNanos {}
    }
}
