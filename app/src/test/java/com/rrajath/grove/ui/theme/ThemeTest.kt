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
        assertEquals(GroveSynthwaveColors, groveColorsFor(ThemePreference.SYNTHWAVE))
        assertEquals(GroveDraculaColors, groveColorsFor(ThemePreference.DRACULA))
        assertEquals(GroveCatppuccinColors, groveColorsFor(ThemePreference.CATPPUCCIN))
        assertEquals(GroveNordColors, groveColorsFor(ThemePreference.NORD))
    }
}
