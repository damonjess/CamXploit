package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.util.Locale

/**
 * Checks browser-discovered URLs before the player opens.
 *
 * A candidate is only treated as playable after a complete JPEG frame has been
 * read. This catches cameras that answer with HTML, a login page, a stale
 * snapshot, or no useful data at all.
 */
object StreamPreflight {

    data class Result(
        val url: String,
        val protocol: String,
        val contentType: String,
        val label: String,
        val detail: String,
        val responseCode: Int,
        val isPlayable: Boolean,
        val candidateIndex: Int = 0,
        val candidateCount: Int = 0
    )

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_500
    private const val MAX_CANDIDATES = 4
    private const val MAX_FRAME_BYTES = 2 * 1024 * 1024
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val ACCEPT =
        "multipart/x-mixed-replace, image/jpeg, image/*, */*;q=0.1"

    suspend fun verifyAll(urls: List<String>): List<Result> = withContext(Dispatchers.IO) {
        val candidates = urls.asSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
            .take(MAX_CANDIDATES)
            .toList()

        val results = mutableListOf<Result>()
        for ((index, url) in candidates.withIndex()) {
            val result = verifyOne(url, index + 1, candidates.size)
            results += result
            // Stop as soon as a real live feed is confirmed. Otherwise keep
            // trying alternates, then fall back to a verified snapshot.
            if (result.isPlayable && result.protocol == "mjpeg") break
        }
        results
    }

    /** Prefers a verified MJPEG stream, then falls back to a verified snapshot. */
    fun best(results: List<Result>): Result? =
        results.firstOrNull { it.isPlayable && it.protocol == "mjpeg" }
            ?: results.firstOrNull { it.isPlayable && it.protocol == "snapshot" }

    private fun verifyOne(rawUrl: String, index: Int, total: Int): Result {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(rawUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                useCaches = false
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", ACCEPT)
                setRequestProperty("Cache-Control", "no-cache")
            }

            val code = connection.responseCode
            val rawType = connection.contentType.orEmpty()
            val type = rawType.substringBefore(';').trim().lowercase(Locale.US)
            val finalUrl = connection.url.toString()

            fun fail(label: String, detail: String): Result = Result(
                url = finalUrl,
                protocol = "",
                contentType = rawType,
                label = label,
                detail = detail,
                responseCode = code,
                isPlayable = false,
                candidateIndex = index,
                candidateCount = total
            )

            when {
                code == 401 || code == 403 -> fail("Needs login", "HTTP $code - credentials required")
                code !in 200..299 -> fail("Unavailable", "HTTP $code")
                type.contains("text/html") || type.contains("text/xml") ||
                    type.contains("application/json") -> fail("Not a feed", "HTML/XML response, not video")
                type.startsWith("video/") -> fail("Unsupported video", type)
                else -> {
                    val hasFrame = try {
                        readFirstJpeg(connection.inputStream)
                    } catch (_: SocketTimeoutException) {
                        false
                    }

                    if (!hasFrame) {
                        fail("No frame", "No JPEG frame within ${READ_TIMEOUT_MS / 1000}s")
                    } else {
                        val protocol = when {
                            type.contains("multipart") || type.contains("mixed-replace") ||
                                type.contains("mjpeg") -> "mjpeg"
                            type.startsWith("image/") -> "snapshot"
                            looksLikeMjpeg(finalUrl) -> "mjpeg"
                            else -> "snapshot"
                        }
                        val live = protocol == "mjpeg"
                        Result(
                            url = finalUrl,
                            protocol = protocol,
                            contentType = rawType,
                            label = if (live) "Verified live" else "Snapshot",
                            detail = if (live) "Live MJPEG frame received" else "Snapshot image received",
                            responseCode = code,
                            isPlayable = true,
                            candidateIndex = index,
                            candidateCount = total
                        )
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            Result(rawUrl, "", "", "No frame", "Timed out waiting for a frame", 0, false, index, total)
        } catch (e: Exception) {
            Result(
                rawUrl,
                "",
                "",
                "Unavailable",
                e.message?.take(100) ?: "Connection failed",
                0,
                false,
                index,
                total
            )
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /** Reads until the first complete JPEG frame; MJPEG boundary data is ignored. */
    private fun readFirstJpeg(input: InputStream): Boolean {
        val buffer = ByteArray(8192)
        var previous = -1
        var inJpeg = false
        var total = 0

        while (total < MAX_FRAME_BYTES) {
            val read = input.read(buffer)
            if (read <= 0) return false
            total += read
            for (i in 0 until read) {
                val current = buffer[i].toInt() and 0xFF
                if (!inJpeg) {
                    if (previous == 0xFF && current == 0xD8) inJpeg = true
                } else if (previous == 0xFF && current == 0xD9) {
                    return true
                }
                previous = current
            }
        }
        return false
    }

    private fun looksLikeMjpeg(url: String): Boolean {
        val lower = url.lowercase(Locale.US)
        if (lower.contains("snapshot") || lower.contains("snap.jpg")) return false
        return listOf(
            "mjpg", "mjpeg", "faststream", "action=stream", "videostream",
            "video.cgi", "getvideo", "/stream", "?stream"
        ).any { lower.contains(it) }
    }
}
