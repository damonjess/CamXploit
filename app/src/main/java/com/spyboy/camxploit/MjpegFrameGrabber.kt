package com.spyboy.camxploit

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import kotlinx.coroutines.*
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class MjpegFrameGrabber(private val streamUrl: String) {

    private val JPEG_SOI = byteArrayOf(0xFF.toByte(), 0xD8.toByte()) // JPEG start
    private val JPEG_EOI = byteArrayOf(0xFF.toByte(), 0xD9.toByte()) // JPEG end

    /**
     * Connects to an MJPEG stream and emits decoded Bitmaps.
     * Parses the multipart/x-mixed-replace stream by scanning for
     * JPEG SOI/EOI markers directly — works regardless of boundary format.
     *
     * @param onFrame  Called with each decoded Bitmap (called on IO thread)
     * @param onError  Called if connection or parsing fails
     */
    suspend fun stream(
        onFrame: (Bitmap) -> Unit,
        onError: (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        try {
            val conn = (URL(streamUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5_000
                readTimeout    = 10_000
                requestMethod  = "GET"
                setRequestProperty("Accept", "multipart/x-mixed-replace, image/jpeg")
            }

            if (conn.responseCode != 200) {
                onError("HTTP ${conn.responseCode}")
                return@withContext
            }

            val input: InputStream = conn.inputStream
            val buffer = ByteArrayOutputStream()
            val readBuf = ByteArray(4096)

            var inJpeg = false
            var frameCount = 0

            while (isActive) {
                val bytesRead = input.read(readBuf)
                if (bytesRead == -1) break

                for (i in 0 until bytesRead) {
                    val b = readBuf[i]
                    buffer.write(b.toInt())

                    val data = buffer.toByteArray()
                    val size = data.size

                    // Detect JPEG start
                    if (!inJpeg && size >= 2 &&
                        data[size - 2] == JPEG_SOI[0] &&
                        data[size - 1] == JPEG_SOI[1]) {
                        inJpeg = true
                        buffer.reset()
                        buffer.write(JPEG_SOI)
                        continue
                    }

                    // Detect JPEG end
                    if (inJpeg && size >= 2 &&
                        data[size - 2] == JPEG_EOI[0] &&
                        data[size - 1] == JPEG_EOI[1]) {
                        inJpeg = false
                        val jpegBytes = buffer.toByteArray()
                        buffer.reset()

                        val bitmap = BitmapFactory.decodeByteArray(
                            jpegBytes, 0, jpegBytes.size
                        )
                        if (bitmap != null) {
                            frameCount++
                            onFrame(bitmap)
                            // Throttle: process max 2 frames/sec for TFLite
                            delay(500)
                        }
                    }
                }
            }
            conn.disconnect()
        } catch (e: Exception) {
            onError(e.message ?: "Stream error")
        }
    }
}
