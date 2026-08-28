package com.rrajath.grove.vault

import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser

/**
 * A notebook = one .org file in the vault. [fileName] is the identity: a
 * vault-relative path like `projects/acme.org` (bare name for a root file).
 */
data class Notebook(
    val fileName: String,
    val noteCount: Int,
    val lastModified: Long,
) {
    /** Vault-relative directory holding this file; "" for a root-level file. */
    val dir: String get() = fileName.substringBeforeLast('/', "")

    /** File name without its directory or `.org` extension. */
    val displayName: String get() = fileName.substringAfterLast('/').removeSuffix(".org")
}

/** Join a vault-relative directory and a file name into a path. "" dir → bare name. */
fun vaultPath(dir: String, name: String): String =
    if (dir.isBlank()) name else "${dir.trim('/')}/$name"

/**
 * Matches an externally-opened .org file (tapped in a file manager, or
 * launched via a VIEW/EDIT intent per the manifest's file-open intent-filter)
 * against a notebook already indexed in this vault. The incoming URI usually
 * comes from a different `content://` authority than our own SAF tree grant,
 * so there's nothing to match on except the file's name; compared
 * case-insensitively since providers don't agree on casing. Pure Kotlin so
 * this is JVM-unit-testable independent of the Uri/ContentResolver plumbing
 * that resolves [requestedFileName] on the Android side.
 *
 * Now that the vault is a tree, [requestedFileName] (usually a bare name from a
 * foreign provider) can match several notebooks in different folders. Order of
 * preference: an exact vault-relative-path match, then a unique basename match,
 * then — if the basename is ambiguous — the first such notebook in list order.
 */
fun matchOpenedFileToNotebook(requestedFileName: String, notebooks: List<Notebook>): Notebook? {
    notebooks.firstOrNull { it.fileName.equals(requestedFileName, ignoreCase = true) }?.let { return it }
    val wantedBase = requestedFileName.substringAfterLast('/')
    return notebooks.firstOrNull {
        it.fileName.substringAfterLast('/').equals(wantedBase, ignoreCase = true)
    }
}

/**
 * Vault facade over a [FileStore]: lists notebooks (applying ignore rules),
 * parses documents, creates new notebooks. Pure Kotlin; android code supplies
 * the FileStore. Parses are cached by (name, mtime, size).
 */
class Vault(
    private val store: FileStore,
    private val keywords: OrgKeywords = OrgKeywords.DEFAULT,
) {
    private data class CacheKey(val name: String, val mtime: Long, val size: Long)

    // accessOrder = true → iteration starts at the least-recently-used entry,
    // so the size-cap eviction in document() drops the LRU parse, not the oldest.
    private val cache = LinkedHashMap<CacheKey, OrgDocument>(16, 0.75f, true)

    suspend fun notebooks(): List<Notebook> {
        val entries = store.list()
        val ignore = entries.firstOrNull { it.name == IgnoreRules.FILE_NAME }
            ?.let { IgnoreRules(store.read(it.name)) }
            ?: IgnoreRules("")
        return entries
            .filter {
                it.name.endsWith(".org") &&
                        !it.name.contains(".sync-conflict-") &&
                        !ignore.isIgnored(it.name)
            }
            .map { entry ->
                val doc = document(entry)
                Notebook(entry.name, doc.headlines.count { it.level == 1 }, entry.lastModified)
            }
    }

    /** Current revision marker ("mtime:size") of a file, or null if missing. */
    suspend fun revision(fileName: String): String? =
        store.stat(fileName)?.let { "${it.lastModified}:${it.size}" }

    suspend fun open(fileName: String): OrgDocument? {
        val entry = store.stat(fileName) ?: return null
        return document(entry)
    }

    /**
     * Create an empty notebook file, in [dir] (a vault-relative directory, ""
     * for the vault root). [name] may itself contain `/` segments, which are
     * appended under [dir]. Missing directories are created. Returns false if
     * the resulting path is already taken.
     */
    suspend fun createNotebook(name: String, dir: String = ""): Boolean {
        val leaf = if (name.endsWith(".org")) name else "$name.org"
        val fileName = vaultPath(dir, leaf)
        if (store.exists(fileName)) return false
        return store.create(fileName)
    }

    /**
     * Rename and/or move a notebook. [newName] may be a bare name (kept in the
     * same directory) or a full vault-relative path (moved). Returns false if
     * the target exists or the source is missing.
     */
    suspend fun renameNotebook(oldName: String, newName: String): Boolean {
        val target = if (newName.endsWith(".org")) newName else "$newName.org"
        val ok = store.rename(oldName, target)
        if (ok) cache.keys.removeAll { it.name == oldName }
        return ok
    }

    /**
     * Move a notebook to a different directory, keeping its file name. [newDir]
     * is a vault-relative directory ("" for the root). Returns the new path on
     * success, or null if the move failed or was a no-op.
     */
    suspend fun moveNotebook(path: String, newDir: String): String? {
        val leaf = path.substringAfterLast('/')
        val target = vaultPath(newDir, leaf)
        if (target == path) return null
        val ok = store.rename(path, target)
        if (ok) cache.keys.removeAll { it.name == path }
        return if (ok) target else null
    }

    /**
     * Soft delete: rename to `<name>.trash` so the file no longer lists as a
     * notebook but stays in the synced folder, recoverable from any device.
     * Picks a fresh `.trash-N` name when one already exists (e.g. the notebook
     * was deleted, recreated, and deleted again), and falls back to a hard
     * delete if the provider refuses to rename.
     */
    suspend fun trashNotebook(name: String): Boolean {
        var trashName = "$name.trash"
        var n = 2
        while (store.exists(trashName)) trashName = "$name.trash-${n++}"
        val ok = store.rename(name, trashName) || store.delete(name)
        if (ok) cache.keys.removeAll { it.name == name }
        return ok
    }

    suspend fun save(fileName: String, content: String) {
        store.write(fileName, content)
        cache.keys.removeAll { it.name == fileName }
    }

    private suspend fun document(entry: FileEntry): OrgDocument {
        val key = CacheKey(entry.name, entry.lastModified, entry.size)
        cache[key]?.let { return it }
        val doc = OrgParser.parse(store.read(entry.name), keywords)
        // Drop stale parses of the same file, cap total cache size.
        cache.keys.removeAll { it.name == entry.name }
        if (cache.size > 64) cache.remove(cache.keys.first())
        cache[key] = doc
        return doc
    }
}
