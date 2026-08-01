package de.lb.mp3tag.ui.browser

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import de.lb.mp3tag.settings.AppSettings
import de.lb.mp3tag.settings.RecentFolder
import de.lb.mp3tag.storage.SafStorage
import kotlinx.coroutines.launch

/**
 * Scoped-storage folder browser: the user grants individual folders via the
 * system picker (persisted SAF tree permissions, revocable per folder) and
 * can drill into subfolders of a granted tree. No global storage permission.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(
    storage: SafStorage,
    settings: AppSettings,
    onOpenDirectory: (dir: SafStorage.Dir, recursive: Boolean) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var recursive by rememberSaveable { mutableStateOf(true) }
    var treesVersion by remember { mutableIntStateOf(0) }
    val trees = remember(treesVersion) { storage.persistedTrees() }
    var currentDir by remember { mutableStateOf<SafStorage.Dir?>(null) }
    // Breadcrumb stack so Back walks up one level at a time.
    val dirStack = remember { ArrayDeque<SafStorage.Dir>() }
    val recents by settings.recentFolders.collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    val pickFolder = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            storage.takeTree(uri)
            treesVersion++
        }
    }

    val subdirs by produceState(initialValue = emptyList<SafStorage.Dir>(), currentDir) {
        value = currentDir?.let { storage.subdirectories(it) } ?: emptyList()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = currentDir?.name ?: "Folders",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    if (currentDir != null) {
                        IconButton(onClick = {
                            currentDir = dirStack.removeLastOrNull()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Up")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { pickFolder.launch(null) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Grant folder access")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Filled.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            currentDir?.let { dir ->
                ExtendedFloatingActionButton(
                    onClick = {
                        scope.launch {
                            settings.addRecentFolder(
                                RecentFolder(dir.path, dir.treeUri.toString(), dir.documentId),
                            )
                        }
                        onOpenDirectory(dir, recursive)
                    },
                    icon = { Icon(Icons.Filled.FolderOpen, contentDescription = null) },
                    text = { Text("Open this folder") },
                )
            }
        },
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (currentDir == null) {
                FolderOverview(
                    trees = trees,
                    recents = recents,
                    onEnterTree = { tree ->
                        dirStack.clear()
                        currentDir = storage.rootDir(tree)
                    },
                    onOpenRecent = { recent ->
                        val dir = SafStorage.Dir(
                            treeUri = recent.treeUri.toUri(),
                            documentId = recent.documentId,
                            name = recent.label.substringAfterLast('/'),
                            path = recent.label,
                        )
                        scope.launch { settings.addRecentFolder(recent) }
                        onOpenDirectory(dir, recursive)
                    },
                    onRevoke = { tree ->
                        storage.releaseTree(tree.uri)
                        scope.launch {
                            settings.retainRecentsForTrees(
                                storage.persistedTrees().map { it.uri.toString() }.toSet(),
                            )
                        }
                        treesVersion++
                    },
                    onAdd = { pickFolder.launch(null) },
                )
            } else {
                Text(
                    text = currentDir?.path.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(
                        text = "Include subfolders",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f).padding(top = 12.dp),
                    )
                    Switch(checked = recursive, onCheckedChange = { recursive = it })
                }
                HorizontalDivider()
                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(subdirs, key = { it.documentId }) { dir ->
                        ListItem(
                            headlineContent = {
                                Text(dir.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                            modifier = Modifier.clickable {
                                currentDir?.let { dirStack.addLast(it) }
                                currentDir = dir
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FolderOverview(
    trees: List<SafStorage.Tree>,
    recents: List<RecentFolder>,
    onEnterTree: (SafStorage.Tree) -> Unit,
    onOpenRecent: (RecentFolder) -> Unit,
    onRevoke: (SafStorage.Tree) -> Unit,
    onAdd: () -> Unit,
) {
    if (trees.isEmpty()) {
        Column(modifier = Modifier.padding(24.dp)) {
            Text(
                text = "No folder access granted",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Mp3tag only accesses music folders you select. " +
                    "Tap + to grant access to a folder; you can revoke it here at any time.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp).clickable { onAdd() },
            )
        }
        return
    }
    val grantedUris = trees.map { it.uri.toString() }.toSet()
    LazyColumn {
        items(trees, key = { it.uri.toString() }) { tree ->
            ListItem(
                headlineContent = { Text(tree.name) },
                supportingContent = { Text("Granted folder", maxLines = 1) },
                leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null) },
                trailingContent = {
                    IconButton(onClick = { onRevoke(tree) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Revoke access")
                    }
                },
                modifier = Modifier.clickable { onEnterTree(tree) },
            )
        }
        val visibleRecents = recents.filter { it.treeUri in grantedUris }
        if (visibleRecents.isNotEmpty()) {
            item { HorizontalDivider() }
            items(visibleRecents, key = { "recent:${it.treeUri}:${it.documentId}" }) { recent ->
                ListItem(
                    headlineContent = {
                        Text(recent.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    leadingContent = { Icon(Icons.Filled.History, contentDescription = null) },
                    modifier = Modifier.clickable { onOpenRecent(recent) },
                )
            }
        }
    }
}
