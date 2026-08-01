package de.lb.mp3tag.storage

import de.lb.mp3tag.domain.LoadedFile

/**
 * Writes m3u8 playlists (UTF-8, #EXTM3U/#EXTINF) into a directory of the
 * session's tree, with entries relative to that directory — portable
 * playlists, since SAF has no absolute paths.
 */
class PlaylistGenerator(private val storage: SafStorage) {

    suspend fun write(dir: SafStorage.Dir, baseName: String, files: List<LoadedFile>): String {
        val content = buildString {
            append("#EXTM3U\n")
            for (file in files) {
                val seconds = (file.props?.lengthMs ?: 0) / 1000
                val artist = file.tags.joined("ARTIST")
                val title = file.tags.joined("TITLE")
                val label = listOf(artist, title)
                    .filter { it.isNotEmpty() }
                    .joinToString(" - ")
                    .ifEmpty { file.nameWithoutExtension }
                append("#EXTINF:$seconds,$label\n")
                append(relativePath(dir, file)).append('\n')
            }
        }
        return storage.writeTextFile(dir, sanitize(baseName) + ".m3u8", "audio/x-mpegurl", content)
    }

    private fun relativePath(dir: SafStorage.Dir, file: LoadedFile): String {
        val prefix = dir.path + "/"
        return if (file.file.path.startsWith(prefix)) {
            file.file.path.removePrefix(prefix)
        } else {
            file.fileName
        }
    }

    private fun sanitize(name: String): String = name
        .map { c -> if (c in ILLEGAL) '_' else c }
        .joinToString("")
        .trim()
        .ifEmpty { "playlist" }

    private companion object {
        val ILLEGAL = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
    }
}
