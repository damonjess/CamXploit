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

        while (isActive && retryCount < maxRetries) {
            var conn: HttpURLConnection? = null
            var frameReceivedInSession = false

            try {
                conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 15_000
                    requestMethod = "GET"
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    setRequestProperty("Accept", "multipart/x-mixed-replace, image/jpeg, */*")
                    setRequestProperty("Connection", "keep-alive")
                    setRequestProperty("Cache-Control", "no-cache")
                }

                val responseCode = conn.responseCode
                if (responseCode !in 200..299) {
                    retryCount++
                    if (retryCount >= maxRetries) {
                        onError("HTTP $responseCode")
                        return@withContext
                    }
                    delay(1000)
                    continue
                }

                val input: InputStream = conn.inputStream
                var prevByte = -1
                var inJpeg = false

                while (isActive) {
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
    }
}
