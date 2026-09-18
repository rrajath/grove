package com.rrajath.grove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexSans
import com.rrajath.grove.ui.theme.grove
import com.rrajath.grove.ui.util.pluralCount

/**
 * Collapsed "linked references" bar (design: Roam Links.dc.html, variant 1a):
 * a persistent row above the note's own bottom chrome. Tapping it opens the
 * full [LinkedReferencesSheet] (variant 1b). Shown in both Read mode
 * (`ReadNoteScreen`'s Scaffold `bottomBar`) and Edit mode (above
 * `EditorToolbar`), always visible so the count is passive until wanted.
 */
@Composable
fun LinkedReferencesBar(
    linkedCount: Int,
    unlinkedCount: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Column(
        modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.line)
            .padding(bottom = 15.dp)
            .clickable(onClick = onClick),
    ) {
        // Centered chevron at the top edge, replacing the old trailing caret --
        // reads as a "pull up to expand" affordance instead of a stray glyph
        // competing with the unlinked-count pill on the right.
        Box(
            Modifier.fillMaxWidth().padding(top = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.KeyboardArrowUp, contentDescription = null,
                tint = c.ink3, modifier = Modifier.size(16.dp),
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 18.dp, end = 18.dp, top = 0.dp, bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ArrowOutward, contentDescription = null,
                    tint = c.accent, modifier = Modifier.size(14.dp),
                )
            }
            Spacer(Modifier.width(11.dp))
            Text(
                pluralCount(linkedCount, "linked reference"),
                fontFamily = PlexSans, fontWeight = FontWeight.SemiBold,
                fontSize = 14.5.sp, color = c.ink,
                modifier = Modifier.weight(1f),
            )
            if (unlinkedCount > 0) {
                Pill(text = "$unlinkedCount unlinked", fg = c.ink2, bg = c.surface2)
            }
        }
    }
}
