package de.lb.mp3tag.ui.tagsources

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import de.lb.mp3tag.domain.BatchEdit
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.online.ReleaseDetail
import de.lb.mp3tag.online.ReleaseSearchResult
import de.lb.mp3tag.online.ReleaseTrack
import de.lb.mp3tag.online.TagSourceKind
import de.lb.mp3tag.online.TrackMatcher
import de.lb.mp3tag.ui.session.SessionState
import de.lb.mp3tag.ui.session.SessionViewModel
import kotlinx.coroutines.launch

private data class ImportFields(
    val title: Boolean = true,
    val artist: Boolean = true,
    val album: Boolean = true,
    val albumArtist: Boolean = true,
    val year: Boolean = true,
    val genre: Boolean = true,
    val track: Boolean = true,
    val cover: Boolean = true,
)

/**
 * Online tag sources: search a release on MusicBrainz or Discogs, match its
 * tracks against the targeted files, choose which fields to import, and apply
 * the result as pending edits (saved through the normal pipeline).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSourcesDialog(
    state: SessionState,
    viewModel: SessionViewModel,
    onDismiss: () -> Unit,
) {
    val targets = remember { state.targetFiles }
    val scope = rememberCoroutineScope()

    var kind by remember { mutableStateOf(TagSourceKind.MUSICBRAINZ) }
    var query by remember {
        mutableStateOf(
            targets.firstOrNull()?.let { first ->
                val tags = BatchEdit.effective(first.tags, state.edits[first.id])
                listOf(tags.joined("ALBUMARTIST").ifEmpty { tags.joined("ARTIST") }, tags.joined("ALBUM"))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
            } ?: "",
        )
    }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<ReleaseSearchResult>>(emptyList()) }
    var detail by remember { mutableStateOf<ReleaseDetail?>(null) }

    fun search() {
        scope.launch {
            busy = true
            error = null
            viewModel.searchReleases(kind, query)
                .onSuccess { results = it }
                .onFailure { error = it.message }
            busy = false
        }
    }

    fun openRelease(result: ReleaseSearchResult) {
        scope.launch {
            busy = true
            error = null
            viewModel.releaseDetail(result.source, result.id)
                .onSuccess { detail = it }
                .onFailure { error = it.message }
            busy = false
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val currentDetail = detail
            if (currentDetail == null) {
                Column {
                    TopAppBar(
                        title = { Text("Tag sources (${targets.size} file(s))") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                    )
                    Row(modifier = Modifier.padding(horizontal = 16.dp)) {
                        for (source in TagSourceKind.entries) {
                            FilterChip(
                                selected = kind == source,
                                onClick = { kind = source },
                                label = { Text(source.label) },
                                modifier = Modifier.padding(end = 8.dp),
                            )
                        }
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    ) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            label = { Text("Search release") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = ::search, enabled = query.isNotBlank() && !busy) {
                            Icon(Icons.Filled.Search, contentDescription = "Search")
                        }
                    }
                    if (busy) {
                        CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                    }
                    error?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    LazyColumn {
                        items(results, key = { "${it.source}:${it.id}" }) { result ->
                            ListItem(
                                headlineContent = {
                                    Text(result.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(
                                        listOfNotNull(
                                            result.artist.ifEmpty { null },
                                            result.year,
                                            result.extra,
                                        ).joinToString(" · "),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier.clickable(enabled = !busy) { openRelease(result) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
            } else {
                MatchingScreen(
                    detail = currentDetail,
                    targets = targets,
                    busy = busy,
                    onBack = { detail = null },
                    onImport = { buffers, wantCover ->
                        scope.launch {
                            busy = true
                            val cover = if (wantCover) viewModel.fetchCover(currentDetail) else null
                            viewModel.applyImport(buffers, cover)
                            busy = false
                            onDismiss()
                        }
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MatchingScreen(
    detail: ReleaseDetail,
    targets: List<LoadedFile>,
    busy: Boolean,
    onBack: () -> Unit,
    onImport: (buffers: Map<Long, Map<String, String>>, wantCover: Boolean) -> Unit,
) {
    val matches = remember(detail) { TrackMatcher.autoMatch(targets, detail.tracks) }
    val included = remember(detail) {
        mutableStateMapOf<Long, Boolean>().apply { matches.keys.forEach { put(it, true) } }
    }
    var fields by remember { mutableStateOf(ImportFields()) }
    val multiDisc = detail.tracks.any { it.disc > 1 }

    fun buildBuffers(): Map<Long, Map<String, String>> {
        val buffers = LinkedHashMap<Long, Map<String, String>>()
        for ((id, track) in matches) {
            if (included[id] != true) continue
            val buffer = LinkedHashMap<String, String>()
            if (fields.title) buffer["TITLE"] = track.title
            if (fields.artist) buffer["ARTIST"] = track.artist ?: detail.albumArtist
            if (fields.album) buffer["ALBUM"] = detail.album
            if (fields.albumArtist && detail.albumArtist.isNotEmpty()) {
                buffer["ALBUMARTIST"] = detail.albumArtist
            }
            if (fields.year && !detail.year.isNullOrEmpty()) buffer["DATE"] = detail.year
            if (fields.genre && !detail.genre.isNullOrEmpty()) buffer["GENRE"] = detail.genre
            if (fields.track) {
                buffer["TRACKNUMBER"] = track.position.toString()
                if (multiDisc) buffer["DISCNUMBER"] = track.disc.toString()
            }
            buffers[id] = buffer
        }
        return buffers
    }

    Column {
        TopAppBar(
            title = {
                Column {
                    Text(detail.album, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOfNotNull(detail.albumArtist.ifEmpty { null }, detail.year)
                            .joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
            },
            actions = {
                TextButton(
                    onClick = { onImport(buildBuffers(), fields.cover) },
                    enabled = !busy && included.values.any { it },
                ) {
                    Text("Import")
                }
            },
        )
        if (busy) {
            CircularProgressIndicator(modifier = Modifier.padding(16.dp))
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            FieldChip("Title", fields.title) { fields = fields.copy(title = it) }
            FieldChip("Artist", fields.artist) { fields = fields.copy(artist = it) }
            FieldChip("Album", fields.album) { fields = fields.copy(album = it) }
            FieldChip("Year", fields.year) { fields = fields.copy(year = it) }
        }
        Row(modifier = Modifier.padding(horizontal = 12.dp)) {
            FieldChip("Track #", fields.track) { fields = fields.copy(track = it) }
            FieldChip("Genre", fields.genre) { fields = fields.copy(genre = it) }
            FieldChip("Cover", fields.cover) { fields = fields.copy(cover = it) }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        LazyColumn {
            items(targets, key = { it.id }) { file ->
                val track = matches[file.id]
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                ) {
                    Checkbox(
                        checked = track != null && included[file.id] == true,
                        onCheckedChange = { checked ->
                            if (track != null) included[file.id] = checked
                        },
                        enabled = track != null,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = file.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = track?.let { trackLabel(it, multiDisc) } ?: "— no matching track —",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (track == null) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                    }
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun FieldChip(label: String, selected: Boolean, onChange: (Boolean) -> Unit) {
    FilterChip(
        selected = selected,
        onClick = { onChange(!selected) },
        label = { Text(label) },
        modifier = Modifier.padding(end = 6.dp),
    )
}

private fun trackLabel(track: ReleaseTrack, multiDisc: Boolean): String {
    val position = if (multiDisc) "${track.disc}-${track.position}" else track.position.toString()
    val duration = track.lengthMs?.let {
        val s = it / 1000
        " (%d:%02d)".format(s / 60, s % 60)
    } ?: ""
    return "→ $position. ${track.title}$duration"
}
