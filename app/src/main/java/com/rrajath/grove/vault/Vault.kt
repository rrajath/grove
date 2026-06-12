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

    private val cache = LinkedHashMap<CacheKey, OrgDocument>()

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
                Notebook(entry.name, doc.headlines.size, entry.lastModified)
            }
    }

    suspend fun open(fileName: String): OrgDocument? {
        val entry = store.list().firstOrNull { it.name == fileName } ?: return null
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
     */
    suspend fun trashNotebook(name: String): Boolean {
        val ok = store.rename(name, "$name.trash")
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
