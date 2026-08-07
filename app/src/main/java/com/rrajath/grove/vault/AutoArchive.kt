package com.rrajath.grove.vault

import com.rrajath.grove.org.ArchiveLocation
import com.rrajath.grove.org.ArchiveTarget
import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgHeadline
import com.rrajath.grove.org.OrgMutations
import com.rrajath.grove.org.OrgParser
import com.rrajath.grove.settings.GroveSettings
import java.time.LocalDateTime

/** Outcome of [AutoArchive.apply]: either a plain state change, or a state change plus a refile. */
sealed class StateChangeResult {
    data class Plain(val fileName: String, val text: String, val doc: OrgDocument) : StateChangeResult()
    data class Archived(
        val sourceFile: String,
        val sourceText: String,
        val sourceDoc: OrgDocument,
        val destFile: String,
        val destText: String,
        /** [destFile]'s content before this write (for undo); equals the pre-mutation source text when same-file. */
        val destTextBefore: String,
        /** Line index of the moved headline within [destText]. */
        val destLineIndex: Int,
        val label: String,
    ) : StateChangeResult()
}

/** Delete-subtree + find-or-create-heading-path + insert, same-file or cross-file. */
data class RefileWrite(
    val sourceFile: String,
    val sourceText: String,
    val destFile: String,
    val destText: String,
    /** [destFile]'s content before this write (for undo); equals the source's pre-mutation text when same-file. */
    val destTextBefore: String,
    /** Line index of the moved headline within [destText]. */
    val destLineIndex: Int,
    val label: String,
)

/**
 * One shared "mark done, maybe auto-archive" mutation, used by every surface that can change a
 * TODO state (Outline, Agenda, Search, edit-mode metadata sheet) so auto-archive behaves
 * identically everywhere: any done-type keyword (not just literal `DONE`) triggers it, and the
 * destination is resolved the same way org resolves `:ARCHIVE:` — heading property, ancestor
 * property, file `#+ARCHIVE:` keyword, then (new) the Settings default.
 *
 * The state change and the refile (when it fires) are computed as one mutation before anything
 * is saved, so a caller's usual pre-mutation undo snapshot reverts both together for free.
 */
object AutoArchive {

    /** The Settings-configured default archive location, or null if none is set. */
    fun settingsFallback(settings: GroveSettings): ArchiveTarget? =
        settings.autoArchiveFile?.let { file ->
            ArchiveTarget(file, settings.autoArchiveHeadingPath.split('/').filter { it.isNotEmpty() })
        }

    suspend fun apply(
        vault: Vault,
        settings: GroveSettings,
        doc: OrgDocument,
        fileName: String,
        headline: OrgHeadline,
        newKeyword: String?,
        now: LocalDateTime,
    ): StateChangeResult {
        val plainText = OrgMutations.changeKeyword(doc, headline, newKeyword, doc.keywords, now)
        val plainDoc = OrgParser.parse(plainText, doc.keywords)
        fun plain() = StateChangeResult.Plain(fileName, plainText, plainDoc)

        val movedHeadline = plainDoc.headlines.firstOrNull { it.lineIndex == headline.lineIndex } ?: return plain()

        // Check the headline's keyword as it actually ended up after the mutation, not the
        // keyword the caller requested: a repeating SCHEDULED/DEADLINE keeps markDone() from
        // ever setting a done keyword (org semantics — it just advances the date), so archiving
        // here based on the requested newKeyword would refile a still-active recurring task.
        val shouldArchive = settings.autoArchiveDoneItems &&
            movedHeadline.keyword != null && doc.keywords.isDone(movedHeadline.keyword)
        if (!shouldArchive) return plain()

        val target = ArchiveLocation.resolve(plainDoc, movedHeadline, settingsFallback(settings)) ?: return plain()
        val write = refileSubtree(vault, plainDoc, fileName, movedHeadline, target) ?: return plain()

        return StateChangeResult.Archived(
            sourceFile = write.sourceFile,
            sourceText = write.sourceText,
            sourceDoc = OrgParser.parse(write.sourceText, doc.keywords),
            destFile = write.destFile,
            destText = write.destText,
            destTextBefore = write.destTextBefore,
            destLineIndex = write.destLineIndex,
            label = write.label,
        )
    }

    /**
     * Refile [source]'s subtree out of [sourceDoc] straight to [target], creating any missing
     * destination file/heading path. Shared by [apply] and the manual per-note "Archive" quick
     * action. Returns null when the destination file doesn't exist and [createFileIfMissing] is
     * false.
     */
    suspend fun refileSubtree(
        vault: Vault,
        sourceDoc: OrgDocument,
        sourceFile: String,
        source: OrgHeadline,
        target: ArchiveTarget,
        createFileIfMissing: Boolean = true,
    ): RefileWrite? {
        val label = (listOf(target.fileName.removeSuffix(".org")) + target.headingPath).joinToString(" › ")
        val subtree = OrgMutations.subtreeText(sourceDoc, source)

        if (target.fileName == sourceFile) {
            val afterDelete = OrgParser.parse(OrgMutations.deleteSubtree(sourceDoc, source), sourceDoc.keywords)
            val (docAfterPath, destHeadline) = ArchiveLocation.findOrCreateHeadingPath(afterDelete, target.headingPath)
            val (finalText, insertAt) = OrgMutations.refileInsert(docAfterPath, destHeadline, subtree)
            return RefileWrite(sourceFile, finalText, sourceFile, finalText, sourceDoc.text, insertAt, label)
        }

        var destDoc = vault.open(target.fileName)
        if (destDoc == null) {
            if (!createFileIfMissing) return null
            vault.createNotebook(target.fileName)
            destDoc = vault.open(target.fileName) ?: return null
        }
        val destTextBefore = destDoc.text
        val srcText = OrgMutations.deleteSubtree(sourceDoc, source)
        val (docAfterPath, destHeadline) = ArchiveLocation.findOrCreateHeadingPath(destDoc, target.headingPath)
        val (dstText, insertAt) = OrgMutations.refileInsert(docAfterPath, destHeadline, subtree)
        return RefileWrite(sourceFile, srcText, target.fileName, dstText, destTextBefore, insertAt, label)
    }
}
