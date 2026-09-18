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

    private fun roamTemplate(
        directory: String = "",
        pattern: String = "%<%Y%m%d%H%M%S>-%(slug)",
        newFileTemplate: String = "#+title: %?",
    ) = CaptureTemplate(
        id = "t1",
        name = "Test",
        targetFile = "unused.org",
        location = TargetLocation.BottomOfFile,
        template = "unused",
        kind = TemplateKind.ROAM_NODE,
        roamDirectory = directory,
        filenamePattern = pattern,
        newFileTemplate = newFileTemplate,
    )

    @Test
    fun `valid roam template has no issues and ignores targetFile`() {
        val issues = TemplateValidator.validate(roamTemplate())
        assertFalse(issues.hasErrors)
        assertEquals(0, issues.count)
    }

    @Test
    fun `roam template with a bad directory reports directoryError only`() {
        val issues = TemplateValidator.validate(roamTemplate(directory = "/bad"))
        assertEquals(1, issues.count)
        assertTrue(issues.directoryError != null)
        assertEquals(null, issues.filenamePatternError)
    }

    @Test
    fun `roam template with a bad pattern reports filenamePatternError only`() {
        val issues = TemplateValidator.validate(roamTemplate(pattern = ""))
        assertEquals(1, issues.count)
        assertTrue(issues.filenamePatternError != null)
        assertEquals(null, issues.directoryError)
    }

    @Test
    fun `roam template invalid placeholders come from newFileTemplate not template`() {
        val issues = TemplateValidator.validate(roamTemplate(newFileTemplate = "%foo"))
        assertEquals(listOf("%foo"), issues.invalidPlaceholders)
    }
}
