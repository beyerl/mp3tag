package de.lb.mp3tag.domain

/**
 * Storage-agnostic reference to an audio file. The production implementation
 * wraps a SAF document URI; tests use a plain value. [path] is a stable
 * display path with '/' separators (tree name + relative path).
 */
interface FileRef {
    val name: String
    val path: String
}

data class AudioProps(
    val lengthMs: Int,
    val bitrateKbps: Int,
    val sampleRateHz: Int,
    val channels: Int,
)

data class LoadedFile(
    val id: Long,
    val file: FileRef,
    val sizeBytes: Long,
    val lastModified: Long,
    val props: AudioProps?,
    val tags: TagFields,
) {
    val fileName: String get() = file.name
    val nameWithoutExtension: String get() = file.name.substringBeforeLast('.', file.name)
    val extension: String get() = file.name.substringAfterLast('.', "")
    val parentPath: String get() = file.path.substringBeforeLast('/', "")
}

sealed interface FieldEdit {
    data class Set(val values: List<String>) : FieldEdit
    data object Remove : FieldEdit
}

sealed interface CoverEdit {
    class Replace(val data: ByteArray, val mimeType: String) : CoverEdit
    data object Remove : CoverEdit
}

data class PendingEdits(
    val fields: Map<String, FieldEdit> = emptyMap(),
    val cover: CoverEdit? = null,
) {
    val isDirty: Boolean get() = fields.isNotEmpty() || cover != null

    fun withField(name: String, edit: FieldEdit): PendingEdits {
        val key = TagFields.canonical(name)
        val normalized = if (edit is FieldEdit.Set && edit.values.all { it.isEmpty() }) {
            FieldEdit.Remove
        } else {
            edit
        }
        return copy(fields = fields + (key to normalized))
    }

    companion object {
        val EMPTY = PendingEdits()
    }
}

enum class SortKey { FILENAME, TITLE, ARTIST, ALBUM, MTIME, PATH }

data class SortSpec(val key: SortKey = SortKey.PATH, val ascending: Boolean = true)
