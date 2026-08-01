package de.lb.mp3tag.scripting

/** Supplies field values to the engine. Names are case-insensitive. */
interface FieldSource {
    /** All values of a field; empty list if absent. */
    fun values(name: String): List<String>

    /** Names of all present fields (used by filter free-text search). */
    fun fieldNames(): Set<String>
}

fun interface ScriptFn {
    operator fun invoke(ctx: EvalContext, args: List<Node.Seq>): String
}

class EvalContext internal constructor(
    private val source: FieldSource,
    private val functions: Map<String, ScriptFn>,
    /** `$put`/`$get` variables; pass a shared map to persist across evaluations (export). */
    val variables: MutableMap<String, String> = HashMap(),
) {
    private val optionalFrames = ArrayDeque<BooleanHolder>()

    fun eval(node: Node): String = when (node) {
        is Node.Seq -> node.children.joinToString("") { eval(it) }
        is Node.Literal -> node.text
        is Node.Field -> {
            val value = resolve(node.name)
            if (value.isNotEmpty()) {
                optionalFrames.forEach { it.value = true }
            }
            value
        }
        is Node.Optional -> {
            val frame = BooleanHolder()
            optionalFrames.addLast(frame)
            val text = eval(node.body)
            optionalFrames.removeLast()
            if (frame.value) text else ""
        }
        is Node.Call -> {
            val fn = functions[node.name]
                ?: throw ScriptException("Unknown function \$${node.name}")
            fn(this, node.args)
        }
    }

    fun resolve(name: String): String = source.values(name).firstOrNull() ?: ""

    fun allValues(name: String): List<String> = source.values(name)

    private class BooleanHolder(var value: Boolean = false)
}

/**
 * Entry point for "generate" mode: parse once, evaluate per file.
 */
class Evaluator(private val functions: Map<String, ScriptFn> = Functions.registry) {

    fun compile(formatString: String): CompiledFormat =
        CompiledFormat(FormatStringParser(formatString).parse(), functions)

    fun evaluate(formatString: String, source: FieldSource): String =
        compile(formatString).evaluate(source)
}

class CompiledFormat internal constructor(
    private val ast: Node.Seq,
    private val functions: Map<String, ScriptFn>,
) {
    fun evaluate(source: FieldSource): String = evaluate(source, HashMap())

    fun evaluate(source: FieldSource, variables: MutableMap<String, String>): String =
        EvalContext(source, functions, variables).eval(ast)
}
