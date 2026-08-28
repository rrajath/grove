package com.rrajath.grove.icon

import com.rrajath.grove.settings.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
        assertEquals(".IconCatppuccin", AppIconManager.targetAlias(enabled = true, theme = ThemePreference.CATPPUCCIN))
    }

    @Test
    fun `notification mark color follows the same toggle as the launcher alias`() {
        // Off: always the default golden mark, whatever the theme.
        for (theme in ThemePreference.entries) {
            assertEquals(0xFFCB9D62.toInt(), AppIconManager.markColor(enabled = false, theme = theme))
        }
        // On: the fill of the matching ic_launcher_foreground_* drawable.
        assertEquals(0xFF8A5A2B.toInt(), AppIconManager.markColor(enabled = true, theme = ThemePreference.LIGHT))
        assertEquals(0xFF7AA2F7.toInt(), AppIconManager.markColor(enabled = true, theme = ThemePreference.TOKYONIGHT))
    }

    @Test
    fun `every theme has a mark color so no theme silently falls back`() {
        for (theme in ThemePreference.entries) {
            val themed = AppIconManager.markColor(enabled = true, theme = theme)
            if (theme != ThemePreference.DARK) {
                // DARK legitimately shares the default mark; every other theme
                // must define its own, so a missing entry can't hide here.
                assertTrue(
                    "no distinct mark color for $theme",
                    themed != AppIconManager.markColor(enabled = false, theme = theme),
                )
            }
        }
    }

    @Test
    fun `mipmap toggle off always falls back to the default launcher icon`() {
        for (theme in ThemePreference.entries) {
            assertEquals(
                AppIconManager.mipmapRes(enabled = false, theme = ThemePreference.LIGHT),
                AppIconManager.mipmapRes(enabled = false, theme = theme),
            )
        }
    }

    @Test
    fun `every theme has a distinct mipmap so no theme silently falls back`() {
        val themedMipmaps = ThemePreference.entries.map { AppIconManager.mipmapRes(enabled = true, theme = it) }
        assertEquals(ThemePreference.entries.size, themedMipmaps.toSet().size)
    }

    // --- skip-when-unchanged guard (PERFORMANCE_AUDIT_2026-08-27 #5) ---

    @Test
    fun `iconChangeNeeded is false when the enabled alias already matches the target`() {
        // Sync off, default alias already active: nothing to do on ON_STOP.
        assertFalse(
            AppIconManager.iconChangeNeeded(AppIconManager.DEFAULT_ALIAS, enabled = false, theme = ThemePreference.DARK),
        )
        // Sync on, the theme's own alias already active: still nothing to do.
        for (theme in ThemePreference.entries) {
            val active = AppIconManager.targetAlias(enabled = true, theme = theme)
            assertFalse("change wrongly flagged for $theme", AppIconManager.iconChangeNeeded(active, enabled = true, theme = theme))
        }
    }

    @Test
    fun `iconChangeNeeded is true when the enabled alias differs from the target`() {
        assertTrue(AppIconManager.iconChangeNeeded(AppIconManager.DEFAULT_ALIAS, enabled = true, theme = ThemePreference.DARK))
        assertTrue(AppIconManager.iconChangeNeeded(".IconLight", enabled = true, theme = ThemePreference.DARK))
        assertTrue(AppIconManager.iconChangeNeeded(".IconLight", enabled = false, theme = ThemePreference.LIGHT))
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
