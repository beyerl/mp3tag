package de.lb.mp3tag.ui.convert

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.lb.mp3tag.domain.FileFieldSource
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.PendingEdits
import de.lb.mp3tag.scripting.Evaluator
import de.lb.mp3tag.scripting.Functions
import de.lb.mp3tag.scripting.PatternMatcher
import de.lb.mp3tag.scripting.ScriptException
import de.lb.mp3tag.ui.session.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

enum class ConverterKind { TAG_TO_FILENAME, FILENAME_TO_TAG, TAG_TO_TAG, AUTO_NUMBER }

data class PreviewRow(
    val id: Long,
    val before: String,
    val after: String?,
    val error: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConverterScaffold(
    title: String,
    targetCount: Int,
    globalError: String?,
    applyEnabled: Boolean,
    onApply: () -> Unit,
    onDismiss: () -> Unit,
    inputs: @Composable () -> Unit,
    rows: List<PreviewRow>,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(onClick = onApply, enabled = applyEnabled) {
                            Text("Apply ($targetCount)")
                        }
                    },
                )
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    inputs()
                    if (globalError != null) {
                        Text(
                            text = globalError,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(rows, key = { it.id }) { row ->
                        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(
                                text = row.before,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (row.error != null) {
                                Text(
                                    text = row.error,
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            } else if (row.after != null) {
                                Text(
                                    text = "→ ${row.after}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Tag -> Filename

@Composable
fun TagToFilenameDialog(
    state: SessionState,
    onApply: (plan: Map<Long, String>) -> Unit,
    onDismiss: () -> Unit,
) {
    var format by rememberSaveable { mutableStateOf("%artist% - %title%") }
    val targets = state.targetFiles

    val result by produceState<Pair<List<PreviewRow>, String?>>(
        initialValue = emptyList<PreviewRow>() to null,
        format, targets, state.edits,
    ) {
        value = withContext(Dispatchers.Default) {
            buildRenamePreview(format, targets, state.edits)
        }
    }
    val (rows, globalError) = result

    ConverterScaffold(
        title = "Tag → Filename",
        targetCount = rows.count { it.error == null && it.after != null && it.after != it.before },
        globalError = globalError,
        applyEnabled = globalError == null &&
            rows.any { it.error == null && it.after != null && it.after != it.before },
        onApply = {
            val plan = rows
                .filter { it.error == null && it.after != null && it.after != it.before }
                .associate { it.id to it.after!! }
            onApply(plan)
        },
        onDismiss = onDismiss,
        inputs = {
            OutlinedTextField(
                value = format,
                onValueChange = { format = it },
                label = { Text("Format string") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        rows = rows,
    )
}

private fun buildRenamePreview(
    format: String,
    targets: List<LoadedFile>,
    edits: Map<Long, PendingEdits>,
): Pair<List<PreviewRow>, String?> {
    if (format.isBlank()) return emptyList<PreviewRow>() to null
    val compiled = try {
        Evaluator().compile(format)
    } catch (e: ScriptException) {
        return emptyList<PreviewRow>() to e.message
    }
    val seenTargets = HashMap<String, Long>()
    val rows = targets.map { loaded ->
        try {
            val raw = compiled.evaluate(FileFieldSource(loaded, edits[loaded.id]))
            val sanitized = sanitizeName(raw)
                ?: return@map PreviewRow(loaded.id, loaded.fileName, null, "Empty result")
            val ext = loaded.extension
            val newName = if (ext.isEmpty()) sanitized else "$sanitized.$ext"
            val collisionKey = loaded.parentPath + "/" + newName.lowercase()
            val collidesWith = seenTargets.putIfAbsent(collisionKey, loaded.id)
            if (collidesWith != null) {
                PreviewRow(loaded.id, loaded.fileName, null, "Duplicate target name")
            } else {
                PreviewRow(loaded.id, loaded.fileName, newName)
            }
        } catch (e: ScriptException) {
            PreviewRow(loaded.id, loaded.fileName, null, e.message)
        }
    }
    return rows to null
}

/**
 * Sanitizes an evaluated filename. Scoped storage cannot create
 * subdirectories on rename, so path separators become '_' too.
 */
private fun sanitizeName(raw: String): String? {
    val sanitized = raw
        .map { c -> if (c in Functions.ILLEGAL_FILENAME_CHARS) '_' else c }
        .joinToString("")
        .trim()
        .trimEnd('.')
    return sanitized.ifEmpty { null }
}

// ---------------------------------------------------------------- Filename -> Tag

@Composable
fun FilenameToTagDialog(
    state: SessionState,
    onApply: (buffers: Map<Long, Map<String, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var pattern by rememberSaveable { mutableStateOf("%artist% - %title%") }
    val targets = state.targetFiles

    val result by produceState<Triple<List<PreviewRow>, Map<Long, Map<String, String>>, String?>>(
        initialValue = Triple(emptyList(), emptyMap(), null),
        pattern, targets,
    ) {
        value = withContext(Dispatchers.Default) {
            buildMatchPreview(pattern, targets)
        }
    }
    val (rows, buffers, globalError) = result

    ConverterScaffold(
        title = "Filename → Tag",
        targetCount = buffers.size,
        globalError = globalError,
        applyEnabled = globalError == null && buffers.isNotEmpty(),
        onApply = { onApply(buffers) },
        onDismiss = onDismiss,
        inputs = {
            OutlinedTextField(
                value = pattern,
                onValueChange = { pattern = it },
                label = { Text("Matching pattern") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        rows = rows,
    )
}

private fun buildMatchPreview(
    pattern: String,
    targets: List<LoadedFile>,
): Triple<List<PreviewRow>, Map<Long, Map<String, String>>, String?> {
    if (pattern.isBlank()) return Triple(emptyList(), emptyMap(), null)
    val matcher = try {
        PatternMatcher(pattern)
    } catch (e: ScriptException) {
        return Triple(emptyList(), emptyMap(), e.message)
    }
    val componentCount = pattern.count { it == '/' || it == '\\' } + 1
    val buffers = LinkedHashMap<Long, Map<String, String>>()
    val rows = targets.map { loaded ->
        val full = loaded.parentPath + "/" + loaded.nameWithoutExtension
        val input = full.split('/').takeLast(componentCount).joinToString("/")
        val extracted = matcher.match(input)
        if (extracted == null) {
            PreviewRow(loaded.id, loaded.fileName, null, "No match")
        } else {
            buffers[loaded.id] = extracted
            val summary = extracted.entries.joinToString("; ") { "${it.key}=${it.value}" }
            PreviewRow(loaded.id, loaded.fileName, summary)
        }
    }
    return Triple(rows, buffers, null)
}

// ---------------------------------------------------------------- Tag -> Tag

@Composable
fun TagToTagDialog(
    state: SessionState,
    onApply: (buffers: Map<Long, Map<String, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var field by rememberSaveable { mutableStateOf("TITLE") }
    var format by rememberSaveable { mutableStateOf("%title%") }
    val targets = state.targetFiles

    val result by produceState<Triple<List<PreviewRow>, Map<Long, Map<String, String>>, String?>>(
        initialValue = Triple(emptyList(), emptyMap(), null),
        field, format, targets, state.edits,
    ) {
        value = withContext(Dispatchers.Default) {
            buildTagToTagPreview(field, format, targets, state.edits)
        }
    }
    val (rows, buffers, globalError) = result

    ConverterScaffold(
        title = "Tag → Tag",
        targetCount = buffers.size,
        globalError = globalError,
        applyEnabled = globalError == null && buffers.isNotEmpty() && field.isNotBlank(),
        onApply = { onApply(buffers) },
        onDismiss = onDismiss,
        inputs = {
            OutlinedTextField(
                value = field,
                onValueChange = { field = it.uppercase() },
                label = { Text("Target field") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = format,
                onValueChange = { format = it },
                label = { Text("Format string") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        rows = rows,
    )
}

private fun buildTagToTagPreview(
    field: String,
    format: String,
    targets: List<LoadedFile>,
    edits: Map<Long, PendingEdits>,
): Triple<List<PreviewRow>, Map<Long, Map<String, String>>, String?> {
    if (field.isBlank() || format.isBlank()) return Triple(emptyList(), emptyMap(), null)
    val compiled = try {
        Evaluator().compile(format)
    } catch (e: ScriptException) {
        return Triple(emptyList(), emptyMap(), e.message)
    }
    val buffers = LinkedHashMap<Long, Map<String, String>>()
    val rows = targets.map { loaded ->
        try {
            val value = compiled.evaluate(FileFieldSource(loaded, edits[loaded.id]))
            buffers[loaded.id] = mapOf(field to value)
            PreviewRow(loaded.id, loaded.fileName, "$field = $value")
        } catch (e: ScriptException) {
            PreviewRow(loaded.id, loaded.fileName, null, e.message)
        }
    }
    return Triple(rows, buffers, null)
}

// ---------------------------------------------------------------- Auto-numbering

@Composable
fun AutoNumberDialog(
    state: SessionState,
    onApply: (buffers: Map<Long, Map<String, String>>) -> Unit,
    onDismiss: () -> Unit,
) {
    var leadingZeros by rememberSaveable { mutableStateOf(true) }
    var includeTotal by rememberSaveable { mutableStateOf(false) }
    var resetPerFolder by rememberSaveable { mutableStateOf(true) }
    val targets = state.targetFiles

    val buffers = buildNumberingPlan(targets, leadingZeros, includeTotal, resetPerFolder)
    val rows = targets.map { loaded ->
        PreviewRow(loaded.id, loaded.fileName, "TRACKNUMBER = ${buffers[loaded.id]?.get("TRACKNUMBER")}")
    }

    ConverterScaffold(
        title = "Auto-numbering",
        targetCount = buffers.size,
        globalError = null,
        applyEnabled = buffers.isNotEmpty(),
        onApply = { onApply(buffers) },
        onDismiss = onDismiss,
        inputs = {
            CheckboxRow("Leading zeros (01, 02, …)", leadingZeros) { leadingZeros = it }
            CheckboxRow("Include total (1/12)", includeTotal) { includeTotal = it }
            CheckboxRow("Restart numbering per folder", resetPerFolder) { resetPerFolder = it }
        },
        rows = rows,
    )
}

private fun buildNumberingPlan(
    targets: List<LoadedFile>,
    leadingZeros: Boolean,
    includeTotal: Boolean,
    resetPerFolder: Boolean,
): Map<Long, Map<String, String>> {
    val groups = if (resetPerFolder) {
        targets.groupBy { it.parentPath }.values
    } else {
        listOf(targets)
    }
    val result = LinkedHashMap<Long, Map<String, String>>()
    for (group in groups) {
        val total = group.size
        group.forEachIndexed { index, loaded ->
            val number = index + 1
            val text = if (leadingZeros) "%02d".format(number) else number.toString()
            val value = if (includeTotal) "$text/$total" else text
            result[loaded.id] = mapOf("TRACKNUMBER" to value)
        }
    }
    return result
}

@Composable
private fun CheckboxRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Checkbox(checked = checked, onCheckedChange = onChange)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 14.dp),
        )
    }
}
