package com.rrajath.grove.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rrajath.grove.R

val PlexSans = FontFamily(
    Font(R.font.ibm_plex_sans_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.ibm_plex_sans_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_semibold, FontWeight.SemiBold),
)

val PlexSerif = FontFamily(
    Font(R.font.ibm_plex_serif_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_serif_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.ibm_plex_serif_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_serif_semibold, FontWeight.SemiBold),
)

val PlexMono = FontFamily(
    Font(R.font.ibm_plex_mono_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_mono_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.ibm_plex_mono_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_mono_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_mono_bold, FontWeight.Bold),
)

/**
 * Typography per design/README.md, scaled by the user's font-size preference.
 * Roles: Sans for UI, Serif for read-mode body, Mono for editor/timestamps.
 */
fun groveTypography(scale: Float = 1f): Typography {
    fun sz(v: Double) = (v * scale).sp
    return Typography(
        // Display — app name on onboarding (30sp / 600)
        displaySmall = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = sz(30.0),
        ),
        // Title Large — screen titles in app bars (19sp / 600)
        titleLarge = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = sz(19.0),
        ),
        // Title Medium — notebook/file names (17sp / 600)
        titleMedium = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = sz(17.0),
        ),
        // Body Large — read mode body text (Serif 16sp, 1.65 line height)
        bodyLarge = TextStyle(
            fontFamily = PlexSerif, fontWeight = FontWeight.Normal, fontSize = sz(16.0),
            lineHeight = 1.65.em,
        ),
        // Body Medium — list items, settings rows (14.5–15sp)
        bodyMedium = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = sz(15.0),
        ),
        // Body Small — subtitles, descriptions (13–13.5sp)
        bodySmall = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.Normal, fontSize = sz(13.5),
        ),
        // Caption — badges, chips, timestamps (11–12sp / 500)
        labelSmall = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = sz(11.5),
        ),
        labelMedium = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.Medium, fontSize = sz(12.5),
        ),
        // Buttons / prominent labels
        labelLarge = TextStyle(
            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold, fontSize = sz(15.0),
        ),
    )
}

/** Mono Body — raw org editor, file names, timestamps (13.5sp). */
fun monoBody(scale: Float = 1f) = TextStyle(
    fontFamily = PlexMono, fontWeight = FontWeight.Normal, fontSize = (13.5 * scale).sp,
)
