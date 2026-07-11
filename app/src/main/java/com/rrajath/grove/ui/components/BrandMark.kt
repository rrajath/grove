package com.rrajath.grove.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rrajath.grove.ui.theme.grove

/**
 * The Grove org-asterisk mark: five rounded spokes radiating from center at
 * 36°/108°/180°/252°/324° (design/Grove.dc.html `spokeSet(16, 7)`, same
 * geometry as the launcher icon), centered in a squircle tile.
 */
@Composable
fun BrandMark(
    tileSize: Dp,
    modifier: Modifier = Modifier,
    tileColor: Color = MaterialTheme.grove.accentSoft,
    barColor: Color = MaterialTheme.grove.accent,
    cornerFraction: Float = 0.27f,
) {
    Box(
        modifier = modifier
            .size(tileSize)
            .clip(RoundedCornerShape(tileSize * cornerFraction))
            .background(tileColor),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(tileSize * 0.53f)) {
            drawAsterisk(barColor)
        }
    }
}

private fun DrawScope.drawAsterisk(color: Color) {
    val spokeLength = size.minDimension / 2f
    val spokeWidth = size.minDimension * (7f / 32f)
    val center = Offset(size.width / 2f, size.height / 2f)
    val topLeft = Offset(center.x - spokeWidth / 2f, center.y)
    val spokeSize = Size(spokeWidth, spokeLength)
    val radius = CornerRadius(spokeWidth / 2f)
    for (angle in listOf(36f, 108f, 180f, 252f, 324f)) {
        rotate(angle, pivot = center) {
            drawRoundRect(color = color, topLeft = topLeft, size = spokeSize, cornerRadius = radius)
        }
    }
}
