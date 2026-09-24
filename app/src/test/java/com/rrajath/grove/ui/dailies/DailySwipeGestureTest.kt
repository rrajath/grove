package com.rrajath.grove.ui.dailies

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DailySwipeGestureTest {

    private val screenWidth = 1080f

    @Test
    fun `a fast rightward drag past the distance threshold is a swipe to previous`() {
        assertEquals(SwipeDirection.PREVIOUS, isDeliberateSwipe(totalDragPx = 300f, velocityPxPerSec = 900f, screenWidthPx = screenWidth))
    }

    @Test
    fun `a fast leftward drag past the distance threshold is a swipe to next`() {
        assertEquals(SwipeDirection.NEXT, isDeliberateSwipe(totalDragPx = -300f, velocityPxPerSec = -900f, screenWidthPx = screenWidth))
    }

    @Test
    fun `a short drag is not a swipe regardless of speed`() {
        assertNull(isDeliberateSwipe(totalDragPx = 20f, velocityPxPerSec = 5000f, screenWidthPx = screenWidth))
    }

    @Test
    fun `a slow long drag is not a swipe`() {
        assertNull(isDeliberateSwipe(totalDragPx = 300f, velocityPxPerSec = 50f, screenWidthPx = screenWidth))
    }
}
