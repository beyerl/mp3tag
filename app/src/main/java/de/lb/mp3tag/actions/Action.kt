package de.lb.mp3tag.actions

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

enum class CaseMode { UPPER, LOWER, CAPS, CAPS2, CAPS3 }

/**
 * One step of an action group, applied per file against its working tags.
 * Serialized to JSON for persistence; the sealed hierarchy gives a stable
 * "type" discriminator.
 */
@Serializable
sealed interface Action {

    @Serializable
    @SerialName("case")
    data class CaseConversion(val field: String, val mode: CaseMode) : Action

    @Serializable
    @SerialName("replace")
    data class Replace(
        val field: String,
        val from: String,
        val to: String,
        val matchCase: Boolean = false,
    ) : Action

    @Serializable
    @SerialName("regex_replace")
    data class RegexReplace(
        val field: String,
        val pattern: String,
        val replacement: String,
    ) : Action

    @Serializable
    @SerialName("format_value")
    data class FormatValue(val field: String, val format: String) : Action

    @Serializable
    @SerialName("guess_values")
    data class GuessValues(val sourceFormat: String, val pattern: String) : Action

    @Serializable
    @SerialName("remove_fields")
    data class RemoveFields(val fields: List<String>) : Action

    @Serializable
    @SerialName("remove_except")
    data class RemoveAllExcept(val fields: List<String>) : Action

    @Serializable
    @SerialName("split_field")
    data class SplitField(val field: String, val separator: String) : Action
}

@Serializable
data class ActionGroup(val name: String, val actions: List<Action>)

/** Short human-readable description for list rows. */
fun Action.describe(): String = when (this) {
    is Action.CaseConversion -> "Case ${mode.name.lowercase()}: $field"
    is Action.Replace -> "Replace in $field: “$from” → “$to”"
    is Action.RegexReplace -> "Regex in $field: $pattern → $replacement"
    is Action.FormatValue -> "Format $field = $format"
    is Action.GuessValues -> "Guess values: $sourceFormat ⇒ $pattern"
    is Action.RemoveFields -> "Remove ${fields.joinToString(", ")}"
    is Action.RemoveAllExcept -> "Remove all except ${fields.joinToString(", ")}"
    is Action.SplitField -> "Split $field by “$separator”"
}
