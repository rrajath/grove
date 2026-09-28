package com.rrajath.grove.data

import com.rrajath.grove.org.OrgParser
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoriteNoteMatchesTest {

    private val doc = OrgParser.parse(
        """
        * Both
        :PROPERTIES:
        :ID: THE-ID
        :CUSTOM_ID: the-custom
        :END:
        * Plain
        """.trimIndent() + "\n"
    )
    private val both = doc.headlines.first { it.title == "Both" }
    private val plain = doc.headlines.first { it.title == "Plain" }

    @Test
    fun `favorite storing the ID matches a heading that also has a CUSTOM_ID`() {
        assertTrue(FavoriteNote("f.org", 0, "Both", customId = "THE-ID").matches(both))
        assertTrue(setOf("THE-ID").containsIdOf(both))
    }

    @Test
    fun `favorite storing the CUSTOM_ID still matches`() {
        assertTrue(FavoriteNote("f.org", 0, "Both", customId = "the-custom").matches(both))
        assertTrue(setOf("the-custom").containsIdOf(both))
    }

    @Test
    fun `id favorite never matches a heading without ids by line coincidence`() {
        assertFalse(FavoriteNote("f.org", plain.lineIndex, "Plain", customId = "THE-ID").matches(plain))
        assertFalse(setOf("THE-ID").containsIdOf(plain))
    }
}
