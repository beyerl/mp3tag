package de.lb.mp3tag.storage

import android.content.ContentResolver
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import de.lb.mp3tag.domain.FileRef
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException

/** A file inside a granted SAF tree. */
data class SafFileRef(
    val documentUri: Uri,
    val treeUri: Uri,
    val parentDocumentId: String,
    override val name: String,
    override val path: String,
) : FileRef

/**
 * All storage access goes through user-granted SAF document trees — the app
 * holds no global storage permission. Enumeration uses DocumentsContract
 * child queries directly (much faster than DocumentFile).
 */
class SafStorage(private val resolver: ContentResolver) {

    data class Tree(val uri: Uri, val name: String)

    data class Dir(
        val treeUri: Uri,
        val documentId: String,
        val name: String,
        /** Display path: tree name + relative path, '/'-separated. */
        val path: String,
    )

    data class AudioEntry(val ref: SafFileRef, val sizeBytes: Long, val lastModified: Long)

    fun persistedTrees(): List<Tree> =
        resolver.persistedUriPermissions
            .filter { it.isReadPermission && it.isWritePermission }
            .map { Tree(it.uri, displayName(rootDocumentUri(it.uri)) ?: fallbackName(it.uri)) }
            .sortedBy { it.name.lowercase() }

    fun takeTree(uri: Uri) {
        resolver.takePersistableUriPermission(uri, PERSIST_FLAGS)
    }

    fun releaseTree(uri: Uri) {
        try {
            resolver.releasePersistableUriPermission(uri, PERSIST_FLAGS)
        } catch (_: SecurityException) {
            // Already revoked.
        }
    }

    fun rootDir(tree: Tree): Dir =
        Dir(tree.uri, DocumentsContract.getTreeDocumentId(tree.uri), tree.name, tree.name)

    suspend fun subdirectories(dir: Dir): List<Dir> = withContext(Dispatchers.IO) {
        queryChildren(dir.treeUri, dir.documentId)
            .filter { it.mimeType == DocumentsContract.Document.MIME_TYPE_DIR }
            .map { Dir(dir.treeUri, it.documentId, it.name, "${dir.path}/${it.name}") }
            .sortedBy { it.name.lowercase() }
    }

