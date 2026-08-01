package com.rrajath.grove.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.navigation.NavBackStackEntry

/**
 * Route transitions for Grove's full-screen surfaces, following the Android
 * predictive-back guidelines:
 * https://developer.android.com/design/ui/mobile/guides/patterns/predictive-back
 *
 * The pattern is a fade-through, not a slide. Every Grove route fills the
 * screen, and sliding one full-screen surface off while another slides on reads
 * as paging through images rather than as moving between levels of the app.
 *
 * Back gesture (pop): the screen being left scales 100% -> 90% and fades out
 * over the first 35% of the gesture; the previous screen then fades in while it
 * scales 110% -> 100%. Because both surfaces shrink, the whole scene reads as
 * one continuous zoom out. Forward navigation mirrors it as a zoom in.
 *
 * `NavHost` seeks these transitions with the back gesture's progress, so the
 * previous screen fades in exactly as far as the user has dragged and rewinds
 * if they let go before the commit point.
 */

/**
 * Full duration of the committed (non-seeked) animation. Matches the
 * predictive-back commit animation for full-screen surfaces.
 */
private const val TOTAL_MS = 450

/**
 * The guidelines' fade-through threshold: the outgoing surface is fully
 * transparent at 35% progress, and only then does the incoming one start to
 * appear, so the two never cross-dissolve over each other.
 */
private const val FADE_THROUGH_POINT = 0.35f
private val FADE_OUT_MS = (TOTAL_MS * FADE_THROUGH_POINT).toInt()
private val FADE_IN_MS = TOTAL_MS - FADE_OUT_MS

/** The guidelines' fade interpolator: leaves quickly, settles slowly. */
private val FadeThroughEasing = CubicBezierEasing(0.1f, 0.1f, 0f, 1f)

/**
 * STANDARD_DECELERATE. The guidelines call for a decelerating curve rather than
 * raw linear gesture progress, so the surface visibly responds the instant the
 * drag starts and eases as it approaches the commit point.
 */
private val StandardDecelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

/** Scale of a surface at its farthest point "behind" the current one. */
private const val SCALE_BEHIND = 0.90f

/** Scale of a surface at its farthest point "in front of" the current one. */
private const val SCALE_AHEAD = 1.10f

private fun fadeThroughIn(fromScale: Float): EnterTransition =
    fadeIn(tween(FADE_IN_MS, delayMillis = FADE_OUT_MS, easing = FadeThroughEasing)) +
        scaleIn(
            initialScale = fromScale,
            animationSpec = tween(TOTAL_MS, easing = StandardDecelerate),
        )

private fun fadeThroughOut(toScale: Float): ExitTransition =
    fadeOut(tween(FADE_OUT_MS, easing = FadeThroughEasing)) +
        scaleOut(
            targetScale = toScale,
            animationSpec = tween(TOTAL_MS, easing = StandardDecelerate),
        )

/** Forward navigation: the new screen rises from behind into place. */
val navEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeThroughIn(SCALE_BEHIND) }

/** Forward navigation: the current screen expands past the viewer and fades. */
val navExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeThroughOut(SCALE_AHEAD) }

/** Back: the previous screen settles down into place as the gesture is held. */
val navPopEnterTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeThroughIn(SCALE_AHEAD) }

/** Back: the current screen shrinks away and fades out first. */
val navPopExitTransition: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeThroughOut(SCALE_BEHIND) }
