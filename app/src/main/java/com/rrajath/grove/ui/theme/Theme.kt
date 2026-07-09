package com.rrajath.grove.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import com.rrajath.grove.settings.FontSizePreference
import com.rrajath.grove.settings.ThemePreference

/** Access the full Grove token set: `MaterialTheme.grove.accent`, etc. */
val MaterialTheme.grove: GroveColors
    @Composable
    @ReadOnlyComposable
    get() = LocalGroveColors.current

private fun materialScheme(c: GroveColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.accent,
        onPrimary = c.accentInk,
        primaryContainer = c.accentSoft,
        onPrimaryContainer = c.accent,
        secondary = c.ink2,
        onSecondary = c.surface,
        background = c.bg,
        onBackground = c.ink,
        surface = c.surface,
        onSurface = c.ink,
        surfaceVariant = c.surface2,
        onSurfaceVariant = c.ink2,
        surfaceContainer = c.surface,
        surfaceContainerLow = c.surface,
        surfaceContainerHigh = c.surface2,
        surfaceContainerHighest = c.surface3,
        outline = c.line2,
        outlineVariant = c.line,
        error = c.red,
        scrim = androidx.compose.ui.graphics.Color(0xFF140E06),
    )
}

@Composable
fun GroveTheme(
    theme: ThemePreference = ThemePreference.LIGHT,
    fontSize: FontSizePreference = FontSizePreference.MEDIUM,
    content: @Composable () -> Unit,
) {
    val groveColors = when (theme) {
        ThemePreference.LIGHT -> GroveLightColors
        ThemePreference.DARK -> GroveDarkColors
        ThemePreference.TOKYONIGHT -> GroveTokyoNightColors
        ThemePreference.SYNTHWAVE -> GroveSynthwaveColors
        ThemePreference.DRACULA -> GroveDraculaColors
        ThemePreference.CATPPUCCIN -> GroveCatppuccinColors
        ThemePreference.NORD -> GroveNordColors
    }
    CompositionLocalProvider(LocalGroveColors provides groveColors) {
        MaterialTheme(
            colorScheme = materialScheme(groveColors),
            typography = groveTypography(fontSize.scale),
            content = content,
        )
    }
}
