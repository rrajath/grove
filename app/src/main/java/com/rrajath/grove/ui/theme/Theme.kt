package com.rrajath.grove.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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

/** The full [GroveColors] token set for a given [ThemePreference], shared by the
 *  app theme and anything (icon preview, drawer logo) that needs a theme's
 *  colors outside of the currently active `MaterialTheme`. */
fun groveColorsFor(theme: ThemePreference): GroveColors = when (theme) {
    ThemePreference.LIGHT -> GroveLightColors
    ThemePreference.DARK -> GroveDarkColors
    ThemePreference.TOKYONIGHT -> GroveTokyoNightColors
    ThemePreference.TOKYODAY -> GroveTokyoDayColors
    ThemePreference.CATPPUCCIN -> GroveCatppuccinColors
    ThemePreference.CATPPUCCINLATTE -> GroveCatppuccinLatteColors
    ThemePreference.ROSEPINEDAWN -> GroveRosePineDawnColors
    ThemePreference.ROSEPINEMOON -> GroveRosePineMoonColors
    ThemePreference.CHALK -> GroveChalkColors
    ThemePreference.COBALT -> GroveCobaltColors
    ThemePreference.DELFT -> GroveDelftColors
    ThemePreference.SAGE -> GroveSageColors
    ThemePreference.ASHROSE -> GroveAshRoseColors
    ThemePreference.OBSIDIAN -> GroveObsidianColors
    ThemePreference.LANTERN -> GroveLanternColors
    ThemePreference.LIGHTHOUSE -> GroveLighthouseColors
    ThemePreference.MOSS -> GroveMossColors
    ThemePreference.WISTERIA -> GroveWisteriaColors
}

@Composable
fun GroveTheme(
    theme: ThemePreference = ThemePreference.LIGHT,
    content: @Composable () -> Unit,
) {
    val groveColors = groveColorsFor(theme)
    // enableEdgeToEdge()'s default SystemBarStyle.auto picks icon color from the
    // *system* dark/light setting, not Grove's own per-theme isDark, so a light
    // Grove theme chosen while the OS is in dark mode (or vice versa) ends up with
    // status/nav bar icons that don't contrast the app's actual background.
    // Every theme switch must re-assert the bar appearance itself.
    //
    // Configuration changes (rotation, ...) no longer recreate the Activity
    // (android:configChanges), and the framework resets the bar-icon appearance
    // when the window is reconfigured on rotation. Keying the effect on the
    // Configuration re-asserts it after every such change; keying on isDark
    // re-asserts it on a theme switch.
    val view = LocalView.current
    val configuration = LocalConfiguration.current
    if (!view.isInEditMode) {
        LaunchedEffect(groveColors.isDark, configuration) {
            val window = (view.context as? android.app.Activity)?.window ?: return@LaunchedEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !groveColors.isDark
            controller.isAppearanceLightNavigationBars = !groveColors.isDark
        }
    }
    CompositionLocalProvider(LocalGroveColors provides groveColors) {
        MaterialTheme(
            colorScheme = materialScheme(groveColors),
            typography = groveTypography(),
            content = content,
        )
    }
}
