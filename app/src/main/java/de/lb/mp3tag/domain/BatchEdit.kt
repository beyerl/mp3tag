package de.lb.mp3tag.domain

/**
 * Mp3tag's `< keep >` batch-editing semantics: the tag panel shows a derived
 * view of the selection, edits are applied per-file only for fields the user
 * explicitly touched, and everything else is kept as-is.
 */
object BatchEdit {

    /** A file's tags with its pending (unsaved) edits overlaid. */
    fun effective(tags: TagFields, edits: PendingEdits?): TagFields {
        if (edits == null || edits.fields.isEmpty()) return tags
        var result = tags
        for ((name, edit) in edits.fields) {
            result = when (edit) {
                is FieldEdit.Set -> result.with(name, edit.values)
                FieldEdit.Remove -> result.without(name)
            }
        }
        return result
    }

    /**
     * The panel value for [field] across a selection: the common joined value
     * if every file agrees (absent counts as ""), or null when values differ
     * (rendered as `< keep >`).
     */
    fun common(effectiveTags: List<TagFields>, field: String): String? {
        if (effectiveTags.isEmpty()) return ""
        val first = effectiveTags.first().joined(field)
        return if (effectiveTags.all { it.joined(field) == first }) first else null
    }

    /** Union of field names across a selection, in first-seen order. */
    fun unionOfFields(effectiveTags: List<TagFields>): List<String> {
        val seen = LinkedHashSet<String>()
        for (tags in effectiveTags) seen.addAll(tags.names)
        return seen.toList()
    }

    /**
     * Applies an explicit panel edit buffer to the selection. [buffer] holds
     * only fields the user touched, mapping field name to the new raw text
     * (multi-values separated by `\\`; empty text removes the field). Returns
     * the updated edits map for the selected file ids.
     */
    fun applyBuffer(
        buffer: Map<String, String>,
        selection: Collection<Long>,
        currentEdits: Map<Long, PendingEdits>,
        baseTags: Map<Long, TagFields>,
    ): Map<Long, PendingEdits> {
        if (buffer.isEmpty() || selection.isEmpty()) return currentEdits
        val result = currentEdits.toMutableMap()
        for (id in selection) {
            var edits = result[id] ?: PendingEdits.EMPTY
            val base = baseTags[id] ?: TagFields.EMPTY
            for ((name, text) in buffer) {
                val values = parseValues(text)
                val edit = if (values.isEmpty()) FieldEdit.Remove else FieldEdit.Set(values)
                // Skip no-op edits so files only become dirty on real changes.
                val unchanged = when (edit) {
                    is FieldEdit.Set -> base[name] == edit.values
                    FieldEdit.Remove -> !base.has(name)
                }
                val hadExplicitEdit = TagFields.canonical(name) in edits.fields
                if (unchanged && !hadExplicitEdit) continue
                edits = if (unchanged) {
                    edits.copy(fields = edits.fields - TagFields.canonical(name))
                } else {
                    edits.withField(name, edit)
                }
            }
            if (edits.isDirty) result[id] = edits else result.remove(id)
        }
        return result
    }

    /**
     * Like [applyBuffer], but with a distinct buffer per file — used by
     * converters and actions, where each file gets its own computed values.
     */
    fun applyPerFile(
        buffers: Map<Long, Map<String, String>>,
        currentEdits: Map<Long, PendingEdits>,
        baseTags: Map<Long, TagFields>,
    ): Map<Long, PendingEdits> {
        var result = currentEdits
        for ((id, buffer) in buffers) {
            result = applyBuffer(buffer, listOf(id), result, baseTags)
        }
        return result
    }

    fun parseValues(text: String): List<String> =
        text.split(TagFields.DISPLAY_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
}
