package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove

/**
 * The `<q` / `<e` / `<s` shorthand at [textState]'s cursor, tracked live. Not gated on
 * any setting: block templates are plain org, unlike the Roam suggestion providers.
 */
@Composable
internal fun rememberBlockTrigger(textState: TextFieldState): State<BlockTrigger?> =
    remember(textState) { derivedStateOf { blockTemplateTriggerAt(textState.text, textState.selection) } }

/** Replaces [trigger]'s shorthand with its block (see [expandBlockTemplate]). */
internal fun TextFieldState.applyBlockTemplate(trigger: BlockTrigger) {
    val ins = expandBlockTemplate(text.toString(), trigger)
    edit {
        replace(ins.start, ins.end, ins.replacement)
        selection = TextRange(ins.cursor)
    }
}

/**
 * One chip ("Insert quote?") in the editor's suggestion slot while a block shorthand
 * sits at the cursor. Same chip styling and strip padding as [AutoLinkSuggestionStrip],
 * so it fills the slot at exactly the same height.
 */
@Composable
internal fun BlockTemplateSuggestionStrip(
    template: BlockTemplate,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    Box(modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
        Text(
            "Insert ${template.label}?",
            fontFamily = PlexMono, fontWeight = FontWeight.Medium,
            fontSize = 13.sp, color = c.ink,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(c.surface2)
                .border(1.dp, c.line, RoundedCornerShape(16.dp))
                .clickable(onClick = onPick)
                .testTag("block_template_chip")
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
    }
}
