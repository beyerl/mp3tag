package de.lb.mp3tag.ui.filelist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.kyant.taglib.Picture
import de.lb.mp3tag.domain.BatchEdit
import de.lb.mp3tag.domain.CoverEdit
import de.lb.mp3tag.domain.TagFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class PanelField(val label: String, val name: String)

private val PANEL_FIELDS = listOf(
    PanelField("Title", "TITLE"),
    PanelField("Artist", "ARTIST"),
    PanelField("Album", "ALBUM"),
    PanelField("Album artist", "ALBUMARTIST"),
    PanelField("Year", "DATE"),
    PanelField("Genre", "GENRE"),
    PanelField("Track", "TRACKNUMBER"),
    PanelField("Disc", "DISCNUMBER"),
    PanelField("Composer", "COMPOSER"),
    PanelField("Comment", "COMMENT"),
)

/**
 * Mp3tag-style tag panel: shows the common value per field across the
 * selection, or a `< keep >` placeholder where values differ. Only fields the
 * user explicitly touches are applied; everything else is kept per-file.
 */
@Composable
fun TagPanel(
    selectionCount: Int,
    effectiveTags: List<TagFields>,
    pendingCover: CoverEdit?,
    loadCover: suspend () -> Picture?,
    onReplaceCover: (ByteArray, String) -> Unit,
    onRemoveCover: () -> Unit,
    onExtendedTags: () -> Unit,
    onApply: (Map<String, String>) -> Unit,
) {
    val commonValues = remember(effectiveTags) {
        PANEL_FIELDS.associate { it.name to BatchEdit.common(effectiveTags, it.name) }
    }
    val buffer = remember(effectiveTags) { mutableStateMapOf<String, String>() }

    Column(
        modifier = Modifier
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            Text(
                text = if (selectionCount == 1) "1 file" else "$selectionCount files",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = onExtendedTags) { Text("Extended tags…") }
        }

        CoverSection(
            pendingCover = pendingCover,
            loadCover = loadCover,
            onReplaceCover = onReplaceCover,
            onRemoveCover = onRemoveCover,
        )

        for (field in PANEL_FIELDS) {
            val common = commonValues[field.name]
            val touched = field.name in buffer
            OutlinedTextField(
                value = buffer[field.name] ?: common ?: "",
                onValueChange = { buffer[field.name] = it },
                label = { Text(field.label) },
                placeholder = {
                    if (common == null) Text("< keep >")
                },
                trailingIcon = {
                    if (touched) {
                        IconButton(onClick = { buffer.remove(field.name) }) {
                            Icon(Icons.Filled.Undo, contentDescription = "Reset ${field.label}")
                        }
                    }
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
            )
        }
        Button(
            onClick = { onApply(buffer.toMap()) },
            enabled = buffer.isNotEmpty(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        ) {
            Text("Apply to $selectionCount file(s)")
        }
    }
}

@Composable
private fun CoverSection(
    pendingCover: CoverEdit?,
    loadCover: suspend () -> Picture?,
    onReplaceCover: (ByteArray, String) -> Unit,
    onRemoveCover: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val coverBytes by produceState<ByteArray?>(initialValue = null, pendingCover) {
        value = when (pendingCover) {
            is CoverEdit.Replace -> pendingCover.data
            CoverEdit.Remove -> null
            null -> loadCover()?.data
        }
    }

    val pickImage = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val (bytes, mime) = withContext(Dispatchers.IO) {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    bytes to (context.contentResolver.getType(uri) ?: "image/jpeg")
                }
                if (bytes != null) onReplaceCover(bytes, mime)
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
    ) {
        if (coverBytes != null) {
            AsyncImage(
                model = coverBytes,
                contentDescription = "Cover art",
                modifier = Modifier.size(72.dp),
            )
        } else {
            Text(
                text = when (pendingCover) {
                    CoverEdit.Remove -> "Cover will be removed"
                    else -> "No cover"
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.size(72.dp).padding(top = 28.dp),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = {
                pickImage.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }) {
                Text("Replace cover")
            }
            OutlinedButton(onClick = onRemoveCover) {
                Text("Remove cover")
            }
        }
    }
}
