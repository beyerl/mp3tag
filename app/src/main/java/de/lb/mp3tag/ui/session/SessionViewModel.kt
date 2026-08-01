package de.lb.mp3tag.ui.session

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kyant.taglib.Picture
import de.lb.mp3tag.actions.ActionGroup
import de.lb.mp3tag.actions.ActionGroupStore
import de.lb.mp3tag.actions.ActionRunner
import de.lb.mp3tag.domain.BatchEdit
import de.lb.mp3tag.domain.CoverEdit
import de.lb.mp3tag.domain.FileFieldSource
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.PendingEdits
import de.lb.mp3tag.domain.SortSpec
import de.lb.mp3tag.domain.SortSpecs
import de.lb.mp3tag.domain.TagFields
import de.lb.mp3tag.online.ReleaseDetail
import de.lb.mp3tag.online.ReleaseSearchResult
import de.lb.mp3tag.online.TagSourceClient
import de.lb.mp3tag.online.TagSourceKind
import de.lb.mp3tag.scripting.FilterParser
import de.lb.mp3tag.scripting.ScriptException
import de.lb.mp3tag.storage.FileScanner
import de.lb.mp3tag.storage.PlaylistGenerator
import de.lb.mp3tag.storage.SafFileRef
import de.lb.mp3tag.storage.SafStorage
import de.lb.mp3tag.storage.ScanEvent
import de.lb.mp3tag.tag.SaveError
import de.lb.mp3tag.tag.SavePipeline
import de.lb.mp3tag.tag.TagIo
import de.lb.mp3tag.tag.TagRepository
import de.lb.mp3tag.tag.UndoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScanProgress(val loaded: Int, val total: Int)

data class SaveProgress(val done: Int, val total: Int)

data class FilterOutcome(val files: List<LoadedFile>, val error: String?)

data class SessionState(
    val rootDir: SafStorage.Dir? = null,
    val files: List<LoadedFile> = emptyList(),
    val edits: Map<Long, PendingEdits> = emptyMap(),
    val selection: Set<Long> = emptySet(),
    val multiSelect: Boolean = false,
    val sort: SortSpec = SortSpec(),
    val scanning: ScanProgress? = null,
    val saving: SaveProgress? = null,
    val lastSaveErrors: List<SaveError> = emptyList(),
    val undoAvailable: Boolean = false,
    val filter: String = "",
    val notice: String? = null,
) {
    val dirtyCount: Int get() = edits.count { it.value.isDirty }
    val selectedFiles: List<LoadedFile> get() = files.filter { it.id in selection }

    /** Selected files' tags with pending edits overlaid — what the panel shows. */
    val selectedEffectiveTags: List<TagFields>
        get() = selectedFiles.map { BatchEdit.effective(it.tags, edits[it.id]) }

    /** Files passing the current filter expression (all files on parse error). */
    val filtered: FilterOutcome by lazy {
        if (filter.isBlank()) {
            FilterOutcome(files, null)
        } else {
            try {
                val predicate = FilterParser.compile(filter)
                FilterOutcome(files.filter { predicate(FileFieldSource(it, edits[it.id])) }, null)
            } catch (e: ScriptException) {
                FilterOutcome(files, e.message)
            }
        }
    }

    /** Converter/action targets: the selection, or every visible file if nothing is selected. */
    val targetFiles: List<LoadedFile>
        get() = selectedFiles.ifEmpty { filtered.files }
}

