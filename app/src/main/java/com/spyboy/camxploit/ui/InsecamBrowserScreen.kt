package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.webkit.*
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InsecamBrowserScreen(onClose: () -> Unit, onStreamUrl: (String, String) -> Unit) {
    val neonGreen = Color(0xFF39FF14)
    var currentUrl by remember { mutableStateOf("http://www.insecam.org/en/") }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.6167.164 Mobile Safari/537.36"
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    settings.builtInZoomControls = true
                    settings.displayZoomControls = false

                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            isLoading = false
                            url?.let { currentUrl = it }
                            
                            // If we are on a view page, try to extract direct stream and prompt user
                            if (url?.contains("/en/view/") == true) {
                                view?.evaluateJavascript(
                                    "(function() { return document.querySelector('#image0')?.src || ''; })();"
                                ) { directUrl ->
                                    val cleanUrl = directUrl.trim('"')
                                    if (cleanUrl.isNotEmpty() && !cleanUrl.contains("null")) {
                                        val id = url.substringAfter("/en/view/").takeWhile { it.isDigit() }
                                        onStreamUrl(cleanUrl, "Camera $id")
                                    }
                                }
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val url = request?.url?.toString() ?: return false
                            // Allow navigation so we can scrape the direct URL on onPageFinished
                            return false
                        }
                    }

                    webChromeClient = WebChromeClient()
                    loadUrl(currentUrl)
                    webView = this
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = 56.dp)
        )

        // Header overlay
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xCC000000))
                .statusBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("PUBLIC CAMS", fontSize = 12.sp, color = neonGreen, fontWeight = FontWeight.Bold)
                Text("insecam.org", fontSize = 11.sp, color = Color.Gray)
            }
            Row {
                TextButton(onClick = { webView?.reload() }) {
                    Text("RELOAD", color = neonGreen, fontSize = 11.sp)
                }
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, "Close", tint = Color.White)
                }
            }
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = neonGreen,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
