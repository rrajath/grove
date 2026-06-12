package com.rrajath.grove.vault

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Storage Access Framework implementation over a persisted tree URI
 * (ACTION_OPEN_DOCUMENT_TREE). Listing uses a single child-documents cursor
 * query — cheap even for large vaults. Writes use mode "wt": without truncate,
 * writing shorter content than the existing file leaves trailing garbage.
 */
class SafFileStore(
    private val context: Context,
    private val treeUri: Uri,
) : FileStore {

    private val resolver get() = context.contentResolver
    private var docIds = mutableMapOf<String, String>()

    override suspend fun list(): List<FileEntry> = withContext(Dispatchers.IO) {
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)
        val entries = mutableListOf<FileEntry>()
        val ids = mutableMapOf<String, String>()
        resolver.query(
            childrenUri,
            arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            ),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val docId = cursor.getString(0)
                val name = cursor.getString(1) ?: continue
                val mime = cursor.getString(4)
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) continue
                ids[name] = docId
                entries.add(FileEntry(name, cursor.getLong(2), cursor.getLong(3)))
            }
        }
        docIds = ids
        entries.sortedBy { it.name }
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
        val treeDocUri = DocumentsContract.buildDocumentUriUsingTree(
            treeUri, DocumentsContract.getTreeDocumentId(treeUri)
        )
        // "text/plain" makes providers append .txt to names without that
        // extension ("test.org" → "test.org.txt"). Octet-stream has no mapped
        // extension, so the name is kept as-is on stock providers.
        var created = DocumentsContract.createDocument(
            resolver, treeDocUri, "application/octet-stream", name,
        ) ?: return@withContext false
        // Belt and braces: if the provider still mangled the name, rename back.
        val actualName = displayNameOf(created)
        if (actualName != null && actualName != name) {
            DocumentsContract.renameDocument(resolver, created, name)?.let { created = it }
        }
        docIds[name] = DocumentsContract.getDocumentId(created)
        true
    }

    private fun displayNameOf(uri: Uri): String? =
        resolver.query(
            uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    override suspend fun rename(oldName: String, newName: String): Boolean =
        withContext(Dispatchers.IO) {
            if (documentUri(newName) != null) return@withContext false
            val uri = documentUri(oldName) ?: return@withContext false
            // Providers may throw instead of returning null (unsupported op,
            // stale doc id after an external rename) — treat both as failure.
            val renamed = runCatching {
                DocumentsContract.renameDocument(resolver, uri, newName)
            }.getOrNull() != null
            if (renamed) {
                docIds.remove(oldName)
                docIds.remove(newName)
            }
            renamed
        }



    override suspend fun delete(name: String): Boolean = withContext(Dispatchers.IO) {
        val uri = documentUri(name) ?: return@withContext false
        val ok = runCatching { DocumentsContract.deleteDocument(resolver, uri) }
            .getOrDefault(false)
        if (ok) docIds.remove(name)
        ok
    }

    override suspend fun exists(name: String): Boolean = documentUri(name) != null

    private suspend fun documentUri(name: String): Uri? {
        if (name !in docIds) list()
        val docId = docIds[name] ?: return null
        return DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
    }
}
