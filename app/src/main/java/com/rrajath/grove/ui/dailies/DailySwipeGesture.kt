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
// On-device swipe-testing checklist: risk scenarios no automated (JVM/Robolectric)
// test can exercise, so a manual pass on a real device must specifically check them.
// DailyNoteScreen.kt's pointerInput block carries a shorter pointer back to this list.
//
// 1. A rightward swipe starting at the screen's left edge can be intercepted by the
//    system's edge-swipe-back gesture (gesture navigation mode) instead of navigating
//    to the previous day -- there's no reliable way to exclude just that edge across
//    the full screen height with this implementation.
// 2. Horizontal-scrolling children (e.g. org-mode tables in Read mode, or any wide
//    content with its own horizontal scroll) will claim the gesture themselves and
//    swallow the swipe.
// 3. The EditorToolbar row (if present) sits inside the same swipeable content Box, so
//    a finger dragging across it can trigger day-navigation instead of a toolbar action.
// 4. Swipes silently do nothing while `nav == null`, which is only until the screen's
//    first day-index listing completes (seconds on a large SAF-backed vault). After
//    that, date changes are resolved in memory by DailiesViewModel.select.
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
