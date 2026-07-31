package com.spyboy.camxploit

import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

class StreamActivity : ComponentActivity() {

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_TITLE = "title"

        fun launch(context: android.content.Context, url: String, title: String) {
            context.startActivity(android.content.Intent(context, StreamActivity::class.java).apply {
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
            })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val url = intent.getStringExtra(EXTRA_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Live Feed"

        // Fullscreen immersive
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        setContent {
            val neonGreen = Color(0xFF39FF14)
            var isLoading by remember { mutableStateOf(true) }
            var hasError by remember { mutableStateOf(false) }
            var webView by remember { mutableStateOf<WebView?>(null) }

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
                // WebView player - handles MJPEG, RTSP plugins, JS players automatically
                AndroidView(
                    factory = { ctx ->
                        WebView(ctx).apply {
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                            settings.loadWithOverviewMode = true
                            settings.useWideViewPort = true
                            settings.builtInZoomControls = true
                            settings.displayZoomControls = false
                            // Using a desktop-like UA often helps with loading embedded players
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
                            
                            webChromeClient = WebChromeClient()
                            webViewClient = object : WebViewClient() {
                                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                    isLoading = true
                                    hasError = false
                                }
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    isLoading = false
                                    // Inject JS to isolate the stream if it's an Insecam view
                                    if (url?.contains("insecam.org/en/view/") == true) {
                                        view?.evaluateJavascript("""
                                            (function() {
                                                var style = document.createElement('style');
                                                style.innerHTML = 'body { background: black !important; margin: 0 !important; padding: 0 !important; overflow: hidden !important; } ' +
                                                                  '* { visibility: hidden !important; } ' +
                                                                  '#image0, #image0 * { visibility: visible !important; position: fixed !important; top: 50% !important; left: 50% !important; transform: translate(-50%, -50%) !important; width: 100% !important; height: auto !important; max-height: 100% !important; z-index: 999999 !important; }';
                                                document.head.appendChild(style);
                                            })();
                                        """.trimIndent(), null)
                                    }
                                }
                                override fun onReceivedError(view: WebView?, request: android.webkit.WebResourceRequest?, error: android.webkit.WebResourceError?) {
                                    if (request?.isForMainFrame == true) {
                                        hasError = true
                                        isLoading = false
                                    }
                                }
                            }
                            loadUrl(url)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .statusBarsPadding(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title.uppercase(),
                        color = neonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { webView?.reload() }) {
                        Icon(Icons.Default.Refresh, "Reload", tint = Color.White)
                    }
                    IconButton(onClick = { finish() }) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }

                if (isLoading) {
                    CircularProgressIndicator(
                        color = neonGreen,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                if (hasError) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("STREAM UNAVAILABLE", color = Color.Red, fontWeight = FontWeight.Bold)
                        Text("Camera may be offline", color = Color.Gray, fontSize = 12.sp)
                        Button(
                            onClick = { webView?.reload() },
                            colors = ButtonDefaults.buttonColors(containerColor = neonGreen)
                        ) {
                            Text("RETRY", color = Color.Black)
                        }
                    }
                }
            }
        }
    }
}
