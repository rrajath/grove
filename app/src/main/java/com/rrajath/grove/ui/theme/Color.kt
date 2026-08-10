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
    synKw = Color(0xFFAD9BD1), // = violet; was ink2, indistinguishable from key/value text
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

val GroveTokyoDayColors = GroveColors(
    bg = Color(0xFFE1E2E7),
    surface = Color(0xFFEDEEF2),
    surface2 = Color(0xFFD5D6DB),
    surface3 = Color(0xFFC4C8DA),
    ink = Color(0xFF3760BF),
    ink2 = Color(0xFF5A638F),
    ink3 = Color(0xFF9699A3),
    line = Color(0xFFC4C8DA),
    line2 = Color(0xFFA1A6C5),
    accent = Color(0xFF2E7DE9),
    accentInk = Color(0xFFFFFFFF),
    accentSoft = Color(0x292E7DE9), // 0.16
    green = Color(0xFF587539),
    greenSoft = Color(0x29587539), // 0.16
    amber = Color(0xFFB15C00),
    amberSoft = Color(0x29B15C00), // 0.16
    red = Color(0xFFF52A65),
    redSoft = Color(0x29F52A65), // 0.16
    blue = Color(0xFF007197),
    blueSoft = Color(0x29007197), // 0.16
    violet = Color(0xFF9854F1),
    synStar = Color(0xFF9854F1),
    synTodo = Color(0xFFB15C00),
    synDone = Color(0xFF587539),
    synKw = Color(0xFF007197), // = blue; was ink2, indistinguishable from key/value text
    synTs = Color(0xFF007197),
    synTag = Color(0xFF2E7DE9),
    synLink = Color(0xFF007197),
    synProp = Color(0xFF9699A3),
    isDark = false,
)

val GroveCatppuccinLatteColors = GroveColors(
    bg = Color(0xFFEFF1F5),
    surface = Color(0xFFE6E9EF),
    surface2 = Color(0xFFCCD0DA),
    surface3 = Color(0xFFBCC0CC),
    ink = Color(0xFF4C4F69),
    ink2 = Color(0xFF6C6F85),
    ink3 = Color(0xFF9CA0B0),
    line = Color(0xFFDCE0E8),
    line2 = Color(0xFFACB0BE),
    accent = Color(0xFF8839EF),
    accentInk = Color(0xFFEFF1F5),
    accentSoft = Color(0x1F8839EF), // 0.12
    green = Color(0xFF40A02B),
    greenSoft = Color(0x2440A02B), // 0.14
    amber = Color(0xFFDF8E1D),
    amberSoft = Color(0x29DF8E1D), // 0.16
    red = Color(0xFFD20F39),
    redSoft = Color(0x21D20F39), // 0.13
    blue = Color(0xFF1E66F5),
    blueSoft = Color(0x211E66F5), // 0.13
    violet = Color(0xFF7287FD),
    synStar = Color(0xFF8839EF),
    synTodo = Color(0xFFDF8E1D),
    synDone = Color(0xFF40A02B),
    synKw = Color(0xFF7287FD), // = violet; was ink2, indistinguishable from key/value text
    synTs = Color(0xFF1E66F5),
    synTag = Color(0xFF8839EF),
    synLink = Color(0xFF1E66F5),
    synProp = Color(0xFF9CA0B0),
    isDark = false,
)

val GroveRosePineDawnColors = GroveColors(
    bg = Color(0xFFFAF4ED),
    surface = Color(0xFFFFFAF3),
    surface2 = Color(0xFFF2E9E1),
    surface3 = Color(0xFFDFDAD9),
    ink = Color(0xFF575279),
    ink2 = Color(0xFF797593),
    ink3 = Color(0xFF9893A5),
    line = Color(0xFFF4EDE8),
    line2 = Color(0xFFCECACD),
    accent = Color(0xFF907AA9),
    accentInk = Color(0xFFFAF4ED),
    accentSoft = Color(0x1F907AA9), // 0.12
    green = Color(0xFF286983),
    greenSoft = Color(0x24286983), // 0.14
    amber = Color(0xFFEA9D34),
    amberSoft = Color(0x29EA9D34), // 0.16
    red = Color(0xFFB4637A),
    redSoft = Color(0x21B4637A), // 0.13
    blue = Color(0xFF56949F),
    blueSoft = Color(0x2156949F), // 0.13
    violet = Color(0xFF907AA9),
    synStar = Color(0xFF907AA9),
    synTodo = Color(0xFFEA9D34),
    synDone = Color(0xFF286983),
    synKw = Color(0xFF56949F), // = blue; was ink2, indistinguishable from key/value text
    synTs = Color(0xFF56949F),
    synTag = Color(0xFFD7827E),
    synLink = Color(0xFF56949F),
    synProp = Color(0xFF9893A5),
    isDark = false,
)

val GroveRosePineMoonColors = GroveColors(
    bg = Color(0xFF232136),
    surface = Color(0xFF2A273F),
    surface2 = Color(0xFF393552),
    surface3 = Color(0xFF44415A),
    ink = Color(0xFFE0DEF4),
    ink2 = Color(0xFF908CAA),
    ink3 = Color(0xFF6E6A86),
    line = Color(0xFF2A283E),
    line2 = Color(0xFF56526E),
    accent = Color(0xFFC4A7E7),
    accentInk = Color(0xFF232136),
    accentSoft = Color(0x2EC4A7E7), // 0.18
    green = Color(0xFF3E8FB0),
    greenSoft = Color(0x2B3E8FB0), // 0.17
    amber = Color(0xFFF6C177),
    amberSoft = Color(0x2BF6C177), // 0.17
    red = Color(0xFFEB6F92),
    redSoft = Color(0x29EB6F92), // 0.16
    blue = Color(0xFF9CCFD8),
    blueSoft = Color(0x299CCFD8), // 0.16
    violet = Color(0xFFC4A7E7),
    synStar = Color(0xFFC4A7E7),
    synTodo = Color(0xFFF6C177),
    synDone = Color(0xFF3E8FB0),
    synKw = Color(0xFF9CCFD8), // = blue; was ink2, indistinguishable from key/value text
    synTs = Color(0xFF9CCFD8),
    synTag = Color(0xFFEA9A97),
    synLink = Color(0xFF9CCFD8),
    synProp = Color(0xFF6E6A86),
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

/**
 * Priority-cookie color on a red→amber→green urgency scale: A (most urgent) is
 * red, B is amber, C (least urgent) is green. The parser accepts any A-Z letter,
 * so anything past C falls back to a neutral tone rather than erroring.
 */
fun GroveColors.priorityColor(priority: Char): Color = when (priority.uppercaseChar()) {
    'A' -> red
    'B' -> amber
    'C' -> green
    else -> ink2
}

/** Soft/background counterpart of [priorityColor], for chip fills. */
fun GroveColors.prioritySoftColor(priority: Char): Color = when (priority.uppercaseChar()) {
    'A' -> redSoft
    'B' -> amberSoft
    'C' -> greenSoft
    else -> surface2
}