class SessionViewModel(
    private val scanner: FileScanner,
    private val savePipeline: SavePipeline,
    private val storage: SafStorage,
    private val tagIo: TagIo,
    private val repository: TagRepository,
    private val undoStore: UndoStore,
    private val actionStore: ActionGroupStore,
    private val tagSources: Map<TagSourceKind, TagSourceClient>,
    private val playlistGenerator: PlaylistGenerator,
) : ViewModel() {

    private val _state = MutableStateFlow(SessionState())
    val state: StateFlow<SessionState> = _state

    private val _actionGroups = MutableStateFlow<List<ActionGroup>>(emptyList())
    val actionGroups: StateFlow<List<ActionGroup>> = _actionGroups

    private var scanJob: Job? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val available = undoStore.batchCount() > 0
            _state.update { it.copy(undoAvailable = available) }
            _actionGroups.value = actionStore.list()
        }
    }

    fun openDirectory(dir: SafStorage.Dir, recursive: Boolean) {
        scanJob?.cancel()
        _state.update {
            SessionState(
                rootDir = dir,
                sort = it.sort,
                scanning = ScanProgress(0, 0),
                undoAvailable = it.undoAvailable,
            )
        }
        scanJob = viewModelScope.launch {
            val collected = ArrayList<LoadedFile>()
            var total = 0
            try {
                scanner.scan(dir, recursive).collect { event ->
                    when (event) {
                        is ScanEvent.Total -> {
                            total = event.count
                            _state.update { it.copy(scanning = ScanProgress(0, total)) }
                        }
                        is ScanEvent.Loaded -> {
                            collected += event.file
                            val publish = collected.size % PUBLISH_EVERY == 0 || collected.size == total
                            _state.update {
                                it.copy(
                                    scanning = ScanProgress(collected.size, total),
                                    files = if (publish) sorted(collected, it.sort) else it.files,
                                )
                            }
                        }
                    }
                }
            } finally {
                // Publish whatever was collected even if the scan was stopped.
                _state.update { it.copy(files = sorted(collected, it.sort), scanning = null) }
            }
        }
    }

    fun stopScan() {
        scanJob?.cancel()
        scanJob = null
        _state.update { it.copy(scanning = null) }
    }

    fun setSort(sort: SortSpec) {
        _state.update { it.copy(sort = sort, files = sorted(it.files, sort)) }
    }

    fun tapFile(id: Long) {
        _state.update { state ->
            if (state.multiSelect) {
                val selection = if (id in state.selection) state.selection - id else state.selection + id
                state.copy(selection = selection, multiSelect = selection.isNotEmpty())
            } else {
                state.copy(selection = setOf(id))
            }
        }
    }

    fun longPressFile(id: Long) {
        _state.update { it.copy(multiSelect = true, selection = it.selection + id) }
    }

    fun selectAll() {
        _state.update {
            it.copy(multiSelect = true, selection = it.filtered.files.map(LoadedFile::id).toSet())
        }
    }

    fun invertSelection() {
        _state.update { state ->
            val selection = state.filtered.files.map(LoadedFile::id).toSet() - state.selection
            state.copy(selection = selection, multiSelect = selection.isNotEmpty())
        }
    }

    fun clearSelection() {
        _state.update { it.copy(selection = emptySet(), multiSelect = false) }
    }

    fun setFilter(text: String) {
        _state.update { it.copy(filter = text) }
    }

    /** Applies the tag panel's explicit edit buffer to the current selection. */
    fun applyPanelBuffer(buffer: Map<String, String>) {
        _state.update { state ->
            val baseTags = state.files.associate { it.id to it.tags }
            state.copy(
                edits = BatchEdit.applyBuffer(buffer, state.selection, state.edits, baseTags),
            )
        }
    }

    /** Applies converter/action results: a distinct field buffer per file. */
    fun applyPerFileBuffers(buffers: Map<Long, Map<String, String>>) {
        _state.update { state ->
            val baseTags = state.files.associate { it.id to it.tags }
            state.copy(edits = BatchEdit.applyPerFile(buffers, state.edits, baseTags))
        }
    }

    fun setCoverForSelection(data: ByteArray, mimeType: String) {
        updateSelectedEdits { it.copy(cover = CoverEdit.Replace(data, mimeType)) }
    }

    fun removeCoverForSelection() {
        updateSelectedEdits { it.copy(cover = CoverEdit.Remove) }
    }

    fun revertSelection() {
        _state.update { it.copy(edits = it.edits - it.selection) }
    }

    fun revertAll() {
        _state.update { it.copy(edits = emptyMap()) }
    }

    fun saveAllDirty() {
        val snapshot = _state.value
        if (snapshot.dirtyCount == 0 || snapshot.saving != null) return
        viewModelScope.launch {
            _state.update { it.copy(saving = SaveProgress(0, it.dirtyCount), lastSaveErrors = emptyList()) }
            undoStore.snapshot(snapshot.files, snapshot.edits)
            val outcome = savePipeline.save(
                files = snapshot.files,
                edits = snapshot.edits,
            ) { done, total ->
                _state.update { it.copy(saving = SaveProgress(done, total)) }
            }
            _state.update { state ->
                val savedById = outcome.saved.associateBy { it.id }
                state.copy(
                    files = sorted(state.files.map { savedById[it.id] ?: it }, state.sort),
                    edits = state.edits - savedById.keys,
                    saving = null,
                    lastSaveErrors = outcome.errors,
                    undoAvailable = true,
                )
            }
        }
    }

    /** Undoes the most recently saved batch by restoring the snapshot from disk. */
    fun undoLastSave() {
        viewModelScope.launch {
            val result = undoStore.undoLast()
            val restoredUris = result.restoredUris.toSet()
            val reloaded = _state.value.files
                .filter {
                    val uri = (it.file as? SafFileRef)?.documentUri?.toString()
                    uri != null && uri in restoredUris
                }
                .map { repository.reload(it) }
                .associateBy { it.id }
            _state.update { state ->
                state.copy(
                    files = sorted(state.files.map { reloaded[it.id] ?: it }, state.sort),
                    lastSaveErrors = result.errors,
                    undoAvailable = undoStore.batchCount() > 0,
                )
            }
        }
    }

    /** Renames the single selected file in place. */
    fun renameSelected(newName: String) {
        val selected = _state.value.selectedFiles.singleOrNull() ?: return
        viewModelScope.launch {
            try {
                val newRef = storage.rename(selected.file as SafFileRef, newName)
                _state.update { state ->
                    state.copy(
                        files = sorted(
                            state.files.map { if (it.id == selected.id) it.copy(file = newRef) else it },
                            state.sort,
                        ),
                    )
                }
            } catch (e: Exception) {
                _state.update {
                    it.copy(lastSaveErrors = listOf(SaveError(selected.fileName, e.message ?: "Rename failed")))
                }
            }
        }
    }

    /** Renames files to converter-computed names. Subdirectories are not supported under SAF. */
    fun renameBatch(plan: Map<Long, String>) {
        if (plan.isEmpty()) return
        viewModelScope.launch {
            val errors = ArrayList<SaveError>()
            val renamed = HashMap<Long, SafFileRef>()
            val byId = _state.value.files.associateBy { it.id }
            for ((id, newName) in plan) {
                val loaded = byId[id] ?: continue
                try {
                    renamed[id] = storage.rename(loaded.file as SafFileRef, newName)
                } catch (e: Exception) {
                    errors += SaveError(loaded.fileName, e.message ?: "Rename failed")
                }
            }
            _state.update { state ->
                state.copy(
                    files = sorted(
                        state.files.map { f -> renamed[f.id]?.let { f.copy(file = it) } ?: f },
                        state.sort,
                    ),
                    lastSaveErrors = errors,
                )
            }
        }
    }

    /** Permanently deletes the selected files. */
    fun deleteSelected() {
        val selected = _state.value.selectedFiles
        if (selected.isEmpty()) return
        viewModelScope.launch {
            val errors = ArrayList<SaveError>()
            val deletedIds = HashSet<Long>()
            for (loaded in selected) {
                try {
                    storage.delete(loaded.file as SafFileRef)
                    deletedIds += loaded.id
                } catch (e: Exception) {
                    errors += SaveError(loaded.fileName, e.message ?: "Delete failed")
                }
            }
            _state.update { state ->
                state.copy(
                    files = state.files.filter { it.id !in deletedIds },
                    edits = state.edits - deletedIds,
                    selection = state.selection - deletedIds,
                    multiSelect = (state.selection - deletedIds).isNotEmpty() && state.multiSelect,
                    lastSaveErrors = errors,
                )
            }
        }
    }

    /** Lazily reads the front cover (or first picture) of a loaded file. */
    suspend fun frontCover(id: Long): Picture? {
        val loaded = _state.value.files.find { it.id == id } ?: return null
        val ref = loaded.file as? SafFileRef ?: return null
        return runCatching {
            val pictures = tagIo.readPictures(ref.documentUri)
            pictures.find { it.pictureType == SavePipeline.FRONT_COVER } ?: pictures.firstOrNull()
        }.getOrNull()
    }

    fun saveActionGroup(group: ActionGroup) {
        viewModelScope.launch {
            actionStore.save(group)
            _actionGroups.value = actionStore.list()
        }
    }

    fun deleteActionGroup(name: String) {
        viewModelScope.launch {
            actionStore.delete(name)
            _actionGroups.value = actionStore.list()
        }
    }

    /** Runs an action group over the current targets, producing pending edits. */
    fun applyActionGroup(group: ActionGroup) {
        val snapshot = _state.value
        val targets = snapshot.targetFiles
        if (targets.isEmpty()) return
        viewModelScope.launch(Dispatchers.Default) {
            try {
                val runner = ActionRunner(group.actions)
                val buffers = LinkedHashMap<Long, Map<String, String>>()
                for (loaded in targets) {
                    val buffer = runner.run(loaded, snapshot.edits[loaded.id])
                    if (buffer.isNotEmpty()) buffers[loaded.id] = buffer
                }
                applyPerFileBuffers(buffers)
            } catch (e: ScriptException) {
                _state.update {
                    it.copy(lastSaveErrors = listOf(SaveError(group.name, e.message ?: "Action failed")))
                }
            }
        }
    }

    fun dismissSaveErrors() {
        _state.update { it.copy(lastSaveErrors = emptyList()) }
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    // ---- online tag sources ----

    suspend fun searchReleases(kind: TagSourceKind, query: String): Result<List<ReleaseSearchResult>> =
        runCatching { tagSources.getValue(kind).search(query) }

    suspend fun releaseDetail(kind: TagSourceKind, id: String): Result<ReleaseDetail> =
        runCatching { tagSources.getValue(kind).release(id) }

    suspend fun fetchCover(detail: ReleaseDetail): Pair<ByteArray, String>? =
        runCatching { tagSources.getValue(detail.source).coverBytes(detail) }.getOrNull()

    /** Applies a tag-source import: per-file field buffers plus an optional shared cover. */
    fun applyImport(
        buffers: Map<Long, Map<String, String>>,
        cover: Pair<ByteArray, String>?,
    ) {
        applyPerFileBuffers(buffers)
        if (cover != null) {
            _state.update { state ->
                val edits = state.edits.toMutableMap()
                for (id in buffers.keys) {
                    edits[id] = (edits[id] ?: PendingEdits.EMPTY)
                        .copy(cover = CoverEdit.Replace(cover.first, cover.second))
                }
                state.copy(edits = edits)
            }
        }
    }

    // ---- playlists ----

    /** Writes one playlist per group into the session root directory. */
    fun generatePlaylists(groups: Map<String, List<Long>>) {
        val snapshot = _state.value
        val rootDir = snapshot.rootDir ?: return
        if (groups.isEmpty()) return
        viewModelScope.launch {
            val byId = snapshot.files.associateBy { it.id }
            val errors = ArrayList<SaveError>()
            val written = ArrayList<String>()
            for ((name, ids) in groups) {
                val files = ids.mapNotNull { byId[it] }
                if (files.isEmpty()) continue
                try {
                    written += playlistGenerator.write(rootDir, name, files)
                } catch (e: Exception) {
                    errors += SaveError("$name.m3u8", e.message ?: "Write failed")
                }
            }
            _state.update {
                it.copy(
                    lastSaveErrors = errors,
                    notice = if (written.isNotEmpty()) {
                        "Created ${written.size} playlist(s) in ${rootDir.name}"
                    } else {
                        it.notice
                    },
                )
            }
        }
    }

    private fun updateSelectedEdits(transform: (PendingEdits) -> PendingEdits) {
        _state.update { state ->
            val edits = state.edits.toMutableMap()
            for (id in state.selection) {
                edits[id] = transform(edits[id] ?: PendingEdits.EMPTY)
            }
            state.copy(edits = edits)
        }
    }

    private fun sorted(files: List<LoadedFile>, sort: SortSpec): List<LoadedFile> =
        files.sortedWith(SortSpecs.comparator(sort))

    private companion object {
        const val PUBLISH_EVERY = 25
    }
}
