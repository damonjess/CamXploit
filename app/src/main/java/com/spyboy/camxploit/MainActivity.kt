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
import androidx.media3.common.util.UnstableApi
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
import android.net.wifi.WifiManager
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.InetAddress
import java.net.Socket
import java.net.InetSocketAddress
import java.io.BufferedReader
import java.io.InputStreamReader
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import androidx.compose.animation.core.*
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

@OptIn(UnstableApi::class)
class MainActivity : ComponentActivity() {
    private var multicastLock: WifiManager.MulticastLock? = null

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
    var showLiveView by remember { mutableStateOf(false) }
    var selectedStreamUrl by remember { mutableStateOf("") }
    var selectedStreamType by remember { mutableStateOf("") }
    var shodanApiKey by remember { mutableStateOf("") }
    var shodanQuery by remember { mutableStateOf("webcam") }
    var showShodanDialog by remember { mutableStateOf(false) }
    var selectedUrl by remember { mutableStateOf("") }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        if (!fineLocationGranted && !coarseLocationGranted) {
            Toast.makeText(context, "Location permission is required for LAN scanning", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (fineLocation != PackageManager.PERMISSION_GRANTED || coarseLocation != PackageManager.PERMISSION_GRANTED) {
            permissionLauncher.launch(arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ))
        }
    }

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
                    label = { Text("STORM") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color(0xFFFFBF00),
                        selectedTextColor = Color(0xFFFFBF00),
                        indicatorColor = Color(0xFF1E1E1E)
                    )
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
                        val (user, pass) = extractCredentials(terminalText)
                        selectedUrl = buildAuthUrl(url, user, pass)
                        selectedTab = 3
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
                    val (user, pass) = extractCredentials(terminalText)
                    selectedUrl = buildAuthUrl(url, user, pass)
                    selectedTab = 3
                }, {
                    val targetIp = ipInput.ifEmpty {
                        Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").findAll(terminalText).lastOrNull()?.value ?: ""
                    }
                    if (targetIp.isBlank()) {
                        Toast.makeText(context, "No active target IP. Run a scan first.", Toast.LENGTH_SHORT).show()
                        return@IntelTab
                    }
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
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                terminalText += "\n[!] ONVIF Error: ${e.message}\n"
                            }
                        }
                    }
                })
                2 -> ArchiveTab(context, selectedTab) { file -> viewingFile = file }
                3 -> StreamTab(terminalText, selectedUrl, { selectedUrl = it })
                4 -> LanScannerTab(
                    onScanComplete = { },
                    onDeviceSelect = { ip ->
                        ipInput = ip
                        selectedTab = 0
                        Toast.makeText(context, "Target set to $ip", Toast.LENGTH_SHORT).show()
                    }
                )
                5 -> StormTab(terminalText) { text: String -> terminalText += text }
            }

            capturedBitmap?.let { bitmap ->
                Spacer(modifier = Modifier.height(20.dp))
                Text("LAST CAPTURED SNAPSHOT", color = Color.Yellow, fontWeight = FontWeight.Black, fontSize = 14.sp)
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
                                        Text("[VIEW]")
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
fun IntelTab(
    terminalText: String,
    onCaptureSnapshot: () -> Unit,
    onPreviewStream: (String) -> Unit,
    onTestOnvif: () -> Unit
) {
    val context = LocalContext.current
    val scope   = rememberCoroutineScope()

    var onvifOutput  by remember { mutableStateOf("") }
    var onvifRunning by remember { mutableStateOf(false) }
    var showOnvifResult by remember { mutableStateOf(false) }

    val streams    = terminalText.lines().filter { it.contains("http") || it.contains("rtsp") }
    val vulns      = terminalText.lines().filter { it.contains("VULNERABILITY") || it.contains("CRITICAL") || it.contains("FIRE") }
    val deviceInfo = terminalText.lines().filter { it.contains("Model:") || it.contains("Firmware:") || it.contains("Manufacturer:") }

    // Pull target IP from last scan
    val targetIp = remember(terminalText) {
        Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").find(terminalText)?.value ?: ""
    }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("CATEGORIZED INTEL", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))

        IntelSection("STREAMS FOUND",           streams,    Color.Green, Icons.Default.Videocam,       onPreviewStream)
        IntelSection("SECURITY VULNERABILITIES", vulns,      Color.Red,   Icons.Default.ReportProblem,  onPreviewStream)
        IntelSection("DEVICE HARDWARE INFO",     deviceInfo, Color.Cyan,  Icons.Default.Info,           onPreviewStream)

        Spacer(modifier = Modifier.height(20.dp))
        Text("QUICK ACTIONS", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))

        // Target display
        if (targetIp.isNotEmpty()) {
            Text(
                "Target: $targetIp",
                color = Color.Gray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        } else {
            Text(
                "⚠️ No target found — run a Console scan first",
                color = Color.Yellow,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = {
                    if (targetIp.isEmpty()) {
                        Toast.makeText(context, "Run a scan first to set a target", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    onvifRunning    = true
                    showOnvifResult = true
                    onvifOutput     = "🔍 Probing $targetIp for ONVIF services...\n"

                    scope.launch(Dispatchers.IO) {
                        try {
                            val py     = Python.getInstance()
                            val module = py.getModule("CamXploit")
                            val result = module.callAttr("onvif_probe", targetIp).toString()
                            withContext(Dispatchers.Main) {
                                onvifOutput  = result
                                onvifRunning = false
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                onvifOutput  = "❌ ONVIF probe error: ${e.message}"
                                onvifRunning = false
                            }
                        }
                    }
                },
                enabled  = !onvifRunning,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(
                    containerColor = if (onvifRunning) Color.DarkGray else Color(0xFF333333)
                ),
                shape = RoundedCornerShape(4.dp)
            ) {
                if (onvifRunning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Color.Cyan,
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(6.dp))
                }
                Text(if (onvifRunning) "PROBING..." else "TEST ONVIF", fontSize = 10.sp)
            }

            Button(
                onClick  = onCaptureSnapshot,
                modifier = Modifier.weight(1f),
                colors   = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                shape    = RoundedCornerShape(4.dp)
            ) {
                Text("CAPTURE SNAP", fontSize = 10.sp)
            }
        }

        // ONVIF result output box
        if (showOnvifResult && onvifOutput.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = Color(0xFF050505)),
                border   = BorderStroke(1.dp, Color(0xFF00AAAA))
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "ONVIF PROBE RESULTS",
                            color = Color.Cyan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Row {
                            // Copy result button
                            IconButton(
                                onClick = {
                                    val clipboard = context.getSystemService(
                                        Context.CLIPBOARD_SERVICE
                                    ) as ClipboardManager
                                    clipboard.setPrimaryClip(
                                        ClipData.newPlainText("ONVIF Result", onvifOutput)
                                    )
                                    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.ContentCopy, null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            // Dismiss button
                            IconButton(
                                onClick = { showOnvifResult = false },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close, null,
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    SelectionContainer {
                        Text(
                            text       = onvifOutput,
                            color      = if (onvifOutput.contains("CREDENTIALS WORK"))
                                             Color(0xFF00FF41)
                                         else Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize   = 11.sp,
                            lineHeight = 16.sp,
                            modifier   = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 400.dp)
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    // If RTSP links found in result, show launch buttons
                    val rtspLinks = onvifOutput.lines()
                        .filter { it.contains("🔗 RTSP:") }
                        .map { it.substringAfter("🔗 RTSP:").trim() }

                    if (rtspLinks.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        Text("DETECTED STREAMS:", color = Color.Cyan, fontSize = 11.sp)
                        rtspLinks.forEach { url ->
                            Button(
                                onClick = {
                                    StreamViewerActivity.launch(context, url, targetIp)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF003300)
                                )
                            ) {
                                Icon(
                                    Icons.Default.Videocam, null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(
                                    url.take(45) + if (url.length > 45) "..." else "",
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("EXTERNAL RECON LINKS", color = Color.Magenta, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReconButton("SHODAN", "https://www.shodan.io/host/$targetIp", context)
            ReconButton("CENSYS", "https://search.censys.io/hosts/$targetIp", context)
            ReconButton("ZOOMEYE", "https://www.zoomeye.org/searchResult?q=$targetIp", context)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReconButton("MAPS", "https://www.google.com/maps/search/$targetIp", context)
            ReconButton("DORK", "https://www.google.com/search?q=inurl:\"/view/viewer_index.shtml\"+ip:$targetIp", context)
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("GOOGLE DORKING SUGGESTIONS", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        val dorks = listOf(
            "inurl:\"/view/viewer_index.shtml\"",
            "intitle:\"Live View / - AXIS\"",
            "inurl:\"/mjpg/video.mjpg\"",
            "inurl:\"view/index.shtml\"",
            "inurl:\"top.htm?login\""
        )
        dorks.forEach { dork ->
            Text(
                text = dork,
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp).clickable {
                    val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://www.google.com/search?q=$dork"))
                    context.startActivity(intent)
                }
            )
        }
        if (terminalText.isEmpty()) {
            Text(
                "No intel collected yet. Start a scan first.",
                color = Color.Gray,
                modifier = Modifier.padding(20.dp)
            )
        }
    }
}

@Composable
fun ReconButton(label: String, url: String, context: Context) {
    Button(
        onClick = {
            val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url))
            context.startActivity(intent)
        },
        modifier = Modifier.height(36.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, Color.DarkGray),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(horizontal = 8.dp)
    ) {
        Text(label, fontSize = 10.sp, color = Color.White)
    }
}

fun extractCredentials(terminalText: String): Pair<String, String> {
    val match = Regex("""CRACKED \((?:HTTP|RTSP)\): ([^:]+):([^\s\n]+)""").find(terminalText)
    return if (match != null) {
        match.groupValues[1] to match.groupValues[2]
    } else {
        "" to ""
    }
}

fun buildAuthUrl(url: String, user: String, pass: String): String {
    if (user.isBlank() || pass.isBlank() || url.contains("@")) return url
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
fun StreamTab(terminalText: String, selectedUrl: String, onUrlSelected: (String) -> Unit) {
    // Auto-extract credentials if any were found in the console
    val credentials = remember(terminalText) {
        val credMatch = Regex("(?:CRACKED|Success)[^\\n]*?(\\w+):(\\w+)\\s*@")
            .find(terminalText)
        val user = credMatch?.groupValues?.get(1) ?: "admin"
        val pass = credMatch?.groupValues?.get(2) ?: "admin"
        user to pass
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

    // Auto-select first found URL if none is selected
    LaunchedEffect(streamUrls) {
        if (selectedUrl.isEmpty() && streamUrls.isNotEmpty()) {
            onUrlSelected(streamUrls.first())
        }
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
                    onClick = { onUrlSelected(url) },
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
                            onUrlSelected(customUrl) 
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
            val (user, pass) = credentials
            val authUrl = selectedUrl
                .replace("http://", "http://$user:$pass@")
                .replace("https://", "https://$user:$pass@")
            val authenticatedUrl = if (selectedUrl.startsWith("rtsp")) {
                buildAuthUrl(selectedUrl, user, pass)
            } else {
                authUrl
            }
            key(authenticatedUrl) { // Recompose player when URL changes
                val isRtsp = authenticatedUrl.startsWith("rtsp")
                val isMjpeg = authenticatedUrl.contains("mjpeg", ignoreCase = true) || 
                             authenticatedUrl.contains("mjpg", ignoreCase = true) ||
                             authenticatedUrl.contains("videostream.cgi", ignoreCase = true) ||
                             authenticatedUrl.contains("faststream", ignoreCase = true)
                val isSnapshot = authenticatedUrl.contains("snapshot", ignoreCase = true) || 
                                authenticatedUrl.contains(".jpg", ignoreCase = true)

                if (isRtsp) {
                    AndroidView<PlayerView>(
                        factory = { ctx ->
                            val player = ExoPlayer.Builder(ctx).build()
                            val mediaItem = MediaItem.fromUri(authenticatedUrl)
                            val rtspMediaSource = RtspMediaSource.Factory()
                                .setForceUseRtpTcp(true)
                                .createMediaSource(mediaItem)
                            player.setMediaSource(rtspMediaSource)
                            player.prepare()
                            player.play()
                            PlayerView(ctx).apply {
                                this.player = player
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, Color(0xFF333333))
                    )
                } else if (isMjpeg) {
                    // MJPEG Stream via WebView
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                            handler?.proceed()
                                        }
                                    }
                                    loadUrl(authUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                } else if (isSnapshot) {
                    var snapshotBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                    var refreshCount by remember { mutableIntStateOf(0) }

                    LaunchedEffect(authenticatedUrl, refreshCount) {
                        withContext(Dispatchers.IO) {
                            try {
                                val bytes = java.net.URL(authenticatedUrl).readBytes()
                                val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                snapshotBitmap = bitmap?.asImageBitmap()
                            } catch (e: Exception) {}
                        }
                        delay(2000)
                        refreshCount++
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .border(1.dp, Color(0xFF333333))
                            .background(Color.Black),
                        contentAlignment = Alignment.Center
                    ) {
                        snapshotBitmap?.let {
                            Image(
                                bitmap = it,
                                contentDescription = "Snapshot",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        } ?: CircularProgressIndicator(color = Color.Magenta)
                    }
                } else {
                    // Fallback for other HTTP links
                    Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    webViewClient = object : WebViewClient() {
                                        override fun onReceivedSslError(view: WebView?, handler: SslErrorHandler?, error: android.net.http.SslError?) {
                                            handler?.proceed()
                                        }
                                    }
                                    loadUrl(authUrl)
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
                                TextButton(
                                    onClick = { onPreviewStream(url) }
                                ) {
                                    Text("[VIEW]", color = Color.Magenta)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ArchiveTab(context: Context, selectedTab: Int, onFileClick: (File) -> Unit) {
    var refreshTrigger by remember { mutableIntStateOf(0) }

    // Auto-refresh whenever Archive tab is opened
    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) refreshTrigger++
    }

    val files = remember(refreshTrigger) {
        val docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val picDir = context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val allFiles = mutableListOf<File>()
        docDir?.listFiles()?.let { allFiles.addAll(it) }
        picDir?.listFiles()?.let { allFiles.addAll(it) }
        allFiles.sortedByDescending { it.lastModified() }
    }

    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "SAVED REPORTS & LOGS",
                color = Color.Yellow,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            // Manual refresh button
            IconButton(onClick = { refreshTrigger++ }) {
                Icon(Icons.Default.Refresh, "Refresh", tint = Color.Cyan)
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Stats bar
        val snapCount   = files.count { it.extension == "jpg" || it.extension == "png" }
        val reportCount = files.count { it.extension == "html" || it.extension == "json" }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("📷 $snapCount snapshots",  color = Color.Gray, fontSize = 11.sp)
            Text("📄 $reportCount reports",  color = Color.Gray, fontSize = 11.sp)
            Text("📁 ${files.size} total",   color = Color.Gray, fontSize = 11.sp)
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (files.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.FolderOpen,
                        null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "No reports found in archive.",
                        color = Color.DarkGray,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "Run a scan or capture a snapshot to populate.",
                        color = Color.DarkGray,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                files.forEach { file ->
                    val isImage = file.extension in listOf("jpg", "jpeg", "png")
                    val isSnap  = file.name.contains("Snap") || file.name.contains("Capture")

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onFileClick(file) },
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSnap  -> Color(0xFF0A1A0A)  // green tint for snapshots
                                else    -> Color(0xFF080808)
                            }
                        ),
                        border = BorderStroke(
                            1.dp,
                            when {
                                isSnap  -> Color(0xFF1A3A1A)
                                else    -> Color(0xFF111111)
                            }
                        ),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail or icon
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .background(Color(0xFF111111), RoundedCornerShape(4.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isImage) {
                                    val bitmap = remember(file.absolutePath) {
                                        try {
                                            val opts = BitmapFactory.Options().apply {
                                                inSampleSize = 4  // downsample for thumbnail
                                            }
                                            BitmapFactory.decodeFile(file.absolutePath, opts)
                                        } catch (_: Exception) { null }
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.BrokenImage, null, tint = Color.Gray)
                                    }
                                } else {
                                    Icon(
                                        imageVector = when (file.extension) {
                                            "html" -> Icons.Default.Code
                                            "json" -> Icons.Default.DataObject
                                            else   -> Icons.Default.Description
                                        },
                                        contentDescription = null,
                                        tint = when (file.extension) {
                                            "html" -> Color.Cyan
                                            "json" -> Color.Yellow
                                            else   -> Color.Gray
                                        },
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(10.dp))

                            // File info
                            Column(modifier = Modifier.weight(1f)) {
                                // Badge + name
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (isSnap) {
                                        Text(
                                            "📷 SNAP",
                                            color = Color.Green,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier
                                                .background(Color(0xFF003300), RoundedCornerShape(3.dp))
                                                .padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                        Spacer(Modifier.width(4.dp))
                                    }
                                    Text(
                                        file.name,
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        maxLines = 1
                                    )
                                }
                                Spacer(Modifier.height(2.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault())
                                            .format(Date(file.lastModified())),
                                        color = Color.Gray,
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        formatFileSize(file.length()),
                                        color = Color.DarkGray,
                                        fontSize = 10.sp
                                    )
                                }
                            }

                            // Action buttons
                            Row {
                                IconButton(
                                    onClick = { openFile(context, file) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isImage) Icons.Default.Image
                                                      else Icons.Default.OpenInNew,
                                        contentDescription = "Open",
                                        tint = Color.Green,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { shareFile(context, file) },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Share, "Share",
                                        tint = Color.Cyan,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                IconButton(
                                    onClick = { if (file.delete()) refreshTrigger++ },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Delete, "Delete",
                                        tint = Color.Red,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Add this helper anywhere in the file
fun formatFileSize(bytes: Long): String = when {
    bytes < 1024        -> "${bytes}B"
    bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024.0)}KB"
    else                -> "${"%.1f".format(bytes / (1024.0 * 1024))}MB"
}

data class ScannedDevice(
    val ip: String,
    val hostname: String?,
    val openPorts: List<Int>
)

@Composable
fun LanScannerTab(onScanComplete: (String) -> Unit, onDeviceSelect: (String) -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var isScanning by remember { mutableStateOf(false) }
    var scanOutput by remember { mutableStateOf("Ready to scan local network...") }
    val discoveredRadarDevices = remember { mutableStateListOf<ScannedDevice>() }
    var discoveredDevices by remember { mutableStateOf<List<String>>(emptyList()) }
    var discoveredRangeDevices by remember { mutableStateOf<List<ScannedDevice>>(emptyList()) }
    var scanProgress by remember { mutableFloatStateOf(0f) }
    var selectedSubTab by remember { mutableIntStateOf(0) }
    val subTabs = listOf("RADAR", "RANGE", "DEVICES", "LOGS")

    // Radar Animation
    val infiniteTransition = rememberInfiniteTransition(label = "RadarTransition")
    val radarRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarRotation"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔍 LAN Scanner", color = Color.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
        Text("Discover devices on your local network", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        TabRow(
            selectedTabIndex = selectedSubTab,
            containerColor = Color.Transparent,
            contentColor = Color.Cyan,
            divider = { Divider(color = Color.DarkGray) },
            indicator = { tabPositions ->
                TabRowDefaults.Indicator(
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
                            .size(200.dp)
                            .padding(10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Background Circles
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val center = size / 2f
                            val radius = size.minDimension / 2f
                            drawCircle(color = Color.DarkGray, radius = radius, style = Stroke(1f))
                            drawCircle(color = Color.DarkGray, radius = radius * 0.66f, style = Stroke(1f))
                            drawCircle(color = Color.DarkGray, radius = radius * 0.33f, style = Stroke(1f))
                            
                            // Crosshairs
                            drawLine(Color.DarkGray, start = androidx.compose.ui.geometry.Offset(center.width, 0f), end = androidx.compose.ui.geometry.Offset(center.width, size.height), strokeWidth = 1f)
                            drawLine(Color.DarkGray, start = androidx.compose.ui.geometry.Offset(0f, center.height), end = androidx.compose.ui.geometry.Offset(size.width, center.height), strokeWidth = 1f)
                        }

                        // Rotating Sweep
                        if (isScanning) {
                            Canvas(modifier = Modifier.fillMaxSize().rotate(radarRotation)) {
                                val radius = size.minDimension / 2f
                                drawArc(
                                    brush = Brush.sweepGradient(
                                        0.0f to Color.Transparent,
                                        0.25f to Color.Green.copy(alpha = 0.4f),
                                        0.5f to Color.Transparent
                                    ),
                                    startAngle = 0f,
                                    sweepAngle = 90f,
                                    useCenter = true
                                )
                                drawLine(
                                    color = Color.Green,
                                    start = center,
                                    end = androidx.compose.ui.geometry.Offset(center.x + radius, center.y),
                                    strokeWidth = 2f
                                )
                            }
                        }
                        
                        Icon(
                            Icons.Default.SettingsInputAntenna,
                            contentDescription = null,
                            tint = if (isScanning) Color.Green else Color.Gray,
                            modifier = Modifier.size(48.dp)
                        )
                    }

                    if (isScanning) {
                        Text(
                            text = "Scanning... ${(scanProgress * 100).toInt()}%",
                            color = Color.Green,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        LinearProgressIndicator(
                            progress = scanProgress,
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 8.dp),
                            color = Color.Green,
                            trackColor = Color.DarkGray,
                            strokeCap = StrokeCap.Round
                        )
                    } else if (discoveredRadarDevices.isNotEmpty()) {
                        Text(
                            text = "Scan Complete: ${discoveredRadarDevices.size} devices found",
                            color = Color.Cyan,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            isScanning = true
                            discoveredRadarDevices.clear()
                            scanProgress = 0f
                            scanOutput = "> Starting native ping sweep...\n"
                            
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val subnets = mutableSetOf<String>()
                                    // 1. Get local IP and determine primary subnet
                                    val interfaces = NetworkInterface.getNetworkInterfaces()
                                    for (inf in Collections.list(interfaces)) {
                                        if (inf.isLoopback || !inf.isUp) continue
                                        for (addr in Collections.list(inf.inetAddresses)) {
                                            if (addr is Inet4Address && !addr.isLoopbackAddress) {
                                                val ip = addr.hostAddress ?: continue
                                                subnets.add(ip.substringBeforeLast("."))
                                            }
                                        }
                                    }
                                    
                                    // Add 192.168.0 as fallback if not present
                                    subnets.add("192.168.1")
                                    subnets.add("192.168.0")
                                    
                                    val targetSubnets = subnets.take(3)
                                    val totalIps = targetSubnets.size * 254
                                    var scannedCount = 0
                                    val portsToCheck = listOf(80, 554, 8080, 8000, 443, 37777)

                                    targetSubnets.forEach { subnet ->
                                        val jobs = (1..254).map { i ->
                                            async {
                                                val ip = "$subnet.$i"
                                                try {
                                                    val address = InetAddress.getByName(ip)
                                                    if (address.isReachable(300)) {
                                                        val hostname = try {
                                                            val host = address.canonicalHostName
                                                            if (host != ip) host else null
                                                        } catch (e: Exception) { null }

                                                        val openPorts = mutableListOf<Int>()
                                                        for (port in portsToCheck) {
                                                            try {
                                                                Socket().use { socket ->
                                                                    socket.connect(InetSocketAddress(ip, port), 150)
                                                                    openPorts.add(port)
                                                                }
                                                            } catch (e: Exception) {}
                                                        }
                                                        
                                                        withContext(Dispatchers.Main) {
                                                            discoveredRadarDevices.add(ScannedDevice(ip, hostname, openPorts))
                                                            scanOutput += "Discovered: $ip ${hostname ?: ""}\n"
                                                        }
                                                    }
                                                } catch (e: Exception) {}
                                                
                                                synchronized(this) {
                                                    scannedCount++
                                                    scanProgress = scannedCount.toFloat() / totalIps
                                                }
                                            }
                                        }
                                        jobs.awaitAll()
                                    }
                                    
                                    withContext(Dispatchers.Main) {
                                        isScanning = false
                                        scanOutput += "\nScan finished. Found ${discoveredRadarDevices.size} devices.\n"
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        isScanning = false
                                        scanOutput += "\n[!] Native Scan Error: ${e.message}\n"
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

                    Spacer(modifier = Modifier.height(16.dp))

                    // Found Devices List for RADAR tab
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(discoveredRadarDevices) { device ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                                border = BorderStroke(1.dp, Color(0xFF1A1A1A))
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (device.openPorts.contains(554) || device.openPorts.contains(8000)) Icons.Default.Videocam else Icons.Default.Router,
                                        contentDescription = null,
                                        tint = if (device.openPorts.isNotEmpty()) Color.Green else Color.Cyan,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(device.ip, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                        if (!device.hostname.isNullOrBlank()) {
                                            Text(device.hostname, color = Color.Gray, fontSize = 11.sp)
                                        }
                                        if (device.openPorts.isNotEmpty()) {
                                            Text("Ports: ${device.openPorts.joinToString(", ")}", color = Color.Green, fontSize = 10.sp)
                                        }
                                    }
                                    Button(
                                        onClick = { onDeviceSelect(device.ip) },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20))
                                    ) {
                                        Text("SCAN", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            1 -> { // RANGE View
                Column(modifier = Modifier.fillMaxSize()) {
                    val subnets = listOf("192.168.1", "192.168.0")
                    Text("Scanning Subnets: ${subnets.joinToString(", ")}", color = Color.Yellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    if (isScanning) {
                        LinearProgressIndicator(
                            progress = scanProgress,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            color = Color.Cyan,
                            trackColor = Color.DarkGray
                        )
                    }

                    Button(
                        onClick = {
                            isScanning = true
                            discoveredRangeDevices = emptyList()
                            scanProgress = 0f
                            scope.launch(Dispatchers.IO) {
                                val results = mutableListOf<ScannedDevice>()
                                val portsToCheck = listOf(80, 554, 8080, 8000)
                                val totalIps = subnets.size * 254
                                var scannedCount = 0

                                subnets.forEach { subnet ->
                                    val jobs = (1..254).map { i ->
                                        async {
                                            val ip = "$subnet.$i"
                                            try {
                                                val address = InetAddress.getByName(ip)
                                                if (address.isReachable(300)) {
                                                    val hostname = try {
                                                        val host = address.canonicalHostName
                                                        if (host != ip) host else null
                                                    } catch (e: Exception) { null }
                                                    
                                                    val openPorts = mutableListOf<Int>()
                                                    for (port in portsToCheck) {
                                                        try {
                                                            Socket().use { socket ->
                                                                socket.connect(InetSocketAddress(ip, port), 200)
                                                                openPorts.add(port)
                                                            }
                                                        } catch (e: Exception) {}
                                                    }
                                                    synchronized(results) {
                                                        results.add(ScannedDevice(ip, hostname, openPorts))
                                                    }
                                                }
                                            } catch (e: Exception) {}
                                            synchronized(this@launch) {
                                                scannedCount++
                                                scanProgress = scannedCount.toFloat() / totalIps
                                            }
                                        }
                                    }
                                    jobs.awaitAll()
                                }
                                withContext(Dispatchers.Main) {
                                    discoveredRangeDevices = results.sortedBy { it.ip }
                                    isScanning = false
                                    scanOutput += "\nRange Scan Complete: ${results.size} devices found."
                                }
                            }
                        },
                        enabled = !isScanning,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333))
                    ) {
                        Text(if (isScanning) "SCANNING..." else "SCAN SUBNET")
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (discoveredRangeDevices.isEmpty() && !isScanning) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No devices found. Tap SCAN SUBNET.", color = Color.Gray)
                        }
                    } else {
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            discoveredRangeDevices.forEach { device ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
                                    border = BorderStroke(1.dp, Color(0xFF1A1A1A))
                                ) {
                                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Router, null, tint = Color.Green, modifier = Modifier.size(24.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(device.ip, color = Color.White, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                                            if (!device.hostname.isNullOrBlank()) {
                                                Text(device.hostname, color = Color.Gray, fontSize = 11.sp)
                                            }
                                            if (device.openPorts.isNotEmpty()) {
                                                Text("Open Ports: ${device.openPorts.joinToString(", ")}", color = Color.Cyan, fontSize = 11.sp)
                                            }
                                        }
                                        Button(
                                            onClick = { onDeviceSelect(device.ip) },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text("SCAN", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            2 -> { // DEVICES View
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
            3 -> { // LOGS View
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
fun StormTab(terminalText: String, onLogUpdate: (String) -> Unit) {
    var targetIp by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("DOS_RESILIENCE") }
    var stormOutput by remember { mutableStateOf("> Ready for stress testing...\n") }
    var expanded by remember { mutableStateOf(false) }
    val options = listOf("DOS_RESILIENCE", "SSDP_FLOOD", "PORT_STRESS")
    val scope = rememberCoroutineScope()

    // Auto-fill IP from terminalText
    LaunchedEffect(terminalText) {
        val ipMatch = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").findAll(terminalText).lastOrNull()
        if (ipMatch != null && targetIp.isEmpty()) {
            targetIp = ipMatch.value
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState())) {
        Text("⚡ STORM MODULE", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Black)
        Text("Network Stress & Resilience Testing", color = Color.Gray, fontSize = 12.sp)

        Spacer(modifier = Modifier.height(16.dp))

        // Warning Box
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF332200)),
            border = BorderStroke(1.dp, Color(0xFFFFBF00)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                "⚠️ WARNING: Only use on your own devices or authorized targets. Stress testing can cause network instability.",
                color = Color(0xFFFFBF00),
                modifier = Modifier.padding(12.dp),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Target IP Input
        OutlinedTextField(
            value = targetIp,
            onValueChange = { targetIp = it },
            label = { Text("Target IP", color = Color(0xFFFFBF00)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFBF00),
                unfocusedBorderColor = Color.DarkGray,
                focusedLabelColor = Color(0xFFFFBF00)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Attack Type Dropdown
        Box(modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth(),
                border = BorderStroke(1.dp, Color.DarkGray)
            ) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(selectedType, color = Color.White)
                    Icon(Icons.Default.ArrowDropDown, null, tint = Color(0xFFFFBF00))
                }
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f).background(Color(0xFF111111))
            ) {
                options.forEach { type ->
                    DropdownMenuItem(
                        text = { Text(type, color = Color.White) },
                        onClick = {
                            selectedType = type
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (targetIp.isNotBlank()) {
                    stormOutput += "> Initiating $selectedType on $targetIp...\n"
                    scope.launch(Dispatchers.IO) {
                        try {
                            val py = Python.getInstance()
                            val module = py.getModule("CamXploit")
                            val sys = py.getModule("sys")
                            val outputStream = TerminalOutputStream { text ->
                                scope.launch(Dispatchers.Main) { stormOutput += text }
                            }
                            sys.put("stdout", outputStream)
                            // Assuming port 80 for basic test
                            module.callAttr("test_dos_resilience", targetIp, 80)
                            withContext(Dispatchers.Main) {
                                stormOutput += "\n> $selectedType Complete.\n"
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                stormOutput += "\n[!] Storm Error: ${e.message}\n"
                            }
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFBF00))
        ) {
            Text("START STRESS TEST", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Results Terminal
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .border(1.dp, Color(0xFF332200), RoundedCornerShape(4.dp)),
            color = Color(0xFF050505)
        ) {
            SelectionContainer {
                Text(
                    text = stormOutput,
                    color = Color(0xFFFFBF00), // Amber
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(10.dp).verticalScroll(rememberScrollState())
                )
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
            AndroidView<PlayerView>(
                factory = { ctx ->
                    val player = ExoPlayer.Builder(ctx).build()
                    val mediaItem = MediaItem.fromUri(streamUrl)
                    val rtspMediaSource = RtspMediaSource.Factory()
                        .setForceUseRtpTcp(true)
                        .createMediaSource(mediaItem)
                    player.setMediaSource(rtspMediaSource)
                    player.prepare()
                    player.play()
                    PlayerView(ctx).apply {
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

fun getLocalSubnet(): String {
    try {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        for (inf in Collections.list(interfaces)) {
            if (inf.isLoopback || !inf.isUp) continue
            for (addr in Collections.list(inf.inetAddresses)) {
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    val ip = addr.hostAddress ?: continue
                    return ip.substringBeforeLast(".") + ".0/24"
                }
            }
        }
    } catch (e: Exception) {}
    return "192.168.1.0/24"
}
