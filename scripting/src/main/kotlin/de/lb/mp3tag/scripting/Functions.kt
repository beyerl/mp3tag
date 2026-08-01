package de.lb.mp3tag.scripting

import java.util.UUID
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException
import kotlin.random.Random

/**
 * Mp3tag's scripting function library. Boolean functions return "1" for true
 * and "" for false; a value is truthy when non-empty and not "0".
 *
 * Known divergences from Windows Mp3tag: `$regexp` uses Java's regex dialect
 * (not PCRE/Boost), `$getenv` returns "", `$ansi` is a no-op, and
 * `$sort`/`$dedup`/`$list`/`$folderdepth`/`$fmtDuration` are not implemented.
 */
object Functions {

    val registry: Map<String, ScriptFn> = buildMap<String, ScriptFn> {
        // ---- conditionals / boolean ----
        put("if") { ctx, args ->
            requireArgs("if", args, 2, 3)
            if (truthy(ctx.eval(args[0]))) ctx.eval(args[1])
            else if (args.size > 2) ctx.eval(args[2]) else ""
        }
        put("if2") { ctx, args ->
            requireArgs("if2", args, 2, 2)
            val first = ctx.eval(args[0])
            first.ifEmpty { ctx.eval(args[1]) }
        }
        put("eql") { ctx, args -> bool(ctx.eval(args[0]) == ctx.eval(args[1])) }
        put("neql") { ctx, args -> bool(ctx.eval(args[0]) != ctx.eval(args[1])) }
        put("grtr") { ctx, args -> bool(num(ctx.eval(args[0])) > num(ctx.eval(args[1]))) }
        put("geql") { ctx, args -> bool(num(ctx.eval(args[0])) >= num(ctx.eval(args[1]))) }
        put("less") { ctx, args -> bool(num(ctx.eval(args[0])) < num(ctx.eval(args[1]))) }
        put("leql") { ctx, args -> bool(num(ctx.eval(args[0])) <= num(ctx.eval(args[1]))) }
        put("and") { ctx, args -> bool(args.all { truthy(ctx.eval(it)) }) }
        put("or") { ctx, args -> bool(args.any { truthy(ctx.eval(it)) }) }
        put("not") { ctx, args -> bool(!truthy(ctx.eval(args[0]))) }
        put("odd") { ctx, args -> bool(num(ctx.eval(args[0])) % 2 != 0L) }
        put("isdigit") { ctx, args ->
            val v = ctx.eval(args[0])
            bool(v.isNotEmpty() && v.all { it.isDigit() })
        }
        put("iflonger") { ctx, args ->
            requireArgs("iflonger", args, 4, 4)
            if (ctx.eval(args[0]).length > num(ctx.eval(args[1]))) ctx.eval(args[2]) else ctx.eval(args[3])
        }
        put("ifgreater") { ctx, args ->
            requireArgs("ifgreater", args, 4, 4)
            if (num(ctx.eval(args[0])) > num(ctx.eval(args[1]))) ctx.eval(args[2]) else ctx.eval(args[3])
        }
        put("strcmp") { ctx, args -> bool(ctx.eval(args[0]) == ctx.eval(args[1])) }
        put("stricmp") { ctx, args -> bool(ctx.eval(args[0]).equals(ctx.eval(args[1]), ignoreCase = true)) }

        // ---- case conversion ----
        put("upper") { ctx, args -> ctx.eval(args[0]).uppercase() }
        put("lower") { ctx, args -> ctx.eval(args[0]).lowercase() }
        put("caps") { ctx, args ->
            caps(ctx.eval(args[0]), extraSeparators(ctx, args), lowerRest = true, anyNonLetter = true)
        }
        put("caps2") { ctx, args ->
            caps(ctx.eval(args[0]), extraSeparators(ctx, args), lowerRest = false, anyNonLetter = true)
        }
        put("caps3") { ctx, args ->
            caps(ctx.eval(args[0]), extraSeparators(ctx, args), lowerRest = true, anyNonLetter = false)
        }

        // ---- substrings ----
        put("left") { ctx, args -> ctx.eval(args[0]).take(num(ctx.eval(args[1])).toInt().coerceAtLeast(0)) }
        put("right") { ctx, args -> ctx.eval(args[0]).takeLast(num(ctx.eval(args[1])).toInt().coerceAtLeast(0)) }
        put("mid") { ctx, args ->
            requireArgs("mid", args, 2, 3)
            val text = ctx.eval(args[0])
            val from = (num(ctx.eval(args[1])).toInt() - 1).coerceAtLeast(0) // 1-based
            val count = if (args.size > 2) num(ctx.eval(args[2])).toInt() else text.length
            if (from >= text.length || count <= 0) ""
            else text.substring(from, (from + count).coerceAtMost(text.length))
        }
        put("cutleft") { ctx, args -> ctx.eval(args[0]).drop(num(ctx.eval(args[1])).toInt().coerceAtLeast(0)) }
        put("cutright") { ctx, args -> ctx.eval(args[0]).dropLast(num(ctx.eval(args[1])).toInt().coerceAtLeast(0)) }
        put("len") { ctx, args -> ctx.eval(args[0]).length.toString() }

        // ---- trim / pad / shape ----
        put("trim") { ctx, args -> trim(ctx, args) { text, chars -> text.trim { it in chars } } }
        put("trimleft") { ctx, args -> trim(ctx, args) { text, chars -> text.trimStart { it in chars } } }
        put("trimright") { ctx, args -> trim(ctx, args) { text, chars -> text.trimEnd { it in chars } } }
        put("num") { ctx, args ->
            requireArgs("num", args, 2, 2)
            val digits = leadingDigits(ctx.eval(args[0]))
            val width = num(ctx.eval(args[1])).toInt().coerceIn(0, 64)
            (digits.ifEmpty { "0" }).trimStart('0').ifEmpty { "0" }.padStart(width, '0')
        }
        put("repeat") { ctx, args -> ctx.eval(args[0]).repeat(num(ctx.eval(args[1])).toInt().coerceIn(0, 4096)) }
        put("reverse") { ctx, args -> ctx.eval(args[0]).reversed() }

        // ---- search / replace ----
        put("replace") { ctx, args ->
            requireArgs("replace", args, 3, 63)
            var text = ctx.eval(args[0])
            var i = 1
            while (i + 1 < args.size) {
                val from = ctx.eval(args[i])
                if (from.isNotEmpty()) text = text.replace(from, ctx.eval(args[i + 1]))
                i += 2
            }
            text
        }
        put("regexp") { ctx, args ->
            requireArgs("regexp", args, 3, 4)
            val text = ctx.eval(args[0])
            val ignoreCase = args.size > 3 && truthy(ctx.eval(args[3]))
            try {
                val flags = if (ignoreCase) Pattern.CASE_INSENSITIVE else 0
                Pattern.compile(ctx.eval(args[1]), flags).matcher(text).replaceAll(ctx.eval(args[2]))
            } catch (e: PatternSyntaxException) {
                throw ScriptException("Invalid regular expression: ${e.description}")
            } catch (e: IndexOutOfBoundsException) {
                throw ScriptException("Invalid group reference in replacement")
            }
        }
        put("strchr") { ctx, args ->
            val c = ctx.eval(args[1]).firstOrNull() ?: return@put "0"
            (ctx.eval(args[0]).indexOf(c) + 1).toString()
        }
        put("strrchr") { ctx, args ->
            val c = ctx.eval(args[1]).firstOrNull() ?: return@put "0"
            (ctx.eval(args[0]).lastIndexOf(c) + 1).toString()
        }
        put("strstr") { ctx, args ->
            (ctx.eval(args[0]).indexOf(ctx.eval(args[1])) + 1).toString()
        }
        put("strrstr") { ctx, args ->
            (ctx.eval(args[0]).lastIndexOf(ctx.eval(args[1])) + 1).toString()
        }
        put("distance") { ctx, args -> levenshtein(ctx.eval(args[0]), ctx.eval(args[1])).toString() }
        put("ord") { ctx, args -> (ctx.eval(args[0]).firstOrNull()?.code ?: 0).toString() }
        put("char") { ctx, args -> num(ctx.eval(args[0])).toInt().toChar().toString() }
        put("validate") { ctx, args ->
            requireArgs("validate", args, 2, 2)
            val repl = ctx.eval(args[1])
            ctx.eval(args[0]).map { if (it in ILLEGAL_FILENAME_CHARS) repl else it.toString() }.joinToString("")
        }
        put("ansi") { ctx, args -> ctx.eval(args[0]) }
        put("fmtnum") { ctx, args ->
            val n = num(ctx.eval(args[0]))
            String.format(java.util.Locale.US, "%,d", n)
        }

        // ---- arithmetic ----
        put("add") { ctx, args -> args.fold(0L) { acc, a -> acc + num(ctx.eval(a)) }.toString() }
        put("sub") { ctx, args ->
            args.drop(1).fold(num(ctx.eval(args[0]))) { acc, a -> acc - num(ctx.eval(a)) }.toString()
        }
        put("mul") { ctx, args -> args.fold(1L) { acc, a -> acc * num(ctx.eval(a)) }.toString() }
        put("div") { ctx, args ->
            args.drop(1).fold(num(ctx.eval(args[0]))) { acc, a ->
                val d = num(ctx.eval(a))
                if (d == 0L) 0L else acc / d
            }.toString()
        }
        put("mod") { ctx, args ->
            val d = num(ctx.eval(args[1]))
            (if (d == 0L) 0L else num(ctx.eval(args[0])) % d).toString()
        }
        put("rand") { _, _ -> Random.nextInt(0, 32768).toString() }
        put("uuid") { _, _ -> UUID.randomUUID().toString() }

        // ---- metadata ----
        put("meta") { ctx, args ->
            requireArgs("meta", args, 1, 2)
            val values = ctx.allValues(ctx.eval(args[0]))
            if (args.size > 1) values.getOrElse(num(ctx.eval(args[1])).toInt()) { "" }
            else values.joinToString(", ")
        }
        put("meta_sep") { ctx, args ->
            requireArgs("meta_sep", args, 2, 2)
            ctx.allValues(ctx.eval(args[0])).joinToString(ctx.eval(args[1]))
        }

        // ---- variables ----
        put("put") { ctx, args ->
            val value = ctx.eval(args[1])
            ctx.variables[ctx.eval(args[0]).lowercase()] = value
            value
        }
        put("puts") { ctx, args ->
            ctx.variables[ctx.eval(args[0]).lowercase()] = ctx.eval(args[1])
            ""
        }
        put("get") { ctx, args -> ctx.variables[ctx.eval(args[0]).lowercase()] ?: "" }
        put("getenv") { _, _ -> "" }
    }

