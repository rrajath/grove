package com.rrajath.grove

import androidx.core.content.pm.ShortcutManagerCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.rrajath.grove.capture.DefaultTemplates
import com.rrajath.grove.capture.ShortcutSyncer
import com.rrajath.grove.settings.ThemePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies [ShortcutSyncer] publishes one dynamic launcher long-press
 * shortcut per capture template, keyed by template id.
 *
 * A non-launcher app can't read a shortcut's intent URI, so the deep-link
 * wiring (grove://capture/<id> -> Routes.capture(id) and the stable built-in
 * template ids) is covered by the JVM RoutesTest / CaptureTemplateTest.
 */
@RunWith(AndroidJUnit4::class)
class LauncherShortcutsTest {
    @Test
    fun dynamicShortcutsMatchConfiguredTemplates() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        ShortcutSyncer.sync(context, DefaultTemplates.all, ThemePreference.LIGHT, iconThemed = false)

        val shortcuts = ShortcutManagerCompat.getDynamicShortcuts(context).associateBy { it.id }

        assertEquals(DefaultTemplates.all.map { it.id }.toSet(), shortcuts.keys)
        DefaultTemplates.all.forEach { template ->
            assertEquals(template.name, shortcuts.getValue(template.id).shortLabel.toString())
        }
        assertTrue("shortcuts should be enabled", shortcuts.values.all { it.isEnabled })
    }
}
