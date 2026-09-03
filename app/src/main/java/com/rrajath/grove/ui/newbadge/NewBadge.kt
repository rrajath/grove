package com.rrajath.grove.ui.newbadge

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rrajath.grove.ui.theme.grove

/**
 * The badge state plus a "these features were reached" callback, provided once
 * near the nav host (see `GroveNavigation`) so any screen can pull it from the
 * composition rather than threading it through every signature.
 */
class NewBadges(
    val state: NewBadgeState,
    val onFeaturesSeen: (List<String>) -> Unit,
)

val LocalNewBadges = compositionLocalOf { NewBadges(NewBadgeState.EMPTY) {} }

/** Diameter of the accent dot marking an element that leads to a new feature. */
private val DotSize = 7.dp

/**
 * A small accent dot for [anchorKey], shown only while that anchor belongs to an
 * active [NewFeature]. Renders nothing otherwise, so it is safe to leave in place
 * permanently at every call site.
 *
 * Place it inline next to a label — inside a `Row` with
 * `verticalAlignment = Alignment.CenterVertically` — so it sits centered beside
 * the text. For an icon button, wrap the button in [NewDotBadge] instead, which
 * corner-mounts the same dot.
 */
@Composable
fun NewDot(anchorKey: String, modifier: Modifier = Modifier) {
    if (!LocalNewBadges.current.state.isNew(anchorKey)) return
    Box(
        modifier
            .size(DotSize)
            .clip(CircleShape)
            .background(MaterialTheme.grove.accent),
    )
}

/**
 * Wraps [content] — an icon button or glyph — and mounts a [NewDot] at its
 * top-right corner, ringed in [ringColor] so it reads clear of the glyph, while
 * [anchorKey] belongs to an active [NewFeature]. Draws just [content] otherwise,
 * so it is safe to leave in place permanently.
 */
@Composable
fun NewDotBadge(
    anchorKey: String,
    ringColor: Color = MaterialTheme.grove.bg,
    content: @Composable () -> Unit,
) {
    val active = LocalNewBadges.current.state.isNew(anchorKey)
    Box(contentAlignment = Alignment.TopEnd) {
        content()
        if (active) {
            Box(
                Modifier
                    .padding(top = 6.dp, end = 6.dp)
                    .size(DotSize + 3.dp)
                    .clip(CircleShape)
                    .background(ringColor),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    Modifier
                        .size(DotSize)
                        .clip(CircleShape)
                        .background(MaterialTheme.grove.accent),
                )
            }
        }
    }
}

/**
 * Marks every feature whose destination is [anchorKey] as seen when this *leaves*
 * composition — i.e. when the user navigates away from the screen the new thing
 * lives on. Retiring on exit rather than on entry keeps any [NewDot] at the
 * destination itself (a section header, say) visible for the whole visit; the
 * dot then clears from the entire trail — drawer item, settings row, header,
 * menu icon — at once. Place it where the new thing lives.
 */
@Composable
fun MarkNewFeatureSeen(anchorKey: String) {
    val badges by rememberUpdatedState(LocalNewBadges.current)
    DisposableEffect(anchorKey) {
        onDispose {
            val reached = badges.state.featuresReachedAt(anchorKey)
            if (reached.isNotEmpty()) badges.onFeaturesSeen(reached)
        }
    }
}
