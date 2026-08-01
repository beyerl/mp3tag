package de.lb.mp3tag.scripting

/**
 * Recursive-descent parser for Mp3tag format strings:
 * - `%fieldname%` placeholders
 * - `'quoted literals'` (with `''` producing a single quote)
 * - `[optional sections]`
 * - `$function(arg, ...)` calls; commas separate arguments only inside calls
 */
class FormatStringParser(private val input: String) {

    private var pos = 0

    fun parse(): Node.Seq {
        val seq = parseSeq(emptySet())
        if (pos < input.length) {
            throw ScriptException("Unexpected '${input[pos]}'", pos)
        }
        return seq
    }

    private fun parseSeq(terminators: Set<Char>): Node.Seq {
        val children = mutableListOf<Node>()
        val literal = StringBuilder()

        fun flush() {
            if (literal.isNotEmpty()) {
                children += Node.Literal(literal.toString())
                literal.clear()
            }
        }

        while (pos < input.length) {
            val c = input[pos]
            if (c in terminators) break
            when (c) {
                '%' -> {
                    flush()
                    children += parseField()
                }
                '\'' -> literal.append(parseQuoted())
                '[' -> {
                    flush()
                    val start = pos
                    pos++
                    val body = parseSeq(setOf(']'))
                    if (pos >= input.length || input[pos] != ']') {
                        throw ScriptException("Unterminated '['", start)
                    }
                    pos++
                    children += Node.Optional(body)
                }
                '$' -> {
                    if (isCallStart()) {
                        flush()
                        children += parseCall()
                    } else {
                        literal.append(c)
                        pos++
                    }
                }
                ']', ')' -> {
                    // Stray closers outside their context are literal text.
                    literal.append(c)
                    pos++
                }
                else -> {
                    literal.append(c)
                    pos++
                }
            }
        }
        flush()
        return Node.Seq(children)
    }

    private fun parseField(): Node.Field {
        val start = pos
        pos++ // %
        val nameStart = pos
        while (pos < input.length && input[pos] != '%') pos++
        if (pos >= input.length) throw ScriptException("Unterminated placeholder", start)
        val name = input.substring(nameStart, pos)
        pos++ // closing %
        if (name.isBlank()) throw ScriptException("Empty placeholder", start)
        return Node.Field(name.trim())
    }

    private fun parseQuoted(): String {
        val start = pos
        pos++ // opening '
        val textStart = pos
        while (pos < input.length && input[pos] != '\'') pos++
        if (pos >= input.length) throw ScriptException("Unterminated quote", start)
        val text = input.substring(textStart, pos)
        pos++ // closing '
        return if (text.isEmpty()) "'" else text
    }

    private fun isCallStart(): Boolean {
        var i = pos + 1
        if (i >= input.length || !input[i].isLetter()) return false
        while (i < input.length && (input[i].isLetterOrDigit() || input[i] == '_')) i++
        return i < input.length && input[i] == '('
    }

    private fun parseCall(): Node.Call {
        val start = pos
        pos++ // $
        val nameStart = pos
        while (pos < input.length && (input[pos].isLetterOrDigit() || input[pos] == '_')) pos++
        val name = input.substring(nameStart, pos).lowercase()
        pos++ // (
        val args = mutableListOf<Node.Seq>()
        if (pos < input.length && input[pos] == ')') {
            pos++
            return Node.Call(name, args)
        }
        while (true) {
            args += parseSeq(setOf(',', ')'))
            if (pos >= input.length) throw ScriptException("Unterminated call to \$$name", start)
            when (input[pos]) {
                ',' -> pos++
                ')' -> {
                    pos++
                    return Node.Call(name, args)
                }
            }
        }
    }
}
