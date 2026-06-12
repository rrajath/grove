package com.rrajath.grove.vault

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IgnoreRulesTest {

    @Test
    fun `empty rules ignore nothing`() {
        val rules = IgnoreRules("")
        assertFalse(rules.isIgnored("notes.org"))
    }

    @Test
    fun `glob star matches`() {
        val rules = IgnoreRules("archive*.org")
        assertTrue(rules.isIgnored("archive.org"))
        assertTrue(rules.isIgnored("archive-2024.org"))
        assertFalse(rules.isIgnored("notes.org"))
    }

    @Test
    fun `question mark matches single char`() {
        val rules = IgnoreRules("v?.org")
        assertTrue(rules.isIgnored("v1.org"))
        assertFalse(rules.isIgnored("v12.org"))
    }

    @Test
    fun `comments and blanks are skipped`() {
        val rules = IgnoreRules("# a comment\n\nsecret.org\n")
        assertTrue(rules.isIgnored("secret.org"))
        assertFalse(rules.isIgnored("# a comment"))
    }

    @Test
    fun `negation re-includes, later rules win`() {
        val rules = IgnoreRules("*.org\n!keep.org")
        assertTrue(rules.isIgnored("drop.org"))
        assertFalse(rules.isIgnored("keep.org"))
    }

    @Test
    fun `literal dots are not wildcards`() {
        val rules = IgnoreRules("a.org")
        assertFalse(rules.isIgnored("aXorg"))
    }
}
