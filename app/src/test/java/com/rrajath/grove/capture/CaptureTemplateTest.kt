package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CaptureTemplateTest {

    @Test
    fun `templates round-trip through json`() {
        val templates = DefaultTemplates.all +
                CaptureTemplate(
                    id = "custom",
                    name = "Meeting",
                    icon = "◎",
                    targetFile = "work.org",
                    location = TargetLocation.UnderHeading(customId = "meetings", appendLast = true),
                    template = "* %^{Topic}\n%U\n%cursor",
                )
        val decoded = TemplateSerializer.decode(TemplateSerializer.encode(templates))
        assertEquals(templates, decoded)
    }

    @Test
    fun `corrupt json decodes to empty list`() {
        assertEquals(emptyList<CaptureTemplate>(), TemplateSerializer.decode("not json"))
        assertEquals(emptyList<CaptureTemplate>(), TemplateSerializer.decode(""))
    }

    @Test
    fun `default templates match the PRD`() {
        val byName = DefaultTemplates.all.associateBy { it.name }
        assertEquals(3, byName.size)
        assertEquals("journal.org", byName["Journal Entry"]!!.targetFile)
        assertTrue(byName["Journal Entry"]!!.location.isDatetree)
        assertEquals(TargetLocation.BottomOfFile, byName["Quick Note"]!!.location)
        assertTrue(byName["TODO"]!!.template.contains("SCHEDULED: %T"))
    }

    @Test
    fun `built-in ids are stable for launcher shortcuts`() {
        // res/xml/shortcuts.xml hard-codes these ids in its grove://capture/<id>
        // deep links (Journal, Quick Note). Renaming a built-in id would silently
        // break the launcher long-press shortcuts, so pin them here.
        val ids = DefaultTemplates.all.map { it.id }.toSet()
        assertTrue("Journal shortcut target missing", "builtin-journal" in ids)
        assertTrue("Quick Note shortcut target missing", "builtin-quick-note" in ids)
    }

    @Test
    fun `location descriptions for the picker`() {
        assertEquals("bottom of file", TargetLocation.BottomOfFile.describe())
        assertEquals("datetree", TargetLocation.DatetreeDatetime.describe())
        assertEquals("under #x", TargetLocation.UnderHeading(customId = "x").describe())
        assertEquals("under Projects", TargetLocation.UnderHeading(title = "Projects").describe())
    }
}
