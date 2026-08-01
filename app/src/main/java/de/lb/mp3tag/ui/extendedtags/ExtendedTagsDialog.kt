package de.lb.mp3tag.ui.extendedtags

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.lb.mp3tag.domain.BatchEdit
import de.lb.mp3tag.domain.TagFields

/**
 * Mp3tag's Extended Tags view: the union of all fields across the selection,
 * editable as multiline text (one value per line), plus add/remove of
 * arbitrary field names. Applies through the same explicit-buffer path as the
 * tag panel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtendedTagsDialog(
    selectionCount: Int,
    effectiveTags: List<TagFields>,
    onApply: (Map<String, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    val fieldNames = remember(effectiveTags) {
        BatchEdit.unionOfFields(effectiveTags).toMutableStateList()
    }
    val commonValues = remember(effectiveTags) {
        BatchEdit.unionOfFields(effectiveTags)
            .associateWith { BatchEdit.common(effectiveTags, it) }
    }
    // Buffer values are newline-separated (one value per line).
    val buffer = remember(effectiveTags) { mutableStateMapOf<String, String>() }
    var newFieldName by remember { mutableStateOf("") }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.imePadding()) {
                TopAppBar(
                    title = { Text("Extended tags ($selectionCount file(s))") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                onApply(buffer.mapValues { (_, text) -> toSeparated(text) })
                            },
                            enabled = buffer.isNotEmpty(),
                        ) {
                            Text("Apply")
                        }
                    },
                )
                LazyColumn(
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(fieldNames, key = { it }) { name ->
                        val common = commonValues[name]
                        val touched = name in buffer
                        OutlinedTextField(
                            value = buffer[name] ?: common?.let(::toMultiline) ?: "",
                            onValueChange = { buffer[name] = it },
                            label = { Text(name) },
                            placeholder = { if (common == null) Text("< keep >") },
                            trailingIcon = {
                                Row {
                                    if (touched) {
                                        IconButton(onClick = { buffer.remove(name) }) {
                                            Icon(Icons.Filled.Undo, contentDescription = "Reset $name")
                                        }
                                    }
                                    IconButton(onClick = { buffer[name] = "" }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove $name")
                                    }
                                }
                            },
                            minLines = 1,
                            maxLines = 4,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    item(key = "add-field") {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        ) {
                            OutlinedTextField(
                                value = newFieldName,
                                onValueChange = { newFieldName = it.uppercase() },
                                label = { Text("New field name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = {
                                    val name = TagFields.canonical(newFieldName)
                                    if (name.isNotEmpty() && name !in fieldNames) {
                                        fieldNames.add(name)
                                        buffer[name] = ""
                                    }
                                    newFieldName = ""
                                },
                                enabled = newFieldName.isNotBlank(),
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add field")
                            }
                        }
                    }
                }
                Text(
                    text = "One value per line for multi-value fields. " +
                        "Deleting a field removes it from all selected files on save.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(16.dp),
                )
            }
        }
    }
}

private fun toMultiline(separated: String): String =
    separated.replace(TagFields.DISPLAY_SEPARATOR, "\n")

private fun toSeparated(multiline: String): String =
    multiline.split("\n")
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(TagFields.DISPLAY_SEPARATOR)