    fun truthy(value: String): Boolean = value.isNotEmpty() && value != "0"

    private fun bool(value: Boolean): String = if (value) "1" else ""

    /** Parses an optionally signed leading integer; non-numeric input is 0. */
    fun num(value: String): Long {
        val trimmed = value.trim()
        if (trimmed.isEmpty()) return 0
        val negative = trimmed[0] == '-'
        val digits = trimmed.drop(if (negative || trimmed[0] == '+') 1 else 0).takeWhile { it.isDigit() }
        val parsed = digits.toLongOrNull() ?: 0
        return if (negative) -parsed else parsed
    }

    private fun leadingDigits(value: String): String = value.trim().takeWhile { it.isDigit() }

    private fun requireArgs(name: String, args: List<Node.Seq>, min: Int, max: Int) {
        if (args.size < min || args.size > max) {
            throw ScriptException("\$$name expects $min..$max arguments, got ${args.size}")
        }
    }

    private fun extraSeparators(ctx: EvalContext, args: List<Node.Seq>): Set<Char> =
        if (args.size > 1) ctx.eval(args[1]).toSet() else emptySet()

    private fun trim(
        ctx: EvalContext,
        args: List<Node.Seq>,
        op: (String, Set<Char>) -> String,
    ): String {
        val chars = if (args.size > 1) ctx.eval(args[1]).toSet() else setOf(' ')
        return op(ctx.eval(args[0]), chars)
    }

    /**
     * Word capitalization. [anyNonLetter] = a new word starts after any
     * non-letter ($caps/$caps2); otherwise only after whitespace ($caps3).
     * Public so the host's case-conversion action can reuse it.
     */
    fun caps(
        text: String,
        extraSeparators: Set<Char>,
        lowerRest: Boolean,
        anyNonLetter: Boolean,
    ): String {
        val out = StringBuilder(text.length)
        var startOfWord = true
        for (c in text) {
            val isSeparator = c in extraSeparators ||
                if (anyNonLetter) !c.isLetter() else c.isWhitespace()
            if (isSeparator) {
                out.append(c)
                startOfWord = true
            } else {
                out.append(
                    when {
                        startOfWord -> c.uppercaseChar()
                        lowerRest -> c.lowercaseChar()
                        else -> c
                    },
                )
                startOfWord = false
            }
        }
        return out.toString()
    }

    private fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var curr = IntArray(b.length + 1)
        for (i in 1..a.length) {
            curr[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                curr[j] = minOf(curr[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = curr
            curr = tmp
        }
        return prev[b.length]
    }

    val ILLEGAL_FILENAME_CHARS: Set<Char> = setOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')
}
