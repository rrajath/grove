package com.rrajath.grove.capture

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CaptureInserterTest {

    private val today = LocalDate.of(2025, 6, 11) // Wednesday

    private fun insert(doc: String, location: TargetLocation, entry: String) =
        CaptureInserter.insert(doc, location, entry, today)

    // --- top / bottom ---

    @Test
    fun `top of file inserts after preamble`() {
        val doc = "#+TITLE: X\n\n* First\nbody\n"
        val result = insert(doc, TargetLocation.TopOfFile, "* New entry")
        assertEquals("#+TITLE: X\n\n* New entry\n* First\nbody\n", result.newText)
        assertEquals(2, result.insertedAtLine)
    }

    @Test
    fun `bottom of file keeps trailing newline`() {
        val doc = "* First\nbody\n"
        val result = insert(doc, TargetLocation.BottomOfFile, "* New entry\ncontent")
        assertEquals("* First\nbody\n* New entry\ncontent\n", result.newText)
    }

    @Test
    fun `bottom of file without trailing newline`() {
        val result = insert("* First", TargetLocation.BottomOfFile, "* New")
        assertEquals("* First\n* New", result.newText)
    }

    @Test
    fun `empty file gets just the entry`() {
        val result = insert("", TargetLocation.BottomOfFile, "* New")
        assertEquals("* New\n", result.newText)
    }

    @Test
    fun `entry without stars becomes a heading`() {
        val result = insert("", TargetLocation.BottomOfFile, "plain text idea")
        assertEquals("* plain text idea\n", result.newText)
    }

    // --- under heading ---

    private val projectDoc = """
        * Projects :work:
        ** Existing child
        child body
        * Other
        :PROPERTIES:
        :CUSTOM_ID: other-id
        :END:
        other body
    """.trimIndent() + "\n"

    @Test
    fun `under heading by title appends as last child with releveling`() {
        val result = insert(
            projectDoc,
            TargetLocation.UnderHeading(title = "Projects"),
            "* New task\ndetails",
        )
        val expected = """
            * Projects :work:
            ** Existing child
            child body
            ** New task
            details
            * Other
            :PROPERTIES:
            :CUSTOM_ID: other-id
            :END:
            other body
        """.trimIndent() + "\n"
        assertEquals(expected, result.newText)
    }

    @Test
    fun `under heading by custom id`() {
        val result = insert(
            projectDoc,
            TargetLocation.UnderHeading(customId = "other-id"),
            "note under other",
        )
        assertTrue(result.newText.contains("other body\n** note under other"))
    }

    @Test
    fun `under heading first-child position`() {
        val result = insert(
            projectDoc,
            TargetLocation.UnderHeading(title = "Projects", appendLast = false),
            "* New first",
        )
        assertTrue(result.newText.contains("* Projects :work:\n** New first\n** Existing child"))
    }

    @Test
    fun `missing heading throws`() {
        assertThrows(CaptureInserter.CaptureTargetNotFound::class.java) {
            insert(projectDoc, TargetLocation.UnderHeading(title = "Nope"), "x")
        }
    }

    @Test
    fun `untouched content is byte-identical around the insertion`() {
        val result = insert(
            projectDoc,
            TargetLocation.UnderHeading(title = "Projects"),
            "* New task",
        )
        val inserted = "** New task\n"
        assertEquals(projectDoc, result.newText.replace(inserted, ""))
    }

    // --- datetree ---

    @Test
    fun `datetree creates full hierarchy in empty file`() {
        val result = insert("", TargetLocation.DatetreeDatetime, "<2025-06-11 Wed 14:32>\nFerry back")
        val expected = """
            * 2025
            ** 2025-06 June
            *** 2025-06-11 Wednesday
            **** <2025-06-11 Wed 14:32>
            Ferry back
        """.trimIndent() + "\n"
        assertEquals(expected, result.newText)
    }

    @Test
    fun `datetree reuses existing hierarchy and appends to day`() {
        val doc = """
            * 2025
            ** 2025-06 June
            *** 2025-06-11 Wednesday
            **** 09:00 Morning entry
            text
        """.trimIndent() + "\n"
        val result = insert(doc, TargetLocation.DatetreeDatetime, "14:32 Afternoon")
        val expected = doc + "**** 14:32 Afternoon\n"
        assertEquals(expected.trimEnd('\n') + "\n", result.newText)
    }

    @Test
    fun `datetree creates missing day under existing month`() {
        val doc = """
            * 2025
            ** 2025-06 June
            *** 2025-06-10 Tuesday
            **** old entry
        """.trimIndent() + "\n"
        val result = insert(doc, TargetLocation.DatetreeDate, "new entry")
        val expected = """
            * 2025
            ** 2025-06 June
            *** 2025-06-10 Tuesday
            **** old entry
            *** 2025-06-11 Wednesday
            **** new entry
        """.trimIndent() + "\n"
        assertEquals(expected, result.newText)
    }

    @Test
    fun `datetree inserts day in chronological position`() {
        val doc = """
            * 2025
            ** 2025-06 June
            *** 2025-06-10 Tuesday
            *** 2025-06-14 Saturday
        """.trimIndent() + "\n"
        val result = insert(doc, TargetLocation.DatetreeDate, "entry")
        val expected = """
            * 2025
            ** 2025-06 June
            *** 2025-06-10 Tuesday
            *** 2025-06-11 Wednesday
            **** entry
            *** 2025-06-14 Saturday
        """.trimIndent() + "\n"
        assertEquals(expected, result.newText)
    }

    @Test
    fun `datetree leaves non-date headings alone`() {
        val doc = """
            * Inbox
            stuff
            * 2024
            ** 2024-12 December
        """.trimIndent() + "\n"
        val result = insert(doc, TargetLocation.DatetreeDate, "entry")
        assertTrue(result.newText.startsWith("* Inbox\nstuff\n"))
        assertTrue(result.newText.contains("* 2024\n** 2024-12 December\n* 2025\n** 2025-06 June\n*** 2025-06-11 Wednesday\n**** entry"))
    }

    @Test
    fun `datetree year inserted before later year`() {
        val doc = "* 2026\n** 2026-01 January\n"
        val result = insert(doc, TargetLocation.DatetreeDate, "entry")
        assertTrue(
            result.newText.indexOf("* 2025") < result.newText.indexOf("* 2026")
        )
    }
}
