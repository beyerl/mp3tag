package de.lb.mp3tag.tag

import android.content.ContentResolver
import android.net.Uri
import com.kyant.taglib.AudioProperties
import com.kyant.taglib.Metadata
import com.kyant.taglib.Picture
import com.kyant.taglib.PropertyMap
import com.kyant.taglib.TagLib
import de.lb.mp3tag.domain.TagFields
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException

/**
 * Sole gateway to the native TagLib library. Files are opened through the
 * ContentResolver (SAF document URIs — no global storage permission); every
 * native call consumes a detached file descriptor, and this class owns that
 * lifecycle so raw fds never escape. JNI concurrency is bounded.
 */
class TagIo(
    private val resolver: ContentResolver,
    parallelism: Int = 4,
) {

    private val semaphore = Semaphore(parallelism)

    suspend fun readMetadata(uri: Uri, readPictures: Boolean = false): Metadata? =
        withFd(uri, "r") { fd -> TagLib.getMetadata(fd, readPictures) }

    suspend fun readAudioProperties(uri: Uri): AudioProperties? =
        withFd(uri, "r") { fd -> TagLib.getAudioProperties(fd) }

    suspend fun readPictures(uri: Uri): Array<Picture> =
        withFd(uri, "r") { fd -> TagLib.getPictures(fd) }

    suspend fun writeProperties(uri: Uri, propertyMap: PropertyMap): Boolean =
        withFd(uri, "rw") { fd -> TagLib.savePropertyMap(fd, propertyMap) }

    suspend fun writePictures(uri: Uri, pictures: Array<Picture>): Boolean =
        withFd(uri, "rw") { fd -> TagLib.savePictures(fd, pictures) }

    private suspend fun <T> withFd(uri: Uri, mode: String, block: (Int) -> T): T =
        withContext(Dispatchers.IO) {
            semaphore.withPermit {
                val pfd = resolver.openFileDescriptor(uri, mode)
                    ?: throw IOException("Cannot open file")
                pfd.use {
                    // The native side takes ownership of the detached fd.
                    block(it.dup().detachFd())
                }
            }
        }
}

fun TagFields.toPropertyMap(): PropertyMap {
    val result = PropertyMap()
    for ((name, values) in asMap()) {
        result[name] = values.toTypedArray()
    }
    return result
}

fun PropertyMap.toTagFields(): TagFields =
    TagFields.from(entries.associate { (name, values) -> name to values.toList() })
