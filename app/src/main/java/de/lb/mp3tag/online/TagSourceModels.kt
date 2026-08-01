package de.lb.mp3tag.online

enum class TagSourceKind(val label: String) {
    MUSICBRAINZ("MusicBrainz"),
    DISCOGS("Discogs"),
}

data class ReleaseSearchResult(
    val source: TagSourceKind,
    val id: String,
    val title: String,
    val artist: String,
    val year: String?,
    val extra: String?,
)

data class ReleaseTrack(
    val disc: Int,
    val position: Int,
    val title: String,
    val artist: String?,
    val lengthMs: Int?,
)

data class ReleaseDetail(
    val source: TagSourceKind,
    val id: String,
    val album: String,
    val albumArtist: String,
    val year: String?,
    val genre: String?,
    val tracks: List<ReleaseTrack>,
    val coverUrl: String?,
)

interface TagSourceClient {
    val kind: TagSourceKind
    suspend fun search(query: String): List<ReleaseSearchResult>
    suspend fun release(id: String): ReleaseDetail
    suspend fun coverBytes(detail: ReleaseDetail): Pair<ByteArray, String>?
}
