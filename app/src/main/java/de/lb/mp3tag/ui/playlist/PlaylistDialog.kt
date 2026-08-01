package de.lb.mp3tag.ui.playlist

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.lb.mp3tag.domain.FileFieldSource
import de.lb.mp3tag.scripting.Evaluator
import de.lb.mp3tag.scripting.ScriptException
import de.lb.mp3tag.ui.session.SessionState

/**
 * Generate m3u8 playlists in the session folder: one playlist for all
 * targeted files, or one per distinct value of a format string
 * (e.g. %artist% - %album%).
 */
@Composable
fun PlaylistDialog(
    state: SessionState,
    onCreate: (groups: Map<String, List<Long>>) -> Unit,
    onDismiss: () -> Unit,
) {
    val targets = remember { state.targetFiles }
    var partitioned by rememberSaveable { mutableStateOf(false) }
    var name by rememberSaveable { mutableStateOf(state.rootDir?.name ?: "playlist") }
    var format by rememberSaveable { mutableStateOf("%artist% - %album%") }

    val groups: Pair<Map<String, List<Long>>, String?> = remember(partitioned, name, format, targets) {
        if (!partitioned) {
            mapOf(name to targets.map { it.id }) to null
        } else {
            try {
                val compiled = Evaluator().compile(format)
                val grouped = targets.groupBy { file ->
                    compiled.evaluate(FileFieldSource(file, state.edits[file.id])).ifEmpty { "unknown" }
                }
                grouped.mapValues { (_, files) -> files.map { it.id } } to null
            } catch (e: ScriptException) {
                emptyMap<String, List<Long>>() to e.message
            }
        }
    }
    val (groupMap, error) = groups

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Generate playlists") },
        text = {
            Column {
                Row {
                    FilterChip(
                        selected = !partitioned,
                        onClick = { partitioned = false },
                        label = { Text("Single playlist") },
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    FilterChip(
                        selected = partitioned,
                        onClick = { partitioned = true },
                        label = { Text("One per value") },
                    )
                }
                if (!partitioned) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                } else {
                    OutlinedTextField(
                        value = format,
                        onValueChange = { format = it },
                        label = { Text("Group by format string") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    )
                }
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = "${groupMap.size} playlist(s) in “${state.rootDir?.name}”, " +
                        "entries relative to that folder:",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
                for ((groupName, ids) in groupMap.entries.take(20)) {
                    Text(
                        text = "$groupName.m3u8 (${ids.size})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (groupMap.size > 20) {
                    Text(
                        text = "… and ${groupMap.size - 20} more",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onCreate(groupMap)
                    onDismiss()
                },
                enabled = error == null && groupMap.isNotEmpty() && targets.isNotEmpty() &&
                    (!partitioned || format.isNotBlank()) && (partitioned || name.isNotBlank()),
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
