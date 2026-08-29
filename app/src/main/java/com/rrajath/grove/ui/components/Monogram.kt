package com.rrajath.grove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.GroveColors
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/**
 * Monogram icons for notebooks and capture templates (design: `Notebook Icons.dc.html`
 * variant 1a). A letter derived from the name, set in the mono face, on a soft-tinted
 * rounded tile. Colour is the only user-picked axis — [ChangeIconColorDialog].
 */

/** Palette keys persisted in settings; resolved against the live theme via [monogramPalette]. */
val MONOGRAM_PALETTE_KEYS: List<String> = listOf("green", "blue", "amber", "red", "accent", "neutral")

/** (foreground, soft background) for a palette [key], falling back to accent for anything unknown. */
fun monogramPalette(c: GroveColors, key: String): Pair<Color, Color> = when (key) {
    "green" -> c.green to c.greenSoft
    "blue" -> c.blue to c.blueSoft
    "amber" -> c.amber to c.amberSoft
    "red" -> c.red to c.redSoft
    "neutral" -> c.ink2 to c.surface2
    else -> c.accent to c.accentSoft
}

/**
 * The uppercase first character of [source] — a notebook's display name (its `#+TITLE:`
 * or file name, per the Notebook display setting) or a template's name. Grapheme-aware
 * for the first code point so an emoji or accented letter survives intact. Falls back to
 * a bullet when [source] is blank (e.g. a template being named for the first time).
 */
fun monogramLetter(source: String): String {
    val trimmed = source.trim()
    if (trimmed.isEmpty()) return "•"
    val codePoint = trimmed.codePointAt(0)
    return String(Character.toChars(codePoint)).uppercase()
}

private fun nameHash(name: String): Int =
    name.hashCode().let { if (it == Int.MIN_VALUE) 0 else kotlin.math.abs(it) }

/** Default palette key for a name that has no user override — keeps the list looking varied. */
fun nameHashPaletteKey(name: String): String =
    MONOGRAM_PALETTE_KEYS[nameHash(name) % MONOGRAM_PALETTE_KEYS.size]

/**
 * A [size]-square rounded tile with [letter] centred in it, tinted by [colorKey].
 * Apply a `combinedClickable`/`clickable` [modifier] at the call site to make it
 * interactive (e.g. long-press to open [ChangeIconColorDialog]).
 */
@Composable
fun MonogramTile(
    letter: String,
    colorKey: String,
    modifier: Modifier = Modifier,
    size: Dp = 42.dp,
    cornerRadius: Dp = 12.dp,
) {
    val c = MaterialTheme.grove
    val (fg, bg) = monogramPalette(c, colorKey)
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(cornerRadius))
            .background(bg)
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            letter,
            fontFamily = PlexMono,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.40f).sp,
            color = fg,
        )
    }
}

/**
 * Colour-only picker for a notebook or template monogram (design 1a "Long press → picker").
 * A preview row (tile + name + hint), then the theme's palette as swatches.
 */
@Composable
fun ChangeIconColorDialog(
    name: String,
    hint: String,
    letter: String,
    currentColorKey: String,
    onPickColor: (String) -> Unit,
    onDismiss: () -> Unit,
    /** When set, the preview tile shows this glyph (e.g. `▪` for a folder) instead of [letter]. */
    glyph: String? = null,
) {
    val c = MaterialTheme.grove
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        title = {
            Text(
                "Change icon color",
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp, color = c.ink,
            )
        },
        text = {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (glyph != null) {
                        val (fg, bg) = monogramPalette(c, currentColorKey)
                        Box(
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(1.dp, fg.copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(glyph, fontFamily = PlexMono, fontSize = 15.sp, color = fg)
                        }
                    } else {
                        MonogramTile(letter = letter, colorKey = currentColorKey, size = 42.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            name,
                            fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                            fontSize = 14.5.sp, color = c.ink,
                        )
                        Text(hint, fontFamily = PlexSans, fontSize = 11.5.sp, color = c.ink3)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    "COLOR",
                    fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                    fontSize = 10.5.sp, letterSpacing = 1.sp, color = c.ink3,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MONOGRAM_PALETTE_KEYS.forEach { key ->
                        val (fg, _) = monogramPalette(c, key)
                        val selected = key == currentColorKey
                        // Prototype selection cue: a 2dp gap (surface shows through) then a
                        // 2dp ring in the swatch's own colour.
                        Box(
                            Modifier
                                .size(34.dp)
                                .then(
                                    if (selected) {
                                        Modifier.border(2.dp, fg, RoundedCornerShape(10.dp))
                                    } else {
                                        Modifier
                                    },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(28.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(fg)
                                    .clickable { onPickColor(key) },
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done", color = c.accent, fontWeight = FontWeight.SemiBold)
            }
        },
    )
}
