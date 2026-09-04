package com.rrajath.grove.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.rrajath.grove.settings.FontSizePreference

/**
 * Scales every `sp`-sized text inside [content] by [fontSize]'s scale, leaving the
 * app-wide typography untouched.
 *
 * Used in two nesting places:
 * - at the composition root ([com.rrajath.grove.ui.GroveApp] / `RescheduleActivity`),
 *   driven by [com.rrajath.grove.settings.GroveSettings.appFontSize] — the app-wide
 *   "Text size" baseline (Settings § Look and Feel).
 * - around read mode and edit mode note content, driven by the per-mode levers
 *   (Settings § Notes).
 *
 * The two compound: a `LocalDensity` `fontScale` override multiplies, so App=Large
 * nested under Read=Large gives ≈ 1.12 × 1.12. App chrome outside the note surfaces
 * only sees the baseline.
 *
 * It works by overriding [LocalDensity] with a proportionally larger/smaller
 * `fontScale`: `TextUnit.sp` → px conversion multiplies by `fontScale`, so all
 * descendant text scales uniformly — including the hardcoded `.sp` sizes in the
 * org renderer and the editor field — with no per-`Text` changes. `.em` line
 * heights track the text, and `.dp` spacing is unaffected.
 *
 * [FontSizePreference.MEDIUM] (scale `1f`) is a no-op and passes [content] through
 * without wrapping.
 */
@Composable
fun ContentFontScale(fontSize: FontSizePreference, content: @Composable () -> Unit) {
    if (fontSize == FontSizePreference.MEDIUM) {
        content()
        return
    }
    val base = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(base.density, base.fontScale * fontSize.scale),
        content = content,
    )
}
