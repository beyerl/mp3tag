package de.lb.mp3tag.domain

/**
 * Immutable, case-insensitive, insertion-ordered multimap of tag fields.
 * Canonical keys are uppercase (TagLib PropertyMap convention); values are
 * never empty lists and never contain blank-only entries.
 */
class TagFields private constructor(
    private val map: Map<String, List<String>>,
) {

    val names: Set<String> get() = map.keys

    operator fun get(name: String): List<String> = map[canonical(name)] ?: emptyList()

    fun first(name: String): String? = get(name).firstOrNull()

    /** Multi-values joined with Mp3tag's display separator `\\`. */
    fun joined(name: String): String = get(name).joinToString(DISPLAY_SEPARATOR)

    fun has(name: String): Boolean = map.containsKey(canonical(name))

    fun isEmpty(): Boolean = map.isEmpty()

    fun with(name: String, values: List<String>): TagFields {
        val cleaned = values.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return without(name)
        val result = LinkedHashMap(map)
        result[canonical(name)] = cleaned
        return TagFields(result)
    }

    fun without(name: String): TagFields {
        val key = canonical(name)
        if (!map.containsKey(key)) return this
        val result = LinkedHashMap(map)
        result.remove(key)
        return TagFields(result)
    }

    fun asMap(): Map<String, List<String>> = map

    override fun equals(other: Any?): Boolean = other is TagFields && map == other.map

    override fun hashCode(): Int = map.hashCode()

    override fun toString(): String = "TagFields($map)"

    companion object {
        const val DISPLAY_SEPARATOR: String = "\\\\"

        val EMPTY: TagFields = TagFields(emptyMap())

        fun canonical(name: String): String = name.trim().uppercase()

        fun of(vararg pairs: Pair<String, String>): TagFields =
            from(pairs.associate { (name, value) -> name to listOf(value) })

        fun from(fields: Map<String, List<String>>): TagFields {
            val result = LinkedHashMap<String, List<String>>()
            for ((name, values) in fields) {
                val cleaned = values.filter { it.isNotEmpty() }
                if (cleaned.isNotEmpty()) result[canonical(name)] = cleaned
            }
            return if (result.isEmpty()) EMPTY else TagFields(result)
        }
    }
}
