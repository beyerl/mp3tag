package de.lb.mp3tag.scripting

/**
 * Mp3tag export templates: free text with placeholders/functions plus a
 * statement layer of `$loop(sortExpr[,limit]) ... $loopend()` sections.
 * Text outside loops renders once (against the first file); a loop body
 * renders per file, sorted by the loop expression. A nested loop groups
 * files by the outer loop's value (album-per-artist style exports).
 *
 * Extra placeholders available during rendering: `%_counter%` (1-based index
 * within the current loop), `%_total_files%`, and `%_total_time%` (h:mm:ss
 * over all files). `$puts`/`$get` variables persist across the whole render.
 */
class ExportTemplate(
    template: String,
    private val functions: Map<String, ScriptFn> = Functions.registry,
) {

    private sealed interface XNode {
        data class Plain(val node: Node) : XNode
        data class Loop(val sortExpr: Node.Seq, val limit: Int?, val body: List<XNode>) : XNode
    }

    private val root: List<XNode> = structure(FormatStringParser(template).parse().children)

    private fun structure(children: List<Node>): List<XNode> {
        val result = mutableListOf<XNode>()
        val stack = ArrayDeque<Pair<Node.Call, MutableList<XNode>>>()
        var current = result
        for (node in children) {
            if (node is Node.Call && node.name == "loop") {
                if (node.args.isEmpty()) throw ScriptException("\$loop needs a sort expression")
                val body = mutableListOf<XNode>()
                stack.addLast(node to body)
                current = body
            } else if (node is Node.Call && node.name == "loopend") {
                val (call, body) = stack.removeLastOrNull()
                    ?: throw ScriptException("\$loopend() without matching \$loop")
                val target = stack.lastOrNull()?.second ?: result
                val limit = call.args.getOrNull(1)?.let { limitArg ->
                    val text = (limitArg.children.singleOrNull() as? Node.Literal)?.text
                    text?.trim()?.toIntOrNull()
                }
                target += XNode.Loop(call.args[0], limit, body)
                current = target
            } else {
                current += XNode.Plain(node)
            }
        }
        if (stack.isNotEmpty()) throw ScriptException("\$loop without matching \$loopend()")
        return result
    }

    fun render(files: List<FieldSource>): String {
        val variables = HashMap<String, String>()
        val totalSeconds = files.sumOf { it.values("_length_seconds").firstOrNull()?.toLongOrNull() ?: 0L }
        val totals = Totals(files.size, totalSeconds)
        val sb = StringBuilder()
        renderNodes(root, files, files.firstOrNull(), counter = null, totals, variables, sb)
        return sb.toString()
    }

    private fun renderNodes(
        nodes: List<XNode>,
        files: List<FieldSource>,
        contextFile: FieldSource?,
        counter: Int?,
        totals: Totals,
        variables: MutableMap<String, String>,
        sb: StringBuilder,
    ) {
        for (node in nodes) {
            when (node) {
                is XNode.Plain ->
                    sb.append(eval(node.node, contextFile, counter, totals, variables))
                is XNode.Loop -> {
                    val sorted = files.sortedBy { file ->
                        eval(node.sortExpr, file, null, totals, variables).lowercase()
                    }
                    val limited = node.limit?.let { sorted.take(it) } ?: sorted
                    if (node.body.any { it is XNode.Loop }) {
                        // Nested loop: group by the outer expression's value.
                        val groups = limited.groupBy { file ->
                            eval(node.sortExpr, file, null, totals, variables).lowercase()
                        }
                        groups.values.forEachIndexed { index, group ->
                            renderNodes(node.body, group, group.first(), index + 1, totals, variables, sb)
                        }
                    } else {
                        limited.forEachIndexed { index, file ->
                            renderNodes(node.body, limited, file, index + 1, totals, variables, sb)
                        }
                    }
                }
            }
        }
    }

    private fun eval(
        node: Node,
        contextFile: FieldSource?,
        counter: Int?,
        totals: Totals,
        variables: MutableMap<String, String>,
    ): String {
        val source = ExportFieldSource(contextFile, counter, totals)
        return EvalContext(source, functions, variables).eval(node)
    }

    private class Totals(val files: Int, val seconds: Long) {
        val time: String
            get() {
                val h = seconds / 3600
                val m = (seconds % 3600) / 60
                val s = seconds % 60
                return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
            }
    }

    private class ExportFieldSource(
        private val delegate: FieldSource?,
        private val counter: Int?,
        private val totals: Totals,
    ) : FieldSource {
        override fun values(name: String): List<String> = when (name.trim().lowercase()) {
            "_counter" -> counter?.let { listOf(it.toString()) } ?: emptyList()
            "_total_files" -> listOf(totals.files.toString())
            "_total_time" -> listOf(totals.time)
            else -> delegate?.values(name) ?: emptyList()
        }

        override fun fieldNames(): Set<String> = delegate?.fieldNames() ?: emptySet()
    }
}
