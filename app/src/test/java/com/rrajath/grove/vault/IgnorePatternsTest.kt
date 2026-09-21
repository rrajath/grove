package com.rrajath.grove.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnorePatternsTest {

    @Test
    fun `bare name matches a folder at the root`() {
        val patterns = IgnorePatterns("archive")
        assertTrue(patterns.isDirIgnored("archive", "archive"))
    }

    @Test
    fun `bare name matches the same folder nested`() {
        val patterns = IgnorePatterns("archive")
        assertTrue(patterns.isDirIgnored("archive", "projects/archive"))
    }

    @Test
    fun `bare name does not match a different name`() {
        val patterns = IgnorePatterns("archive")
        assertFalse(patterns.isDirIgnored("notes", "projects/notes"))
    }

    @Test
    fun `path-containing pattern matches only that exact path`() {
        val patterns = IgnorePatterns("projects/archive")
        assertTrue(patterns.isDirIgnored("archive", "projects/archive"))
        assertFalse(patterns.isDirIgnored("archive", "other/archive"))
    }

    @Test
    fun `glob wildcard bare file`() {
        val patterns = IgnorePatterns("*.bak")
        assertTrue(patterns.isFileIgnored("notes.bak", "a/b/notes.bak"))
        assertFalse(patterns.isFileIgnored("notes.org", "a/b/notes.org"))
    }

    @Test
    fun `glob wildcard does not cross slash`() {
        val patterns = IgnorePatterns("drafts/*.org")
        assertFalse(patterns.isFileIgnored("x.org", "drafts/sub/x.org"))
    }

    @Test
    fun `comments and blank lines are ignored`() {
        val withComments = IgnorePatterns("# comment\n\narchive")
        val plain = IgnorePatterns("archive")
        assertTrue(withComments.isDirIgnored("archive", "archive"))
        assertTrue(plain.isDirIgnored("archive", "archive"))
    }

    @Test
    fun `negated line is inert`() {
        val patterns = IgnorePatterns("!archive")
        assertFalse(patterns.isDirIgnored("archive", "archive"))
    }

    @Test
    fun `empty rules text matches nothing`() {
        val patterns = IgnorePatterns("")
        assertFalse(patterns.isDirIgnored("archive", "archive"))
        assertFalse(patterns.isFileIgnored("notes.org", "notes.org"))
    }
}
