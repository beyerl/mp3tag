package de.lb.mp3tag.online

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException

const val USER_AGENT = "Mp3tagAndroid/0.3 (https://github.com/beyerl/mp3tag)"

/** Serializes requests and enforces a minimum interval between them. */
class RateLimiter(private val minIntervalMs: Long) {

    private val mutex = Mutex()
    private var lastRequestAt = 0L

    suspend fun <T> withPermit(block: suspend () -> T): T = mutex.withLock {
        val wait = lastRequestAt + minIntervalMs - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        try {
            block()
        } finally {
            lastRequestAt = System.currentTimeMillis()
        }
    }
}

suspend fun OkHttpClient.getText(url: String, headers: Map<String, String>): String =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code} from ${response.request.url.host}")
            }
            response.body?.string() ?: throw IOException("Empty response")
        }
    }

/** @return body bytes and content type, or null on 404. */
suspend fun OkHttpClient.getBytes(url: String, headers: Map<String, String>): Pair<ByteArray, String>? =
    withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            headers.forEach { (name, value) -> header(name, value) }
        }.build()
        newCall(request).execute().use { response ->
            when {
                response.code == 404 -> null
                !response.isSuccessful ->
                    throw IOException("HTTP ${response.code} from ${response.request.url.host}")
                else -> {
                    val bytes = response.body?.bytes() ?: throw IOException("Empty response")
                    bytes to (response.body?.contentType()?.toString() ?: "image/jpeg")
                }
            }
        }
    }