    /** Breadth-first enumeration of audio files under [dir], by extension. */
    suspend fun enumerateAudio(dir: Dir, recursive: Boolean): List<AudioEntry> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<AudioEntry>()
            val queue = ArrayDeque<Dir>()
            queue.add(dir)
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                for (child in queryChildren(current.treeUri, current.documentId)) {
                    if (child.mimeType == DocumentsContract.Document.MIME_TYPE_DIR) {
                        if (recursive && !child.name.startsWith(".")) {
                            queue.add(
                                Dir(current.treeUri, child.documentId, child.name, "${current.path}/${child.name}"),
                            )
                        }
                    } else if (child.name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS) {
                        val documentUri =
                            DocumentsContract.buildDocumentUriUsingTree(current.treeUri, child.documentId)
                        result += AudioEntry(
                            ref = SafFileRef(
                                documentUri = documentUri,
                                treeUri = current.treeUri,
                                parentDocumentId = current.documentId,
                                name = child.name,
                                path = "${current.path}/${child.name}",
                            ),
                            sizeBytes = child.size,
                            lastModified = child.lastModified,
                        )
                    }
                }
            }
            result.sortedBy { it.ref.path.lowercase() }
        }

    /** Size and mtime of a single document, or null if it is gone. */
    suspend fun queryMeta(documentUri: Uri): Pair<Long, Long>? = withContext(Dispatchers.IO) {
        try {
            resolver.query(
                documentUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_SIZE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                ),
                null, null, null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) to cursor.getLong(1) else null
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Renames in place; case-only renames go through a temporary name. */
    suspend fun rename(ref: SafFileRef, newName: String): SafFileRef = withContext(Dispatchers.IO) {
        if (newName.isBlank() || newName.contains('/') || newName.contains('\\')) {
            throw IOException("Invalid file name")
        }
        if (newName == ref.name) return@withContext ref
        val caseOnly = newName.equals(ref.name, ignoreCase = true)
        if (!caseOnly) {
            val siblings = queryChildren(ref.treeUri, ref.parentDocumentId)
            if (siblings.any { it.name.equals(newName, ignoreCase = true) }) {
                throw IOException("A file named “$newName” already exists")
            }
        }
        val finalUri = if (caseOnly) {
            val tempName = ".rename-tmp-$newName"
            val tempUri = DocumentsContract.renameDocument(resolver, ref.documentUri, tempName)
                ?: throw IOException("Rename failed")
            DocumentsContract.renameDocument(resolver, tempUri, newName)
                ?: throw IOException("Rename failed")
        } else {
            DocumentsContract.renameDocument(resolver, ref.documentUri, newName)
                ?: throw IOException("Rename failed")
        }
        ref.copy(
            documentUri = finalUri,
            name = newName,
            path = ref.path.substringBeforeLast('/') + "/" + newName,
        )
    }

    suspend fun delete(ref: SafFileRef): Unit = withContext(Dispatchers.IO) {
        if (!DocumentsContract.deleteDocument(resolver, ref.documentUri)) {
            throw IOException("Delete failed")
        }
    }

    /** Creates or overwrites a text file in [dir] (UTF-8). Returns its display name. */
    suspend fun writeTextFile(
        dir: Dir,
        displayName: String,
        mimeType: String,
        content: String,
    ): String = withContext(Dispatchers.IO) {
        val existing = queryChildren(dir.treeUri, dir.documentId)
            .firstOrNull { it.name.equals(displayName, ignoreCase = true) }
        val uri = if (existing != null) {
            DocumentsContract.buildDocumentUriUsingTree(dir.treeUri, existing.documentId)
        } else {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(dir.treeUri, dir.documentId)
            DocumentsContract.createDocument(resolver, parentUri, mimeType, displayName)
                ?: throw IOException("Could not create $displayName")
        }
        // "wt" truncates, so overwriting an existing longer file leaves no tail.
        resolver.openOutputStream(uri, "wt")?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
        } ?: throw IOException("Could not write $displayName")
        displayName
    }

    private data class Child(
        val documentId: String,
        val name: String,
        val mimeType: String,
        val size: Long,
        val lastModified: Long,
    )

    private fun queryChildren(treeUri: Uri, parentDocumentId: String): List<Child> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        return try {
            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                buildList {
                    while (cursor.moveToNext()) {
                        add(
                            Child(
                                documentId = cursor.getString(0),
                                name = cursor.getString(1) ?: continue,
                                mimeType = cursor.getString(2) ?: "",
                                size = cursor.getLong(3),
                                lastModified = cursor.getLong(4),
                            ),
                        )
                    }
                }
            } ?: emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun rootDocumentUri(treeUri: Uri): Uri =
        DocumentsContract.buildDocumentUriUsingTree(treeUri, DocumentsContract.getTreeDocumentId(treeUri))

    private fun displayName(documentUri: Uri): String? = try {
        resolver.query(
            documentUri,
            arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
            null, null, null,
        )?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
    } catch (_: Exception) {
        null
    }

    private fun fallbackName(treeUri: Uri): String =
        DocumentsContract.getTreeDocumentId(treeUri)
            .substringAfterLast(':')
            .substringAfterLast('/')
            .ifEmpty { "Folder" }

    companion object {
        private const val PERSIST_FLAGS =
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        val AUDIO_EXTENSIONS = setOf(
            "mp3", "m4a", "m4b", "mp4", "flac", "ogg", "oga", "opus", "spx",
            "wav", "aif", "aiff", "aifc", "wma", "ape", "wv", "mpc", "tta",
            "tak", "dsf", "dff", "ofr", "ofs",
        )
    }
}
