package de.lb.mp3tag.ui.filelist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.SortKey
import de.lb.mp3tag.domain.SortSpec
import de.lb.mp3tag.ui.actions.ActionsDialog
import de.lb.mp3tag.ui.convert.AutoNumberDialog
import de.lb.mp3tag.ui.convert.ConverterKind
import de.lb.mp3tag.ui.convert.FilenameToTagDialog
import de.lb.mp3tag.ui.convert.TagToFilenameDialog
import de.lb.mp3tag.ui.convert.TagToTagDialog
import de.lb.mp3tag.settings.AppSettings
import de.lb.mp3tag.ui.export.ExportDialog
import de.lb.mp3tag.ui.extendedtags.ExtendedTagsDialog
import de.lb.mp3tag.ui.playlist.PlaylistDialog
import de.lb.mp3tag.ui.session.SessionViewModel
import de.lb.mp3tag.ui.tagsources.TagSourcesDialog

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FileListScreen(
    viewModel: SessionViewModel,
    settings: AppSettings,
    onBack: () -> Unit,
) {
    val state by viewModel.state.collectAsState()
    var showPanel by remember { mutableStateOf(false) }
    var showExtendedTags by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var sortMenuOpen by remember { mutableStateOf(false) }
    var overflowOpen by remember { mutableStateOf(false) }
    var convertMenuOpen by remember { mutableStateOf(false) }
    var converter by remember { mutableStateOf<ConverterKind?>(null) }
    var showFilterBar by remember { mutableStateOf(false) }
    var showActions by remember { mutableStateOf(false) }
    var showTagSources by remember { mutableStateOf(false) }
    var showExport by remember { mutableStateOf(false) }
    var showPlaylists by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.rootDir?.name ?: "Files",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val visible = state.filtered.files.size
                        Text(
                            text = (
                                if (visible != state.files.size) "$visible/${state.files.size} files"
                                else "${state.files.size} files"
                                ) +
                                if (state.selection.isNotEmpty()) ", ${state.selection.size} selected" else "",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.saveAllDirty() },
                        enabled = state.dirtyCount > 0 && state.saving == null,
                    ) {
                        BadgedBox(badge = {
                            if (state.dirtyCount > 0) Badge { Text("${state.dirtyCount}") }
                        }) {
                            Icon(Icons.Filled.Save, contentDescription = "Save tags")
                        }
                    }
                    IconButton(
                        onClick = { showTagSources = true },
                        enabled = state.files.isNotEmpty(),
                    ) {
                        Icon(Icons.Filled.CloudDownload, contentDescription = "Tag sources")
                    }
                    IconButton(onClick = {
                        showFilterBar = !showFilterBar
                        if (!showFilterBar) viewModel.setFilter("")
                    }) {
                        Icon(Icons.Filled.FilterList, contentDescription = "Filter")
                    }
                    Box {
                        IconButton(onClick = { convertMenuOpen = true }) {
                            Icon(Icons.Filled.SwapHoriz, contentDescription = "Convert")
                        }
                        DropdownMenu(
                            expanded = convertMenuOpen,
                            onDismissRequest = { convertMenuOpen = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Tag → Filename") },
                                onClick = { converter = ConverterKind.TAG_TO_FILENAME; convertMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Filename → Tag") },
                                onClick = { converter = ConverterKind.FILENAME_TO_TAG; convertMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Tag → Tag") },
                                onClick = { converter = ConverterKind.TAG_TO_TAG; convertMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Auto-numbering") },
                                onClick = { converter = ConverterKind.AUTO_NUMBER; convertMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Actions…") },
                                onClick = { showActions = true; convertMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Export…") },
                                onClick = { showExport = true; convertMenuOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Generate playlists…") },
                                onClick = { showPlaylists = true; convertMenuOpen = false },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { sortMenuOpen = true }) {
                            Icon(Icons.Filled.Sort, contentDescription = "Sort")
                        }
                        SortMenu(
                            expanded = sortMenuOpen,
                            current = state.sort,
                            onDismiss = { sortMenuOpen = false },
                            onSelect = { viewModel.setSort(it); sortMenuOpen = false },
                        )
                    }
                    Box {
                        IconButton(onClick = { overflowOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "More")
                        }
                        DropdownMenu(expanded = overflowOpen, onDismissRequest = { overflowOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Select all") },
                                onClick = { viewModel.selectAll(); overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Invert selection") },
                                onClick = { viewModel.invertSelection(); overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Clear selection") },
                                onClick = { viewModel.clearSelection(); overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Revert selected") },
                                onClick = { viewModel.revertSelection(); overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Revert all") },
                                onClick = { viewModel.revertAll(); overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Undo last save") },
                                enabled = state.undoAvailable && state.saving == null,
                                onClick = { viewModel.undoLastSave(); overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Rename…") },
                                enabled = state.selection.size == 1,
                                onClick = { showRenameDialog = true; overflowOpen = false },
                            )
                            DropdownMenuItem(
                                text = { Text("Delete…") },
                                enabled = state.selection.isNotEmpty(),
                                onClick = { showDeleteDialog = true; overflowOpen = false },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.selection.isNotEmpty() && state.saving == null) {
                ExtendedFloatingActionButton(
                    onClick = { showPanel = true },
                    icon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                    text = { Text("Edit tags") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (showFilterBar) {
                androidx.compose.material3.OutlinedTextField(
                    value = state.filter,
                    onValueChange = { viewModel.setFilter(it) },
                    label = { Text("Filter (e.g. artist HAS x AND year GREATER 2000)") },
                    isError = state.filtered.error != null,
                    supportingText = {
                        state.filtered.error?.let { Text(it) }
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                )
            }
            state.scanning?.let { progress ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = {
                            if (progress.total == 0) 0f
                            else progress.loaded.toFloat() / progress.total
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "${progress.loaded}/${progress.total}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                    TextButton(onClick = { viewModel.stopScan() }) { Text("Stop") }
                }
            }
            state.saving?.let { progress ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LinearProgressIndicator(
                        progress = {
                            if (progress.total == 0) 0f
                            else progress.done.toFloat() / progress.total
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = "Saving ${progress.done}/${progress.total}",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(state.filtered.files, key = { it.id }) { file ->
                    FileRow(
                        file = file,
                        selected = file.id in state.selection,
                        showCheckbox = state.multiSelect,
                        dirty = state.edits[file.id]?.isDirty == true,
                        onTap = { viewModel.tapFile(file.id) },
                        onLongPress = { viewModel.longPressFile(file.id) },
                    )
                }
            }
        }
    }

    if (showPanel && state.selection.isNotEmpty()) {
        val firstSelectedId = state.selectedFiles.first().id
        ModalBottomSheet(onDismissRequest = { showPanel = false }) {
            TagPanel(
                selectionCount = state.selection.size,
                effectiveTags = state.selectedEffectiveTags,
                pendingCover = state.edits[firstSelectedId]?.cover,
                loadCover = { viewModel.frontCover(firstSelectedId) },
                onReplaceCover = viewModel::setCoverForSelection,
                onRemoveCover = viewModel::removeCoverForSelection,
                onExtendedTags = { showExtendedTags = true },
                onApply = { buffer ->
                    viewModel.applyPanelBuffer(buffer)
                    showPanel = false
                },
            )
        }
    }

    if (showExtendedTags && state.selection.isNotEmpty()) {
        ExtendedTagsDialog(
            selectionCount = state.selection.size,
            effectiveTags = state.selectedEffectiveTags,
            onApply = { buffer ->
                viewModel.applyPanelBuffer(buffer)
                showExtendedTags = false
            },
            onDismiss = { showExtendedTags = false },
        )
    }

    if (showTagSources) {
        TagSourcesDialog(
            state = state,
            viewModel = viewModel,
            onDismiss = { showTagSources = false },
        )
    }

    if (showExport) {
        ExportDialog(
            state = state,
            settings = settings,
            onDismiss = { showExport = false },
        )
    }

    if (showPlaylists) {
        PlaylistDialog(
            state = state,
            onCreate = viewModel::generatePlaylists,
            onDismiss = { showPlaylists = false },
        )
    }

    state.notice?.let { notice ->
        AlertDialog(
            onDismissRequest = { viewModel.dismissNotice() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissNotice() }) { Text("OK") }
            },
            text = { Text(notice) },
        )
    }

    if (showActions) {
        val groups by viewModel.actionGroups.collectAsState()
        ActionsDialog(
            groups = groups,
            targetCount = state.targetFiles.size,
            onApply = { group ->
                viewModel.applyActionGroup(group)
                showActions = false
            },
            onSave = viewModel::saveActionGroup,
            onDelete = viewModel::deleteActionGroup,
            onDismiss = { showActions = false },
        )
    }

    when (converter) {
        ConverterKind.TAG_TO_FILENAME -> TagToFilenameDialog(
            state = state,
            onApply = { plan ->
                viewModel.renameBatch(plan)
                converter = null
            },
            onDismiss = { converter = null },
        )
        ConverterKind.FILENAME_TO_TAG -> FilenameToTagDialog(
            state = state,
            onApply = { buffers ->
                viewModel.applyPerFileBuffers(buffers)
                converter = null
            },
            onDismiss = { converter = null },
        )
        ConverterKind.TAG_TO_TAG -> TagToTagDialog(
            state = state,
            onApply = { buffers ->
                viewModel.applyPerFileBuffers(buffers)
                converter = null
            },
            onDismiss = { converter = null },
        )
        ConverterKind.AUTO_NUMBER -> AutoNumberDialog(
            state = state,
            onApply = { buffers ->
                viewModel.applyPerFileBuffers(buffers)
                converter = null
            },
            onDismiss = { converter = null },
        )
        null -> Unit
    }

    if (showRenameDialog) {
        val selected = state.selectedFiles.singleOrNull()
        if (selected == null) {
            showRenameDialog = false
        } else {
            var newName by remember { mutableStateOf(selected.fileName) }
            AlertDialog(
                onDismissRequest = { showRenameDialog = false },
                title = { Text("Rename file") },
                text = {
                    androidx.compose.material3.OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        singleLine = true,
                        label = { Text("File name") },
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.renameSelected(newName)
                            showRenameDialog = false
                        },
                        enabled = newName.isNotBlank() && newName != selected.fileName,
                    ) {
                        Text("Rename")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") }
                },
            )
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("Delete ${state.selection.size} file(s)?") },
            text = { Text("The files will be permanently deleted from the device.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteSelected()
                    showDeleteDialog = false
                }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("Cancel") }
            },
        )
    }

    if (state.lastSaveErrors.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSaveErrors() },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSaveErrors() }) { Text("OK") }
            },
            title = { Text("${state.lastSaveErrors.size} file(s) failed to save") },
            text = {
                LazyColumn {
                    items(state.lastSaveErrors.size) { index ->
                        val error = state.lastSaveErrors[index]
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(error.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                error.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FileRow(
    file: LoadedFile,
    selected: Boolean,
    showCheckbox: Boolean,
    dirty: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
) {
    val background = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    androidx.compose.material3.Surface(color = background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onTap, onLongClick = onLongPress)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showCheckbox) {
                Checkbox(checked = selected, onCheckedChange = { onTap() })
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (dirty) {
                        Icon(
                            Icons.Filled.Circle,
                            contentDescription = "Unsaved changes",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(8.dp),
                        )
                    }
                    Text(
                        text = file.fileName,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = if (dirty) 6.dp else 0.dp),
                    )
                }
                val artist = file.tags.joined("ARTIST")
                val title = file.tags.joined("TITLE")
                if (artist.isNotEmpty() || title.isNotEmpty()) {
                    Text(
                        text = listOf(artist, title).filter { it.isNotEmpty() }.joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            file.props?.let { props ->
                Column(horizontalAlignment = Alignment.End) {
                    Text(formatDuration(props.lengthMs), style = MaterialTheme.typography.bodySmall)
                    Text("${props.bitrateKbps} kbps", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun SortMenu(
    expanded: Boolean,
    current: SortSpec,
    onDismiss: () -> Unit,
    onSelect: (SortSpec) -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        for (key in SortKey.entries) {
            val label = when (key) {
                SortKey.FILENAME -> "Filename"
                SortKey.TITLE -> "Title"
                SortKey.ARTIST -> "Artist"
                SortKey.ALBUM -> "Album / track"
                SortKey.MTIME -> "Modified"
                SortKey.PATH -> "Path"
            }
            val isCurrent = current.key == key
            DropdownMenuItem(
                text = {
                    Text(if (isCurrent) "$label ${if (current.ascending) "↑" else "↓"}" else label)
                },
                onClick = {
                    // Selecting the active key flips direction, like a column header.
                    onSelect(SortSpec(key, ascending = if (isCurrent) !current.ascending else true))
                },
            )
        }
    }
}

private fun formatDuration(lengthMs: Int): String {
    val totalSeconds = lengthMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
