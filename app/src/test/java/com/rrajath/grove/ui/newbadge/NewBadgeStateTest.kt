package com.rrajath.grove.ui.newbadge

import com.rrajath.grove.settings.GroveSettings
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NewBadgeStateTest {

    private val feature = NewFeature(
        id = "f1",
        since = 10300,
        anchors = setOf("a.drawer", "a.row", "a.dest"),
        destination = "a.dest",
    )

    private fun settings(baseline: Int?, seen: Set<String> = emptySet()) =
        GroveSettings(newBadgeBaseline = baseline, seenNewFeatures = seen)

    @Test
    fun `no baseline recorded yet shows nothing`() {
        val state = NewBadgeState.from(settings(baseline = null), listOf(feature))
        assertFalse(state.isNew("a.drawer"))
    }

    @Test
    fun `fresh install seeded at the current build shows nothing`() {
        // Onboarding stamps the baseline with the current build, so a feature that
        // shipped in that same build is not "new" to this install.
        val state = NewBadgeState.from(settings(baseline = 10300), listOf(feature))
        assertFalse(state.isNew("a.drawer"))
        assertFalse(state.isNew("a.dest"))
    }

    @Test
    fun `update from an older build badges every anchor`() {
        val state = NewBadgeState.from(settings(baseline = 10200), listOf(feature))
        assertTrue(state.isNew("a.drawer"))
        assertTrue(state.isNew("a.row"))
        assertTrue(state.isNew("a.dest"))
        assertFalse(state.isNew("a.unrelated"))
    }

    @Test
    fun `a seen feature no longer badges`() {
        val state = NewBadgeState.from(settings(baseline = 10200, seen = setOf("f1")), listOf(feature))
        assertFalse(state.isNew("a.drawer"))
    }

    @Test
    fun `only the destination anchor retires the feature`() {
        val state = NewBadgeState.from(settings(baseline = 10200), listOf(feature))
        assertEquals(listOf("f1"), state.featuresReachedAt("a.dest"))
        assertTrue(state.featuresReachedAt("a.drawer").isEmpty())
    }

    @Test
    fun `reset drops the baseline to zero and re-arms the feature`() {
        val state = NewBadgeState.from(settings(baseline = 0), listOf(feature))
        assertTrue(state.isNew("a.dest"))
    }

    @Test
    fun `the shipped registry stays internally consistent`() {
        NEW_FEATURES.forEach { f ->
            assertTrue(
                "${f.id}: destination must be one of its anchors",
                f.destination in f.anchors,
            )
        }
    }
}
