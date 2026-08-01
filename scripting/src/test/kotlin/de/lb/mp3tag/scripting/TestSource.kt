package de.lb.mp3tag.scripting

class TestSource(private val fields: Map<String, List<String>>) : FieldSource {

    constructor(vararg pairs: Pair<String, String>) :
        this(pairs.associate { (k, v) -> k.uppercase() to listOf(v) })

    override fun values(name: String): List<String> = fields[name.trim().uppercase()] ?: emptyList()

    override fun fieldNames(): Set<String> = fields.keys

    companion object {
        fun multi(vararg pairs: Pair<String, List<String>>): TestSource =
            TestSource(pairs.associate { (k, v) -> k.uppercase() to v })
    }
}

fun eval(format: String, source: FieldSource = TestSource()): String =
    Evaluator().evaluate(format, source)
