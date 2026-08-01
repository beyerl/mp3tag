package de.lb.mp3tag.domain

import de.lb.mp3tag.scripting.FieldSource

/**
 * Exposes a loaded file (with pending edits overlaid) to the scripting
 * engine, including read-only `%_technical%` placeholders.
 */
class FileFieldSource(
    private val loaded: LoadedFile,
    edits: PendingEdits?,
) : FieldSource {

    private val tags: TagFields = BatchEdit.effective(loaded.tags, edits)

    override fun values(name: String): List<String> {
        val trimmed = name.trim()
        if (trimmed.startsWith("_")) {
            return technical(trimmed.lowercase())?.let { listOf(it) } ?: emptyList()
        }
        return tags[trimmed]
    }

    override fun fieldNames(): Set<String> = tags.names + "_FILENAME"

    private fun technical(name: String): String? = when (name) {
        "_filename" -> loaded.nameWithoutExtension
        "_filename_ext" -> loaded.fileName
        "_extension" -> loaded.extension
        "_directory" -> loaded.parentPath.substringAfterLast('/', loaded.parentPath).ifEmpty { null }
        "_folderpath" -> loaded.parentPath.ifEmpty { null }
        "_path" -> loaded.file.path
        "_size" -> loaded.sizeBytes.toString()
        "_bitrate" -> loaded.props?.bitrateKbps?.toString()
        "_samplerate" -> loaded.props?.sampleRateHz?.toString()
        "_channels" -> loaded.props?.channels?.toString()
        "_length" -> loaded.props?.let {
            val totalSeconds = it.lengthMs / 1000
            "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
        }
        "_length_seconds" -> loaded.props?.let { (it.lengthMs / 1000).toString() }
        else -> null
    }
}
