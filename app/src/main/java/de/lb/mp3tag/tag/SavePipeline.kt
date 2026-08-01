package de.lb.mp3tag.tag

import com.kyant.taglib.Picture
import de.lb.mp3tag.domain.BatchEdit
import de.lb.mp3tag.domain.CoverEdit
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.PendingEdits
import de.lb.mp3tag.storage.SafFileRef
import de.lb.mp3tag.storage.SafStorage

data class SaveError(val name: String, val message: String)

data class SaveOutcome(
    val saved: List<LoadedFile>,
    val errors: List<SaveError>,
)

class SavePipeline(
    private val tagIo: TagIo,
    private val storage: SafStorage,
    private val repository: TagRepository,
) {

    /**
     * Writes pending edits to disk, file by file. Failures never abort the
     * batch; each is collected with its reason. Saved entries are re-read from
     * disk so callers can swap them into the session.
     */
    suspend fun save(
        files: List<LoadedFile>,
        edits: Map<Long, PendingEdits>,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> },
    ): SaveOutcome {
        val dirty = files.filter { edits[it.id]?.isDirty == true }
        val saved = ArrayList<LoadedFile>(dirty.size)
        val errors = ArrayList<SaveError>()

        dirty.forEachIndexed { index, loaded ->
            val fileEdits = edits.getValue(loaded.id)
            try {
                saveOne(loaded, fileEdits)
                saved += repository.reload(loaded)
            } catch (e: Exception) {
                errors += SaveError(loaded.fileName, e.message ?: e.javaClass.simpleName)
            }
            onProgress(index + 1, dirty.size)
        }
        return SaveOutcome(saved, errors)
    }

    private suspend fun saveOne(loaded: LoadedFile, edits: PendingEdits) {
        val ref = loaded.file as SafFileRef
        val meta = storage.queryMeta(ref.documentUri)
            ?: throw SaveException("File no longer exists")
        if (meta.second != loaded.lastModified) {
            throw SaveException("File was modified by another app since it was loaded")
        }

        if (edits.fields.isNotEmpty()) {
            val merged = BatchEdit.effective(loaded.tags, edits)
            if (!tagIo.writeProperties(ref.documentUri, merged.toPropertyMap())) {
                throw SaveException("TagLib failed to write tags")
            }
        }

        when (val cover = edits.cover) {
            is CoverEdit.Replace -> {
                val others = tagIo.readPictures(ref.documentUri).filter { it.pictureType != FRONT_COVER }
                val newFront = Picture(
                    data = cover.data,
                    description = "",
                    pictureType = FRONT_COVER,
                    mimeType = cover.mimeType,
                )
                if (!tagIo.writePictures(ref.documentUri, (others + newFront).toTypedArray())) {
                    throw SaveException("TagLib failed to write cover art")
                }
            }
            CoverEdit.Remove -> {
                val others = tagIo.readPictures(ref.documentUri).filter { it.pictureType != FRONT_COVER }
                if (!tagIo.writePictures(ref.documentUri, others.toTypedArray())) {
                    throw SaveException("TagLib failed to remove cover art")
                }
            }
            null -> Unit
        }
    }

    private class SaveException(message: String) : Exception(message)

    companion object {
        const val FRONT_COVER = "Front Cover"
    }
}
