package com.rrajath.grove.vault

import com.rrajath.grove.settings.GroveSettings
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.LocalDateTime

class AutoArchiveTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun vault(): Vault = Vault(JvmFileStore(tmp.root))

    private val now = LocalDateTime.of(2026, 8, 1, 9, 0)

    @Test
    fun `plain when auto-archive is off`() = runTest {
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(autoArchiveDoneItems = false)

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "DONE", now)

        assertTrue(result is StateChangeResult.Plain)
        assertTrue((result as StateChangeResult.Plain).text.contains("DONE Task"))
    }

    @Test
    fun `plain when the new keyword is not a done-type keyword`() = runTest {
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "IN-PROGRESS", now)

        assertTrue(result is StateChangeResult.Plain)
    }

    @Test
    fun `plain when auto-archive is on but nothing resolves a destination`() = runTest {
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(autoArchiveDoneItems = true) // no autoArchiveFile set

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "DONE", now)

        assertTrue(result is StateChangeResult.Plain)
    }

    @Test
    fun `archives same-file to the heading's own ARCHIVE property`() = runTest {
        tmp.newFile("todo.org").writeText(
            """
            * TODO Task
            :PROPERTIES:
            :ARCHIVE: ./todo.org::* Archive
            :END:
            * Archive
            """.trimIndent() + "\n"
        )
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first { it.title == "Task" }
        val settings = GroveSettings(autoArchiveDoneItems = true)

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "DONE", now)

        assertTrue(result is StateChangeResult.Archived)
        result as StateChangeResult.Archived
        assertEquals("todo.org", result.sourceFile)
        assertEquals("todo.org", result.destFile)
        assertEquals("todo › Archive", result.label)
        assertTrue(result.sourceDoc.headlines.none { it.title == "Task" && it.level == 1 })
        val archived = result.sourceDoc.headlines.first { it.title == "Task" }
        assertEquals("DONE", archived.keyword)
        assertEquals(2, archived.level)
    }

    @Test
    fun `archives cross-file using the Settings fallback, creating the destination file`() = runTest {
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(
            autoArchiveDoneItems = true,
            autoArchiveFile = "archive.org",
            autoArchiveHeadingPath = "Done",
        )

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "DONE", now)

        assertTrue(result is StateChangeResult.Archived)
        result as StateChangeResult.Archived
        assertEquals("todo.org", result.sourceFile)
        assertEquals("archive.org", result.destFile)
        assertEquals("archive › Done", result.label)
        assertEquals("", result.destTextBefore.trim())
        assertTrue(result.destText.contains("Done"))
        assertTrue(result.destText.contains("DONE Task"))
        assertTrue(result.sourceDoc.headlines.isEmpty())
    }

    @Test
    fun `a heading's own ARCHIVE property wins over the Settings fallback`() = runTest {
        tmp.newFile("todo.org").writeText(
            """
            * TODO Task
            :PROPERTIES:
            :ARCHIVE: ./own.org::* Own
            :END:
            """.trimIndent() + "\n"
        )
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(
            autoArchiveDoneItems = true,
            autoArchiveFile = "fallback.org",
        )

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "DONE", now)

        assertTrue(result is StateChangeResult.Archived)
        assertEquals("own.org", (result as StateChangeResult.Archived).destFile)
    }

    @Test
    fun `any done-type keyword triggers archiving, not just literal DONE`() = runTest {
        // OrgKeywords.DEFAULT ("TODO IN-PROGRESS | DONE CANCELLED") treats CANCELLED as done-type too.
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "CANCELLED", now)

        assertTrue(result is StateChangeResult.Archived)
    }

    @Test
    fun `allowArchive false marks done but never refiles, even when auto-archive is on`() = runTest {
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val doc = v.open("todo.org")!!
        val headline = doc.headlines.first()
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.apply(v, settings, doc, "todo.org", headline, "DONE", now, allowArchive = false)

        assertTrue(result is StateChangeResult.Plain)
        assertTrue((result as StateChangeResult.Plain).text.contains("DONE Task"))
    }

    @Test
    fun `marking the last child done cascades to complete and archive the parent's cookie`() = runTest {
        tmp.newFile("todo.org").writeText(
            """
            * TODO Ship it [/]
            ** DONE Write code
            ** TODO Write tests
            """.trimIndent() + "\n"
        )
        val v = vault()
        val doc = v.open("todo.org")!!
        val child = doc.headlines.first { it.title == "Write tests" }
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.apply(v, settings, doc, "todo.org", child, "DONE", now)

        // The child's own DONE transition doesn't itself archive (only the
        // parent is done-type-eligible with autoArchiveDoneItems watching it
        // via the cascade); the parent completes and is the one refiled.
        assertTrue(result is StateChangeResult.Archived)
        result as StateChangeResult.Archived
        assertTrue(result.destText.contains("Ship it [2/2]"))
        assertTrue(result.sourceDoc.headlines.none { it.title.startsWith("Ship it") })
    }

    @Test
    fun `parent cascade respects allowArchive false too`() = runTest {
        tmp.newFile("todo.org").writeText(
            """
            * TODO Ship it [/]
            ** DONE Write code
            ** TODO Write tests
            """.trimIndent() + "\n"
        )
        val v = vault()
        val doc = v.open("todo.org")!!
        val child = doc.headlines.first { it.title == "Write tests" }
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.apply(v, settings, doc, "todo.org", child, "DONE", now, allowArchive = false)

        assertTrue(result is StateChangeResult.Plain)
        val text = (result as StateChangeResult.Plain).text
        assertTrue(text.contains("Ship it [2/2]"))
        assertTrue(text.contains("DONE Ship it"))
    }

    @Test
    fun `parent cascade does not fire when the parent still has an open child`() = runTest {
        tmp.newFile("todo.org").writeText(
            """
            * TODO Ship it [/]
            ** TODO Write code
            ** TODO Write tests
            """.trimIndent() + "\n"
        )
        val v = vault()
        val doc = v.open("todo.org")!!
        val child = doc.headlines.first { it.title == "Write tests" }
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.apply(v, settings, doc, "todo.org", child, "DONE", now)

        assertTrue(result is StateChangeResult.Plain)
        val text = (result as StateChangeResult.Plain).text
        assertTrue(text.contains("Ship it [1/2]"))
        assertTrue(text.contains("TODO Ship it"))
    }

    @Test
    fun `archiveIfStillDone refiles a heading that is currently done on disk`() = runTest {
        tmp.newFile("todo.org").writeText("* DONE Task\n")
        val v = vault()
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        val result = AutoArchive.archiveIfStillDone(v, settings, "todo.org", 0)

        assertTrue(result != null)
        assertEquals("archive.org", result!!.destFile)
    }

    @Test
    fun `archiveIfStillDone returns null when the heading is no longer done`() = runTest {
        tmp.newFile("todo.org").writeText("* TODO Task\n")
        val v = vault()
        val settings = GroveSettings(autoArchiveDoneItems = true, autoArchiveFile = "archive.org")

        assertTrue(AutoArchive.archiveIfStillDone(v, settings, "todo.org", 0) == null)
    }

    @Test
    fun `archiveIfStillDone returns null when auto-archive is off`() = runTest {
        tmp.newFile("todo.org").writeText("* DONE Task\n")
        val v = vault()
        val settings = GroveSettings(autoArchiveDoneItems = false, autoArchiveFile = "archive.org")

        assertTrue(AutoArchive.archiveIfStillDone(v, settings, "todo.org", 0) == null)
    }
}
