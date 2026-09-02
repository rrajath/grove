package com.rrajath.grove.ui.newbadge

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.rrajath.grove.ui.components.Pill
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

/**
 * A small "NEW" pill for [anchorKey], shown only while that anchor belongs to an
 * active [NewFeature]. Renders nothing otherwise, so it is safe to leave in place
 * permanently at every call site.
 */
@Composable
fun NewBadge(anchorKey: String, modifier: Modifier = Modifier) {
    val c = MaterialTheme.grove
    if (!LocalNewBadges.current.state.isNew(anchorKey)) return
    Pill(text = "NEW", fg = c.accentInk, bg = c.accent, modifier = modifier)
}

/**
 * Marks every feature whose destination is [anchorKey] as seen when this enters
 * composition — reaching the destination clears the badge from the whole trail
 * (drawer item, settings row, section header, …) at once. Place it where the new
 * thing lives.
 */
@Composable
fun MarkNewFeatureSeen(anchorKey: String) {
    val badges = LocalNewBadges.current
    LaunchedEffect(anchorKey, badges) {
        val reached = badges.state.featuresReachedAt(anchorKey)
        if (reached.isNotEmpty()) badges.onFeaturesSeen(reached)
    }
}
