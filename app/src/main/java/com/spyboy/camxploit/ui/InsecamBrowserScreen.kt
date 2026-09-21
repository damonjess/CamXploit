package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.webkit.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InsecamBrowserScreen(onClose: () -> Unit, onStreamUrl: (String, String) -> Unit) {
    val neonGreen = Color(0xFF39FF14)
    val darkSurface = Color(0xFF141414)
    val darkCard = Color(0xFF1E1E1E)
    
    var currentUrl by remember { mutableStateOf("http://www.insecam.org/en/") }
    var inputUrl by remember { mutableStateOf("http://www.insecam.org/en/") }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var detectedStreamUrl by remember { mutableStateOf<String?>(null) }
    var detectedTitle by remember { mutableStateOf("Live Feed") }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }

    val focusManager = LocalFocusManager.current

    // Universal JS Sniffer script
    val snifferScript = """
        (function() {
            var candidates = [];
            
            // 1. Direct camera IDs/elements
            var img0 = document.querySelector('#image0, #main-image, #camera-image, img.camera-image, img.webcam');
            if (img0 && img0.src) candidates.push(img0.src);
            
            // 2. Scan image elements for camera stream keywords or direct IPs
            var imgs = document.getElementsByTagName('img');
            for (var i = 0; i < imgs.length; i++) {
                var src = imgs[i].src || imgs[i].getAttribute('data-src') || imgs[i].getAttribute('data-original') || '';
                if (!src) continue;
                var lower = src.toLowerCase();
                if (lower.includes('mjpg') || lower.includes('mjpeg') || lower.includes('cgi-bin') || 
                    lower.includes('stream') || lower.includes('video') || lower.includes('snapshot') ||
                    lower.includes('axis-cgi') || lower.includes('live') || lower.includes('current')) {
                    candidates.push(src);
                } else if (src.match(/^https?:\/\/\d+\.\d+\.\d+\.\d+/)) {
                    candidates.push(src);
                }
            }

            // 3. Check embedded frames/video tags
            var iframes = document.querySelectorAll('iframe, video, embed');
            for (var j = 0; j < iframes.length; j++) {
                var vsrc = iframes[j].src || '';
                if (vsrc && !vsrc.includes('google') && !vsrc.includes('facebook') && !vsrc.includes('youtube')) {
                    candidates.push(vsrc);
                }
            }

            // 4. Filter out common web assets
            var valid = candidates.filter(function(url) {
                var l = url.toLowerCase();
                return !l.includes('logo') && !l.includes('banner') && !l.includes('icon') && 
                       !l.includes('favicon') && !l.includes('avatar') && !l.includes('button') &&
                       !l.endsWith('.gif');
            });

            return valid.length > 0 ? valid[0] : '';
        })();
    """.trimIndent()

    fun runSniffer(view: WebView?) {
        view?.evaluateJavascript(snifferScript) { rawResult ->
            val clean = rawResult?.trim('"')?.replace("\\\"", "\"")?.trim() ?: ""
            if (clean.isNotBlank() && clean != "null" && clean != "undefined") {
                detectedStreamUrl = clean
                detectedTitle = when {
                    currentUrl.contains("insecam.org/en/view/") -> {
                        val id = currentUrl.substringAfter("/en/view/").takeWhile { it.isDigit() }
                        "Insecam Camera #$id"
                    }
                    currentUrl.contains("opentopia.com") -> "Opentopia Stream"
                    else -> view?.title?.ifBlank { "Detected Feed" } ?: "Detected Feed"
                }
            } else {
                detectedStreamUrl = null
            }
        }
    }

    fun loadFormattedUrl(raw: String) {
        val trimmed = raw.trim()
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "http://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        currentUrl = target
        inputUrl = target
        webView?.loadUrl(target)
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        // 1. TOP CONTROL BAR (Title & Preset Directory Chips)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(darkSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, null, tint = neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "OSINT RECON BROWSER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // Preset directory quick-chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "Insecam" to "http://www.insecam.org/en/",
                    "Opentopia" to "https://www.opentopia.com/",
                    "WorldCam" to "https://worldcam.eu/",
                    "EarthCam" to "https://www.earthcam.com/",
                    "Shodan Dork" to "https://www.google.com/search?q=inurl:%22view/viewer_index.shtml%22"
                )

                presets.forEach { (label, url) ->
                    AssistChip(
                        onClick = { loadFormattedUrl(url) },
                        label = { Text(label, fontSize = 9.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = darkCard),
                        border = BorderStroke(1.dp, if (currentUrl.contains(url.take(15))) neonGreen else Color.DarkGray),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }
        }

        // 2. NAVIGATION & URL ADDRESS BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(darkSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webView?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) Color.White else Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { webView?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, "Forward",
                    tint = if (canGoForward) Color.White else Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { webView?.reload() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Refresh, "Reload", tint = neonGreen, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            // URL Address Input Box
            BasicTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(Color.Black, shape = RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, Color.DarkGray), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                singleLine = true,
                cursorBrush = SolidColor(neonGreen),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { loadFormattedUrl(inputUrl) }),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputUrl.isEmpty()) {
                                Text("Enter URL or search...", fontSize = 11.sp, color = Color.Gray)
                            }
                            innerTextField()
                        }
                        IconButton(
                            onClick = { loadFormattedUrl(inputUrl) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Search, "Go", tint = neonGreen, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )

            Spacer(Modifier.width(4.dp))

            // Manual Sniff Button
            IconButton(
                onClick = { runSniffer(webView) },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Videocam, "Sniff Stream", tint = neonGreen, modifier = Modifier.size(18.dp))
            }
        }

        // 3. WEBVIEW DISPLAY
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                
                                // Run universal stream sniffer
                                runSniffer(view)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    color = neonGreen,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }

        // 4. FLOATING STREAM DETECTED ACTION BANNER
        detectedStreamUrl?.let { streamUrl ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, neonGreen), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, null, tint = neonGreen, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                "LIVE STREAM DETECTED",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = neonGreen
                            )
                        }
                        Text(
                            detectedTitle,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1
                        )
                    }

                    Button(
                        onClick = { onStreamUrl(streamUrl, detectedTitle) },
                        colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("LAUNCH PLAYER", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
