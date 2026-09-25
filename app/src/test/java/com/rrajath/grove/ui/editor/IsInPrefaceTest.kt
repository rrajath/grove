package com.rrajath.grove.ui.editor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IsInPrefaceTest {

    private val roamFile = """
        :PROPERTIES:
        :ID: abc
        :END:
        #+title: Alpha
        #+filetags: :x:

        Intro text here
        * Heading one
        #+title: not a preface
    """.trimIndent()

    private fun offsetOf(needle: String) = roamFile.indexOf(needle).also { check(it >= 0) } + needle.length

    @Test
    fun `cursor on a keyword line is in the preface`() {
        assertTrue(isInPreface(roamFile, offsetOf("#+title: Alp")))
        assertTrue(isInPreface(roamFile, offsetOf("#+filetags: :x:")))
    }

    @Test
    fun `cursor in the file drawer is not in the preface`() {
        assertFalse(isInPreface(roamFile, offsetOf(":ID: ab")))
    }

    @Test
    fun `cursor in the intro or under a heading is not in the preface`() {
        assertFalse(isInPreface(roamFile, offsetOf("Intro te")))
        assertFalse(isInPreface(roamFile, offsetOf("Heading on")))
        assertFalse(isInPreface(roamFile, offsetOf("not a pref")))
    }

    @Test
    fun `blank separator after the preface is not in it`() {
        assertFalse(isInPreface(roamFile, offsetOf("#+filetags: :x:\n")))
    }

    @Test
    fun `buffer starting with a heading has no preface`() {
        val text = "* Note\nsome body"
        assertFalse(isInPreface(text, text.length))
    }

    @Test
    fun `keyword-only buffer without headings is all preface`() {
        val text = "#+title: Solo"
        assertTrue(isInPreface(text, text.length))
    }
}
