package com.rrajath.grove.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework implementation over a persisted tree URI
 * (ACTION_OPEN_DOCUMENT_TREE).
 *
 * The vault is a tree: [list] walks every subdirectory (one child-documents
 * cursor query per directory), skipping dot-directories. Names are
 * vault-relative paths with `/` separators. Writes use mode "wt": without
 * truncate, writing shorter content than the existing file leaves trailing
 * garbage.
 *
 * Path -> document-id caches (files and directories) are warmed by [list] and
 * rebuilt on a cache miss. Recursive listing costs one query per directory;
 * fine for typical org vaults, a watch item for very large trees.
 */
class SafFileStore(
    private val context: Context,
    private val treeUri: Uri,
) : FileStore {

    private val resolver get() = context.contentResolver
    private val rootDocId get() = DocumentsContract.getTreeDocumentId(treeUri)

    /** relative file path -> document id */
    private var docIds = mutableMapOf<String, String>()

    /** relative directory path ("" == vault root) -> document id */
    private var dirDocIds = mutableMapOf<String, String>()

    private val projection = arrayOf(
        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        DocumentsContract.Document.COLUMN_SIZE,
        DocumentsContract.Document.COLUMN_MIME_TYPE,
    )

    override suspend fun list(): List<FileEntry> = withContext(Dispatchers.IO) {
        val files = mutableMapOf<String, String>()
        val dirs = mutableMapOf("" to rootDocId)
        val entries = mutableListOf<FileEntry>()

        val queue = ArrayDeque<Pair<String, String>>() // relativeDir to docId
        queue.add("" to rootDocId)
        while (queue.isNotEmpty()) {
            val (dir, docId) = queue.removeFirst()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    val childDocId = cursor.getString(0)
                    val name = cursor.getString(1) ?: continue
                    val mime = cursor.getString(4)
                    val path = if (dir.isEmpty()) name else "$dir/$name"
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (isSkippedVaultDir(name)) continue
                        dirs[path] = childDocId
                        queue.add(path to childDocId)
                    } else {
                        files[path] = childDocId
                        entries.add(FileEntry(path, cursor.getLong(2), cursor.getLong(3)))
                    }
                }
            }
        }
        docIds = files
        dirDocIds = dirs
        entries.sortedBy { it.name }
    }

    override suspend fun stat(name: String): FileEntry? = withContext(Dispatchers.IO) {
        val uri = documentUri(name) ?: return@withContext null
        resolver.query(
            uri,
            arrayOf(
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
            ),
            null, null, null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) FileEntry(name, cursor.getLong(0), cursor.getLong(1)) else null
        }
    }

    override suspend fun read(name: String): String = withContext(Dispatchers.IO) {
        val uri = documentUri(name) ?: error("File not found in vault: $name")
        resolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Cannot open $name")
    }

    override suspend fun write(name: String, content: String): Unit = withContext(Dispatchers.IO) {
        val uri = documentUri(name) ?: error("File not found in vault: $name")
        // "wt" = write + truncate. Plain "w" does NOT truncate on all providers.
        resolver.openOutputStream(uri, "wt")?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
            ?: error("Cannot write $name")
    }

    override suspend fun create(name: String): Boolean = withContext(Dispatchers.IO) {
        if (documentUri(name) != null) return@withContext false
        val (dir, leaf) = splitPath(name)
        val parentDocId = resolveDir(dir, createMissing = true) ?: return@withContext false
        val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, parentDocId)
        // "application/octet-stream" has no mapped extension, so stock providers
        // keep the name as-is ("text/plain" would make "test.org" -> "test.org.txt").
        var created = DocumentsContract.createDocument(
            resolver, parentUri, "application/octet-stream", leaf,
        ) ?: return@withContext false
        val actualName = displayNameOf(created)
        if (actualName != null && actualName != leaf) {
            DocumentsContract.renameDocument(resolver, created, leaf)?.let { created = it }
        }
        docIds[name] = DocumentsContract.getDocumentId(created)
        true
    }

    override suspend fun rename(oldName: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (oldName == newName) return@withContext false
            if (documentUri(newName) != null) return@withContext false
            val uri = documentUri(oldName) ?: return@withContext false
            val (oldDir, _) = splitPath(oldName)
            val (newDir, newLeaf) = splitPath(newName)

            val result: Uri? = if (oldDir == newDir) {
                runCatching { DocumentsContract.renameDocument(resolver, uri, newLeaf) }.getOrNull()
            } else {
                moveAcrossDirs(uri, oldName, oldDir, newDir, newLeaf, newName)
            }

            if (result != null) {
                docIds.remove(oldName)
                docIds.remove(newName)
                true
            } else {
                false
            }
        }

    /**
     * Cross-directory move via [DocumentsContract.moveDocument], falling back to
     * copy + delete for providers that refuse it. Returns a non-null URI on
     * success.
     */
    private suspend fun moveAcrossDirs(
        uri: Uri,
        oldName: String,
        oldDir: String,
        newDir: String,
        newLeaf: String,
        newName: String,
    ): Uri? {
        val sourceParentId = resolveDir(oldDir, createMissing = false) ?: return null
        val targetParentId = resolveDir(newDir, createMissing = true) ?: return null
        val sourceParentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, sourceParentId)
        val targetParentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, targetParentId)

        val moved = runCatching {
            DocumentsContract.moveDocument(resolver, uri, sourceParentUri, targetParentUri)
        }.getOrNull()

        if (moved == null) {
            // Provider refused moveDocument: copy the bytes then delete the source.
            val content = runCatching { read(oldName) }.getOrNull() ?: return null
            if (!create(newName)) return null
            write(newName, content)
            return if (delete(oldName)) documentUri(newName) else null
        }

        val currentName = displayNameOf(moved)
        if (currentName != null && currentName != newLeaf) {
            runCatching { DocumentsContract.renameDocument(resolver, moved, newLeaf) }
                .getOrNull()?.let { return it }
        }
        return moved
    }

    override suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        val uri = documentUri(name) ?: return@withContext false
        val ok = runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            .getOrDefault(false)
        if (ok) docIds.remove(name)
        ok
    }

    override suspend fun exists(name: String): Boolean = documentUri(name) != null

    override suspend fun pruneEmptyDirs(dir: String): Unit = withContext(Dispatchers.IO) {
        val trimmed = dir.trim('/')
        if (trimmed.isEmpty()) return@withContext
        if (dirDocIds.keys.none { it == trimmed || it.startsWith("$trimmed/") }) list()

        // Descendants (and the dir itself) deepest-first, so an emptied parent
        // whose only child was an also-empty subdirectory still collapses.
        dirDocIds.keys
            .filter { it == trimmed || it.startsWith("$trimmed/") }
            .sortedByDescending { path -> path.count { it == '/' } }
            .forEach { removeDirIfEmpty(it) }

        var current = trimmed.substringBeforeLast('/', "")
        while (current.isNotEmpty() && removeDirIfEmpty(current)) {
            current = current.substringBeforeLast('/', "")
        }
    }

    /** Deletes [path] if it has no child documents. Returns whether it was removed. */
    private fun removeDirIfEmpty(path: String): Boolean {
        val docId = dirDocIds[path] ?: return false
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
        val empty = resolver.query(
            childrenUri,
            arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { !it.moveToFirst() } ?: false
        if (!empty) return false
        val uri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val removed = runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            .getOrDefault(false)
        if (removed) dirDocIds.remove(path)
        return removed
    }

    private fun displayNameOf(uri: Uri): String? =
        resolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private suspend fun documentUri(name: String): Uri? {
        if (name !in docIds) list()
        val docId = docIds[name] ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }

    /**
     * Document id for a relative directory path, walking (and optionally
     * creating) one segment at a time. Returns the tree root id for "".
     */
    private suspend fun resolveDir(dir: String, createMissing: Boolean): String? {
        if (dir.isEmpty()) return rootDocId
        dirDocIds[dir]?.let { return it }
        list()
        dirDocIds[dir]?.let { return it }
        if (!createMissing) return null

        var currentPath = ""
        var currentDocId = rootDocId
        for (segment in dir.split('/')) {
            val nextPath = if (currentPath.isEmpty()) segment else "$currentPath/$segment"
            currentDocId = dirDocIds[nextPath] ?: run {
                val parentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, currentDocId)
                val newDir = DocumentsContract.createDocument(
                    resolver, parentUri, DocumentsContract.Document.MIME_TYPE_DIR, segment,
                ) ?: return null
                DocumentsContract.getDocumentId(newDir).also { dirDocIds[nextPath] = it }
            }
            currentPath = nextPath
        }
        return currentDocId
    }

    private fun splitPath(path: String): Pair<String, String> {
        val i = path.lastIndexOf('/')
        return if (i < 0) "" to path else path.substring(0, i) to path.substring(i + 1)
    }
}
