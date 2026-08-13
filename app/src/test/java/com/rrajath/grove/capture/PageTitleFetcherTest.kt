package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the site-agnostic placeholder-title detection in PageTitleFetcher: a fetched
 * <title> is rejected as a generic app-shell placeholder when it reduces to nothing but the
 * shared URL's own brand, derived from its host, rather than by matching known site names.
 */
class PageTitleFetcherTest {

    private fun brand(url: String) = PageTitleFetcher.brandTokenFromUrl(url)

    private fun placeholder(title: String, url: String) = PageTitleFetcher.isPlaceholderTitle(title, url)

    @Test
    fun `brand token is the label before a simple TLD`() {
        assertEquals("reddit", brand("https://www.reddit.com/r/programming/"))
        assertEquals("youtube", brand("https://www.youtube.com/watch?v=abc"))
        assertEquals("youtube", brand("https://m.youtube.com/watch?v=abc"))
    }

    @Test
    fun `brand token steps past a generic second level label`() {
        assertEquals("bbc", brand("https://www.bbc.co.uk/news/12345"))
    }

    @Test
    fun `brand token is null for an unparseable url`() {
        assertNull(brand("not a url"))
    }

    @Test
    fun `bare brand name is a placeholder`() {
        assertTrue(placeholder("Reddit", "https://www.reddit.com/r/programming/"))
        assertTrue(placeholder("YouTube", "https://www.youtube.com/watch?v=abc"))
        assertTrue(placeholder("Instagram", "https://www.instagram.com/nasa/"))
    }

    @Test
    fun `brand plus tagline with no closing brand is a placeholder`() {
        assertTrue(placeholder("Reddit - The heart of the internet", "https://www.reddit.com/r/programming/"))
        assertTrue(placeholder("Reddit | Dive into anything", "https://www.reddit.com/r/programming/"))
    }

    @Test
    fun `real content title suffixed with the brand is not a placeholder`() {
        assertFalse(
            placeholder(
                "Rick Astley - Never Gonna Give You Up (Official Video) (4K Remaster) - YouTube",
                "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
            ),
        )
    }

    @Test
    fun `real content title that opens and closes with the brand is not a placeholder`() {
        assertFalse(
            placeholder(
                "GitHub - torvalds/linux: Linux kernel source tree · GitHub",
                "https://github.com/torvalds/linux",
            ),
        )
    }

    @Test
    fun `real content title unrelated to the brand is not a placeholder`() {
        assertFalse(placeholder("Elon Musk (@elonmusk) / X", "https://x.com/elonmusk"))
    }

    @Test
    fun `bot check interstitial title is a placeholder regardless of brand`() {
        assertTrue(placeholder("Just a moment...", "https://stackoverflow.com/questions/1"))
    }

    @Test
    fun `placeholder check must use the post-redirect url, not a shortener's`() {
        // youtu.be redirects to youtube.com before serving anything; checking against the
        // shortener's own host ("youtu") misses that "YouTube" is youtube.com's shell title.
        assertFalse(placeholder("YouTube", "https://youtu.be/dQw4w9WgXcQ"))
        assertTrue(placeholder("YouTube", "https://www.youtube.com/watch?v=dQw4w9WgXcQ"))
    }
}
