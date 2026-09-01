package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Test

class FilenameValidationTest {

    private fun error(name: String) = FilenameValidation.errorFor(name)

    @Test
    fun `plain org filename is valid`() {
        assertNull(error("inbox.org"))
        assertNull(error("reading-list.org"))
        assertNull(error("2025 journal.org"))
    }

    @Test
    fun `blank or whitespace-only is invalid`() {
        assertNotNull(error(""))
        assertNotNull(error("   "))
    }

    @Test
    fun `dot and dot-dot are invalid`() {
        assertNotNull(error("."))
        assertNotNull(error(".."))
    }

    @Test
    fun `just the extension with no stem is invalid`() {
        assertNotNull(error(".org"))
    }

    @Test
    fun `missing org extension is invalid`() {
        assertNotNull(error("inbox"))
        assertNotNull(error("inbox.txt"))
    }

    @Test
    fun `forward-slash subfolder paths are valid`() {
        assertNull(error("sub/inbox.org"))
        assertNull(error("a/b/c/deep.org"))
        assertNull(error("  projects/2025/notes.org  "))
    }

    @Test
    fun `backslash is still invalid`() {
        assertNotNull(error("sub\\inbox.org"))
    }

    @Test
    fun `malformed paths are invalid`() {
        assertNotNull(error("/inbox.org"))
        assertNotNull(error("sub/inbox.org/"))
        assertNotNull(error("sub//inbox.org"))
        assertNotNull(error("sub/../inbox.org"))
        assertNotNull(error("./inbox.org"))
        assertNotNull(error("sub/inbox"))
        assertNotNull(error("sub/.org"))
    }

    @Test
    fun `reserved filesystem characters are invalid`() {
        for (ch in listOf(':', '*', '?', '"', '<', '>', '|')) {
            assertNotNull("char '$ch' should be rejected", error("in${ch}box.org"))
        }
    }

    @Test
    fun `surrounding whitespace is trimmed before validation`() {
        assertNull(error("  inbox.org  "))
    }

    @Test
    fun `error messages are distinct per failure kind`() {
        assertEquals("Enter a filename", error(""))
        assertEquals("Filename must end in .org", error("inbox"))
    }
}
