package de.lb.mp3tag.storage

import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.tag.TagRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

sealed interface ScanEvent {
    data class Total(val count: Int) : ScanEvent
    data class Loaded(val file: LoadedFile) : ScanEvent
}

class FileScanner(
    private val storage: SafStorage,
    private val repository: TagRepository,
) {

    /**
     * Enumerates audio documents under [dir] (fast child queries), then reads
     * their tags with bounded parallelism, emitting each file as soon as it
     * is ready.
     */
    fun scan(dir: SafStorage.Dir, recursive: Boolean): Flow<ScanEvent> = channelFlow {
        val entries = storage.enumerateAudio(dir, recursive)
        send(ScanEvent.Total(entries.size))
        for (entry in entries) {
            launch {
                send(ScanEvent.Loaded(repository.load(entry.ref, entry.sizeBytes, entry.lastModified)))
            }
        }
    }.flowOn(Dispatchers.IO)
}
