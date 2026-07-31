package com.spyboy.camxploit

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@androidx.media3.common.util.UnstableApi
class StreamActivity : ComponentActivity() {
    companion object {
        const val EXTRA_MODE = "mode"       // "webview" or "exo"
        const val EXTRA_URL = "url"         // direct stream URL
        const val EXTRA_TITLE = "title"     // IP or location

        fun launch(context: android.content.Context, url: String, title: String) {
            val mode = when {
                url.contains("insecam.org") -> "webview"
                url.startsWith("rtsp://") -> "exo"
                url.endsWith(".mjpg") || url.endsWith(".mjpeg") -> "exo"
                else -> "webview"
            }
            context.startActivity(android.content.Intent(context, StreamActivity::class.java).apply {
                putExtra(EXTRA_MODE, mode)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val mode = intent.getStringExtra(EXTRA_MODE) ?: "webview"
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Live Feed"

        setContent {
            val neonGreen = Color(0xFF39FF14)

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                when (mode) {
                    "exo" -> ExoPlayerScreen(url)
                    else -> WebViewScreen(url)
                }

                // Overlay header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LIVE FEED", fontSize = 12.sp, color = neonGreen, fontWeight = FontWeight.Bold)
                        Text(title, fontSize = 14.sp, color = Color.White, maxLines = 1)
                    }
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun WebViewScreen(url: String) {
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@androidx.media3.common.util.UnstableApi
@Composable
private fun ExoPlayerScreen(url: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val player = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            playWhenReady = true
            repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    DisposableEffect(Unit) {
        onDispose { player.release() }
    }

    AndroidView(
        factory = { ctx ->
            @androidx.media3.common.util.UnstableApi
            fun setupPlayerView(view: PlayerView) {
                view.player = player
                view.useController = true
                view.controllerHideOnTouch = true
                view.controllerShowTimeoutMs = 3000
                view.setBackgroundColor(android.graphics.Color.BLACK)
            }
            PlayerView(ctx).apply { setupPlayerView(this) }
        },
        modifier = Modifier.fillMaxSize()
    )
}
