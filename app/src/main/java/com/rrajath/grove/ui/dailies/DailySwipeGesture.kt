package com.rrajath.grove.ui.dailies

enum class SwipeDirection { PREVIOUS, NEXT }

/** Minimum horizontal travel, as a fraction of screen width, before a drag counts
 *  as a deliberate page-turn rather than incidental text-selection/cursor drag. */
private const val MIN_DISTANCE_FRACTION = 0.20f

/** Minimum release velocity (px/s) required alongside the distance threshold —
 *  a slow, deliberate long drag (e.g. dragging a selection handle across most of
 *  the screen) still shouldn't trigger navigation. */
private const val MIN_VELOCITY_PX_PER_SEC = 600f

/**
 * Whether a completed horizontal drag ([totalDragPx], [velocityPxPerSec] at
 * release) constitutes a deliberate swipe to navigate a day, or `null` if it
 * should be left alone (passed through to normal text-field touch handling —
 * tapping, cursor placement, text selection). Both the distance *and* velocity
 * thresholds must be cleared: distance alone would fire on a slow, deliberate
 * long drag (e.g. dragging a text-selection handle); velocity alone would fire
 * on a short, fast flick that barely moved. A rightward drag (positive px)
 * reveals the previous day, matching how a page peels back from the left;
 * leftward reveals the next.
 */
fun isDeliberateSwipe(totalDragPx: Float, velocityPxPerSec: Float, screenWidthPx: Float): SwipeDirection? {
    val minDistance = screenWidthPx * MIN_DISTANCE_FRACTION
    if (kotlin.math.abs(totalDragPx) < minDistance) return null
    if (kotlin.math.abs(velocityPxPerSec) < MIN_VELOCITY_PX_PER_SEC) return null
    // Direction must agree between distance and velocity — a drag that reversed
    // direction near release (net distance one way, final velocity the other)
    // is ambiguous and should not navigate.
    if ((totalDragPx > 0) != (velocityPxPerSec > 0)) return null
    return if (totalDragPx > 0) SwipeDirection.PREVIOUS else SwipeDirection.NEXT
}
