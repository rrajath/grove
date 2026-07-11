package com.rrajath.grove.icon

import com.rrajath.grove.settings.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppIconManagerTest {

    @Test
    fun `toggle off always targets the default alias`() {
        for (theme in ThemePreference.entries) {
            assertEquals(AppIconManager.DEFAULT_ALIAS, AppIconManager.targetAlias(enabled = false, theme = theme))
        }
    }

    @Test
    fun `toggle on targets the alias matching the selected theme`() {
        assertEquals(".IconLight", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.LIGHT))
        assertEquals(".IconDark", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.DARK))
        assertEquals(".IconTokyoNight", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.TOKYONIGHT))
        assertEquals(".IconSynthwave", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.SYNTHWAVE))
        assertEquals(".IconDracula", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.DRACULA))
        assertEquals(".IconCatppuccin", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.CATPPUCCIN))
        assertEquals(".IconNord", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.NORD))
    }

    @Test
    fun `every theme has a distinct alias and is included in ALL_ALIASES`() {
        assertEquals(ThemePreference.entries.size, AppIconManager.THEME_ALIASES.size)
        assertEquals(AppIconManager.THEME_ALIASES.values.toSet().size, AppIconManager.THEME_ALIASES.size)
        for (alias in AppIconManager.THEME_ALIASES.values) {
            assertTrue(AppIconManager.ALL_ALIASES.contains(alias))
        }
        assertTrue(AppIconManager.ALL_ALIASES.contains(AppIconManager.DEFAULT_ALIAS))
    }
}
