package com.rrajath.grove.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Full Grove design-token palette from design/README.md. Material's colorScheme
 * only covers a subset of these, so the complete set is exposed through
 * [LocalGroveColors] / `MaterialTheme.grove`.
 */
@Immutable
data class GroveColors(
    val bg: Color,
    val surface: Color,
    val surface2: Color,
    val surface3: Color,
    val ink: Color,
    val ink2: Color,
    val ink3: Color,
    val line: Color,
    val line2: Color,
    val accent: Color,
    val accentInk: Color,
    val accentSoft: Color,
    val green: Color,
    val greenSoft: Color,
    val amber: Color,
    val amberSoft: Color,
    val red: Color,
    val redSoft: Color,
    val blue: Color,
    val blueSoft: Color,
    val violet: Color,
    // Org syntax highlighting
    val synStar: Color,
    val synTodo: Color,
    val synDone: Color,
    val synKw: Color,
    val synTs: Color,
    val synTag: Color,
    val synLink: Color,
    val synProp: Color,
    val isDark: Boolean,
)

val GroveLightColors = GroveColors(
    bg = Color(0xFFF3EDE1),
    surface = Color(0xFFFBF8F1),
    surface2 = Color(0xFFECE4D5),
    surface3 = Color(0xFFE3D9C6),
    ink = Color(0xFF2A251F),
    ink2 = Color(0xFF6C6356),
    ink3 = Color(0xFF9C9384),
    line = Color(0xFFE3DBCB),
    line2 = Color(0xFFD3C9B6),
    accent = Color(0xFF8A5A2B),
    accentInk = Color(0xFFFFFAF2),
    accentSoft = Color(0x1F8A5A2B), // rgba(138,90,43,0.12)
    green = Color(0xFF4F7A3A),
    greenSoft = Color(0x244F7A3A), // 0.14
    amber = Color(0xFFA9761D),
    amberSoft = Color(0x29A9761D), // 0.16
    red = Color(0xFFA5462F),
    redSoft = Color(0x21A5462F), // 0.13
    blue = Color(0xFF3F6F86),
    blueSoft = Color(0x213F6F86), // 0.13
    violet = Color(0xFF6E5A8E),
    synStar = Color(0xFF4F7A3A),
    synTodo = Color(0xFFA9761D),
    synDone = Color(0xFF4F7A3A),
    synKw = Color(0xFF7D7466),
    synTs = Color(0xFF3F6F86),
    synTag = Color(0xFF8A5A2B),
    synLink = Color(0xFF3F6F86),
    synProp = Color(0xFFA39A89),
    isDark = false,
)

val GroveDarkColors = GroveColors(
    bg = Color(0xFF16130E),
    surface = Color(0xFF201C15),
    surface2 = Color(0xFF2A251B),
    surface3 = Color(0xFF352F23),
    ink = Color(0xFFECE4D5),
    ink2 = Color(0xFFB4A98F),
    ink3 = Color(0xFF7C7460),
    line = Color(0xFF322C20),
    line2 = Color(0xFF433A2B),
    accent = Color(0xFFCB9D62),
    accentInk = Color(0xFF1A160D),
    accentSoft = Color(0x2ECB9D62), // 0.18
    green = Color(0xFF8FB46A),
    greenSoft = Color(0x2B8FB46A), // 0.17
    amber = Color(0xFFD7A64F),
    amberSoft = Color(0x2BD7A64F), // 0.17
    red = Color(0xFFD2856A),
    redSoft = Color(0x29D2856A), // 0.16
    blue = Color(0xFF7FB0C4),
    blueSoft = Color(0x297FB0C4), // 0.16
    violet = Color(0xFFAD9BD1),
    synStar = Color(0xFF8FB46A),
    synTodo = Color(0xFFD7A64F),
    synDone = Color(0xFF8FB46A),
    synKw = Color(0xFFB4A98F),
    synTs = Color(0xFF7FB0C4),
    synTag = Color(0xFFCB9D62),
    synLink = Color(0xFF7FB0C4),
    synProp = Color(0xFF7C7460),
    isDark = true,
)

