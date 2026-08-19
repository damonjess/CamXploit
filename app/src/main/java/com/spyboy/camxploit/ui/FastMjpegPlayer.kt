package com.spyboy.camxploit.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * Native MJPEG renderer. Scans the stream for JPEG SOI/EOI markers (0xFFD8 / 0xFFD9)
 * and decodes frames directly via BitmapFactory — no WebView overhead.
 */
@Composable
fun FastMjpegPlayer(
    url: String,
    modifier: Modifier = Modifier,
    onFrame: (Bitmap) -> Unit = {},
    onError: () -> Unit = {}
) {
    var frame by remember { mutableStateOf<Bitmap?>(null) }
    var isBuffering by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    DisposableEffect(url) {
        var connection: HttpURLConnection? = null
        val job = scope.launch(Dispatchers.IO) {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                connection = conn
                conn.readTimeout = 15000
                conn.connectTimeout = 15000
                conn.setRequestProperty("Connection", "Keep-Alive")
                conn.doInput = true
                conn.connect()

                val input = conn.inputStream.buffered()
                val buffer = ByteArrayOutputStream(65536)
                var prev = 0
                var curr: Int

                while (isActive) {
                    curr = input.read()
                    if (curr == -1) break

                    if (prev == 0xFF && curr == 0xD8) {
                        // Start of Image marker
                        buffer.reset()
                        buffer.write(0xFF)
                        buffer.write(0xD8)
                    } else if (prev == 0xFF && curr == 0xD9) {
                        // End of Image marker — decode frame
                        buffer.write(curr)
                        val frameBytes = buffer.toByteArray()
                        val bmp = BitmapFactory.decodeByteArray(frameBytes, 0, frameBytes.size)
                        if (bmp != null) {
                            withContext(Dispatchers.Main) {
                                frame = bmp
                                isBuffering = false
                            }
                            // Invoke on the decoder coroutine so recording work never blocks Compose.
                            onFrame(bmp)
                        }
                        buffer.reset()
                    } else {
                        buffer.write(curr)
                    }
                    prev = curr
                }
            } catch (ignored: Exception) {
                if (isActive) {
                    withContext(Dispatchers.Main) { onError() }
                }
            } finally {
                connection?.disconnect()
            }
        }

        onDispose {
            job.cancel()
            connection?.disconnect()
        }
    }

    Box(modifier = modifier) {
        frame?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Live camera feed",
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isBuffering) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
