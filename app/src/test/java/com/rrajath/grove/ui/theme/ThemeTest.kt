package com.rrajath.grove.ui.theme

import com.rrajath.grove.settings.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {

    @Test
    fun `groveColorsFor maps every ThemePreference to its matching palette`() {
        assertEquals(GroveLightColors, groveColorsFor(ThemePreference.LIGHT))
        assertEquals(GroveDarkColors, groveColorsFor(ThemePreference.DARK))
        assertEquals(GroveTokyoNightColors, groveColorsFor(ThemePreference.TOKYONIGHT))
        assertEquals(GroveTokyoDayColors, groveColorsFor(ThemePreference.TOKYODAY))
        assertEquals(GroveCatppuccinColors, groveColorsFor(ThemePreference.CATPPUCCIN))
        assertEquals(GroveCatppuccinLatteColors, groveColorsFor(ThemePreference.CATPPUCCINLATTE))
        assertEquals(GroveRosePineDawnColors, groveColorsFor(ThemePreference.ROSEPINEDAWN))
        assertEquals(GroveRosePineMoonColors, groveColorsFor(ThemePreference.ROSEPINEMOON))
    }
}
