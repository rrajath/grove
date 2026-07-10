package com.rrajath.grove.vault

import com.rrajath.grove.org.OrgDocument
import com.rrajath.grove.org.OrgKeywords
import com.rrajath.grove.org.OrgParser

/** A notebook = one .org file in the vault. */
data class Notebook(
    val fileName: String,
    val noteCount: Int,
    val lastModified: Long,
) {
    val displayName: String get() = fileName.removeSuffix(".org")
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

    /** Create an empty notebook file. Returns false if the name is taken. */
    suspend fun createNotebook(name: String): Boolean {
        val fileName = if (name.endsWith(".org")) name else "$name.org"
        if (store.exists(fileName)) return false
        return store.create(fileName)
    }

    suspend fun renameNotebook(oldName: String, newName: String): Boolean {
        val target = if (newName.endsWith(".org")) newName else "$newName.org"
        val ok = store.rename(oldName, target)
        if (ok) cache.keys.removeAll { it.name == oldName }
        return ok
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
