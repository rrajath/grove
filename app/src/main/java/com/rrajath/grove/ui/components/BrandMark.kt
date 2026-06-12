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
 * The Grove org-asterisk mark: three rounded bars rotated 0°/60°/120°
 * (design/README.md "App icon mark"), centered in a squircle tile.
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
    val barLength = size.height
    val barWidth = barLength * 0.19f
    val topLeft = Offset((size.width - barWidth) / 2f, 0f)
    val barSize = Size(barWidth, barLength)
    val radius = CornerRadius(barWidth / 2f)
    for (angle in listOf(0f, 60f, 120f)) {
        rotate(angle) {
            drawRoundRect(color = color, topLeft = topLeft, size = barSize, cornerRadius = radius)
        }
    }
}
