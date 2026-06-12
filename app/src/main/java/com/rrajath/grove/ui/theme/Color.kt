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

val LocalGroveColors = staticCompositionLocalOf { GroveLightColors }
