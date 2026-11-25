package com.spyboy.camxploit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
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
 * snapshot, or no useful data at all. The first frame is kept as a small
 * thumbnail so the feed picker can preview each candidate.
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
        val candidateCount: Int = 0,
        val candidateUrl: String = "",
        val thumbnail: Bitmap? = null
    )

    private const val CONNECT_TIMEOUT_MS = 4_000
    private const val READ_TIMEOUT_MS = 4_500
    private const val MAX_FRAME_BYTES = 2 * 1024 * 1024
    private const val MAX_MANIFEST_BYTES = 256 * 1024
    private const val MAX_CONCURRENT_CHECKS = 5
    private const val THUMBNAIL_MAX_WIDTH = 320
    private const val THUMBNAIL_MAX_HEIGHT = 240
    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"
    private const val ACCEPT =
        "multipart/x-mixed-replace, image/jpeg, image/*, */*;q=0.1"

    /**
     * Checks every candidate, in parallel with a small concurrency limit, and
     * invokes [onResult] on the main thread as each one finishes. No candidate
     * is skipped: the caller decides what to do with playable and failed URLs.
     */
    suspend fun verifyAll(
        urls: List<String>,
        onResult: suspend (Result) -> Unit = {}
    ): List<Result> = coroutineScope {
        val candidates = urls.asSequence()
            .map { it.trim() }
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
            .toList()
        val gate = Semaphore(MAX_CONCURRENT_CHECKS)

        candidates.mapIndexed { index, url ->
            async(Dispatchers.IO) {
                val result = gate.withPermit { verifyOne(url, index + 1, candidates.size) }
                withContext(Dispatchers.Main) { onResult(result) }
                result
            }
        }.awaitAll()
    }

    /** Prefers a verified MJPEG stream, then falls back to a verified snapshot. */
    fun best(results: List<Result>): Result? =
        results.firstOrNull { it.isPlayable && it.protocol == "mjpeg" }
            ?: results.firstOrNull { it.isPlayable && it.protocol == "hls" }
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
                candidateCount = total,
                candidateUrl = rawUrl
            )

            when {
                code == 401 || code == 403 -> fail("Needs login", "HTTP $code - credentials required")
                code !in 200..299 -> fail("Unavailable", "HTTP $code")
                type.contains("text/html") || type.contains("text/xml") ||
                    type.contains("application/json") -> fail("Not a feed", "HTML/XML response, not video")
                type.startsWith("video/") -> fail("Unsupported video", type)
                looksLikeHls(finalUrl, type) -> {
                    val manifest = try {
                        readManifest(connection.inputStream)
                    } catch (_: SocketTimeoutException) {
                        null
                    }
                    if (manifest?.contains("#EXTM3U") == true) {
                        Result(
                            url = finalUrl,
                            protocol = "hls",
                            contentType = rawType,
                            label = "Verified live",
                            detail = "HLS playlist received",
                            responseCode = code,
                            isPlayable = true,
                            candidateIndex = index,
                            candidateCount = total,
                            candidateUrl = rawUrl
                        )
                    } else {
                        fail("No playlist", "No HLS manifest received")
                    }
                }
                else -> {
                    val frame = try {
                        readFirstJpeg(connection.inputStream)
                    } catch (_: SocketTimeoutException) {
                        null
                    }

                    if (frame == null) {
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
                            candidateCount = total,
                            candidateUrl = rawUrl,
                            thumbnail = decodeThumbnail(frame)
                        )
                    }
                }
            }
        } catch (_: SocketTimeoutException) {
            Result(
                url = rawUrl,
                protocol = "",
                contentType = "",
                label = "No frame",
                detail = "Timed out waiting for a frame",
                responseCode = 0,
                isPlayable = false,
                candidateIndex = index,
                candidateCount = total,
                candidateUrl = rawUrl
            )
        } catch (e: Exception) {
            Result(
                url = rawUrl,
                protocol = "",
                contentType = "",
                label = "Unavailable",
                detail = e.message?.take(100) ?: "Connection failed",
                responseCode = 0,
                isPlayable = false,
                candidateIndex = index,
                candidateCount = total,
                candidateUrl = rawUrl
            )
        } finally {
            try {
                connection?.disconnect()
            } catch (_: Exception) {
            }
        }
    }

    /**
     * Reads until the first complete JPEG frame and returns its bytes.
     * MJPEG boundary/header data before the frame is ignored.
     */
    private fun readFirstJpeg(input: InputStream): ByteArray? {
        val out = ByteArrayOutputStream(64 * 1024)
        val buffer = ByteArray(8192)
        var previous = -1
        var inJpeg = false
        var startOffset = 0
        var total = 0

        while (total < MAX_FRAME_BYTES) {
            val read = input.read(buffer)
            if (read <= 0) return null
            val sizeBeforeRead = out.size()
            out.write(buffer, 0, read)
            total += read
            for (i in 0 until read) {
                val current = buffer[i].toInt() and 0xFF
                if (!inJpeg) {
                    if (previous == 0xFF && current == 0xD8) {
                        inJpeg = true
                        startOffset = sizeBeforeRead + i - 1
                    }
                } else if (previous == 0xFF && current == 0xD9) {
                    val all = out.toByteArray()
                    if (startOffset < 0 || startOffset >= all.size) return null
                    return all.copyOfRange(startOffset, all.size)
                }
                previous = current
            }
        }
        return null
    }

    /** Reads the short text body of an HLS manifest. */
    private fun readManifest(input: InputStream): String? {
        val out = ByteArrayOutputStream(16 * 1024)
        val buffer = ByteArray(8192)
        var total = 0
        while (total < MAX_MANIFEST_BYTES) {
            val read = input.read(buffer)
            if (read <= 0) break
            out.write(buffer, 0, read)
            total += read
        }
        return out.toByteArray().toString(Charsets.UTF_8)
            .takeIf { it.contains("#EXTM3U") }
    }

    private fun looksLikeHls(url: String, contentType: String): Boolean {
        val clean = url.substringBefore('?').lowercase(Locale.US)
        return clean.endsWith(".m3u8") ||
            contentType.contains("mpegurl") ||
            contentType.contains("m3u8")
    }

    /** Decodes a small preview so the picker never holds full camera frames. */
    private fun decodeThumbnail(frame: ByteArray): Bitmap? {
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(frame, 0, frame.size, bounds)
            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

            var sampleSize = 1
            while (bounds.outWidth / (sampleSize * 2) >= THUMBNAIL_MAX_WIDTH ||
                bounds.outHeight / (sampleSize * 2) >= THUMBNAIL_MAX_HEIGHT
            ) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeByteArray(frame, 0, frame.size, options)
        } catch (_: Exception) {
            null
        }
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
