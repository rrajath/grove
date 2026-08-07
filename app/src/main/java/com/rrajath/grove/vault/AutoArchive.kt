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

    /**
     * @param allowArchive Read/Edit mode passes `false` while the note being changed is the one
     * currently on screen: the keyword/CLOSED-stamp change still lands on disk immediately (so
     * sync, search, and the agenda see it right away), but the refile itself is deferred until
     * the user actually leaves that note (see [com.rrajath.grove.ui.GroveApp]'s note-route
     * lifecycle observer), so marking something done doesn't yank it out from under them
     * mid-read/edit. Every other caller (Outline/Agenda/Search swipe) leaves this `true`.
     */
    suspend fun apply(
        vault: Vault,
        settings: GroveSettings,
        doc: OrgDocument,
        fileName: String,
        headline: OrgHeadline,
        newKeyword: String?,
        now: LocalDateTime,
        allowArchive: Boolean = true,
    ): StateChangeResult {
        // A heading whose *parent* carries a statistics cookie is a checklist-style member of
        // that group (same as a checkbox item never getting archived on its own): even though
        // this heading itself just went done-type, it stays put until the whole group does, so
        // the primary step below is never allowed to archive it individually — only the cascade,
        // once the parent's cookie actually completes (taking this heading with it as part of
        // the parent's subtree), can.
        val parentHasCookie = doc.parent(headline)?.let { OrgMutations.hasStatisticsCookie(it.title) } == true
        val primary = markAndMaybeArchive(
            vault, settings, doc, fileName, headline, newKeyword, now, allowArchive && !parentHasCookie,
        )
        val primaryDoc = when (primary) {
            // Already refiled: the whole subtree (and any parent-cookie bookkeeping in the
            // source file) moved away with it, so there's nothing left here to cascade against.
            is StateChangeResult.Archived -> return primary
            is StateChangeResult.Plain -> primary.doc
        }
        return cascadeParentCookie(vault, settings, fileName, primaryDoc, headline, now, allowArchive) ?: primary
    }

    /** [apply] minus the parent-cookie cascade: mark [headline] with [newKeyword] and, if that
     *  makes it done-type and [allowArchive], refile it. */
    private suspend fun markAndMaybeArchive(
        vault: Vault,
        settings: GroveSettings,
        doc: OrgDocument,
        fileName: String,
        headline: OrgHeadline,
        newKeyword: String?,
        now: LocalDateTime,
        allowArchive: Boolean,
    ): StateChangeResult {
        val plainText = OrgMutations.changeKeyword(doc, headline, newKeyword, doc.keywords, now)
        val plainDoc = OrgParser.parse(plainText, doc.keywords)
        fun plain() = StateChangeResult.Plain(fileName, plainText, plainDoc)
        if (!allowArchive) return plain()

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
     * After [headline]'s TODO state changed, refresh its *immediate* parent's own `[/]`/`[%]`
     * title cookie (if it has one) to match its direct TODO-keyword children, and — same as any
     * other checklist reaching 100% — mark the parent itself done too when they all are. Only
     * ever touches this one parent, not further ancestors, mirroring
     * [OrgMutations.updateParentCookie]'s single-level rule (this also keeps it faithful to
     * vanilla org-mode, where `org-update-parent-todo-statistics` only ever updates the direct
     * parent, not a chain of ancestors). Returns null when there's no cookie to refresh, nothing
     * changed, or the parent doesn't need to transition.
     */
    private suspend fun cascadeParentCookie(
        vault: Vault,
        settings: GroveSettings,
        fileName: String,
        doc: OrgDocument,
        headline: OrgHeadline,
        now: LocalDateTime,
        allowArchive: Boolean,
    ): StateChangeResult? {
        val movedHeadline = doc.headlines.firstOrNull { it.lineIndex == headline.lineIndex } ?: return null
        val parent = doc.parent(movedHeadline) ?: return null
        val cookie = OrgMutations.refreshHeadingCookie(doc, parent) ?: return null
        val cookieDoc = if (cookie.text == doc.text) doc else OrgParser.parse(cookie.text, doc.keywords)
        if (!cookie.complete) return StateChangeResult.Plain(fileName, cookieDoc.text, cookieDoc)

        val cookieParent = cookieDoc.headlines.firstOrNull { it.lineIndex == parent.lineIndex }
            ?: return StateChangeResult.Plain(fileName, cookieDoc.text, cookieDoc)
        val doneKeyword = cookieDoc.keywords.done.firstOrNull()
        if (cookieParent.keyword == null || cookieDoc.keywords.isDone(cookieParent.keyword) || doneKeyword == null) {
            return StateChangeResult.Plain(fileName, cookieDoc.text, cookieDoc)
        }
        return markAndMaybeArchive(vault, settings, cookieDoc, fileName, cookieParent, doneKeyword, now, allowArchive)
    }

    /**
     * Re-checks [fileName]:[lineIndex]'s *current on-disk* keyword — not one captured earlier —
     * and refiles it if it's still done-type and auto-archive is enabled. Used when the mark-done
     * and the archive are deliberately split across time (see [apply]'s `allowArchive`): Read/Edit
     * mode call this once the user actually leaves the note, so re-reading state here (rather than
     * trusting a flag set at mark-done time) means changing your mind and reopening the item
     * before leaving quietly cancels the archive, with no special-casing needed.
     */
    suspend fun archiveIfStillDone(
        vault: Vault,
        settings: GroveSettings,
        fileName: String,
        lineIndex: Int,
    ): StateChangeResult.Archived? {
        if (!settings.autoArchiveDoneItems) return null
        val doc = vault.open(fileName) ?: return null
        val headline = doc.headlines.firstOrNull { it.lineIndex == lineIndex } ?: return null
        // Same checklist-style guard as apply()'s parentHasCookie: a heading whose parent tracks
        // it via a statistics cookie is never archived on its own, only as part of the parent's
        // subtree once the whole group is done — otherwise this would peel just this one heading
        // (e.g. the note the user was actually looking at) out of its still-together siblings.
        val target = doc.parent(headline)?.takeIf { OrgMutations.hasStatisticsCookie(it.title) } ?: headline
        if (target.keyword == null || !doc.keywords.isDone(target.keyword)) return null
        val archiveTarget = ArchiveLocation.resolve(doc, target, settingsFallback(settings)) ?: return null
        val write = refileSubtree(vault, doc, fileName, target, archiveTarget) ?: return null
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
