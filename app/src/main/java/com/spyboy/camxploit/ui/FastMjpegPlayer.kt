package com.spyboy.camxploit.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import com.spyboy.camxploit.MjpegFrameGrabber
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Native MJPEG renderer. Uses [MjpegFrameGrabber] with automatic byte buffer
 * and Bitmap frame recycling (inBitmap) for high-FPS, low-overhead live viewing.
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
        val grabber = MjpegFrameGrabber(url, frameDelayMs = 0L)
        val job = scope.launch(Dispatchers.IO) {
            grabber.stream(
                onFrame = { bmp ->
                    withContext(Dispatchers.Main) {
                        frame = bmp
                        isBuffering = false
                    }
                    // Invoke on the decoder coroutine so recording work never blocks Compose.
                    onFrame(bmp)
                },
                onError = {
                    withContext(Dispatchers.Main) { onError() }
                }
            )
        }

        onDispose {
            job.cancel()
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
