package com.rrajath.grove.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.rrajath.grove.org.parseOrgTable
import com.rrajath.grove.ui.theme.PlexMono
import com.rrajath.grove.ui.theme.grove

private val CELL_PAD_H = 12.dp
private val CELL_PAD_V = 7.dp
private val MIN_COL_WIDTH = 40.dp
private val MAX_COL_WIDTH = 240.dp
private const val MAX_BODY_HEIGHT_FRACTION = 0.55f

/**
 * Renders an org table (Read mode) as a grid with a sticky, bolded header row.
 *
 * The whole grid shares one horizontal scroll, so a wide table pans sideways with
 * the header tracking its columns. The body has a capped height and scrolls
 * vertically inside that cap while the header stays pinned above it. Column widths
 * are measured from the widest cell in each column (clamped, then the text wraps).
 *
 * A double-tap anywhere on the table switches to edit mode, matching every other
 * block in the renderer. v1 keeps cells as plain text (no inline markup) and
 * left-aligns every column.
 */
@Composable
fun OrgTableView(
    lines: List<String>,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = MaterialTheme.grove
    val model = remember(lines) { parseOrgTable(lines) }
    if (model.isEmpty) return

    val bodyStyle = TextStyle(fontFamily = PlexMono, fontSize = 12.5.sp, lineHeight = 1.5.em, color = c.ink)
    val headerStyle = bodyStyle.copy(fontWeight = FontWeight.SemiBold)

    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    // Keyed on `density` too: ContentFontScale (the Settings § Notes font lever)
    // swaps LocalDensity's fontScale, which changes measured text widths.
    val colWidths = remember(model, density) {
        (0 until model.columnCount).map { col ->
            val headerPx = model.headerRows.maxOfOrNull {
                measurer.measure(it[col], headerStyle).size.width
            } ?: 0
            val bodyPx = model.bodyRows.maxOfOrNull {
                measurer.measure(it[col], bodyStyle).size.width
            } ?: 0
            with(density) { maxOf(headerPx, bodyPx).toDp() }
                .coerceIn(MIN_COL_WIDTH, MAX_COL_WIDTH) + CELL_PAD_H * 2
        }
    }
    // Grid content width: every column plus a 1dp separator between columns.
    val gridWidth = colWidths.fold(0.dp) { acc, w -> acc + w } + (model.columnCount - 1).dp

    val maxBodyHeight = (LocalConfiguration.current.screenHeightDp * MAX_BODY_HEIGHT_FRACTION).dp
    val hScroll = rememberScrollState()
    val vScroll = rememberScrollState()

    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, c.line, RoundedCornerShape(10.dp))
            .background(c.surface)
            .doubleTapToEdit(layoutResult = { null }, onDoubleTap = onDoubleTap),
    ) {
        Column(
            Modifier
                .horizontalScroll(hScroll)
                .width(gridWidth),
        ) {
            // Header: outside the vertical scroll, so it stays pinned.
            model.headerRows.forEachIndexed { i, row ->
                TableRow(row, colWidths, headerStyle, c.surface2, c.line, divider = i < model.headerRows.lastIndex)
            }
            if (model.bodyRows.isNotEmpty()) {
                HorizontalDivider(color = c.line2)
                // Body: scrolls vertically within the height cap.
                Column(
                    Modifier
                        .heightIn(max = maxBodyHeight)
                        .verticalScroll(vScroll),
                ) {
                    model.bodyRows.forEachIndexed { i, row ->
                        TableRow(row, colWidths, bodyStyle, Color.Transparent, c.line, divider = i < model.bodyRows.lastIndex)
                    }
                }
            }
        }
    }
}

@Composable
private fun TableRow(
    cells: List<String>,
    colWidths: List<Dp>,
    style: TextStyle,
    background: Color,
    dividerColor: Color,
    divider: Boolean,
) {
    Row(
        Modifier
            .background(background)
            .height(IntrinsicSize.Min),
    ) {
        cells.forEachIndexed { i, cell ->
            if (i > 0) {
                Box(Modifier.width(1.dp).fillMaxHeight().background(dividerColor))
            }
            Text(
                cell,
                style = style,
                modifier = Modifier
                    .width(colWidths[i])
                    .padding(horizontal = CELL_PAD_H, vertical = CELL_PAD_V),
            )
        }
    }
    if (divider) HorizontalDivider(color = dividerColor)
}
