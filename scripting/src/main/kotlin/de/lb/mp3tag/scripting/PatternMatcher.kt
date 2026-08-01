package de.lb.mp3tag.scripting

/**
 * "Match" mode of format strings (Filename → Tag): a pattern of literals and
 * placeholders is matched against a filename or relative path, extracting
 * field values. `%dummy%` captures and discards. Placeholders never match
 * across directory separators; literal `/` and `\` match either separator.
 */
class PatternMatcher(pattern: String) {

    private val fieldNames: List<String>
    private val regex: Regex

    init {
        val seq = FormatStringParser(pattern).parse()
        val names = mutableListOf<String>()
        val sb = StringBuilder("^")
        appendNodes(seq, names, sb)
        sb.append("$")
        fieldNames = names
        if (names.isEmpty()) throw ScriptException("Pattern contains no placeholders")
        regex = try {
            Regex(sb.toString(), RegexOption.IGNORE_CASE)
        } catch (e: Exception) {
            throw ScriptException("Invalid matching pattern: ${e.message}")
        }
    }

    private fun appendNodes(seq: Node.Seq, names: MutableList<String>, sb: StringBuilder) {
        seq.children.forEachIndexed { index, node ->
            when (node) {
                is Node.Literal -> {
                    for (c in node.text) {
                        if (c == '/' || c == '\\') sb.append("[/\\\\]")
                        else sb.append(Regex.escape(c.toString()))
                    }
                }
                is Node.Field -> {
                    names += node.name
                    val isLast = index == seq.children.lastIndex
                    // Lazy up to the next literal; the trailing field takes the rest.
                    sb.append(if (isLast) "([^/\\\\]+)" else "([^/\\\\]+?)")
                }
                else -> throw ScriptException(
                    "Only placeholders and literal text are allowed in matching patterns",
                )
            }
        }
    }

    /** @return extracted fields (uppercase names, %dummy% dropped), or null if no match. */
    fun match(input: String): Map<String, String>? {
        val result = regex.matchEntire(input) ?: return null
        val extracted = LinkedHashMap<String, String>()
        fieldNames.forEachIndexed { index, name ->
            if (!name.equals("dummy", ignoreCase = true)) {
                extracted[name.uppercase()] = result.groupValues[index + 1].trim()
            }
        }
        return extracted
    }
}
