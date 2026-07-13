package com.rrajath.grove.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import kotlinx.coroutines.launch

/** One action cell behind a swipeable row (design spec Gestures screen). */
data class SwipeAction(
    val glyph: String,
    val label: String,
    val fg: Color,
    val bg: Color,
    val onClick: () -> Unit,
)

// Design-spec swipe physics: 4×46dp panels, 66dp open threshold, rubber band
// past the panel width, 340ms settle with the prototype's cubic-bezier ease.
private val PanelWidth = 184.dp
private val CellWidth = 46.dp
private val OpenThreshold = 66.dp
private const val RubberBandFactor = 0.18f
private const val SettleMillis = 340
private val SettleEasing = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

/**
 * A row that swipes right to reveal [leftActions] (anchored at the left edge)
 * or left to reveal [rightActions]. Tapping the open card closes it; tapping a
 * cell closes and fires. The parent keeps at most one row open by flipping
 * [forceClose] from [onOpenChanged] callbacks.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SwipeRevealRow(
    leftActions: List<SwipeAction>,
    rightActions: List<SwipeAction>,
    enabled: Boolean,
    forceClose: Boolean,
    onOpenChanged: (Boolean) -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val c = MaterialTheme.grove
    val density = LocalDensity.current
    val panelPx = with(density) { PanelWidth.toPx() }
    val thresholdPx = with(density) { OpenThreshold.toPx() }
    val offset = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    // Raw finger travel this gesture; the visible offset rubber-bands past the panel.
    val dragRaw = remember { mutableFloatStateOf(0f) }
    val isOpen = offset.value != 0f

    fun rubberBand(x: Float): Float = when {
        x > panelPx -> panelPx + (x - panelPx) * RubberBandFactor
        x < -panelPx -> -panelPx + (x + panelPx) * RubberBandFactor
        else -> x
    }

    fun animateTo(target: Float) {
        scope.launch {
            if (target != 0f) onOpenChanged(true)
            offset.animateTo(target, tween(SettleMillis, easing = SettleEasing))
            if (target == 0f) onOpenChanged(false)
        }
    }

    fun settle() = animateTo(
        when {
            offset.value >= thresholdPx -> panelPx
            offset.value <= -thresholdPx -> -panelPx
            else -> 0f
        }
    )

    fun close() = animateTo(0f)

    LaunchedEffect(forceClose) {
        if (forceClose && offset.value != 0f) {
            offset.animateTo(0f, tween(SettleMillis, easing = SettleEasing))
        }
    }

    Box(modifier.clipToBounds()) {
        if (offset.value > 0f) {
            ActionPanel(leftActions, anchorEnd = false, onAction = ::close)
        }
        if (offset.value < 0f) {
            ActionPanel(rightActions, anchorEnd = true, onAction = ::close)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .graphicsLayer { translationX = offset.value }
                .background(c.bg)
                .combinedClickable(
                    onClick = { if (isOpen) close() else onTap() },
                    onLongClick = if (!isOpen && enabled) onLongPress else null,
                )
                .draggable(
                    state = rememberDraggableState { delta ->
                        dragRaw.floatValue += delta
                        scope.launch { offset.snapTo(rubberBand(dragRaw.floatValue)) }
                    },
                    orientation = Orientation.Horizontal,
                    enabled = enabled,
                    onDragStarted = { dragRaw.floatValue = offset.value },
                    onDragStopped = { settle() },
                ),
        ) {
            content()
        }
    }
}

/** The four 46dp action cells, anchored to one edge behind the card. */
@Composable
private fun androidx.compose.foundation.layout.BoxScope.ActionPanel(
    actions: List<SwipeAction>,
    anchorEnd: Boolean,
    onAction: () -> Unit,
) {
    Row(
        Modifier.matchParentSize(),
        horizontalArrangement = if (anchorEnd) Arrangement.End else Arrangement.Start,
    ) {
        actions.forEach { action ->
            Column(
                Modifier
                    .width(CellWidth)
                    .fillMaxHeight()
                    .background(action.bg)
                    .clickable {
                        onAction()
                        action.onClick()
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(action.glyph, fontFamily = PlexSans, fontSize = 16.sp, color = action.fg)
                Text(
                    action.label,
                    fontFamily = PlexSans, fontWeight = FontWeight.Medium,
                    fontSize = 9.sp, color = action.fg,
                )
            }
        }
    }
}
