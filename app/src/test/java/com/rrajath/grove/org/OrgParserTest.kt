package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgParserTest {

    private fun golden(name: String): String =
        javaClass.getResourceAsStream("/golden/$name")!!.readBytes().toString(Charsets.UTF_8)

    // --- Round trip: parse → serialize must be byte-identical ---

    @Test
    fun `round trip is byte-identical for all golden files`() {
        for (name in listOf("travel.org", "edge-cases.org")) {
            val text = golden(name)
            assertEquals("round-trip failed for $name", text, OrgParser.parse(text).serialize())
        }
    }

    @Test
    fun `round trip preserves empty and quirky content`() {
        for (text in listOf("", "\n", "no headings at all", "* h", "* h\n", "*  spaced  \n\n\n")) {
            assertEquals(text, OrgParser.parse(text).serialize())
        }
    }

    // --- Structure ---

    @Test
    fun `parses headline hierarchy from golden file`() {
        val doc = OrgParser.parse(golden("travel.org"))
        assertEquals(listOf("trip"), doc.fileTags)

        val japan = doc.headlines.first()
        assertEquals(1, japan.level)
        assertEquals("Japan — Spring 2025", japan.title)
        assertEquals(listOf("japan"), japan.tags)
        assertEquals("11111111-aaaa-bbbb-cccc-000000000001", japan.id)

        val children = doc.directChildren(japan).map { it.title }
        assertEquals(listOf("Book Kyoto ryokan", "Kyoto — Day 2", "Order JR Rail Pass"), children)

        val kyotoDay2 = doc.findByCustomId("kyoto-day-2")!!
        assertEquals(3, doc.directChildren(kyotoDay2).size)
        assertEquals(3, doc.subtree(kyotoDay2).size)
        assertEquals("Japan — Spring 2025", doc.parent(kyotoDay2)!!.title)
    }

    @Test
    fun `parses keyword priority and planning`() {
        val doc = OrgParser.parse(golden("travel.org"))

        val ryokan = doc.findByTitle("Book Kyoto ryokan")!!
        assertEquals("TODO", ryokan.keyword)
        assertEquals("2025-04-09", ryokan.planning.scheduled!!.date.toString())

        val passport = doc.findByTitle("Renew passport")!!
        assertEquals("TODO", passport.keyword)
        assertEquals('A', passport.priority)
        assertEquals(listOf("admin"), passport.tags)
        assertEquals("2025-06-27", passport.planning.deadline!!.date.toString())

        val railPass = doc.findByTitle("Order JR Rail Pass")!!
        assertEquals("DONE", railPass.keyword)
        assertNotNull(railPass.planning.closed)

        val review = doc.findByTitle("Weekly review")!!
        assertEquals(RepeaterType.CUMULATIVE, review.planning.scheduled!!.repeater!!.type)
    }

    @Test
    fun `inherited tags include ancestors and file tags`() {
        val doc = OrgParser.parse(golden("travel.org"))
        val lunch = doc.findByTitle("Lunch")!!
        assertEquals(listOf("kyoto", "japan", "trip"), doc.inheritedTags(lunch))
    }

    @Test
    fun `inheritedTagsAll matches per-headline inheritedTags`() {
        val doc = OrgParser.parse(golden("travel.org"))
        val all = doc.inheritedTagsAll()
        doc.headlines.forEachIndexed { i, h ->
            assertEquals(doc.inheritedTags(h), all[i])
        }
    }

    @Test
    fun `body excludes planning and properties`() {
        val doc = OrgParser.parse(golden("travel.org"))
        val ryokan = doc.findByTitle("Book Kyoto ryokan")!!
        assertEquals(
            listOf("Check the one with the garden view first.", ""),
            doc.bodyOf(ryokan),
        )
        val japan = doc.headlines.first()
        assertTrue(doc.bodyOf(japan).first().startsWith("Planning notes"))
    }

    // --- Edge cases ---

    @Test
    fun `keyword must be exact configured word`() {
        val doc = OrgParser.parse(golden("edge-cases.org"))
        assertNull(doc.findByTitle("TODOX is a title not a keyword")!!.keyword)
        val bare = doc.headlines.first { it.title.isEmpty() }
        assertEquals("TODO", bare.keyword)
    }

    @Test
    fun `priority parses without keyword`() {
        val doc = OrgParser.parse(golden("edge-cases.org"))
        val h = doc.findByTitle("Priority without keyword")!!
        assertEquals('B', h.priority)
        assertNull(h.keyword)
    }

    @Test
    fun `tags with spaces are not tags`() {
        val doc = OrgParser.parse(golden("edge-cases.org"))
        assertNotNull(doc.findByTitle("Tags that are not tags :not a tag:"))
    }

    @Test
    fun `indented or starless lines are not headlines`() {
        val doc = OrgParser.parse(golden("edge-cases.org"))
        assertNull(doc.headlines.firstOrNull { it.title.contains("Also not a heading") })
        assertNull(doc.headlines.firstOrNull { it.title.contains("no star prefix") })
    }

    @Test
    fun `unclosed properties drawer does not swallow the file`() {
        val doc = OrgParser.parse(golden("edge-cases.org"))
        val h = doc.findByTitle("Drawer not closed")!!
        // Drawer never closed → treated as body, no properties extracted
        assertTrue(h.properties.isEmpty())
        // Following headline still parsed
        assertNotNull(doc.headlines.firstOrNull { it.title.startsWith("Unicode") })
    }

    @Test
    fun `multiple keywords config is respected`() {
        val kw = OrgKeywords.parse("NEXT WAITING | DONE")
        val doc = OrgParser.parse("* NEXT Call bank\n* TODO not a keyword here", kw)
        assertEquals("NEXT", doc.headlines[0].keyword)
        assertNull(doc.headlines[1].keyword)
        assertEquals("TODO not a keyword here", doc.headlines[1].title)
    }
}
