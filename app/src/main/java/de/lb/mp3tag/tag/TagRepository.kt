package de.lb.mp3tag.tag

import de.lb.mp3tag.domain.AudioProps
import de.lb.mp3tag.domain.LoadedFile
import de.lb.mp3tag.domain.TagFields
import de.lb.mp3tag.storage.SafFileRef
import de.lb.mp3tag.storage.SafStorage
import java.util.concurrent.atomic.AtomicLong

class TagRepository(
    private val tagIo: TagIo,
    private val storage: SafStorage,
) {

    private val nextId = AtomicLong(1)

    /** Reads a document into a [LoadedFile]. Unreadable tags yield empty fields. */
    suspend fun load(
        ref: SafFileRef,
        sizeBytes: Long,
        lastModified: Long,
        id: Long = nextId.getAndIncrement(),
    ): LoadedFile {
        val tags = runCatching { tagIo.readMetadata(ref.documentUri)?.propertyMap?.toTagFields() }
            .getOrNull() ?: TagFields.EMPTY
        val props = runCatching { tagIo.readAudioProperties(ref.documentUri) }.getOrNull()?.let {
            AudioProps(
                lengthMs = it.length,
                bitrateKbps = it.bitrate,
                sampleRateHz = it.sampleRate,
                channels = it.channels,
            )
        }
        return LoadedFile(
            id = id,
            file = ref,
            sizeBytes = sizeBytes,
            lastModified = lastModified,
            props = props,
            tags = tags,
        )
    }

    /** Re-reads a file after a save, keeping its session id. */
    suspend fun reload(loaded: LoadedFile): LoadedFile {
        val ref = loaded.file as SafFileRef
        val meta = storage.queryMeta(ref.documentUri)
        return load(
            ref = ref,
            sizeBytes = meta?.first ?: loaded.sizeBytes,
            lastModified = meta?.second ?: loaded.lastModified,
            id = loaded.id,
        )
    }
}
