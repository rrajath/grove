package com.rrajath.grove

import android.content.pm.ShortcutManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies the manifest-declared launcher long-press shortcuts (Journal and
 * Quick Note) are registered and labelled as declared in res/xml/shortcuts.xml.
 *
 * A non-launcher app can't read a shortcut's intent URI, so the deep-link
 * wiring (grove://capture/<id> → Routes.capture(id) and the stable built-in
 * template ids) is covered by the JVM RoutesTest / CaptureTemplateTest.
 */
@RunWith(AndroidJUnit4::class)
class LauncherShortcutsTest {
    @Test
    fun manifestShortcutsAreDeclared() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val manager = context.getSystemService(ShortcutManager::class.java)
        val shortcuts = manager.manifestShortcuts.associateBy { it.id }

        assertEquals(setOf("journal", "quick_note"), shortcuts.keys)
        assertEquals("Journal", shortcuts.getValue("journal").shortLabel.toString())
        assertEquals("Quick Note", shortcuts.getValue("quick_note").shortLabel.toString())
        assertTrue("shortcuts should be enabled", shortcuts.values.all { it.isEnabled })
    }
}
