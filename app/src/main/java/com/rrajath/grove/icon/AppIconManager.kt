package com.rrajath.grove.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import com.rrajath.grove.settings.ThemePreference

/**
 * Switches the launcher icon between the default mark and a themed variant by
 * enabling exactly one `activity-alias` declared in AndroidManifest.xml and
 * disabling the rest. See "Sync App Icon with Theme" in Settings > Appearance.
 */
object AppIconManager {

    const val DEFAULT_ALIAS = ".IconDefault"

    // Alias classes resolve against the manifest package, not the applicationId —
    // in debug builds packageName is "com.rrajath.grove.debug" (applicationIdSuffix)
    // while the alias class stays "com.rrajath.grove.IconDefault".
    private const val MANIFEST_PACKAGE = "com.rrajath.grove"

    val THEME_ALIASES: Map<ThemePreference, String> = mapOf(
        ThemePreference.LIGHT to ".IconLight",
        ThemePreference.DARK to ".IconDark",
        ThemePreference.TOKYONIGHT to ".IconTokyoNight",
        ThemePreference.SYNTHWAVE to ".IconSynthwave",
        ThemePreference.DRACULA to ".IconDracula",
        ThemePreference.CATPPUCCIN to ".IconCatppuccin",
        ThemePreference.NORD to ".IconNord",
    )

    val ALL_ALIASES: List<String> = listOf(DEFAULT_ALIAS) + THEME_ALIASES.values

    /**
     * The alias that should be enabled for a given (enabled, theme) pair — pure
     * mapping logic, split out from [applyIcon] so it's JVM-testable without a
     * Context/PackageManager.
     */
    fun targetAlias(enabled: Boolean, theme: ThemePreference): String =
        if (enabled) THEME_ALIASES[theme] ?: DEFAULT_ALIAS else DEFAULT_ALIAS

    /**
     * Enables the alias matching [theme] when [enabled] is true (icon follows
     * the selected theme); otherwise enables the default alias. Disables every
     * other alias so exactly one launcher icon is ever active.
     */
    fun applyIcon(context: Context, enabled: Boolean, theme: ThemePreference) {
        val target = targetAlias(enabled, theme)
        val pm = context.packageManager
        for (alias in ALL_ALIASES) {
            val state = if (alias == target) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            } else {
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED
            }
            pm.setComponentEnabledSetting(
                ComponentName(context.packageName, "$MANIFEST_PACKAGE$alias"),
                state,
                PackageManager.DONT_KILL_APP,
            )
        }
    }
}
