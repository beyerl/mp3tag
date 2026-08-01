package de.lb.mp3tag.scripting

/**
 * Mp3tag's filter expression language:
 *
 *     word word                  — every word is a substring of some field
 *     field HAS x / IS x / MATCHES re / GREATER n / LESS n / EQUAL n
 *     field PRESENT / ABSENT / MISSING
 *     NOT expr, expr AND expr, expr OR expr, ( ... )
 *
 * Keywords must be uppercase; everything else is case-insensitive. Operands
 * with spaces use double quotes; `"%_bitrate%"`-style placeholders are
 * resolved as fields.
 */
object FilterParser {

    fun compile(text: String): (FieldSource) -> Boolean {
        val tokens = tokenize(text)
        if (tokens.isEmpty()) return { true }
        val parser = Parser(tokens)
        val predicate = parser.parseOr()
        parser.expectEnd()
        return predicate
    }

    // ---- tokenizer ----

    private sealed interface Token {
        data class Word(val text: String, val quoted: Boolean) : Token
        data object LParen : Token
        data object RParen : Token
    }

    private fun tokenize(text: String): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0
        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++
                c == '(' -> { tokens += Token.LParen; i++ }
                c == ')' -> { tokens += Token.RParen; i++ }
                c == '"' -> {
                    val end = text.indexOf('"', i + 1)
                    if (end < 0) throw ScriptException("Unterminated quote in filter", i)
                    tokens += Token.Word(text.substring(i + 1, end), quoted = true)
                    i = end + 1
                }
                else -> {
                    val start = i
                    while (i < text.length && !text[i].isWhitespace() && text[i] != '(' && text[i] != ')') i++
                    tokens += Token.Word(text.substring(start, i), quoted = false)
                }
            }
        }
        return tokens
    }

    // ---- parser ----

    private val COMPARISON_OPS = setOf("HAS", "IS", "MATCHES", "GREATER", "LESS", "EQUAL")
    private val UNARY_OPS = setOf("PRESENT", "ABSENT", "MISSING")
    private val ALL_KEYWORDS = COMPARISON_OPS + UNARY_OPS + setOf("AND", "OR", "NOT")

    private class Parser(private val tokens: List<Token>) {
        private var pos = 0

        fun parseOr(): (FieldSource) -> Boolean {
            var left = parseAnd()
            while (isKeyword("OR")) {
                pos++
                val l = left
                val r = parseAnd()
                left = { source -> l(source) || r(source) }
            }
            return left
        }

        fun parseAnd(): (FieldSource) -> Boolean {
            var left = parseNot()
            // Explicit AND or simple adjacency (bare-word search terms).
            while (true) {
                if (isKeyword("AND")) pos++
                else if (atEnd() || isKeyword("OR") || peek() is Token.RParen) break
                val l = left
                val r = parseNot()
                left = { source -> l(source) && r(source) }
            }
            return left
        }

        fun parseNot(): (FieldSource) -> Boolean {
            if (isKeyword("NOT")) {
                pos++
                val inner = parseNot()
                return { source -> !inner(source) }
            }
            return parsePrimary()
        }

        fun parsePrimary(): (FieldSource) -> Boolean {
            val token = peek() ?: throw ScriptException("Unexpected end of filter")
            if (token is Token.LParen) {
                pos++
                val inner = parseOr()
                if (peek() !is Token.RParen) throw ScriptException("Missing ')' in filter")
                pos++
                return inner
            }
            val word = token as? Token.Word ?: throw ScriptException("Unexpected ')' in filter")
            pos++

            val next = peek()
            if (next is Token.Word && !next.quoted && next.text in COMPARISON_OPS) {
                pos++
                val operand = peek() as? Token.Word
                    ?: throw ScriptException("Missing operand after ${next.text}")
                pos++
                return comparison(word.text, next.text, operand.text)
            }
            if (next is Token.Word && !next.quoted && next.text in UNARY_OPS) {
                pos++
                val fieldName = word.text
                return when (next.text) {
                    "PRESENT" -> { source -> fieldValues(source, fieldName).isNotEmpty() }
                    else -> { source -> fieldValues(source, fieldName).isEmpty() }
                }
            }
            // Bare word: substring search across all fields.
            if (!word.quoted && word.text in ALL_KEYWORDS) {
                throw ScriptException("Unexpected keyword ${word.text} in filter")
            }
            val term = word.text.lowercase()
            return { source ->
                source.fieldNames().any { name ->
                    source.values(name).any { it.lowercase().contains(term) }
                }
            }
        }

        fun expectEnd() {
            if (!atEnd()) throw ScriptException("Unexpected trailing input in filter")
        }

        private fun comparison(field: String, op: String, operand: String): (FieldSource) -> Boolean {
            return when (op) {
                "HAS" -> { source -> joined(source, field).contains(operand, ignoreCase = true) }
                "IS" -> { source ->
                    fieldValues(source, field).any { it.equals(operand, ignoreCase = true) }
                }
                "MATCHES" -> {
                    val regex = try {
                        Regex(operand, RegexOption.IGNORE_CASE)
                    } catch (e: Exception) {
                        throw ScriptException("Invalid regex in filter: ${e.message}")
                    }
                    { source -> regex.containsMatchIn(joined(source, field)) }
                }
                "GREATER" -> { source -> numeric(source, field)?.let { it > toNumber(operand) } == true }
                "LESS" -> { source -> numeric(source, field)?.let { it < toNumber(operand) } == true }
                "EQUAL" -> { source -> numeric(source, field)?.let { it == toNumber(operand) } == true }
                else -> throw ScriptException("Unknown operator $op")
            }
        }

        private fun atEnd(): Boolean = pos >= tokens.size

        private fun peek(): Token? = tokens.getOrNull(pos)

        private fun isKeyword(keyword: String): Boolean {
            val token = peek()
            return token is Token.Word && !token.quoted && token.text == keyword
        }
    }

    private fun fieldValues(source: FieldSource, fieldRef: String): List<String> {
        val name = fieldRef.removeSurrounding("%")
        return source.values(name)
    }

    private fun joined(source: FieldSource, fieldRef: String): String =
        fieldValues(source, fieldRef).joinToString(" ")

    private fun numeric(source: FieldSource, fieldRef: String): Double? =
        fieldValues(source, fieldRef).firstOrNull()?.let(::toNumber)

    private fun toNumber(value: String): Double {
        val trimmed = value.trim()
        val numberPart = trimmed.takeWhile { it.isDigit() || it == '.' || it == '-' || it == '+' }
        return numberPart.toDoubleOrNull() ?: 0.0
    }
}
