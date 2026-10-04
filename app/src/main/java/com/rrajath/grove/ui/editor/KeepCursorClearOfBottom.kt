package com.rrajath.grove.ui.editor

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.text.TextLayoutResult
import kotlinx.coroutines.flow.filterNotNull
import kotlin.math.roundToInt

/** How many lines the editors keep between the cursor (or the last line) and the bottom edge. */
internal const val EditorBottomMarginLines = 2

/**
 * Keeps the cursor [lines] text lines clear of a self-scrolling
 * [androidx.compose.foundation.text.BasicTextField]'s bottom edge while typing. The
 * field itself only scrolls the cursor just into view, so the line being typed sits
 * right on the edge; this nudges it the rest of the way after each edit.
 *
 * [textLayout] is the field's latest layout (capture it from `onTextLayout`). Pair
 * with [OrgSyntaxHighlight]'s `trailingBlankLines` set to [lines]: without that room
 * past the end of the text, the scroll range can't lift the last line off the edge.
 *
 * Runs on text edits only, never on scroll or a bare cursor move, so it can't fight
 * a fling the way a resize does (see [scrollAwareTopInset]); only ever scrolls down;
 * and only acts while the cursor's line is on screen, so it never takes over a
 * programmatic load that parks the cursor off screen.
 */
@Composable
fun KeepCursorClearOfBottom(
    state: TextFieldState,
    scrollState: ScrollState,
    textLayout: () -> TextLayoutResult?,
    lines: Int,
) {
    LaunchedEffect(state, scrollState, lines) {
        var laidOutText: String? = null
        snapshotFlow { textLayout() }.filterNotNull().collect { layout ->
            val text = layout.layoutInput.text.text
            val edited = laidOutText != null && text != laidOutText
            laidOutText = text
            if (!edited) return@collect
            // Offsets into the real text are the same in the displayed text: the
            // highlighter only appends past the end.
            val line = layout.getLineForOffset(state.selection.end.coerceIn(0, text.length))
            val lineTop = layout.getLineTop(line)
            val lineBottom = layout.getLineBottom(line)
            val viewportBottom = scrollState.value + scrollState.viewportSize
            if (lineTop < scrollState.value || lineBottom > viewportBottom + 1) return@collect
            val wanted = lineBottom + lines * (lineBottom - lineTop) - scrollState.viewportSize
            // Never so far that the cursor's own line leaves the top of a short viewport.
            val target = minOf(wanted, lineTop).roundToInt().coerceAtMost(scrollState.maxValue)
            if (target > scrollState.value) scrollState.scrollTo(target)
        }
    }
}
