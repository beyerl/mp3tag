package de.lb.mp3tag.online

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient

@Serializable
private data class MbSearchResponse(val releases: List<MbRelease> = emptyList())

@Serializable
private data class MbRelease(
    val id: String,
    val title: String = "",
    val date: String? = null,
    val country: String? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    @SerialName("track-count") val trackCount: Int? = null,
    val media: List<MbMedium> = emptyList(),
)

@Serializable
private data class MbArtistCredit(val name: String = "", val joinphrase: String = "")

@Serializable
private data class MbMedium(val position: Int = 1, val tracks: List<MbTrack> = emptyList())

@Serializable
private data class MbTrack(
    val position: Int = 0,
    val title: String = "",
    val length: Int? = null,
    @SerialName("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
)

/**
 * MusicBrainz web service v2 (no auth; mandatory User-Agent; max 1 request
 * per second) plus the Cover Art Archive for front covers.
 */
class MusicBrainzClient(private val http: OkHttpClient) : TagSourceClient {

    override val kind = TagSourceKind.MUSICBRAINZ

    private val json = Json { ignoreUnknownKeys = true }
    private val limiter = RateLimiter(minIntervalMs = 1100)
    private val headers = mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")

    override suspend fun search(query: String): List<ReleaseSearchResult> {
        val url = "https://musicbrainz.org/ws/2/release/".toHttpUrl().newBuilder()
            .addQueryParameter("query", query)
            .addQueryParameter("fmt", "json")
            .addQueryParameter("limit", "25")
            .build()
        val body = limiter.withPermit { http.getText(url.toString(), headers) }
        return json.decodeFromString<MbSearchResponse>(body).releases.map { release ->
            ReleaseSearchResult(
                source = kind,
                id = release.id,
                title = release.title,
                artist = release.artistCredit.credit(),
                year = release.date?.take(4),
                extra = listOfNotNull(
                    release.country,
                    release.trackCount?.let { "$it tracks" },
                ).joinToString(", ").ifEmpty { null },
            )
        }
    }

    override suspend fun release(id: String): ReleaseDetail {
        val url = "https://musicbrainz.org/ws/2/release/$id" +
            "?inc=recordings+artist-credits&fmt=json"
        val body = limiter.withPermit { http.getText(url, headers) }
        val release = json.decodeFromString<MbRelease>(body)
        val albumArtist = release.artistCredit.credit()
        val tracks = release.media.flatMap { medium ->
            medium.tracks.map { track ->
                ReleaseTrack(
                    disc = medium.position,
                    position = track.position,
                    title = track.title,
                    artist = track.artistCredit.credit().ifEmpty { null },
                    lengthMs = track.length,
                )
            }
        }
        return ReleaseDetail(
            source = kind,
            id = release.id,
            album = release.title,
            albumArtist = albumArtist,
            year = release.date?.take(4),
            genre = null,
            tracks = tracks,
            coverUrl = "https://coverartarchive.org/release/${release.id}/front-500",
        )
    }

    override suspend fun coverBytes(detail: ReleaseDetail): Pair<ByteArray, String>? {
        val url = detail.coverUrl ?: return null
        return limiter.withPermit { http.getBytes(url, mapOf("User-Agent" to USER_AGENT)) }
    }
}

private fun List<MbArtistCredit>.credit(): String =
    joinToString("") { it.name + it.joinphrase }
