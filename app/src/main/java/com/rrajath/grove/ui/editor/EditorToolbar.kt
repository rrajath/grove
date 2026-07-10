package com.rrajath.grove.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.OrgTimestamp
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove
import java.time.LocalDateTime

/**
 * Formatting toolbar shared by the regular note editor and quick capture, so
 * both editing surfaces offer the same bold/italic/underline/code/link/etc.
 * buttons (design spec §6).
 */
@Composable
fun EditorToolbar(
    onWrap: (Char) -> Unit,
    onInsert: (String) -> Unit,
    onLink: () -> Unit,
    onHeading: () -> Unit,
    onIndent: (Int) -> Unit,
) {
    val c = MaterialTheme.grove
    // Each button gets equal width (RowScope.weight) so the row always fits
    // the screen exactly, with no scrolling and identical spacing between
    // every icon regardless of how wide its glyph happens to be.
    Row(
        Modifier
            .fillMaxWidth()
            .background(c.surface)
            .border(1.dp, c.line)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolButton("B", c.ink, Modifier.weight(1f), bold = true) { onWrap('*') }
        ToolButton("I", c.ink, Modifier.weight(1f), italic = true) { onWrap('/') }
        ToolButton("U", c.ink, Modifier.weight(1f), underline = true) { onWrap('_') }
        ToolButton("</>", c.ink, Modifier.weight(1f)) { onWrap('~') }
        Box(Modifier.width(1.dp).height(24.dp).background(c.line))
        ToolButton("[[]]", c.synLink, Modifier.weight(1f)) { onLink() }
        // The clock glyph is drawn smaller than the letters at a given size, so
        // bump its font so it reads at the same height as the other buttons.
        ToolButton("◷", c.synTs, Modifier.weight(1f), fontSize = 27.sp) {
            val now = LocalDateTime.now()
            onInsert(OrgTimestamp(now.toLocalDate(), time = now.toLocalTime().withSecond(0).withNano(0)).format())
        }
        ToolButton("*", c.synStar, Modifier.weight(1f), bold = true) { onHeading() }
        // List indent: « promotes a sub-list item, » demotes into a sub-list.
        ToolButton("«", c.ink, Modifier.weight(1f)) { onIndent(-1) }
        ToolButton("»", c.ink, Modifier.weight(1f)) { onIndent(+1) }
    }
}

@Composable
private fun ToolButton(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    italic: Boolean = false,
    underline: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = 20.sp,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .heightIn(min = 44.dp)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            fontFamily = PlexMono,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (italic) FontStyle.Italic else null,
            textDecoration = if (underline) androidx.compose.ui.text.style.TextDecoration.Underline else null,
            fontSize = fontSize,
            color = color,
        )
    }
}

/** Wrap the selection in emphasis markers, or insert a pair at the cursor. */
internal fun wrapSelection(value: TextFieldValue, marker: Char): TextFieldValue {
    val sel = value.selection
    val text = value.text
    return if (sel.collapsed) {
        val insert = "$marker$marker"
        TextFieldValue(
            text.substring(0, sel.start) + insert + text.substring(sel.start),
            TextRange(sel.start + 1),
        )
    } else {
        val selected = text.substring(sel.min, sel.max)
        TextFieldValue(
            text.substring(0, sel.min) + marker + selected + marker + text.substring(sel.max),
            TextRange(sel.max + 2),
        )
    }
}

internal fun insertAtCursor(value: TextFieldValue, snippet: String): TextFieldValue {
    val at = value.selection.start
    return TextFieldValue(
        value.text.substring(0, at) + snippet + value.text.substring(at),
        TextRange(at + snippet.length),
    )
}

/**
 * Insert an org link template with named placeholders and pre-select "link" so
 * the user can type the URL over it, then move to "description".
 */
internal fun insertLinkTemplate(value: TextFieldValue): TextFieldValue {
    val at = value.selection.start
    val template = "[[link][description]]"
    val linkStart = at + 2 // just inside the opening "[["
    return TextFieldValue(
        value.text.substring(0, at) + template + value.text.substring(at),
        TextRange(linkStart, linkStart + "link".length),
    )
}
