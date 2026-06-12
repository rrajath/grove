package com.rrajath.grove.widget

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.action.clickable
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rrajath.grove.MainActivity

/**
 * Home-screen "Capture" widget (PRD §8): one tap opens the capture picker
 * via the grove://capture deep link.
 */
class CaptureWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent {
            GlanceTheme {
                val intent = Intent(Intent.ACTION_VIEW, "grove://capture".toUri())
                    .setClass(context, MainActivity::class.java)
                Row(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(17.dp)
                        .background(ColorProvider(Color(0xFF8A5A2B), Color(0xFFCB9D62)))
                        .clickable(actionStartActivity(intent))
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        "✱  Capture",
                        style = TextStyle(
                            color = ColorProvider(Color(0xFFFFFAF2), Color(0xFF1A160D)),
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        ),
                    )
                }
            }
        }
    }
}

class CaptureWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CaptureWidget()
}
