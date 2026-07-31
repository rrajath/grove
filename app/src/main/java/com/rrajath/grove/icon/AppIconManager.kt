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
        ThemePreference.TOKYODAY to ".IconTokyoDay",
        ThemePreference.SYNTHWAVE to ".IconSynthwave",
        ThemePreference.DRACULA to ".IconDracula",
        ThemePreference.CATPPUCCIN to ".IconCatppuccin",
        ThemePreference.CATPPUCCINLATTE to ".IconCatppuccinLatte",
        ThemePreference.NORD to ".IconNord",
        ThemePreference.ROSEPINEDAWN to ".IconRosePineDawn",
        ThemePreference.ROSEPINEMOON to ".IconRosePineMoon",
    )

    val ALL_ALIASES: List<String> = listOf(DEFAULT_ALIAS) + THEME_ALIASES.values

    /** Mark color of the default launcher icon (`ic_launcher_foreground.xml`). */
    private const val DEFAULT_MARK_COLOR = 0xFFCB9D62.toInt()

    // Mark colors of the per-theme `ic_launcher_foreground_*.xml` drawables.
    private val THEME_MARK_COLORS: Map<ThemePreference, Int> = mapOf(
        ThemePreference.LIGHT to 0xFF8A5A2B.toInt(),
        ThemePreference.DARK to 0xFFCB9D62.toInt(),
        ThemePreference.TOKYONIGHT to 0xFF7AA2F7.toInt(),
        ThemePreference.TOKYODAY to 0xFF2E7DE9.toInt(),
        ThemePreference.SYNTHWAVE to 0xFFFF7EDB.toInt(),
        ThemePreference.DRACULA to 0xFFBD93F9.toInt(),
        ThemePreference.CATPPUCCIN to 0xFFCBA6F7.toInt(),
        ThemePreference.CATPPUCCINLATTE to 0xFF8839EF.toInt(),
        ThemePreference.NORD to 0xFF88C0D0.toInt(),
        ThemePreference.ROSEPINEDAWN to 0xFF907AA9.toInt(),
        ThemePreference.ROSEPINEMOON to 0xFFC4A7E7.toInt(),
    )

    /**
     * The color a notification should tint the Grove mark with, for the same
     * (enabled, theme) pair [targetAlias] resolves.
     *
     * Android renders a notification's small icon as an alpha mask and tints it
     * with `NotificationCompat.Builder.setColor`, so this — not the drawable —
     * is what actually makes the notification icon follow the launcher icon.
     * Every `ic_launcher_foreground_*` variant is the identical five-spoke path
     * differing only in fill, so matching the color matches the icon.
     */
    fun markColor(enabled: Boolean, theme: ThemePreference): Int =
        if (enabled) THEME_MARK_COLORS[theme] ?: DEFAULT_MARK_COLOR else DEFAULT_MARK_COLOR

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
