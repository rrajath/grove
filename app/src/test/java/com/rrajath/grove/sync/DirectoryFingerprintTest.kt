package com.rrajath.grove.sync

import com.rrajath.grove.vault.FileEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class DirectoryFingerprintTest {

    private fun entry(name: String, mtime: Long = 1_000, size: Long = 10) =
        FileEntry(name, mtime, size)

    @Test
    fun `identical listings hash the same`() {
        val a = listOf(entry("a.org"), entry("b.org"), entry("c.org"))
        val b = listOf(entry("a.org"), entry("b.org"), entry("c.org"))
        assertEquals(directoryFingerprint(a), directoryFingerprint(b))
    }

    @Test
    fun `listing order does not matter`() {
        val a = listOf(entry("a.org"), entry("b.org"), entry("c.org"))
        val shuffled = listOf(entry("c.org"), entry("a.org"), entry("b.org"))
        assertEquals(directoryFingerprint(a), directoryFingerprint(shuffled))
    }

    @Test
    fun `a changed mtime changes the fingerprint`() {
        val before = listOf(entry("a.org", mtime = 1_000))
        val after = listOf(entry("a.org", mtime = 2_000))
        assertNotEquals(directoryFingerprint(before), directoryFingerprint(after))
    }

    @Test
    fun `a changed size changes the fingerprint`() {
        val before = listOf(entry("a.org", size = 10))
        val after = listOf(entry("a.org", size = 11))
        assertNotEquals(directoryFingerprint(before), directoryFingerprint(after))
    }

    @Test
    fun `adding a file changes the fingerprint`() {
        val before = listOf(entry("a.org"))
        val after = listOf(entry("a.org"), entry("b.org"))
        assertNotEquals(directoryFingerprint(before), directoryFingerprint(after))
    }

    @Test
    fun `removing a file changes the fingerprint`() {
        val before = listOf(entry("a.org"), entry("b.org"))
        val after = listOf(entry("a.org"))
        assertNotEquals(directoryFingerprint(before), directoryFingerprint(after))
    }

    @Test
    fun `renaming a file changes the fingerprint`() {
        val before = listOf(entry("a.org"))
        val after = listOf(entry("renamed.org"))
        assertNotEquals(directoryFingerprint(before), directoryFingerprint(after))
    }

    @Test
    fun `an empty listing is stable`() {
        assertEquals(directoryFingerprint(emptyList()), directoryFingerprint(emptyList()))
    }
}
