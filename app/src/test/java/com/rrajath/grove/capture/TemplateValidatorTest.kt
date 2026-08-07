package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TemplateValidatorTest {

    private fun template(targetFile: String, body: String) = CaptureTemplate(
        id = "t1",
        name = "Test",
        targetFile = targetFile,
        location = TargetLocation.BottomOfFile,
        template = body,
    )

    @Test
    fun `valid template has no issues`() {
        val issues = TemplateValidator.validate(template("inbox.org", "* %^{Title}\n%cursor"))
        assertFalse(issues.hasErrors)
        assertEquals(0, issues.count)
    }

    @Test
    fun `invalid filename alone counts as one error`() {
        val issues = TemplateValidator.validate(template("inbox", "%cursor"))
        assertTrue(issues.hasErrors)
        assertEquals(1, issues.count)
    }

    @Test
    fun `invalid placeholders each add to the count`() {
        val issues = TemplateValidator.validate(template("inbox.org", "%foo %bar"))
        assertTrue(issues.hasErrors)
        assertEquals(2, issues.count)
    }

    @Test
    fun `repeated invalid placeholder is only counted once`() {
        val issues = TemplateValidator.validate(template("inbox.org", "%foo %foo %foo"))
        assertEquals(1, issues.count)
        assertEquals(listOf("%foo"), issues.invalidPlaceholders)
    }

    @Test
    fun `filename and placeholder errors combine`() {
        val issues = TemplateValidator.validate(template("inbox", "%foo"))
        assertEquals(2, issues.count)
    }
}
