package com.rrajath.grove.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for FavoriteNoteSerializer round-trip correctness and resilience to
 * corrupt or forward-compatible JSON.
 */
class FavoriteNoteSerializerTest {

    @Test
    fun `empty list encodes and decodes to empty list`() {
        val encoded = FavoriteNoteSerializer.encode(emptyList())
        assertEquals(emptyList<FavoriteNote>(), FavoriteNoteSerializer.decode(encoded))
    }

    @Test
    fun `single favorite survives a round-trip`() {
        val fav = FavoriteNote(fileName = "inbox.org", lineIndex = 3, title = "Buy groceries")
        val decoded = FavoriteNoteSerializer.decode(FavoriteNoteSerializer.encode(listOf(fav)))
        assertEquals(listOf(fav), decoded)
    }

    @Test
    fun `multiple favorites preserve order through round-trip`() {
        val favorites = listOf(
            FavoriteNote("notes.org", 0, "First note"),
            FavoriteNote("journal.org", 10, "Second note"),
            FavoriteNote("work.org", 42, "Third note"),
        )
        val decoded = FavoriteNoteSerializer.decode(FavoriteNoteSerializer.encode(favorites))
        assertEquals(favorites, decoded)
    }

    @Test
    fun `favorite with special characters in title round-trips`() {
        val fav = FavoriteNote("inbox.org", 7, "Notes: \"quotes\" & <brackets>")
        val decoded = FavoriteNoteSerializer.decode(FavoriteNoteSerializer.encode(listOf(fav)))
        assertEquals(listOf(fav), decoded)
    }

    @Test
    fun `corrupt json decodes to empty list`() {
        assertTrue(FavoriteNoteSerializer.decode("not json").isEmpty())
        assertTrue(FavoriteNoteSerializer.decode("").isEmpty())
        assertTrue(FavoriteNoteSerializer.decode("{]").isEmpty())
    }

    @Test
    fun `unknown fields in json are ignored`() {
        val json = """{"favorites":[{"fileName":"notes.org","lineIndex":1,"title":"Hello","futureField":"x"}]}"""
        val decoded = FavoriteNoteSerializer.decode(json)
        assertEquals(1, decoded.size)
        assertEquals(FavoriteNote("notes.org", 1, "Hello"), decoded[0])
    }

    @Test
    fun `line index zero is preserved`() {
        val fav = FavoriteNote("inbox.org", 0, "Top note")
        val decoded = FavoriteNoteSerializer.decode(FavoriteNoteSerializer.encode(listOf(fav)))
        assertEquals(0, decoded[0].lineIndex)
    }
}
