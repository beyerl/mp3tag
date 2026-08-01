package de.lb.mp3tag.tag

import android.net.Uri
import com.kyant.taglib.Picture
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.PendingEdits
import de.lb.mp3tag.domain.TagFields
import de.lb.mp3tag.storage.SafFileRef
import de.lb.mp3tag.storage.SafStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
private data class UndoPicture(
    val file: String,
    val description: String,
    val pictureType: String,
    val mimeType: String,
)

@Serializable
private data class UndoEntry(
    val documentUri: String,
    val name: String,
    val fields: Map<String, List<String>>,
    /** null = pictures were not touched by the saved batch. */
    val pictures: List<UndoPicture>? = null,
)

@Serializable
private data class UndoBatch(val entries: List<UndoEntry>)

data class UndoResult(val restoredUris: List<String>, val errors: List<SaveError>)

/**
 * Snapshots tag state before each save batch into bounded on-disk history
 * (JSON + picture sidecar files under cacheDir), and restores the most
 * recent batch on undo. Files are addressed by their persisted SAF document
 * URIs. Renames/deletes are not undoable.
 */
class UndoStore(
    private val baseDir: File,
    private val tagIo: TagIo,
    private val storage: SafStorage,
    private val maxDepth: Int = 10,
) {
    private val json = Json { ignoreUnknownKeys = true }

    fun batchCount(): Int = batchDirs().size

    /** Captures the pre-save state of every dirty file in [edits]. */
    suspend fun snapshot(files: List<LoadedFile>, edits: Map<Long, PendingEdits>) {
        val dirty = files.filter { edits[it.id]?.isDirty == true }
        if (dirty.isEmpty()) return
        withContext(Dispatchers.IO) {
            val batchDir = File(baseDir, System.currentTimeMillis().toString())
            batchDir.mkdirs()
            val entries = dirty.mapIndexed { index, loaded ->
                val ref = loaded.file as SafFileRef
                val coverTouched = edits[loaded.id]?.cover != null
                val pictures = if (coverTouched) {
                    runCatching { tagIo.readPictures(ref.documentUri) }.getOrDefault(emptyArray())
                        .mapIndexed { picIndex, picture ->
                            val sidecar = File(batchDir, "$index-$picIndex.img")
                            sidecar.writeBytes(picture.data)
                            UndoPicture(
                                file = sidecar.name,
                                description = picture.description,
                                pictureType = picture.pictureType,
                                mimeType = picture.mimeType,
                            )
                        }
                } else {
                    null
                }
                UndoEntry(
                    documentUri = ref.documentUri.toString(),
                    name = loaded.fileName,
                    fields = loaded.tags.asMap(),
                    pictures = pictures,
                )
            }
            File(batchDir, "batch.json").writeText(json.encodeToString(UndoBatch(entries)))
            prune()
        }
    }

    /** Restores the most recent batch. Returns restored document URIs and per-file errors. */
    suspend fun undoLast(): UndoResult {
        val batchDir = batchDirs().lastOrNull() ?: return UndoResult(emptyList(), emptyList())
        val batch = withContext(Dispatchers.IO) {
            runCatching {
                json.decodeFromString<UndoBatch>(File(batchDir, "batch.json").readText())
            }.getOrNull()
        } ?: run {
            withContext(Dispatchers.IO) { batchDir.deleteRecursively() }
            return UndoResult(emptyList(), emptyList())
        }

        val restored = ArrayList<String>()
        val errors = ArrayList<SaveError>()
        for (entry in batch.entries) {
            val uri = Uri.parse(entry.documentUri)
            try {
                if (storage.queryMeta(uri) == null) {
                    throw IllegalStateException("File no longer exists")
                }
                val tags = TagFields.from(entry.fields)
                if (!tagIo.writeProperties(uri, tags.toPropertyMap())) {
                    throw IllegalStateException("TagLib failed to restore tags")
                }
                entry.pictures?.let { pictures ->
                    val restoredPictures = pictures.map { pic ->
                        Picture(
                            data = File(batchDir, pic.file).readBytes(),
                            description = pic.description,
                            pictureType = pic.pictureType,
                            mimeType = pic.mimeType,
                        )
                    }
                    if (!tagIo.writePictures(uri, restoredPictures.toTypedArray())) {
                        throw IllegalStateException("TagLib failed to restore cover art")
                    }
                }
                restored += entry.documentUri
            } catch (e: Exception) {
                errors += SaveError(entry.name, e.message ?: e.javaClass.simpleName)
            }
        }
        withContext(Dispatchers.IO) { batchDir.deleteRecursively() }
        return UndoResult(restored, errors)
    }

    private fun batchDirs(): List<File> =
        baseDir.listFiles()
            ?.filter { it.isDirectory }
            ?.sortedBy { it.name.toLongOrNull() ?: 0L }
            ?: emptyList()

    private fun prune() {
        val dirs = batchDirs()
        if (dirs.size > maxDepth) {
            dirs.take(dirs.size - maxDepth).forEach { it.deleteRecursively() }
        }
    }
}
