package com.spyboy.camxploit.osint

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

object ThumbnailResolver {

    private val SNAPSHOT_GUESSES = listOf(
        "/snapshot.jpg", "/current.jpg", "/image.jpg",
        "/img/snapshot.cgi", "/cgi-bin/snapshot.cgi",
        "/tmpfs/auto.jpg", "/snap.jpg", "/pic.jpg"
    )

    /**
     * Tries to fetch a thumbnail for a camera.
     * 1. Uses existing thumbnailUrl if valid
     * 2. Guesses common snapshot paths
     * 3. Falls back to first frame of MJPEG stream
     */
    suspend fun resolve(camera: com.spyboy.camxploit.StreamSource): Bitmap? = withContext(Dispatchers.IO) {
        // Try existing thumbnail first
        if (!camera.thumbnailUrl.isNullOrBlank()) {
            fetchBitmap(camera.thumbnailUrl)?.let { return@withContext it }
        }

        // Try snapshot guesses based on stream/page URL
        val base = if (camera.streamUrl.isNotBlank()) camera.streamUrl else camera.pageUrl
        if (base.isNotBlank()) {
            // If it's a direct MJPEG stream URL, try to guess snapshots on same host/path
            val baseUrl = base.substringBeforeLast("/")
            for (guess in SNAPSHOT_GUESSES) {
                val url = baseUrl + guess
                fetchBitmap(url)?.let { return@withContext it }
            }
        }

        // Fallback: grab first frame from MJPEG stream
        val targetUrl = if (base.isNotBlank()) base else camera.url
        if (targetUrl.contains("mjpg", ignoreCase = true) || 
            targetUrl.contains("mjpeg", ignoreCase = true) ||
            targetUrl.contains("cgi-bin", ignoreCase = true)) {
            return@withContext fetchFirstMjpegFrame(targetUrl)
        }

        null
    }

    private fun fetchBitmap(url: String): Bitmap? {
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                doInput = true
            }
            val bmp = conn.inputStream.use { BitmapFactory.decodeStream(it) }
            conn.disconnect()
            bmp
        } catch (_: Exception) { null }
    }

    private fun fetchFirstMjpegFrame(mjpegUrl: String): Bitmap? {
        return try {
            val conn = URL(mjpegUrl).openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                doInput = true
            }
            val stream = conn.inputStream.buffered()
            val buffer = java.io.ByteArrayOutputStream(65536)
            var prev = 0
            var curr: Int
            while (true) {
                curr = stream.read()
                if (curr == -1) break
                if (prev == 0xFF && curr == 0xD8) {
                    buffer.reset()
                    buffer.write(0xFF)
                    buffer.write(0xD8)
                } else if (prev == 0xFF && curr == 0xD9) {
                    buffer.write(curr)
                    val frame = buffer.toByteArray()
                    conn.disconnect()
                    return BitmapFactory.decodeByteArray(frame, 0, frame.size)
                } else {
                    buffer.write(curr)
                }
                prev = curr
            }
            conn.disconnect()
            null
        } catch (_: Exception) { null }
    }
}
