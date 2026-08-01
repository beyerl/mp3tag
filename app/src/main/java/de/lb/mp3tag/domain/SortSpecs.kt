package de.lb.mp3tag.domain

object SortSpecs {

    fun comparator(spec: SortSpec): Comparator<LoadedFile> {
        val base: Comparator<LoadedFile> = when (spec.key) {
            SortKey.FILENAME ->
                compareBy<LoadedFile, String>(caseInsensitive) { it.fileName }
            SortKey.TITLE ->
                compareBy<LoadedFile, String>(caseInsensitive) { it.tags.joined("TITLE") }
            SortKey.ARTIST ->
                compareBy<LoadedFile, String>(caseInsensitive) { it.tags.joined("ARTIST") }
                    .thenBy(caseInsensitive) { it.tags.joined("ALBUM") }
                    .thenBy { trackNumber(it.tags) }
            SortKey.ALBUM ->
                compareBy<LoadedFile, String>(caseInsensitive) { it.tags.joined("ALBUM") }
                    .thenBy { discNumber(it.tags) }
                    .thenBy { trackNumber(it.tags) }
            SortKey.MTIME ->
                compareBy<LoadedFile> { it.lastModified }
            SortKey.PATH ->
                compareBy<LoadedFile, String>(caseInsensitive) { it.file.path }
        }
        val tieBreak = base.thenBy(caseInsensitive) { it.file.path }
        return if (spec.ascending) tieBreak else tieBreak.reversed()
    }

    /** Parses the leading number of values like "3", "03", or "3/12". */
    fun leadingNumber(value: String?): Int {
        if (value.isNullOrEmpty()) return Int.MAX_VALUE
        val digits = value.takeWhile { it.isDigit() }
        return digits.toIntOrNull() ?: Int.MAX_VALUE
    }

    private fun trackNumber(tags: TagFields): Int = leadingNumber(tags.first("TRACKNUMBER"))

    private fun discNumber(tags: TagFields): Int = leadingNumber(tags.first("DISCNUMBER"))

    private val caseInsensitive: Comparator<String> = String.CASE_INSENSITIVE_ORDER
}
