package com.spyboy.camxploit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import android.content.ClipboardManager
import android.content.ClipData
import org.json.JSONObject
import org.json.JSONArray
import androidx.compose.foundation.BorderStroke
import android.util.Base64
import androidx.compose.foundation.shape.CircleShape
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import android.webkit.*
import android.net.http.SslError
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.foundation.Image
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.*
import java.net.URL

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContent {
            CamGuardianApp()
        }
    }
}

class TerminalOutputStream(val onUpdate: (String) -> Unit) : OutputStream() {
    override fun write(b: Int) {
        onUpdate(b.toChar().toString())
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        onUpdate(String(b, off, len))
    }
    @Suppress("unused")
    fun write(s: String) { onUpdate(s) }
    override fun flush() {}
}

@Composable
fun CamGuardianApp() {
    var terminalText by remember { mutableStateOf("> System Initialized. Awaiting Target...\n") }
    var ipInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var showDisclaimer by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var activeStreamUrl by remember { mutableStateOf<String?>(null) }
    var showLiveView by remember { mutableStateOf(false) }
    var selectedStreamUrl by remember { mutableStateOf("") }
    var selectedStreamType by remember { mutableStateOf("") }
    var detectedUsername by remember { mutableStateOf("") }
    var detectedPassword by remember { mutableStateOf("") }
    var shodanApiKey by remember { mutableStateOf("") }
    var shodanQuery by remember { mutableStateOf("webcam") }
    var showShodanDialog by remember { mutableStateOf(false) }
    var lanScanResult by remember { mutableStateOf("Press Scan to discover devices on your network.") }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    if (showDisclaimer) {
        AlertDialog(
            onDismissRequest = { },
            title = { Text("LEGAL DISCLAIMER", color = Color.Red, fontWeight = FontWeight.Black) },
            text = {
                Text(
                    "This tool is for educational and authorized security testing purposes only. " +
                    "Unauthorized access to computer systems is illegal. " +
                    "The authors are not responsible for any misuse or damage caused by this application.",
                    color = Color.White
                )
            },
            confirmButton = {
                Button(onClick = { showDisclaimer = false }) {
                    Text("I AGREE & UNDERSTAND")
                }
            },
            containerColor = Color(0xFF111111),
            shape = RoundedCornerShape(8.dp)
        )
    }

    if (showShodanDialog) {
        AlertDialog(
            onDismissRequest = { showShodanDialog = false },
            title = { Text("GLOBAL SHODAN SEARCH", color = Color.Magenta, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Search for exposed cameras globally using Shodan API.", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(
                        value = shodanApiKey,
                        onValueChange = { shodanApiKey = it },
                        label = { Text("Shodan API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = shodanQuery,
                        onValueChange = { shodanQuery = it },
                        label = { Text("Search Query (e.g., 'webcam xp')") },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(color = Color.White)
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    showShodanDialog = false
                    if (shodanApiKey.isNotBlank()) {
                        terminalText = "> Initiating Global Shodan Search...\n"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                val module = py.getModule("CamXploit")
                                val sys = py.getModule("sys")
                                val outputStream = TerminalOutputStream { text ->
                                    scope.launch(Dispatchers.Main) { terminalText += text }
                                }
                                sys.put("stdout", outputStream)
                                module.callAttr("shodan_search", shodanApiKey, shodanQuery)
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    terminalText += "\n[!] Shodan Error: ${e.message}"
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "API Key Required", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("SEARCH")
                }
            },
            dismissButton = {
                TextButton(onClick = { showShodanDialog = false }) {
                    Text("CANCEL")
                }
            },
            containerColor = Color(0xFF111111)
        )
    }

    // File Viewer Dialog
    if (viewingFile != null) {
        AlertDialog(
            onDismissRequest = { viewingFile = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            confirmButton = {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = { 
                        viewingFile?.let { openFile(context, it) }
                    }) {
                        Text("OPEN EXTERNAL", color = Color.Green)
                    }
                    TextButton(onClick = { viewingFile = null }) {
                        Text("CLOSE", color = Color.Cyan)
                    }
                }
            },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = viewingFile?.name ?: "Log Viewer",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewingFile?.let { shareFile(context, it) } }) {
                        Icon(Icons.Default.Share, "Share", tint = Color.Cyan, modifier = Modifier.size(20.dp))
                    }
                }
            },
            text = {
                Surface(
                    modifier = Modifier.fillMaxSize().border(1.dp, Color.DarkGray),
                    color = Color(0xFF050505)
                ) {
                    if (viewingFile?.extension == "png") {
                        val bitmap = remember(viewingFile) {
                            BitmapFactory.decodeFile(viewingFile?.absolutePath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text("Error loading image", color = Color.Red, modifier = Modifier.padding(16.dp))
                        }
                    } else {
                        val content = remember(viewingFile) {
                            try { viewingFile?.readText() ?: "" } catch (e: Exception) { "Error reading file" }
                        }
                        SelectionContainer {
                            Text(
                                text = content,
                                color = Color(0xFF00FF41),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                            )
                        }
                    }
                }
            },
            containerColor = Color(0xFF121212)
        )
    }

    Scaffold(
        bottomBar = {
            NavigationBar(
                containerColor = Color(0xFF121212),
                contentColor = Color.Cyan
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Info, "Console") },
                    label = { Text("CONSOLE") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Home, "Intel") },
                    label = { Text("INTEL") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.List, "Archive") },
                    label = { Text("ARCHIVE") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Videocam, "Stream") },
                    label = { Text("STREAM") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Magenta,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color.Magenta,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(                    // ← New LAN Scanner Tab
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.Search, "LAN Scan") },
                    label = { Text("LAN SCAN") }
                )
                NavigationBarItem(
                    selected = selectedTab == 5,
                    onClick = { selectedTab = 5 },
                    icon = { Icon(Icons.Default.FlashOn, "Storm") },
                    label = { Text("STORM") }
                )
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "CAM VIGIL",
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "NETWORK RECONNAISSANCE UNIT",
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row {
                    IconButton(onClick = { showShodanDialog = true }) {
                        Icon(Icons.Default.Public, "Shodan Search", tint = Color.Magenta)
                    }
                    IconButton(onClick = { captureScreenshot(context, view) }) {
                        Icon(Icons.Default.PhotoCamera, "Screenshot", tint = Color.Cyan)
                    }
                    IconButton(onClick = { generateHtmlReport(context, terminalText) }) {
                        Icon(Icons.Default.CheckCircle, "Save Report", tint = Color.Green)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            when (selectedTab) {
                0 -> ConsoleTab(
                    context = context,
                    ipInput = ipInput,
                    onIpChange = { ipInput = it },
                    terminalText = terminalText,
                    onTerminalClear = { terminalText = "> Console Reset.\n" },
                    isScanning = isScanning,
                    scrollState = scrollState,
                    onStartScan = {
                        if (ipInput.isNotEmpty() && !isScanning) {
                            isScanning = true
                            terminalText = "> Starting Reconnaissance on $ipInput...\n"
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val py = Python.getInstance()
                                    val module = py.getModule("CamXploit")
                                    val sys = py.getModule("sys")
                                    val outputStream = TerminalOutputStream { text ->
                                        scope.launch(Dispatchers.Main) { terminalText += text }
                                    }
                                    sys.put("stdout", outputStream)
                                    module.callAttr("main", ipInput)
                                    withContext(Dispatchers.Main) { 
                                        isScanning = false 
                                        saveJsonReport(context, terminalText, ipInput)
                                        saveContentToFile(context, terminalText, "Scan_Log", "txt")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        terminalText += "\n[!] ERROR: ${e.message}"
                                        isScanning = false
                                    }
                                }
                            }
                        }
                    },
                    onStreamSelect = { url, type ->
                        selectedStreamUrl = url
                        selectedStreamType = type
                        
                        // Parse credentials from terminalText if they exist
                        // Look for: "CRACKED (HTTP): admin:password"
                        val credMatch = Regex("""CRACKED \(HTTP\): ([^:]+):([^ ]+)""").find(terminalText)
                        if (credMatch != null) {
                            detectedUsername = credMatch.groupValues[1]
                            detectedPassword = credMatch.groupValues[2]
                        } else {
                            detectedUsername = ""
                            detectedPassword = ""
                        }
                        
                        showLiveView = true
                    }
                )
                1 -> IntelTab(terminalText, {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val module = py.getModule("CamXploit")
                            
                            // Extract first IP and Port found in terminalText
                            val ipMatch = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").find(terminalText)
                            val portMatch = Regex("""Port (\d+)""").find(terminalText)
                            
                            if (ipMatch != null) {
                                val targetIp = ipMatch.value
                                val targetPort = portMatch?.groupValues?.get(1)?.toInt() ?: 80
                                
                                // Extract credentials if found: "CRACKED (HTTP): admin:password"
                                val credMatch = Regex("""CRACKED \(HTTP\): (\w+):(\w+)""").find(terminalText)
                                val user = credMatch?.groupValues?.get(1)
                                val pass = credMatch?.groupValues?.get(2)
                                
                                val b64Data = module.callAttr("manual_snapshot_capture", targetIp, targetPort, user, pass).toString()
                                if (b64Data != "None") {
                                    val decodedString = Base64.decode(b64Data, Base64.DEFAULT)
                                    val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                    withContext(Dispatchers.Main) {
                                        capturedBitmap = bitmap
                                        // Also save to file
                                        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Manual_Snap_$timeStamp.png")
                                        FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
                                        Toast.makeText(context, "Snapshot Captured & Saved", Toast.LENGTH_SHORT).show()
                                    }
                                } else {
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Snapshot Capture Failed", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }, { url ->
                    activeStreamUrl = url
                }, {
                    // TEST ONVIF logic
                    // Look for the last IP found in the terminal, as it's likely the current target
                    val ipMatch = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").findAll(terminalText).lastOrNull()
                    if (ipMatch != null) {
                        val targetIp = ipMatch.value
                        Toast.makeText(context, "Probing ONVIF on $targetIp...", Toast.LENGTH_SHORT).show()
                        terminalText += "\n[>] Initiating Targeted ONVIF Probe for $targetIp...\n"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                val module = py.getModule("CamXploit")
                                val sys = py.getModule("sys")
                                val outputStream = TerminalOutputStream { text ->
                                    scope.launch(Dispatchers.Main) { terminalText += text }
                                }
                                sys.put("stdout", outputStream)
                                module.callAttr("discover_onvif", targetIp)
                                withContext(Dispatchers.Main) {
                                    terminalText += "\n[🏁] ONVIF Probe Complete for $targetIp.\n"
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) {
                                    terminalText += "\n[!] ONVIF Error: ${e.message}\n"
                                }
                            }
                        }
                    } else {
                        Toast.makeText(context, "No target IP found in terminal. Start a scan first.", Toast.LENGTH_LONG).show()
                    }
                })
                2 -> ArchiveTab(context) { file -> viewingFile = file }
                3 -> StreamTab(terminalText)
                4 -> LanScannerTab(
                    onScanComplete = { result -> lanScanResult = result },
                    onDeviceSelect = { ip ->
                        ipInput = ip
                        selectedTab = 0
                        Toast.makeText(context, "Target set to $ip", Toast.LENGTH_SHORT).show()
                    }
                )
                5 -> StormBreakerTab { text: String -> terminalText += text }
            }

            capturedBitmap?.let { bitmap ->
                Spacer(modifier = Modifier.height(20.dp))
                Text("LAST CAPTURED SNAPSHOT", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .border(1.dp, Color.Gray, RoundedCornerShape(8.dp))
                        .background(Color.Black),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = "Captured Snapshot",
                        modifier = Modifier.fillMaxSize()
                    )
                    IconButton(
                        onClick = { capturedBitmap = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }

            activeStreamUrl?.let { url ->
                Spacer(modifier = Modifier.height(20.dp))
                Text("LIVE STREAM PREVIEW", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(240.dp)
                        .border(1.dp, Color.Green, RoundedCornerShape(8.dp))
                        .background(Color.Black)
                ) {
                    if (url.startsWith("rtsp")) {
                        AndroidView(
                            factory = { ctx ->
                                val player = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                                player.setMediaItem(androidx.media3.common.MediaItem.fromUri(url))
                                player.prepare()
                                player.playWhenReady = true
                                androidx.media3.ui.PlayerView(ctx).apply {
                                    this.player = player
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                            handler?.proceed()
                                        }
                                    }
                                    loadUrl(url)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    IconButton(
                        onClick = { activeStreamUrl = null },
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(Icons.Default.Close, "Close", tint = Color.White)
                    }
                }
            }

// === VIEWER LOGIC - Put this at the end of the main Column ===
        if (showLiveView && selectedStreamUrl.isNotEmpty()) {
            if (selectedStreamType.contains("SNAPSHOT")) {
                SnapshotViewer(
                    imageUrl = selectedStreamUrl,
                    onBack = { showLiveView = false }
                )
            } else {
                LiveViewScreen(
                    streamUrl = selectedStreamUrl,
                    streamType = selectedStreamType,
                    onBack = { showLiveView = false }
                )
            }
        }
        }
    }
}

@Composable
fun ConsoleTab(
    context: Context,
    ipInput: String,
    onIpChange: (String) -> Unit,
    terminalText: String,
    onTerminalClear: () -> Unit,
    isScanning: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    onStartScan: () -> Unit,
    onStreamSelect: (String, String) -> Unit
) {
    Column {
        // Target Input Area
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .padding(12.dp)
        ) {
            Column {
                Text("TARGET HOST / RANGE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = ipInput,
                        onValueChange = onIpChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Green)
                    )
                    IconButton(onClick = onStartScan) {
                        Icon(
                            imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.Search,
                            contentDescription = "Scan",
                            tint = if (isScanning) Color.Yellow else Color.Green
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Terminal
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp)),
            color = Color(0xFF050505)
        ) {
            Box {
                SelectionContainer {
                    Text(
                        text = terminalText,
                        color = Color(0xFF00FF41), // Terminal Green
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(10.dp)
                            .verticalScroll(scrollState)
                    )
                }
                
                // Clear button overlay
                IconButton(
                    onClick = onTerminalClear,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)
                ) {
                    Icon(Icons.Default.Delete, "Clear", tint = Color.DarkGray, modifier = Modifier.size(16.dp))
                }
            }
        }
        
        LaunchedEffect(terminalText) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }

        // Auto-Detected Links Panel - Scrollable + Router Warning
        if (terminalText.contains("===LINKS_START===")) {
            Spacer(modifier = Modifier.height(12.dp))
            
            Text("🎯 Auto-Detected Links", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            
            // Router Warning
            if (terminalText.contains("Vodafone") || terminalText.contains("Wi-Fi Hub")) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A00)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text(
                        "⚠️ This appears to be your Router, not a Camera.\nTry scanning your actual camera IP.",
                        color = Color.Yellow,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            val lines = terminalText.lines()
            val start = lines.indexOfFirst { it.contains("===LINKS_START===") }
            val end = lines.indexOfFirst { it.contains("===LINKS_END===") }

            if (start != -1 && end != -1) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    for (i in start + 1 until end) {
                        val line = lines[i].trim()
                        if (line.contains("|")) {
                            val parts = line.split("|")
                            if (parts.size >= 2) {
                                val linkType = parts[0]
                                val url = parts[1]
                                val status = if (parts.size > 2) parts[2] else ""

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp)
                                        .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            linkType,
                                            color = if (linkType.contains("SNAPSHOT")) Color.Magenta else Color.Yellow,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(url.take(52) + "...", color = Color.LightGray, fontSize = 12.sp)
                                        Text("Status: $status", color = Color.Green, fontSize = 11.sp)
                                    }

                                    Button(
                                        onClick = {
                                            onStreamSelect(url, linkType)
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = if (linkType.contains("SNAPSHOT")) Color.Magenta else Color.Green
                                        )
                                    ) {
                                        Text(if (linkType.contains("SNAPSHOT")) "📸 View" else "▶️ Live")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (terminalText.length > 50) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(
                    onClick = { 
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        val clip = ClipData.newPlainText("CamVigil Intel", terminalText)
                        clipboard.setPrimaryClip(clip)
                        Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Copy Output", fontSize = 10.sp)
                }
                Button(
                    onClick = { generateHtmlReport(context, terminalText) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Export HTML", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun IntelTab(terminalText: String, onCaptureSnapshot: () -> Unit, onPreviewStream: (String) -> Unit, onTestOnvif: () -> Unit) {
    val streams = terminalText.lines().filter { it.contains("http") || it.contains("rtsp") }
    val vulns = terminalText.lines().filter { it.contains("VULNERABILITY") || it.contains("CRITICAL") || it.contains("FIRE") }
    val deviceInfo = terminalText.lines().filter { it.contains("Model:") || it.contains("Firmware:") || it.contains("Manufacturer:") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("CATEGORIZED INTEL", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        IntelSection("STREAMS FOUND", streams, Color.Green, Icons.Default.Videocam, onPreviewStream)
        IntelSection("SECURITY VULNERABILITIES", vulns, Color.Red, Icons.Default.ReportProblem, onPreviewStream)
        IntelSection("DEVICE HARDWARE INFO", deviceInfo, Color.Cyan, Icons.Default.Info, onPreviewStream)
        
        Spacer(modifier = Modifier.height(20.dp))
        Text("QUICK ACTIONS", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onTestOnvif,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("TEST ONVIF", fontSize = 10.sp)
            }
            Button(
                onClick = onCaptureSnapshot,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text("CAPTURE SNAP", fontSize = 10.sp)
            }
        }
        
        if (terminalText.isEmpty()) {
            Text("No intel collected yet. Start a scan first.", color = Color.Gray, modifier = Modifier.padding(20.dp))
        }
    }
}

fun buildAuthUrl(url: String, user: String, pass: String): String {
    if (user.isBlank() || pass.isBlank()) return url
    return try {
        if (url.startsWith("rtsp://")) {
            url.replace("rtsp://", "rtsp://$user:$pass@")
        } else if (url.startsWith("http://")) {
            url.replace("http://", "http://$user:$pass@")
        } else {
            url
        }
    } catch (e: Exception) {
        url
    }
}

@Composable
fun StreamTab(terminalText: String) {
    // Auto-extract credentials if any were found in the console
    val credentials = remember(terminalText) {
        // Match either HTTP or RTSP cracked credentials
        val match = Regex("""CRACKED \((?:HTTP|RTSP)\): ([^:]+):([^\s\n]+)""").find(terminalText)
        if (match != null) {
            match.groupValues[1] to match.groupValues[2]
        } else {
            "" to ""
        }
    }

    // Auto-extract ALL stream URLs from scan results
    val streamUrls = remember(terminalText) {
        val start = terminalText.indexOf("===LINKS_START===")
        val end = terminalText.indexOf("===LINKS_END===")

        if (start != -1 && end != -1) {
            // Parse the structured block
            terminalText.substring(start, end)
                .lines()
                .filter { it.contains("rtsp://") || it.contains("http://") }
                .mapNotNull { line ->
                    Regex("(rtsp://[^|\\s\\\\n]+|http://[^|\\s\\\\n]+)")
                        .find(line)?.value?.trim()
                }
                .distinct()
        } else {
            // Fallback to raw scan for older results
            terminalText.lines()
                .mapNotNull { line ->
                    Regex("(rtsp://[^\\s]+|http://[^\\s]+)")
                        .find(line)?.value?.trim()
                }
                .distinct()
        }
    }

    // Auto-select first found URL
    var selectedUrl by remember(streamUrls) {
        mutableStateOf(streamUrls.firstOrNull() ?: "")
    }
    var customUrl by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Text("STREAM VIEWER", color = Color.Magenta,
            fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))

        if (streamUrls.isNotEmpty()) {
            Text("AUTO-DETECTED STREAMS (${streamUrls.size})",
                color = Color.Gray, fontSize = 10.sp,
                fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))

            streamUrls.forEach { url ->
                val isSelected = url == selectedUrl
                TextButton(
                    onClick = { selectedUrl = url },
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(
                            1.dp,
                            if (isSelected) Color.Magenta else Color(0xFF222222),
                            RoundedCornerShape(4.dp)
                        )
                        .background(
                            if (isSelected) Color(0xFF1A001A)
                            else Color.Transparent
                        )
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            if (url.startsWith("rtsp")) Icons.Default.Videocam
                            else Icons.Default.Language,
                            contentDescription = null,
                            tint = if (isSelected) Color.Magenta else Color.Gray,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            url,
                            color = if (isSelected) Color.Magenta else Color.Cyan,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(4.dp))
                    .padding(12.dp)
            ) {
                Text(
                    "No streams detected yet.\nRun a scan on the CONSOLE tab first.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Manual override
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                .background(Color(0xFF0A0A0A))
                .padding(12.dp)
        ) {
            Column {
                Text("MANUAL OVERRIDE", color = Color.Gray, fontSize = 10.sp,
                    fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = customUrl,
                        onValueChange = { customUrl = it },
                        textStyle = TextStyle(
                            color = Color.White, fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Magenta),
                        decorationBox = { inner ->
                            if (customUrl.isEmpty()) Text(
                                "rtsp://user:pass@ip:port/stream",
                                color = Color.DarkGray, fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            inner()
                        }
                    )
                    IconButton(onClick = { 
                        if (customUrl.isNotEmpty()) {
                            selectedUrl = customUrl 
                        }
                    }) {
                        Icon(Icons.Default.PlayArrow, "Play", tint = Color.Magenta)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Player - auto-plays selected URL
        if (selectedUrl.isNotEmpty()) {
            val authenticatedUrl = buildAuthUrl(selectedUrl, credentials.first, credentials.second)
            key(authenticatedUrl) { // Recompose player when URL changes
                if (authenticatedUrl.startsWith("rtsp")) {
                    AndroidView(
                        factory = { ctx ->
                            val player = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                            player.setMediaItem(
                                androidx.media3.common.MediaItem.fromUri(authenticatedUrl)
                            )
                            player.prepare()
                            player.playWhenReady = true
                            androidx.media3.ui.PlayerView(ctx).apply {
                                this.player = player
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, Color(0xFF333333))
                    )
                } else {
                    // HTTP Stream / Snapshot
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.loadWithOverviewMode = true
                                    settings.useWideViewPort = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                            handler?.proceed()
                                        }
                                    }
                                    loadUrl(authenticatedUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(Color(0xFF050505))
                    .border(1.dp, Color(0xFF1A1A1A)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Videocam, null,
                        tint = Color(0xFF333333),
                        modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Awaiting stream source...",
                        color = Color(0xFF333333),
                        fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

@Composable
fun IntelSection(
    title: String,
    items: List<String>,
    color: Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onPreviewStream: (String) -> Unit
) {
    if (items.isNotEmpty()) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
            border = BorderStroke(1.dp, Color(0xFF1A1A1A))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(8.dp))
                items.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = item.trim().removePrefix("    ").removePrefix("       "),
                            color = Color.LightGray,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.weight(1f)
                        )
                        if (title == "STREAMS FOUND") {
                            // Extract URL from various possible formats
                            val url = item.substringAfter("🔗 ")
                                        .substringAfter("ACTIVE MJPEG STREAM: ")
                                        .substringAfter("POTENTIAL VIDEO STREAM: ")
                                        .substringAfter("RTSP URL: ")
                                        .substringAfter("RTSP Stream: ")
                                        .substringAfter("🔗 ") // Handle nested or duplicated symbols
                                        .split("|").first() // Handle structured link format
                                        .trim()
                            
                            if (url.startsWith("http") || url.startsWith("rtsp")) {
                                Text(
                                    text = "[VIEW]",
                                    color = if (url.startsWith("rtsp")) Color.Magenta else Color.Green,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { onPreviewStream(url) }.padding(4.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveTab(context: Context, onFileClick: (File) -> Unit) {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    val files = remember(refreshTrigger) {
        val docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val picDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        
        val allFiles = mutableListOf<File>()
        docDir?.listFiles()?.let { allFiles.addAll(it) }
        picDir?.listFiles()?.let { allFiles.addAll(it) }
        
        allFiles.sortedByDescending { it.lastModified() }
    }

    Column {
        Text("SAVED REPORTS & LOGS", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (files.isEmpty()) {
            Text("No reports found in archive.", color = Color.DarkGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(20.dp))
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp)
                            .border(1.dp, Color(0xFF111111), RoundedCornerShape(4.dp))
                            .background(Color(0xFF080808))
                            .clickable { onFileClick(file) }
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(file.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                            Text(SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date(file.lastModified())), color = Color.Gray, fontSize = 10.sp)
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { openFile(context, file) }, modifier = Modifier.size(32.dp)) {
                                Icon(
                                    imageVector = if (file.extension == "png") Icons.Default.Image else Icons.Default.OpenInNew,
                                    contentDescription = "Open", 
                                    tint = Color.Green, 
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(onClick = { shareFile(context, file) }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Share, "Share", tint = Color.Cyan, modifier = Modifier.size(18.dp))
                            }
                            IconButton(onClick = { 
                                if (file.delete()) refreshTrigger++
                            }, modifier = Modifier.size(32.dp)) {
                                Icon(Icons.Default.Delete, "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LanScannerTab(onScanComplete: (String) -> Unit, onDeviceSelect: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scanOutput by remember { mutableStateOf("Ready to scan local network...") }
    var discoveredDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("RADAR", "DEVICES", "LOGS")

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔍 LAN Scanner", color = Color.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Discover devices on your local network", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            contentColor = Color.Cyan,
            divider = { HorizontalDivider(color = Color.DarkGray) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedSubTab]),
                    color = Color.Cyan
                )
            }
        ) {
            subTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedSubTab == index,
                    onClick = { selectedSubTab = index },
                    text = { Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedSubTab) {
            0 -> { // RADAR View
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    Box(
                        modifier = Modifier
                            .size(150.dp)
                            .border(2.dp, if (isScanning) Color.Green else Color.DarkGray, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isScanning) {
                            CircularProgressIndicator(modifier = Modifier.size(120.dp), color = Color.Green, strokeWidth = 2.dp)
                        }
                        Icon(
                            Icons.Default.SettingsInputAntenna,
                            contentDescription = null,
                            tint = if (isScanning) Color.Green else Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            isScanning = true
                            scanOutput = "> Initializing ARP & SSDP Scan...\n"
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val py = Python.getInstance()
                                    val module = py.getModule("CamXploit")
                                    val result = module.callAttr("lan_scan").toString()
                                    withContext(Dispatchers.Main) {
                                        scanOutput += result
                                        // Extract IPs for the devices tab
                                        discoveredDevices = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").findAll(result).map { it.value }.distinct().toList()
                                        isScanning = false
                                        onScanComplete(result)
                                        // Auto-save LAN scan logs
                                        saveContentToFile(context, result, "LAN_Scan", "txt")
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        scanOutput += "\n[!] Scan Error: ${e.message}"
                                        isScanning = false
                                    }
                                }
                            }
                        },
                        enabled = !isScanning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                    ) {
                        Text(if (isScanning) "SCANNING..." else "INITIATE NETWORK SCAN")
                    }
                }
            }
            1 -> { // DEVICES View
                if (discoveredDevices.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No devices discovered yet.", color = Color.Gray)
                    }
                } else {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        discoveredDevices.forEach { ip ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                                border = BorderStroke(1.dp, Color(0xFF1A1A1A))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Router, null, tint = Color.Cyan, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text(ip, color = Color.White, fontFamily = FontFamily.Monospace)
                                    }
                                    Button(
                                        onClick = { onDeviceSelect(ip) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("SELECT", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> { // LOGS View
                Surface(
                    modifier = Modifier.fillMaxSize().border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp)),
                    color = Color(0xFF050505)
                ) {
                    SelectionContainer {
                        Text(
                            text = scanOutput,
                            color = Color(0xFF00FF41),
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                        )
                    }
                }
            }
        }
    }
}

// Global functions
fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Open with"))
    } catch (e: Exception) {
        Toast.makeText(context, "Cannot open file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun shareFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share via"))
    } catch (e: Exception) {
        Toast.makeText(context, "Share Failed", Toast.LENGTH_SHORT).show()
    }
}

fun saveContentToFile(context: Context, content: String, prefix: String, extension: String) {
    try {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "${prefix}_$timeStamp.$extension"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        FileOutputStream(file).use { it.write(content.toByteArray()) }
        Toast.makeText(context, "Saved to Documents", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Save Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun captureScreenshot(context: Context, view: android.view.View) {
    try {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Vigil_Capture_$timeStamp.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Toast.makeText(context, "Screenshot Saved", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Capture Failed", Toast.LENGTH_SHORT).show()
    }
}

fun generateHtmlReport(context: Context, terminalText: String) {
    val report = """
        <html>
        <head><title>CamVigil Recon Report</title>
        <style>
            body { background: #000; color: #0f0; font-family: monospace; padding: 20px; }
            .box { border: 1px solid #333; padding: 15px; border-radius: 5px; }
            h1 { color: cyan; border-bottom: 1px solid #333; }
            pre { white-space: pre-wrap; word-wrap: break-word; color: #00FF41; }
        </style></head>
        <body><div class="box"><h1>CAMVIGIL INTEL REPORT</h1><pre>${terminalText.replace("<", "&lt;").replace(">", "&gt;")}</pre></div></body></html>
    """.trimIndent()
    saveContentToFile(context, report, "Vigil_Report", "html")
}

fun saveJsonReport(context: Context, terminalText: String, target: String) {
    try {
        val report = JSONObject().apply {
            put("target", target)
            put("timestamp", System.currentTimeMillis())
            put("summary", "Automated Reconnaissance Report")
            
            val findings = JSONArray()
            if (terminalText.contains("CRACKED")) findings.put("Default Credentials Found (CRITICAL)")
            if (terminalText.contains("VULNERABILITY")) findings.put("Known CVE Detected")
            if (terminalText.contains("EXPOSED")) findings.put("Sensitive Directory Exposed")
            if (terminalText.contains("ONVIF AUTH SUCCESS")) findings.put("ONVIF Service compromised")
            
            put("findings", findings)
            put("raw_output", terminalText)
        }
        
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Vigil_Data_${target.replace("/", "_").replace(".", "_")}_$timeStamp.json"
        val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), fileName)
        FileOutputStream(file).use { it.write(report.toString(4).toByteArray()) }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}


@Composable
fun StormBreakerTab(onLogUpdate: (String) -> Unit) {
    var template by remember { mutableStateOf("NearYou") }
    var redirectUrl by remember { mutableStateOf("https://www.youtube.com/watch?v=dQw4w9WgXcQ") }
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("⚡ STORMBREAKER", color = Color.Red, fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("Social Engineering & Tracking Link Generator", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(24.dp))

        Text("Select Template", color = Color.White, fontWeight = FontWeight.Bold)
        val templates = listOf("NearYou", "Webcam", "Google-Drive", "WhatsApp")
        templates.forEach { t ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { template = t }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                RadioButton(
                    selected = (template == t),
                    onClick = { template = t },
                    colors = RadioButtonDefaults.colors(selectedColor = Color.Red)
                )
                Text(t, color = Color.LightGray, modifier = Modifier.padding(start = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = redirectUrl,
            onValueChange = { redirectUrl = it },
            label = { Text("Redirect URL (After capture)") },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.White)
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = {
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("CamXploit")
                        val sys = py.getModule("sys")
                        val outputStream = TerminalOutputStream { text ->
                            scope.launch(Dispatchers.Main) { onLogUpdate(text) }
                        }
                        sys.put("stdout", outputStream)
                        module.callAttr("storm_breaker_gen", template, redirectUrl)
                    } catch (e: Exception) {
                        scope.launch(Dispatchers.Main) { onLogUpdate("\n[!] Storm Error: ${e.message}") }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
        ) {
            Text("GENERATE TRACKING LINK", fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        Text("Active Results", color = Color.Cyan, fontWeight = FontWeight.Bold)
        
        var results by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
        
        LaunchedEffect(Unit) {
            while(true) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("CamXploit")
                    val jsonStr = module.callAttr("get_storm_results").toString()
                    val array = JSONArray(jsonStr)
                    val newList = mutableListOf<Map<String, String>>()
                    for (i in 0 until array.length()) {
                        val obj = array.getJSONObject(i)
                        val map = mutableMapOf<String, String>()
                        obj.keys().forEach { key -> map[key] = obj.get(key).toString() }
                        newList.add(map)
                    }
                    results = newList.reversed()
                } catch (e: Exception) {}
                delay(3000)
            }
        }

        if (results.isEmpty()) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111))
            ) {
                Text(
                    "No data captured yet. Waiting for target click...",
                    modifier = Modifier.padding(16.dp),
                    color = Color.Gray,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        } else {
            results.forEach { result ->
                Card(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A1F0A)),
                    border = BorderStroke(1.dp, Color.Green)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("TARGET: ${result["ip"]}", color = Color.Green, fontWeight = FontWeight.Bold)
                            Text(result["time"] ?: "", color = Color.Gray, fontSize = 10.sp)
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Device: ${result["ua"]?.take(40)}...", color = Color.LightGray, fontSize = 11.sp)
                        if (result.containsKey("lat")) {
                            Text("📍 Location: ${result["lat"]}, ${result["lon"]}", color = Color.Cyan, fontSize = 12.sp)
                        }
                        if (result.containsKey("image")) {
                            Text("📸 Webcam Capture Received!", color = Color.Magenta, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveViewScreen(
    streamUrl: String,
    streamType: String,
    onBack: () -> Unit
) {
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("📺 $streamType") },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back")
                }
            }
        )

        if (isLoading && !streamUrl.startsWith("rtsp")) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (streamUrl.startsWith("rtsp")) {
            AndroidView(
                factory = { ctx ->
                    val player = androidx.media3.exoplayer.ExoPlayer.Builder(ctx).build()
                    player.setMediaItem(
                        androidx.media3.common.MediaItem.fromUri(streamUrl)
                    )
                    player.prepare()
                    player.playWhenReady = true
                    androidx.media3.ui.PlayerView(ctx).apply {
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AndroidView(
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: SslError?) {
                                handler?.proceed()
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                            }

                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, err: WebResourceError?) {
                                isLoading = false
                                error = "Failed to load - Try another link"
                            }
                        }
                    }
                },
                update = { it.loadUrl(streamUrl) },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotViewer(imageUrl: String, onBack: () -> Unit) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(imageUrl) {
        withContext(Dispatchers.IO) {
            try {
                val bytes = java.net.URL(imageUrl).readBytes()
                val bitmap = android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                imageBitmap = bitmap?.asImageBitmap()
            } catch (e: Exception) {
                // error
            }
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("📸 Camera Snapshot") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, "Back") } }
        )

        if (isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap!!,
                contentDescription = "Snapshot",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text("Failed to load snapshot", color = Color.Red, modifier = Modifier.padding(16.dp))
        }
    }
}
