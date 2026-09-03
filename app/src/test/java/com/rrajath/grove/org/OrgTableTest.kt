package com.rrajath.grove.org

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OrgTableTest {

    @Test
    fun `header is the row above the first rule`() {
        val model = parseOrgTable(
            listOf(
                "| Name  | Age |",
                "|-------+-----|",
                "| Alice | 30  |",
                "| Bob   | 25  |",
            )
        )
        assertEquals(listOf(listOf("Name", "Age")), model.headerRows)
        assertEquals(listOf(listOf("Alice", "30"), listOf("Bob", "25")), model.bodyRows)
        assertEquals(2, model.columnCount)
    }

    @Test
    fun `multiple rows above the rule are all header`() {
        val model = parseOrgTable(
            listOf(
                "| a | b |",
                "| c | d |",
                "|---+---|",
                "| e | f |",
            )
        )
        assertEquals(2, model.headerRows.size)
        assertEquals(1, model.bodyRows.size)
    }

    @Test
    fun `no rule falls back to first row as header`() {
        val model = parseOrgTable(
            listOf(
                "| x | y |",
                "| 1 | 2 |",
                "| 3 | 4 |",
            )
        )
        assertEquals(listOf(listOf("x", "y")), model.headerRows)
        assertEquals(2, model.bodyRows.size)
    }

    @Test
    fun `leading rule is ignored for header detection`() {
        val model = parseOrgTable(
            listOf(
                "|---+---|",
                "| h1 | h2 |",
                "| a | b |",
            )
        )
        assertEquals(listOf(listOf("h1", "h2")), model.headerRows)
        assertEquals(listOf(listOf("a", "b")), model.bodyRows)
    }

    @Test
    fun `short rows are padded to the widest column count`() {
        val model = parseOrgTable(
            listOf(
                "| a | b | c |",
                "|---|",
                "| d | e |",
            )
        )
        assertEquals(3, model.columnCount)
        assertEquals(listOf(listOf("d", "e", "")), model.bodyRows)
    }

    @Test
    fun `cells are trimmed`() {
        val model = parseOrgTable(listOf("|  a  |   b   |"))
        assertEquals(listOf(listOf("a", "b")), model.headerRows)
    }

    @Test
    fun `empty input is empty`() {
        assertTrue(parseOrgTable(emptyList()).isEmpty)
        assertTrue(parseOrgTable(listOf("|---+---|")).isEmpty)
    }
}