val GroveTokyoNightColors = GroveColors(
    bg = Color(0xFF1A1B26),
    surface = Color(0xFF1F2335),
    surface2 = Color(0xFF24283B),
    surface3 = Color(0xFF292E42),
    ink = Color(0xFFC0CAF5),
    ink2 = Color(0xFF9AA5CE),
    ink3 = Color(0xFF565F89),
    line = Color(0xFF232741),
    line2 = Color(0xFF2F344D),
    accent = Color(0xFF7AA2F7),
    accentInk = Color(0xFF16161E),
    accentSoft = Color(0x297AA2F7), // 0.16
    green = Color(0xFF9ECE6A),
    greenSoft = Color(0x299ECE6A), // 0.16
    amber = Color(0xFFE0AF68),
    amberSoft = Color(0x29E0AF68), // 0.16
    red = Color(0xFFF7768E),
    redSoft = Color(0x29F7768E), // 0.16
    blue = Color(0xFF7DCFFF),
    blueSoft = Color(0x297DCFFF), // 0.16
    violet = Color(0xFFBB9AF7),
    synStar = Color(0xFFBB9AF7),
    synTodo = Color(0xFFE0AF68),
    synDone = Color(0xFF9ECE6A),
    synKw = Color(0xFF7DCFFF),
    synTs = Color(0xFF73DACA),
    synTag = Color(0xFFBB9AF7),
    synLink = Color(0xFF7AA2F7),
    synProp = Color(0xFF565F89),
    isDark = true,
)

val GroveSynthwaveColors = GroveColors(
    bg = Color(0xFF262335),
    surface = Color(0xFF2A2140),
    surface2 = Color(0xFF34294D),
    surface3 = Color(0xFF3F3159),
    ink = Color(0xFFF8F8F2),
    ink2 = Color(0xFFC4B7E0),
    ink3 = Color(0xFF8B7DAE),
    line = Color(0xFF3A2F52),
    line2 = Color(0xFF4A3D68),
    accent = Color(0xFFFF7EDB),
    accentInk = Color(0xFF1A1226),
    accentSoft = Color(0x29FF7EDB), // 0.16
    green = Color(0xFF72F1B8),
    greenSoft = Color(0x2972F1B8), // 0.16
    amber = Color(0xFFFEDE5D),
    amberSoft = Color(0x29FEDE5D), // 0.16
    red = Color(0xFFFE4450),
    redSoft = Color(0x29FE4450), // 0.16
    blue = Color(0xFF03EDF9),
    blueSoft = Color(0x2903EDF9), // 0.16
    violet = Color(0xFFB967FF), // no violet token in the design palette; derived to keep the star-color cycle distinct
    synStar = Color(0xFFFEDE5D),
    synTodo = Color(0xFFFF8B39),
    synDone = Color(0xFF72F1B8),
    synKw = Color(0xFFFF7EDB),
    synTs = Color(0xFF03EDF9),
    synTag = Color(0xFFFF7EDB),
    synLink = Color(0xFF03EDF9),
    synProp = Color(0xFF8B7DAE),
    isDark = true,
)

val GroveDraculaColors = GroveColors(
    bg = Color(0xFF282A36),
    surface = Color(0xFF2D2F3D),
    surface2 = Color(0xFF343746),
    surface3 = Color(0xFF44475A),
    ink = Color(0xFFF8F8F2),
    ink2 = Color(0xFFC3C6D4),
    ink3 = Color(0xFF6272A4),
    line = Color(0xFF383B4A),
    line2 = Color(0xFF44475A),
    accent = Color(0xFFBD93F9),
    accentInk = Color(0xFF21222C),
    accentSoft = Color(0x29BD93F9), // 0.16
    green = Color(0xFF50FA7B),
    greenSoft = Color(0x2650FA7B), // 0.15
    amber = Color(0xFFFFB86C),
    amberSoft = Color(0x29FFB86C), // 0.16
    red = Color(0xFFFF5555),
    redSoft = Color(0x26FF5555), // 0.15
    blue = Color(0xFF8BE9FD),
    blueSoft = Color(0x298BE9FD), // 0.16
    violet = Color(0xFFBD93F9),
    synStar = Color(0xFFBD93F9),
    synTodo = Color(0xFFFFB86C),
    synDone = Color(0xFF50FA7B),
    synKw = Color(0xFFFF79C6),
    synTs = Color(0xFF8BE9FD),
    synTag = Color(0xFF50FA7B),
    synLink = Color(0xFF8BE9FD),
    synProp = Color(0xFF6272A4),
    isDark = true,
)

