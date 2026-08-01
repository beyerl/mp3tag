package de.lb.mp3tag.scripting

/** AST of a parsed Mp3tag format string. */
sealed interface Node {
    /** Concatenation of children. */
    data class Seq(val children: List<Node>) : Node

    data class Literal(val text: String) : Node

    /** A `%fieldname%` placeholder. */
    data class Field(val name: String) : Node

    /** A `[...]` section: emitted only if a contained placeholder resolves non-empty. */
    data class Optional(val body: Seq) : Node

    /** A `$function(arg, ...)` call. Arguments are unevaluated sub-expressions. */
    data class Call(val name: String, val args: List<Seq>) : Node
}

class ScriptException(message: String, val position: Int = -1) :
    Exception(if (position >= 0) "$message (at position $position)" else message)
