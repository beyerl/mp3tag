package de.lb.mp3tag.ui.export

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.lb.mp3tag.domain.FileFieldSource
import de.lb.mp3tag.scripting.ExportTemplate
import de.lb.mp3tag.scripting.ScriptException
import de.lb.mp3tag.settings.AppSettings
import de.lb.mp3tag.ui.session.SessionState
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class BuiltinTemplate(val label: String, val fileName: String, val template: String)

private val BUILTINS = listOf(
    BuiltinTemplate(
        label = "Text list",
        fileName = "export.txt",
        template = "\$loop(%_path%)%artist% - %album% - %title%\n\$loopend()" +
            "\n%_total_files% file(s), %_total_time%\n",
    ),
    BuiltinTemplate(
        label = "CSV",
        fileName = "export.csv",
        template = "artist;album;title;track;year;length\n" +
            "\$loop(%_path%)%artist%;%album%;%title%;%track%;%year%;%_length%\n\$loopend()",
    ),
    BuiltinTemplate(
        label = "HTML",
        fileName = "export.html",
        template = "<html><body><table border='1'>\n" +
            "<tr><th>Artist</th><th>Album</th><th>Title</th></tr>\n" +
            "\$loop(%_path%)<tr><td>%artist%</td><td>%album%</td><td>%title%</td></tr>\n\$loopend()" +
            "</table></body></html>\n",
    ),
)

/**
 * Export the targeted files through an Mp3tag export template, then save the
 * result via the system file picker or hand it to the share sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportDialog(
    state: SessionState,
    settings: AppSettings,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val targets = remember { state.targetFiles }

    var selectedBuiltin by rememberSaveable { mutableStateOf(0) }
    var template by rememberSaveable { mutableStateOf(BUILTINS[0].template) }
    var customized by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val stored = settings.exportTemplate.first()
        if (stored.isNotEmpty() && !customized) {
            template = stored
            customized = true
        }
    }

    val rendered = remember(template, targets) {
        try {
            val sources = targets.map { FileFieldSource(it, state.edits[it.id]) }
            ExportTemplate(template).render(sources) to null
        } catch (e: ScriptException) {
            null to e.message
        }
    }
    val (output, error) = rendered

    val saveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        val content = output ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri, "wt")?.use { stream ->
                        stream.write(content.toByteArray(Charsets.UTF_8))
                    }
                }
                onDismiss()
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Column {
                TopAppBar(
                    title = { Text("Export (${targets.size} file(s))") },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Close")
                        }
                    },
                    actions = {
                        TextButton(
                            onClick = {
                                val name = BUILTINS.getOrNull(selectedBuiltin)?.fileName ?: "export.txt"
                                saveLauncher.launch(name)
                            },
                            enabled = output != null && targets.isNotEmpty(),
                        ) {
                            Text("Save…")
                        }
                        TextButton(
                            onClick = {
                                val intent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, output)
                                }
                                context.startActivity(Intent.createChooser(intent, "Export"))
                            },
                            enabled = output != null && targets.isNotEmpty(),
                        ) {
                            Text("Share")
                        }
                    },
                )
                Row(modifier = Modifier.padding(horizontal = 12.dp)) {
                    BUILTINS.forEachIndexed { index, builtin ->
                        FilterChip(
                            selected = selectedBuiltin == index && !customized,
                            onClick = {
                                selectedBuiltin = index
                                template = builtin.template
                                customized = false
                            },
                            label = { Text(builtin.label) },
                            modifier = Modifier.padding(end = 6.dp),
                        )
                    }
                }
                OutlinedTextField(
                    value = template,
                    onValueChange = {
                        template = it
                        customized = true
                        scope.launch { settings.setExportTemplate(it) }
                    },
                    label = { Text("Template (\$loop/\$loopend, placeholders, functions)") },
                    minLines = 3,
                    maxLines = 6,
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                }
                HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
                Text(
                    text = output?.lineSequence()?.take(60)?.joinToString("\n") ?: "",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .horizontalScroll(rememberScrollState())
                        .padding(16.dp),
                )
            }
        }
    }
}