val GroveCatppuccinColors = GroveColors(
    bg = Color(0xFF1E1E2E),
    surface = Color(0xFF292A3D),
    surface2 = Color(0xFF313244),
    surface3 = Color(0xFF45475A),
    ink = Color(0xFFCDD6F4),
    ink2 = Color(0xFFA6ADC8),
    ink3 = Color(0xFF6C7086),
    line = Color(0xFF2C2D40),
    line2 = Color(0xFF3A3C50),
    accent = Color(0xFFCBA6F7),
    accentInk = Color(0xFF181825),
    accentSoft = Color(0x29CBA6F7), // 0.16
    green = Color(0xFFA6E3A1),
    greenSoft = Color(0x29A6E3A1), // 0.16
    amber = Color(0xFFFAB387),
    amberSoft = Color(0x29FAB387), // 0.16
    red = Color(0xFFF38BA8),
    redSoft = Color(0x29F38BA8), // 0.16
    blue = Color(0xFF89B4FA),
    blueSoft = Color(0x2989B4FA), // 0.16
    violet = Color(0xFFF5C2E7),
    synStar = Color(0xFFCBA6F7),
    synTodo = Color(0xFFFAB387),
    synDone = Color(0xFFA6E3A1),
    synKw = Color(0xFFF5C2E7),
    synTs = Color(0xFF94E2D5),
    synTag = Color(0xFFCBA6F7),
    synLink = Color(0xFF89B4FA),
    synProp = Color(0xFF6C7086),
    isDark = true,
)

val GroveNordColors = GroveColors(
    bg = Color(0xFF2E3440),
    surface = Color(0xFF333B4A),
    surface2 = Color(0xFF3B4252),
    surface3 = Color(0xFF434C5E),
    ink = Color(0xFFECEFF4),
    ink2 = Color(0xFFD8DEE9),
    ink3 = Color(0xFF7B88A1),
    line = Color(0xFF3B4252),
    line2 = Color(0xFF4C566A),
    accent = Color(0xFF88C0D0),
    accentInk = Color(0xFF2E3440),
    accentSoft = Color(0x2E88C0D0), // 0.18
    green = Color(0xFFA3BE8C),
    greenSoft = Color(0x2BA3BE8C), // 0.17
    amber = Color(0xFFEBCB8B),
    amberSoft = Color(0x2BEBCB8B), // 0.17
    red = Color(0xFFBF616A),
    redSoft = Color(0x2BBF616A), // 0.17
    blue = Color(0xFF81A1C1),
    blueSoft = Color(0x2B81A1C1), // 0.17
    violet = Color(0xFFB48EAD),
    synStar = Color(0xFF88C0D0),
    synTodo = Color(0xFFEBCB8B),
    synDone = Color(0xFFA3BE8C),
    synKw = Color(0xFF81A1C1),
    synTs = Color(0xFF8FBCBB),
    synTag = Color(0xFFB48EAD),
    synLink = Color(0xFF88C0D0),
    synProp = Color(0xFF7B88A1),
    isDark = true,
)

val LocalGroveColors = staticCompositionLocalOf { GroveLightColors }

/**
 * Heading-star color cycled by outline depth: green, blue, amber, red, violet,
 * brown, then back to green for deeper nesting.
 */
fun GroveColors.starColor(level: Int): Color {
    val cycle = listOf(green, blue, amber, red, violet, accent)
    return cycle[((level - 1).coerceAtLeast(0)) % cycle.size]
}
