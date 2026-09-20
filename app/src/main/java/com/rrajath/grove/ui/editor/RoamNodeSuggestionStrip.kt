package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.capture.CaptureTemplate
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove

/**
 * Selection-triggered counterpart to [AutoLinkSuggestionStrip]: docked in the
 * same bottom-start slot, offering one chip per eligible Roam-kind capture
 * template ([com.rrajath.grove.capture.hasUserDefinedTitle]) to turn the
 * current non-collapsed selection into a link to a new or existing roam node.
 */
@Composable
fun RoamNodeSuggestionStrip(
    templates: List<CaptureTemplate>,
    selectedText: String,
    expandedKeys: Set<String>,
    onToggleExpand: (String) -> Unit,
    onPick: (CaptureTemplate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    LazyRow(
        modifier.fadingTrailingEdge(40.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
    ) {
        item {
            Text(
                "Create a roam node?",
                fontFamily = PlexMono, fontSize = 13.sp, color = c.ink2,
                modifier = Modifier.padding(end = 4.dp),
            )
        }
        items(templates, key = { it.id }) { template ->
            val expanded = template.id in expandedKeys
            val truncated = selectedText.length >= 10
            RoamNodeChip(
                label = "${template.name}: " + ellipsizeChipLabel(selectedText, expanded),
                onClick = {
                    if (truncated && !expanded) onToggleExpand(template.id) else onPick(template)
                },
            )
        }
    }
}

@Composable
private fun RoamNodeChip(
    label: String,
    onClick: () -> Unit,
) {
    val c = MaterialTheme.grove
    Row(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(c.surface2)
            .border(1.dp, c.line, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            fontFamily = PlexMono, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, color = c.ink,
        )
    }
}
