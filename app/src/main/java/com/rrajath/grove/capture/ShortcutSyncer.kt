package com.rrajath.grove.capture

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.net.Uri
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.rrajath.grove.settings.ThemePreference
import com.rrajath.grove.ui.theme.GroveLightColors
import com.rrajath.grove.ui.theme.groveColorsFor

/**
 * Publishes one dynamic launcher long-press shortcut per configured capture
 * template, so Settings-managed templates (add/edit/remove/reorder) are
 * always reflected without a manifest change. Replaces the old static
 * res/xml/shortcuts.xml, which only covered the two built-in templates and
 * stopped showing once MainActivity was no longer the enabled LAUNCHER
 * component (see AppIconManager's activity-alias icon switching).
 */
object ShortcutSyncer {

    private const val ICON_SIZE_PX = 108

    /**
     * [theme]/[iconThemed] mirror the same fields AppIconManager uses for the
     * launcher icon itself, so shortcut glyphs stay visually consistent with
     * whichever icon (default warm mark, or theme-tinted) is currently shown.
     */
    fun sync(context: Context, templates: List<CaptureTemplate>, theme: ThemePreference, iconThemed: Boolean) {
        val colors = if (iconThemed) groveColorsFor(theme) else GroveLightColors
        val bg = colors.accent.toArgb()
        val fg = colors.accentInk.toArgb()
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(context).takeIf { it > 0 } ?: 4
        val shortcuts = templates.take(max).map { toShortcut(context, it, bg, fg) }
        ShortcutManagerCompat.setDynamicShortcuts(context, shortcuts)
    }

    private fun toShortcut(context: Context, template: CaptureTemplate, bg: Int, fg: Int): ShortcutInfoCompat {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("grove://capture/${template.id}"))
            .setClassName(context.packageName, "com.rrajath.grove.MainActivity")
        return ShortcutInfoCompat.Builder(context, template.id)
            .setShortLabel(template.name)
            .setLongLabel("New ${template.name}")
            .setIcon(IconCompat.createWithBitmap(glyphBitmap(template.icon, bg, fg)))
            .setIntent(intent)
            .build()
    }

    private fun glyphBitmap(glyph: String, bg: Int, fg: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(ICON_SIZE_PX, ICON_SIZE_PX, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = bg }
        canvas.drawCircle(ICON_SIZE_PX / 2f, ICON_SIZE_PX / 2f, ICON_SIZE_PX / 2f, bgPaint)
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = fg
            textSize = ICON_SIZE_PX * 0.5f
            textAlign = Paint.Align.CENTER
        }
        val yPos = ICON_SIZE_PX / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
        canvas.drawText(glyph, ICON_SIZE_PX / 2f, yPos, textPaint)
        return bitmap
    }
}
