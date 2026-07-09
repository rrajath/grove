package com.rrajath.grove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.settings.ThemePreference
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove

/** Small rounded pill badge ("Modified", "1 conflict", "Recommended"…). */
@Composable
fun Pill(
    text: String,
    fg: Color,
    bg: Color,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val clickMod = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .then(clickMod)
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = fg,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 11.sp,
        )
    }
}

/** Segmented control per design spec (surface-2 container, accent active pill). */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Row(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        options.forEachIndexed { i, label ->
            val active = i == selectedIndex
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) c.accent else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (active) c.accentInk else c.ink2,
                    fontFamily = PlexSans,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * 56dp app bar row per design spec: leading slot, title block, trailing actions.
 * Consumes the status-bar inset itself — Scaffold does not pad the topBar slot,
 * so without this the bar sits under the status bar in edge-to-edge mode.
 */
@Composable
fun GroveTopBar(
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    title: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading()
        Box(Modifier.weight(1f)) { title() }
        actions()
    }
}

private data class ThemeSwatch(
    val pref: ThemePreference,
    val label: String,
    val bg: Color,
    val ink: Color,
    val dots: List<Color>,
)

/** Preview colors per design/GroveThemes.dc.html `themeList()` — intentionally hardcoded rather than derived from [com.rrajath.grove.ui.theme.GroveColors], since e.g. the dark theme's chip uses its surface color rather than its bg for legibility. */
private val ThemeSwatches = listOf(
    ThemeSwatch(ThemePreference.LIGHT, "Light", Color(0xFFF3EDE1), Color(0xFF2A251F), listOf(Color(0xFF8A5A2B), Color(0xFF4F7A3A), Color(0xFF3F6F86))),
    ThemeSwatch(ThemePreference.DARK, "Dark", Color(0xFF201C15), Color(0xFFECE4D5), listOf(Color(0xFFCB9D62), Color(0xFF8FB46A), Color(0xFF7FB0C4))),
    ThemeSwatch(ThemePreference.TOKYONIGHT, "Tokyo Night", Color(0xFF1A1B26), Color(0xFFC0CAF5), listOf(Color(0xFF7AA2F7), Color(0xFFBB9AF7), Color(0xFF7DCFFF))),
    ThemeSwatch(ThemePreference.SYNTHWAVE, "Synthwave", Color(0xFF262335), Color(0xFFF8F8F2), listOf(Color(0xFFFF7EDB), Color(0xFF03EDF9), Color(0xFFFEDE5D))),
    ThemeSwatch(ThemePreference.DRACULA, "Dracula", Color(0xFF282A36), Color(0xFFF8F8F2), listOf(Color(0xFFBD93F9), Color(0xFFFF79C6), Color(0xFF50FA7B))),
    ThemeSwatch(ThemePreference.CATPPUCCIN, "Catppuccin", Color(0xFF1E1E2E), Color(0xFFCDD6F4), listOf(Color(0xFFCBA6F7), Color(0xFFF38BA8), Color(0xFFA6E3A1))),
    ThemeSwatch(ThemePreference.NORD, "Nord", Color(0xFF2E3440), Color(0xFFECEFF4), listOf(Color(0xFF88C0D0), Color(0xFFA3BE8C), Color(0xFFB48EAD))),
)

/** Wrapping 7-swatch theme picker per design spec. */
@Composable
fun ThemeSwatchPicker(
    selected: ThemePreference,
    onSelect: (ThemePreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ThemeSwatches.forEach { swatch ->
            ThemeSwatchChip(swatch, active = swatch.pref == selected, onClick = { onSelect(swatch.pref) })
        }
    }
}

@Composable
private fun ThemeSwatchChip(swatch: ThemeSwatch, active: Boolean, onClick: () -> Unit) {
    val borderColor = if (active) swatch.dots[0] else Color(0x38808080) // rgba(128,128,128,0.22)
    Row(
        Modifier
            .clip(RoundedCornerShape(11.dp))
            .background(swatch.bg)
            .border(2.dp, borderColor, RoundedCornerShape(11.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        swatch.dots.forEachIndexed { i, dot ->
            if (i > 0) Box(Modifier.size(4.dp))
            Box(
                Modifier
                    .size(9.dp)
                    .clip(CircleShape)
                    .background(dot)
            )
        }
        Text(
            swatch.label,
            color = swatch.ink,
            fontFamily = PlexSans,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}
