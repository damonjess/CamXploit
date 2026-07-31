package com.spyboy.camxploit

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.WindowManager
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Modern Immersive fullscreen
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let { controller ->
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        val streamUrl = intent.getStringExtra(EXTRA_URL) ?: ""
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Live Feed"

        setContent {
            val neonGreen = Color(0xFF39FF14)
            var isLoading by remember { mutableStateOf(true) }
            var hasError by remember { mutableStateOf(false) }
            var webView by remember { mutableStateOf<WebView?>(null) }

            val isolationJs = """
                (function() {
                    var style = document.getElementById('camxploit-isolation');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = 'camxploit-isolation';
                        document.head.appendChild(style);
                    }
                    style.innerHTML = `
                        * { visibility: hidden !important; }
                        html, body { 
                            background: black !important; 
                            margin: 0 !important; 
                            padding: 0 !important; 
                            overflow: hidden !important; 
                            width: 100vw !important; 
                            height: 100vh !important; 
                            visibility: visible !important; 
                        }
                        img#image0, img#image1, video, canvas, .stream img, .video-container img, 
                        [class*='stream'], [class*='video'], [id*='stream'], [id*='video'],
                        #image0, #image1 {
                            visibility: visible !important;
                            position: fixed !important;
                            top: 0 !important;
                            left: 0 !important;
                            width: 100vw !important;
                            height: 100vh !important;
                            object-fit: contain !important;
                            z-index: 999999 !important;
                            display: block !important;
                            background: black !important;
                        }
                        /* Hide common modal/popup/dialog elements */
                        .modal, .popup, .dialog, .overlay, .alert, .message-box, 
                        [class*='modal'], [class*='popup'], [class*='dialog'],
                        [id*='modal'], [id*='popup'], [id*='dialog'] {
                            display: none !important;
                            visibility: hidden !important;
                            z-index: -1 !important;
                        }
                    `;
                })()
            """.trimIndent()

            Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {

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
                            // Spoof Chrome 44 to bypass legacy browser sniffing ("browser too new")
                            settings.userAgentString = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/44.0.2403.157 Safari/537.36"

                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    if (newProgress > 60) {
                                        view?.evaluateJavascript(isolationJs, null)
                                    }
                                    if (newProgress == 100) {
                                        isLoading = false
                                    }
                                }

                                // Suppress JS Popups (Alerts, Confirms, Prompts)
                                override fun onJsAlert(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                    result?.confirm()
                                    return true
                                }
                                override fun onJsConfirm(view: WebView?, url: String?, message: String?, result: android.webkit.JsResult?): Boolean {
                                    result?.confirm()
                                    return true
                                }
                                override fun onJsPrompt(view: WebView?, url: String?, message: String?, defaultValue: String?, result: android.webkit.JsPromptResult?): Boolean {
                                    result?.confirm()
                                    return true
                                }

                                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                    return true // Suppress console logs in prod, but helps with debugging if needed
                                }
                            }

                            webViewClient = object : WebViewClient() {
                                override fun onPageFinished(view: WebView?, url: String?) {
                                    view?.evaluateJavascript(isolationJs, null)
                                    isLoading = false
                                    hasError = false
                                }

                                override fun onReceivedError(
                                    view: WebView?,
                                    errorCode: Int,
                                    description: String?,
                                    failingUrl: String?
                                ) {
                                    if (failingUrl == streamUrl) {
                                        hasError = true
                                        isLoading = false
                                    }
                                }

                                override fun shouldOverrideUrlLoading(
                                    view: WebView?,
                                    request: WebResourceRequest?
                                ): Boolean {
                                    return false // Keep navigation internal
                                }
                            }

                            loadUrl(streamUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxSize().alpha(if (isLoading) 0f else 1f)
                )

                // Header overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xCC000000))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "LIVE FEED",
                            fontSize = 11.sp,
                            color = neonGreen,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            title,
                            fontSize = 13.sp,
                            color = Color.White,
                            maxLines = 1
                        )
                    }
                    Row {
                        IconButton(onClick = {
                            hasError = false
                            isLoading = true
                            webView?.reload()
                        }) {
                            Icon(Icons.Default.Refresh, "Reload", tint = Color.White)
                        }
                        IconButton(onClick = { finish() }) {
                            Icon(Icons.Default.Close, "Close", tint = Color.White)
                        }
                    }
                }

                // Loading spinner
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            color = neonGreen,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }

                // Error state
                if (hasError) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "STREAM OFFLINE",
                                color = Color.Red,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Camera may be down or blocked",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    hasError = false
                                    isLoading = true
                                    webView?.reload()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = neonGreen)
                            ) {
                                Text("RETRY", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}
