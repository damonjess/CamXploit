package com.spyboy.camxploit.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * For cameras that only serve snapshot JPEGs.
 * Refreshes the image every [refreshMs] to simulate live video.
 * Appends a cache-buster so the camera serves a fresh frame each time.
 */
@Composable
fun AutoRefreshImage(
    url: String,
    modifier: Modifier = Modifier,
    refreshMs: Long = 1500,
    onError: () -> Unit = {}
) {
    var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorCount by remember { mutableStateOf(0) }
    var tick by remember { mutableStateOf(0L) }

    LaunchedEffect(url) {
        while (isActive && errorCount < 5) {
            try {
                val cacheBuster = "?t=${System.currentTimeMillis()}"
                val fullUrl = if (url.contains("?")) "$url&cb=$tick" else "$url$cacheBuster"
                
                val newBmp = withContext(Dispatchers.IO) {
                    val conn = URL(fullUrl).openConnection() as HttpURLConnection
                    conn.apply {
                        connectTimeout = 8000
                        readTimeout = 8000
                        setRequestProperty("User-Agent", "Mozilla/5.0")
                        useCaches = false
                        doInput = true
                    }
                    conn.inputStream.use { stream ->
                        android.graphics.BitmapFactory.decodeStream(stream)
                    }
                }
                if (newBmp != null) {
                    bitmap = newBmp
                    isLoading = false
                    errorCount = 0
                }
            } catch (e: Exception) {
                errorCount++
                if (errorCount >= 5 && isActive) {
                    onError()
                    break
                }
            }
            tick++
            delay(refreshMs)
        }
    }

    Box(modifier = modifier) {
        bitmap?.let {
            Image(
                bitmap = it.asImageBitmap(),
                contentDescription = "Live snapshot",
                modifier = Modifier.fillMaxSize()
            )
        }
        if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
        Text(
            text = "● LIVE",
            color = Color(0xFF4CAF50),
            modifier = Modifier.align(Alignment.TopStart).padding(12.dp)
        )
    }
}
