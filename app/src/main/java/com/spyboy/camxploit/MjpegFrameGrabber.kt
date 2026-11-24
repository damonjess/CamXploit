package com.spyboy.camxploit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resilient MJPEG frame grabber.
 * Parses multipart/x-mixed-replace stream by scanning for JPEG SOI (0xFFD8)
 * and EOI (0xFFD9) markers with automatic retry reconnection for network resiliency.
 */
class MjpegFrameGrabber(
    private val streamUrl: String,
    private val frameDelayMs: Long = 0L
) {

    private class ReusableByteArrayOutputStream(initialCapacity: Int = 128 * 1024) :
        ByteArrayOutputStream(initialCapacity)

    /**
     * Connects to an MJPEG stream and emits decoded Bitmaps with automatic reconnect on transient errors.
     *
     * @param onFrame Called with each decoded Bitmap (called on IO thread)
     * @param onError Called if connection or parsing repeatedly fails after retries
     */
    suspend fun stream(
        onFrame: suspend (Bitmap) -> Unit,
        onError: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val frameBuffer = ReusableByteArrayOutputStream(128 * 1024)
        val readBuf = ByteArray(8192)
        var retryCount = 0
        val maxRetries = 5
        var emptySessions = 0
        val firstFrameTimeoutMs = 10_000L

        while (isActive && retryCount < maxRetries) {
            var conn: HttpURLConnection? = null
            var frameReceivedInSession = false

            try {
                conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 8_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "multipart/x-mixed-replace, image/jpeg, */*")
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val responseCode = conn.responseCode
                if (responseCode == 401 || responseCode == 403) {
                    onError("HTTP $responseCode - this endpoint needs a login")
                    return@withContext
                }
                if (responseCode !in 200..299) {
                    retryCount++
                    if (retryCount >= maxRetries) {
                        onError("HTTP $responseCode")
                        return@withContext
                    }
                    delay(1000)
                    continue
                }

                // A login page, an XML error or a JSON answer is never a video feed.
                val contentType = conn.contentType?.lowercase().orEmpty()
                if (contentType.contains("text/html") || contentType.contains("text/xml") ||
                    contentType.contains("application/json")) {
                    onError("Not a video stream (${conn.contentType})")
                    return@withContext
                }

                val input: InputStream = conn.inputStream
                var prevByte = -1
                var inJpeg = false
                val sessionStartedAt = System.currentTimeMillis()

                while (isActive) {
                    // Bail out when a 2xx response never produces a frame.
                    if (!frameReceivedInSession &&
                        System.currentTimeMillis() - sessionStartedAt > firstFrameTimeoutMs) break

                    val bytesRead = input.read(readBuf)
                    if (bytesRead == -1) break

                    for (i in 0 until bytesRead) {
                        val curr = readBuf[i].toInt() and 0xFF

                        if (!inJpeg) {
                            if (prevByte == 0xFF && curr == 0xD8) {
                                inJpeg = true
                                frameBuffer.reset()
                                frameBuffer.write(0xFF)
                                frameBuffer.write(0xD8)
                            }
                        } else {
                            frameBuffer.write(curr)

                            if (prevByte == 0xFF && curr == 0xD9) {
                                inJpeg = false
                                val frameLength = frameBuffer.size()

                                if (frameLength > 2) {
                                    val data = frameBuffer.toByteArray()
                                    val bitmap = BitmapFactory.decodeByteArray(data, 0, frameLength)

                                    if (bitmap != null) {
                                        retryCount = 0 // Reset retries on successful frame decode
                                        frameReceivedInSession = true
                                        onFrame(bitmap)

                                        if (frameDelayMs > 0) {
                                            delay(frameDelayMs)
                                        }
                                    }
                                }
                                frameBuffer.reset()
                            }
                        }
                        prevByte = curr
                    }
                }

                if (frameReceivedInSession) {
                    emptySessions = 0
                } else {
                    emptySessions++
                    if (emptySessions >= 2) {
                        onError("No video frames received from $streamUrl")
                        return@withContext
                    }
                    delay(500)
                }
            } catch (e: java.net.SocketTimeoutException) {
                if (!isActive) break
                if (!frameReceivedInSession) {
                    emptySessions++
                    if (emptySessions >= 2) {
                        onError("Timed out waiting for a video frame from $streamUrl")
                        return@withContext
                    }
                }
                retryCount++
                if (retryCount >= maxRetries) {
                    onError("Stream timed out after $maxRetries attempts")
                    return@withContext
                }
                delay(500)
            } catch (e: Exception) {
                if (!isActive) break
                retryCount++
                if (retryCount >= maxRetries && !frameReceivedInSession) {
                    onError(e.message ?: "Stream connection error")
                    return@withContext
                }
                delay(1000)
            } finally {
                try {
                    conn?.disconnect()
                } catch (_: Exception) {}
            }
        }

        if (isActive && retryCount >= maxRetries) {
            onError("Stream unavailable after $maxRetries attempts")
        }
    }
}
