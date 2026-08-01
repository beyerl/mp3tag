package de.lb.mp3tag.online

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.IOException

@Serializable
private data class DgSearchResponse(val results: List<DgResult> = emptyList())

@Serializable
private data class DgResult(
    val id: Long,
    val title: String = "",
    val year: String? = null,
    val country: String? = null,
    val format: List<String> = emptyList(),
)

@Serializable
private data class DgRelease(
    val id: Long,
    val title: String = "",
    val year: Int? = null,
    val artists: List<DgArtist> = emptyList(),
    val genres: List<String> = emptyList(),
    val styles: List<String> = emptyList(),
    val tracklist: List<DgTrack> = emptyList(),
    val images: List<DgImage> = emptyList(),
)

@Serializable
private data class DgArtist(val name: String = "")

@Serializable
private data class DgTrack(
    val position: String = "",
    val title: String = "",
    val duration: String = "",
)

@Serializable
private data class DgImage(val uri: String = "", val type: String = "")

/**
 * Discogs API with a user-supplied personal access token (Settings).
 * Rate limit with a token is 60 requests/minute.
 */
class DiscogsClient(
    private val http: OkHttpClient,
    private val tokenProvider: suspend () -> String?,
) : TagSourceClient {

    override val kind = TagSourceKind.DISCOGS

    private val json = Json { ignoreUnknownKeys = true }
    private val limiter = RateLimiter(minIntervalMs = 1100)

    private suspend fun headers(): Map<String, String> {
        val token = tokenProvider()?.takeIf { it.isNotBlank() }
            ?: throw IOException("Discogs needs a personal access token — add it in Settings")
        return mapOf(
            "User-Agent" to USER_AGENT,
            "Authorization" to "Discogs token=$token",
        )
    }

    override suspend fun search(query: String): List<ReleaseSearchResult> {
        val url = "https://api.discogs.com/database/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("type", "release")
            .addQueryParameter("per_page", "25")
            .build()
        val body = limiter.withPermit { http.getText(url.toString(), headers()) }
        return json.decodeFromString<DgSearchResponse>(body).results.map { result ->
            // Discogs search titles are "Artist - Title".
            val artist = result.title.substringBefore(" - ", "")
            val title = result.title.substringAfter(" - ", result.title)
            ReleaseSearchResult(
                source = kind,
                id = result.id.toString(),
                title = title,
                artist = artist,
                year = result.year,
                extra = listOfNotNull(result.country, result.format.firstOrNull())
                    .joinToString(", ").ifEmpty { null },
            )
        }
    }

    override suspend fun release(id: String): ReleaseDetail {
        val body = limiter.withPermit {
            http.getText("https://api.discogs.com/releases/$id", headers())
        }
        val release = json.decodeFromString<DgRelease>(body)
        var lastDisc = 1
        val tracks = release.tracklist
            .filter { it.position.isNotBlank() } // skip headings
            .mapIndexed { index, track ->
                val disc = track.position.substringBefore('-', "").toIntOrNull() ?: lastDisc
                lastDisc = disc
                val position = track.position.substringAfterLast('-')
                    .filter { it.isDigit() }
                    .toIntOrNull() ?: (index + 1)
                ReleaseTrack(
                    disc = disc,
                    position = position,
                    title = track.title,
                    artist = null,
                    lengthMs = parseDuration(track.duration),
                )
            }
        return ReleaseDetail(
            source = kind,
            id = release.id.toString(),
            album = release.title,
            albumArtist = release.artists.joinToString(", ") { it.name }.ifEmpty { "" },
            year = release.year?.toString(),
            genre = (release.styles.firstOrNull() ?: release.genres.firstOrNull()),
            tracks = tracks,
            coverUrl = release.images.firstOrNull { it.type == "primary" }?.uri
                ?: release.images.firstOrNull()?.uri,
        )
    }

    override suspend fun coverBytes(detail: ReleaseDetail): Pair<ByteArray, String>? {
        val url = detail.coverUrl?.takeIf { it.isNotBlank() } ?: return null
        return limiter.withPermit { http.getBytes(url, mapOf("User-Agent" to USER_AGENT)) }
    }

    private fun parseDuration(duration: String): Int? {
        if (duration.isBlank()) return null
        val parts = duration.split(':').map { it.trim().toIntOrNull() ?: return null }
        return when (parts.size) {
            2 -> (parts[0] * 60 + parts[1]) * 1000
            3 -> ((parts[0] * 60 + parts[1]) * 60 + parts[2]) * 1000
            else -> null
        }
    }
}
