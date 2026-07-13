package com.rrajath.grove.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove

/**
 * Collapsible, faded (66% alpha), monospace key-value section for file-level
 * `#+` keyword lines or a `:PROPERTIES:` drawer (design/Grove.dc.html lines
 * 499-552, style block at 1682+). Header row is the only tap target; body is
 * hidden unless [expanded]. Display-only — never mutates the source file.
 */
@Composable
fun CollapsibleKvSection(
    label: String,
    entries: List<Pair<String, String>>,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    val caretRotation by animateFloatAsState(if (expanded) 90f else 0f, label = "collapsibleCaret")
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(c.surface2)
            .alpha(0.66f),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "▸",
                fontFamily = PlexMono, fontSize = 10.sp, color = c.ink3,
                modifier = Modifier.width(10.dp).rotate(caretRotation),
            )
            Spacer(Modifier.width(8.dp))
            Text(label, fontFamily = PlexMono, fontSize = 12.sp, color = c.ink3)
            Spacer(Modifier.weight(1f))
            Text(entries.size.toString(), fontFamily = PlexMono, fontSize = 11.sp, color = c.ink3)
        }
        if (expanded) {
            Column(
                Modifier.padding(start = 30.dp, end = 12.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                entries.forEach { (key, value) ->
                    Row {
                        Text("$key ", fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 1.5.em, color = c.synKw)
                        Text(value, fontFamily = PlexMono, fontSize = 12.sp, lineHeight = 1.5.em, color = c.ink2)
                    }
                }
            }
        }
    }
}
