package com.rrajath.grove.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class QueryMatcherTest {

    private val today = LocalDate.of(2025, 6, 11)

    private fun note(
        title: String,
        fileName: String = "notes.org",
        keyword: String? = null,
        done: Boolean = false,
        priority: String? = null,
        tags: List<String> = emptyList(),
        inherited: List<String> = tags,
        scheduled: String? = null,
        deadline: String? = null,
        closed: String? = null,
        created: String? = null,
        body: String = "",
        modified: Long = 0L,
    ) = NoteMeta(
        fileName, 0, title, keyword, done, priority, tags, inherited,
        scheduled, deadline, closed, created, modified, "$title\n$body",
    )

    private fun run(query: String, vararg notes: NoteMeta): List<String> =
        QueryMatcher.filter(notes.toList(), QueryParser.parse(query), today).map { it.title }

    @Test
    fun `text terms match title and body case-insensitively`() {
        val a = note("Ramen places", body = "the best shoyu")
        val b = note("Sushi", body = "try the RAMEN here too")
        val c = note("Pasta")
        assertEquals(listOf("Ramen places", "Sushi"), run("ramen", a, b, c))
        assertEquals(listOf("Sushi"), run("ramen sushi", a, b, c))
    }

    @Test
    fun `state notebook tag priority conditions`() {
        val a = note("A", keyword = "TODO", fileName = "work.org", tags = listOf("project"), priority = "A")
        val b = note("B", keyword = "DONE", done = true, fileName = "home.org", tags = listOf("beeblebrox"))
        assertEquals(listOf("A"), run("i.todo", a, b))
        assertEquals(listOf("B"), run("i.done", a, b))
        assertEquals(listOf("A"), run("b.work", a, b))
        // tag substring match (PRD §5.6)
        assertEquals(listOf("B"), run("t.bee", a, b))
        assertEquals(listOf("A"), run("p.a", a, b))
    }

    @Test
    fun `inherited vs own tags`() {
        val child = note("Child", tags = emptyList(), inherited = listOf("trip"))
        assertEquals(listOf("Child"), run("t.trip", child))
        assertEquals(emptyList<String>(), run("tn.trip", child))
    }

    @Test
    fun `i none matches keywordless notes`() {
        val a = note("Plain")
        val b = note("Task", keyword = "TODO")
        assertEquals(listOf("Plain"), run("i.none", a, b))
    }

    @Test
    fun `scheduled within period includes overdue`() {
        val past = note("Past", scheduled = "<2025-06-01 Sun>")
        val todayNote = note("Today", scheduled = "<2025-06-11 Wed>")
        val future = note("Future", scheduled = "<2025-06-20 Fri>")
        val none = note("None")
        assertEquals(listOf("Past", "Today"), run("s.today", past, todayNote, future, none))
        assertEquals(listOf("Past", "Today", "Future"), run("s.2w", past, todayNote, future, none))
    }

    @Test
    fun `closed within past window`() {
        val recent = note("Recent", closed = "[2025-06-10 Tue]")
        val old = note("Old", closed = "[2025-05-01 Thu]")
        assertEquals(listOf("Recent"), run("c.2d", recent, old))
        assertEquals(emptyList<String>(), run("c.today", recent, old))
    }

    @Test
    fun `negation and OR`() {
        val a = note("A", keyword = "TODO", tags = listOf("work"))
        val b = note("B", keyword = "DONE", done = true, tags = listOf("work"))
        val c = note("C", tags = listOf("home"))
        assertEquals(listOf("A"), run("t.work .i.done", a, b, c))
        assertEquals(listOf("A", "C"), run("i.todo OR t.home", a, b, c).sorted())
    }

    @Test
    fun `ranking exact title then contains then body`() {
        val exact = note("ramen", modified = 1)
        val inTitle = note("ramen places", modified = 2)
        val inBody = note("Sushi", body = "ramen mention", modified = 3)
        assertEquals(
            listOf("ramen", "ramen places", "Sushi"),
            run("ramen", inBody, inTitle, exact),
        )
    }

    @Test
    fun `explicit sort overrides ranking`() {
        val a = note("A", scheduled = "<2025-06-20 Fri>")
        val b = note("B", scheduled = "<2025-06-12 Thu>")
        assertEquals(listOf("B", "A"), run("s.2w o.scheduled", a, b))
    }

    @Test
    fun `agenda groups by day with overdue on today`() {
        val overdue = note("Overdue", keyword = "TODO", scheduled = "<2025-06-01 Sun>")
        val todayNote = note("Today", deadline = "<2025-06-11 Wed>")
        val thursday = note("Thursday", scheduled = "<2025-06-12 Thu>")
        val nextWeek = note("NextWeek", scheduled = "<2025-06-25 Wed>")

        val agenda = QueryMatcher.agenda(listOf(overdue, todayNote, thursday, nextWeek), 7, today)
        assertEquals(2, agenda.size)
        assertEquals(today, agenda[0].date)
        assertEquals(listOf("Overdue", "Today"), agenda[0].notes.map { it.title })
        assertEquals(today.plusDays(1), agenda[1].date)
        assertEquals(listOf("Thursday"), agenda[1].notes.map { it.title })
    }

    @Test
    fun `snippet highlights the matched term`() {
        val snippet = Snippets.build("some long body with the magic word inside of it", listOf("magic"))
        assertNotNull(snippet.highlight)
        assertEquals("magic", snippet.text.substring(snippet.highlight!!))
        val plain = Snippets.build("no match here", listOf("absent"))
        assertNull(plain.highlight)
        assertEquals("no match here", plain.text)
    }
}
