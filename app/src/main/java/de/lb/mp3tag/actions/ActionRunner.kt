package de.lb.mp3tag.actions

import de.lb.mp3tag.domain.BatchEdit
import de.lb.mp3tag.domain.FileFieldSource
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.PendingEdits
import de.lb.mp3tag.domain.TagFields
import de.lb.mp3tag.scripting.CompiledFormat
import de.lb.mp3tag.scripting.Evaluator
import de.lb.mp3tag.scripting.FieldSource
import de.lb.mp3tag.scripting.Functions
import de.lb.mp3tag.scripting.PatternMatcher
import de.lb.mp3tag.scripting.ScriptException
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * Compiles an action list once (surfacing format/regex errors before any file
 * is touched), then folds it over each file's working tags. The result is an
 * edit buffer (field → new display value, "" = remove) for the standard
 * per-file edit path, so saves and undo work unchanged.
 */
class ActionRunner(actions: List<Action>) {

    private val evaluator = Evaluator()

    private val steps: List<Step> = actions.map { compile(it) }

    private fun interface Step {
        fun apply(tags: TagFields, source: FieldSource): TagFields
    }

    fun run(loaded: LoadedFile, edits: PendingEdits?): Map<String, String> {
        val initial = BatchEdit.effective(loaded.tags, edits)
        var working = initial
        val technical = FileFieldSource(loaded, edits)
        val source = object : FieldSource {
            override fun values(name: String): List<String> =
                if (name.trim().startsWith("_")) technical.values(name) else working[name]

            override fun fieldNames(): Set<String> = working.names
        }
        for (step in steps) {
            working = step.apply(working, source)
        }
        val buffer = LinkedHashMap<String, String>()
        for (name in initial.names + working.names) {
            if (initial[name] != working[name]) {
                buffer[name] = working.joined(name)
            }
        }
        return buffer
    }

    private fun compile(action: Action): Step = when (action) {
        is Action.CaseConversion -> {
            Step { tags, _ ->
                mapValues(tags, action.field) { value ->
                    when (action.mode) {
                        CaseMode.UPPER -> value.uppercase()
                        CaseMode.LOWER -> value.lowercase()
                        CaseMode.CAPS -> Functions.caps(value, emptySet(), lowerRest = true, anyNonLetter = true)
                        CaseMode.CAPS2 -> Functions.caps(value, emptySet(), lowerRest = false, anyNonLetter = true)
                        CaseMode.CAPS3 -> Functions.caps(value, emptySet(), lowerRest = true, anyNonLetter = false)
                    }
                }
            }
        }
        is Action.Replace -> {
            val regex = if (action.matchCase) {
                null
            } else {
                Regex(Regex.escape(action.from), RegexOption.IGNORE_CASE)
            }
            Step { tags, _ ->
                mapValues(tags, action.field) { value ->
                    if (action.from.isEmpty()) value
                    else if (action.matchCase) value.replace(action.from, action.to)
                    else regex!!.replace(value, Regex.escapeReplacement(action.to))
                }
            }
        }
        is Action.RegexReplace -> {
            val pattern = try {
                Pattern.compile(action.pattern)
            } catch (e: PatternSyntaxException) {
                throw ScriptException("Invalid regular expression: ${e.description}")
            }
            Step { tags, _ ->
                mapValues(tags, action.field) { value ->
                    try {
                        pattern.matcher(value).replaceAll(action.replacement)
                    } catch (e: IndexOutOfBoundsException) {
                        throw ScriptException("Invalid group reference in replacement")
                    }
                }
            }
        }
        is Action.FormatValue -> {
            val compiled = compileFormat(action.format)
            Step { tags, source ->
                tags.with(action.field, listOf(compiled.evaluate(source)))
            }
        }
        is Action.GuessValues -> {
            val compiled = compileFormat(action.sourceFormat)
            val matcher = PatternMatcher(action.pattern)
            Step { tags, source ->
                val extracted = matcher.match(compiled.evaluate(source)) ?: return@Step tags
                var result = tags
                for ((name, value) in extracted) {
                    result = result.with(name, listOf(value))
                }
                result
            }
        }
        is Action.RemoveFields -> Step { tags, _ ->
            action.fields.fold(tags) { acc, field -> acc.without(field) }
        }
        is Action.RemoveAllExcept -> {
            val keep = action.fields.map { TagFields.canonical(it) }.toSet()
            Step { tags, _ ->
                tags.names.filterNot { it in keep }.fold(tags) { acc, field -> acc.without(field) }
            }
        }
        is Action.SplitField -> Step { tags, _ ->
            if (action.separator.isEmpty()) return@Step tags
            val values = tags[action.field]
                .flatMap { it.split(action.separator) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            if (values.isEmpty()) tags else tags.with(action.field, values)
        }
    }

    private fun compileFormat(format: String): CompiledFormat = evaluator.compile(format)

    private fun mapValues(tags: TagFields, field: String, transform: (String) -> String): TagFields {
        val values = tags[field]
        if (values.isEmpty()) return tags
        return tags.with(field, values.map(transform))
    }
}
