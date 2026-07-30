@file:OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spyboy.camxploit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.*
import android.provider.MediaStore
import android.media.projection.MediaProjectionManager
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.util.Base64
import android.view.TextureView
import android.webkit.*
import android.widget.Toast
import android.widget.ImageButton
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.rtsp.RtspMediaSource
import androidx.media3.ui.PlayerView
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.json.JSONArray
import org.json.JSONObject
import java.io.*
import java.net.*
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { CamGuardianApp() }
    }

    fun openWebcamSearch(ip: String, brand: String? = null) {
        val baseShodan = "https://www.shodan.io/search?query="
        val query = when {
            brand?.contains("hikvision", ignoreCase = true) == true -> "hikvision+port:80+OR+port:554"
            brand?.contains("dahua", ignoreCase = true) == true -> "dahua+rtsp"
            else -> "webcam+OR+ip-camera+OR+rtsp+$ip"
        }
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(baseShodan + query)))
        } catch (e: Exception) {
            Toast.makeText(this, "Cannot open browser: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}

@Composable
fun NmapTab(
    context: Context,
    lastIp: String? = null,
    onTabSwitch: (Int) -> Unit,
    onIpSelected: (String) -> Unit
) {
    var nmapIp by remember { mutableStateOf("") }
    var nmapOutput by remember { mutableStateOf("> Ready for scan...\n") }
    var nmapIsScanning by remember { mutableStateOf(false) }
    var scanType by remember { mutableStateOf("QUICK") }
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(nmapOutput.lines().size) {
        val lines = nmapOutput.lines()
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        if (lastIp != null) nmapIp = lastIp
    }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "🛰️ NMAP NETWORK AUDIT", color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))

        Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(12.dp)) {
            Column {
                Text(text = "TARGET IP / SUBNET", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                BasicTextField(
                    value = nmapIp,
                    onValueChange = { nmapIp = it },
                    textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Green)
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("QUICK", "SERVICE", "SUBNET").forEach { type ->
                Button(
                    onClick = { scanType = type },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (scanType == type) Color(0xFF1B5E20) else Color(0xFF111111)
                    ),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, if (scanType == type) Color.Green else Color(0xFF333333))
                ) {
                    Text(type, color = if (scanType == type) Color.White else Color.Gray, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp)),
            color = Color(0xFF050505)
        ) {
            Box {
                SelectionContainer {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(10.dp),
                        state = listState
                    ) {
                        items(nmapOutput.lines()) { line ->
                            NmapAnnotatedText(line)
                        }
                    }
                }
                if (nmapIsScanning) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter),
                        color = Color.Green,
                        trackColor = Color.Transparent
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                if (nmapIp.isBlank()) {
                    Toast.makeText(context, "Enter target", Toast.LENGTH_SHORT).show()
                    return@Button
                }
                if (scanType == "SUBNET" && !nmapIp.contains("/")) {
                    Toast.makeText(context, "Use CIDR (e.g. /24)", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                nmapIsScanning = true
                val displayCmd = when(scanType) {
                    "QUICK" -> "nmap -T4 --open -F"
                    "SERVICE" -> "nmap -p 80,443,554,8080,8000... -T4 --open -F"
                    "SUBNET" -> "nmap -sn"
                    else -> "nmap"
                }
                nmapOutput = "> $displayCmd $nmapIp...\n"
                
                scope.launch(Dispatchers.IO) {
                    try {
                        when (scanType) {
                            "QUICK" -> quickScan(context, nmapIp, { nmapOutput += it + "\n" }, { nmapIsScanning = false })
                            "SERVICE" -> cameraScan(context, nmapIp, { nmapOutput += it + "\n" }, { nmapIsScanning = false })
                            "SUBNET" -> subnetScan(context, nmapIp, { nmapOutput += it + "\n" }, { nmapIsScanning = false })
                            else -> nmapIsScanning = false
                        }
                    } catch (e: Exception) {
                        nmapOutput += "[!] ERROR: ${e.message}\n"
                        nmapIsScanning = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF003300)),
            enabled = !nmapIsScanning,
            shape = RoundedCornerShape(4.dp),
            border = BorderStroke(1.dp, Color.Green)
        ) {
            Icon(if (nmapIsScanning) Icons.Default.Refresh else Icons.Default.Search, null, tint = Color.Green)
            Spacer(Modifier.width(8.dp))
            Text(if (nmapIsScanning) "SCANNING..." else "START AUDIT", color = Color.Green, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = {
                val ipMatch = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").find(nmapOutput)?.value
                if (ipMatch != null) {
                    onIpSelected(ipMatch)
                    onTabSwitch(0)
                } else {
                    Toast.makeText(context, "No target IP found in results", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth().height(45.dp),
            colors = ButtonDefaults.buttonColors(Color(0xFF1B5E20)),
            shape = RoundedCornerShape(4.dp)
        ) {
            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("SEND TO CONSOLE", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NmapAnnotatedText(line: String) {
    val annotatedString = buildAnnotatedString {
        when {
            line.contains("Nmap scan report") -> {
                withStyle(style = SpanStyle(color = Color.Cyan, fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            }
            line.contains("open") -> {
                withStyle(style = SpanStyle(color = Color.Green, fontWeight = FontWeight.Bold)) {
                    append(line)
                }
            }
            line.contains("closed") || line.contains("filtered") -> {
                withStyle(style = SpanStyle(color = Color.Red)) {
                    append(line)
                }
            }
            line.startsWith(">") -> {
                withStyle(style = SpanStyle(color = Color.Yellow)) {
                    append(line)
                }
            }
            else -> {
                withStyle(style = SpanStyle(color = Color.Gray, fontFamily = FontFamily.Monospace)) {
                    append(line)
                }
            }
        }
    }
    Text(
        text = annotatedString,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val filename = "Snapshot_${System.currentTimeMillis()}.png"
    var fos: OutputStream? = null
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver?.also { resolver ->
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }
                val imageUri: Uri? = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) }
            }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
            val image = File(imagesDir, filename)
            fos = FileOutputStream(image)
        }
        fos?.use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
            return true
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return false
}

@Composable
fun CamGuardianApp() {
    val context = LocalContext.current
    var terminalText by remember { mutableStateOf("> System Initialized. Awaiting Target...\n") }
    var currentPulse by remember { mutableStateOf("") }
    var consoleIpInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    
    val lanViewModel: LanViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val lanScanResults by lanViewModel.devices.collectAsState()
    val lanIsScanning by lanViewModel.isScanning.collectAsState()
    val lanProgress by lanViewModel.progress.collectAsState()

    var lanSubnet by remember { mutableStateOf("") }
    var networkSummary by remember { mutableStateOf<NetworkDiscoveryHelper.NetworkSummary?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var publicIntel by remember { mutableStateOf("") }
    var showShodanDialog by remember { mutableStateOf(false) }
    var shodanApiKey by remember { mutableStateOf("") }
    var shodanQuery by remember { mutableStateOf("webcam") }
    var selectedUrl by remember { mutableStateOf("") }
    
    val streamViewModel: StreamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val isRecording by streamViewModel.isRecording.collectAsState()
    val recordingDuration by streamViewModel.recordingDuration.collectAsState()

    val stormViewModel: StormViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = StormViewModel.Factory(context)
    )
    
    var lanNmapMode by remember { mutableStateOf(false) }
    var lanScanOutput by remember { mutableStateOf("") }
    var showExternalSearchDialog by remember { mutableStateOf(false) }
    var showDorksDialog by remember { mutableStateOf(false) }
    var selectedHostForDetail by remember { mutableStateOf<LanHost?>(null) }
    
    var isMonitorServiceRunning by remember { mutableStateOf(CameraMonitorService.isRunning) }

    val cameras by CameraDatabase.getDatabase(context).cameraDao().getAllCameras().collectAsState(initial = emptyList())
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val toggleMonitorService = {
        val intent = Intent(context, CameraMonitorService::class.java)
        if (isMonitorServiceRunning) {
            context.stopService(intent)
            isMonitorServiceRunning = false
        } else {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
            isMonitorServiceRunning = true
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            // Coordinator cleanup handled by ViewModel/Activity lifecycle
        }
    }
    
    // collectors removed (now in LanViewModel)

    val startReconScan = { targetIp: String ->
        if (targetIp.isBlank()) {
            Toast.makeText(context, "⚠️ Please select a target IP", Toast.LENGTH_SHORT).show()
        } else if (!isScanning) {
            consoleIpInput = targetIp; selectedTab = 0; isScanning = true; terminalText = "> Starting Reconnaissance on $targetIp...\n"; publicIntel = ""
            scope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val intel = py.getModule("CamXploit").callAttr("get_internetdb_info", targetIp)
                    if (intel != null) publicIntel = intel.toString()
                } catch (e: Exception) {}

                try {
                    val py = Python.getInstance()
                    val module = py.getModule("CamXploit")
                    val sys = py.getModule("sys")
                    val outputQueue = java.util.concurrent.LinkedBlockingQueue<String>()
                    val pyOutputStream = TerminalOutputStream { outputQueue.offer(it) }
                    
                    val consumerJob = launch(Dispatchers.Main) {
                        while (isActive) {
                            val batch = mutableListOf<String>()
                            outputQueue.drainTo(batch)
                            if (batch.isNotEmpty()) {
                                val newText = terminalText + batch.joinToString("")
                                val lines = newText.lines()
                                terminalText = if (lines.size > 3000) lines.takeLast(3000).joinToString("\n") else newText
                            }
                            delay(150)
                        }
                    }
                    
                    sys.put("stdout", pyOutputStream)
                    sys.put("stderr", pyOutputStream)
                    
                    val heartbeat = launch(Dispatchers.Main) {
                        val dots = listOf("⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏")
                        var i = 0
                        while (isActive) {
                            currentPulse = " " + dots[i]
                            i = (i + 1) % dots.size
                            delay(400)
                        }
                    }

                    try {
                        withTimeout(360_000) { module.callAttr("main", consoleIpInput) }
                    } finally {
                        heartbeat.cancel()
                        currentPulse = ""
                        pyOutputStream.flush()
                        delay(200)
                        val finalBatch = mutableListOf<String>()
                        outputQueue.drainTo(finalBatch)
                        if (finalBatch.isNotEmpty()) terminalText += finalBatch.joinToString("")
                        consumerJob.cancel()
                    }
                    
                    withContext(Dispatchers.Main) {
                        saveJsonReport(context, terminalText, targetIp)
                        saveContentToFile(context, terminalText, "Scan_Log", "txt")
                        if (terminalText.contains("CRACKED")) {
                            val (user, pass) = extractCredentials(terminalText)
                            val brand = Regex("""Brand:\s*(\w+)""").find(terminalText)?.groupValues?.get(1) ?: "Unknown"
                            val streamUrl = Regex("""(rtsp://\S+|http://\S+)""").find(terminalText)?.value ?: ""
                            scope.launch(Dispatchers.IO) {
                                val dao = CameraDatabase.getDatabase(context).cameraDao()
                                if (dao.getCameraByIp(targetIp) == null) dao.insertCamera(SavedCamera(nickname = "Auto $brand", ip = targetIp, username = user, password = pass, brand = brand, streamUrl = streamUrl, isOnline = true))
                            }
                        }
                    }
                } catch (e: TimeoutCancellationException) {
                    withContext(Dispatchers.Main) { terminalText += "\n[!] SCAN ABORTED: Operation timed out.\n" }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) { terminalText += "\n[!] ERROR: ${e.message}\n" }
                } finally {
                    withContext(Dispatchers.Main) { isScanning = false }
                }
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (!(permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) && !(permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)) Toast.makeText(context, "Location permission required", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
            permissions.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) permissionLauncher.launch(permissions.toTypedArray())
    }

    if (showDisclaimer) {
        AlertDialog(onDismissRequest = { }, title = { Text("LEGAL DISCLAIMER", color = Color.Red, fontWeight = FontWeight.Black) }, text = { Text("This tool is for educational and authorized security testing purposes only.", color = Color.White) }, confirmButton = { Button(onClick = { showDisclaimer = false }) { Text("I AGREE") } }, containerColor = Color(0xFF111111), shape = RoundedCornerShape(8.dp))
    }

    if (showShodanDialog) {
        var osintTab by remember { mutableIntStateOf(0) }
        val shodanFilters = listOf("Hikvision" to "product:\"Hikvision IP Camera\"", "Dahua" to "http.title:\"WEB VIEW\" Dahua", "Axis" to "product:\"Axis Communications AB\"", "Exposed RTSP" to "port:554 has_screenshot:true")

        AlertDialog(
            onDismissRequest = { showShodanDialog = false },
            title = { Text("GLOBAL OSINT RECON", color = Color.Magenta, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TabRow(selectedTabIndex = osintTab, containerColor = Color.Transparent, contentColor = Color.Magenta) {
                        Tab(selected = osintTab == 0, onClick = { osintTab = 0 }) { Text("SHODAN API", modifier = Modifier.padding(8.dp), fontSize = 10.sp) }
                        Tab(selected = osintTab == 1, onClick = { osintTab = 1 }) { Text("WEB SEARCH", modifier = Modifier.padding(8.dp), fontSize = 10.sp) }
                    }
                    Spacer(Modifier.height(16.dp))
                    if (osintTab == 0) {
                        OutlinedTextField(value = shodanApiKey, onValueChange = { shodanApiKey = it }, label = { Text("Shodan API Key") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White), singleLine = true)
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = shodanQuery, onValueChange = { shodanQuery = it }, label = { Text("Search Query") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White), singleLine = true)
                        Spacer(Modifier.height(12.dp))
                        LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(100.dp)) {
                            items(shodanFilters.size) { index -> Button(onClick = { shodanQuery = shodanFilters[index].second }, colors = ButtonDefaults.buttonColors(Color(0xFF111111)), shape = RoundedCornerShape(4.dp)) { Text(shodanFilters[index].first, fontSize = 9.sp) } }
                        }
                    } else {
                        OutlinedTextField(value = shodanQuery, onValueChange = { shodanQuery = it }, label = { Text("Search Query") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White))
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openBrowserSearch(context, "GOOGLE", shodanQuery) }, modifier = Modifier.weight(1f)) { Text("GOOGLE", fontSize = 10.sp) }
                            Button(onClick = { selectedUrl = "https://www.shodan.io/search?query=${Uri.encode(shodanQuery)}"; selectedTab = 3; showShodanDialog = false }, modifier = Modifier.weight(1f)) { Text("SHODAN", fontSize = 10.sp) }
                        }
                    }
                }
            },
            confirmButton = { if (osintTab == 0) Button(onClick = { showShodanDialog = false; if (shodanApiKey.isNotBlank()) { terminalText = "> Initiating Global Shodan Search...\n"; scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val module = py.getModule("CamXploit"); val sys = py.getModule("sys"); sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } }); module.callAttr("shodan_search", shodanApiKey, shodanQuery) } catch (e: Exception) { withContext(Dispatchers.Main) { terminalText += "\n[!] Shodan Error: ${e.message}" } } } } }) { Text("RUN SCAN") } },
            dismissButton = { TextButton(onClick = { showShodanDialog = false }) { Text("CLOSE") } },
            containerColor = Color(0xFF0A0A0A)
        )
    }

    if (viewingFile != null) {
        AlertDialog(
            onDismissRequest = { viewingFile = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            confirmButton = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.End) {
                    TextButton(onClick = { viewingFile?.let { openFile(context, it) } }) { Text("OPEN EXTERNAL", color = Color.Green) }
                    TextButton(onClick = { viewingFile?.let { shareFile(context, it) } }) { Text("SHARE", color = Color.Cyan) }
                    TextButton(onClick = { viewingFile = null }) { Text("CLOSE", color = Color.Red) }
                }
            },
            text = {
                Column(Modifier.fillMaxSize()) {
                    Text(text = viewingFile?.name ?: "", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer { Text(text = viewingFile?.readText() ?: "", color = Color.LightGray, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState())) }
                }
            },
            containerColor = Color(0xFF050505)
        )
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Surface(
                color = Color(0xFF0A0A0A),
                modifier = Modifier.navigationBarsPadding()
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = Color.Green,
                    edgePadding = 0.dp,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color.Green,
                            height = 3.dp
                        )
                    }
                ) {
                    val tabs = listOf(
                        Icons.Default.Terminal to "CONSOLE",
                        Icons.Default.Psychology to "INTEL",
                        Icons.Default.FolderOpen to "ARCHIVE",
                        Icons.Default.Videocam to "STREAM",
                        Icons.Default.Wifi to "LAN",
                        Icons.Default.Bolt to "STORM",
                        Icons.Default.Bookmarks to "SAVED",
                        Icons.Default.Shield to "SENTINEL"
                    )
                    tabs.forEachIndexed { index, (icon, label) ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(label, fontSize = 11.sp) },
                            icon = { Icon(icon, null, modifier = Modifier.size(22.dp)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFF0A0A0A)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(text = "CAMXPLOIT", color = Color.Green, fontSize = 28.sp, fontWeight = FontWeight.Black, style = TextStyle(letterSpacing = 4.sp))
                            Text(text = "ADVANCED AUDIT DASHBOARD", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { showShodanDialog = true }, modifier = Modifier.background(Color.Magenta.copy(0.1f), CircleShape).size(36.dp)) { Icon(Icons.Default.Public, null, tint = Color.Magenta, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { captureScreenshot(context, view) }, modifier = Modifier.background(Color.Cyan.copy(0.1f), CircleShape).size(36.dp)) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { generatePdfReport(context, terminalText); generateHtmlReport(context, terminalText) }, modifier = Modifier.background(Color.Green.copy(0.1f), CircleShape).size(36.dp)) { Icon(Icons.Default.CheckCircle, null, tint = Color.Green, modifier = Modifier.size(18.dp)) }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth().background(Color.Black.copy(0.3f), RoundedCornerShape(4.dp)).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp)).padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(6.dp).background(if (isScanning) Color.Yellow else Color.Green, CircleShape))
                            Spacer(Modifier.width(8.dp))
                            Text(text = if (isScanning) "SYSTEM SCANNING..." else "SYSTEM READY", color = if (isScanning) Color.Yellow else Color.Green, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(text = "SECURE_SESSION: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}", color = Color.DarkGray, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp).weight(1f)) {
                when (selectedTab) {
                    0 -> ConsoleTab(consoleIpInput, { consoleIpInput = it }, terminalText + currentPulse, { terminalText = "> Console Reset.\n" }, isScanning, scrollState, { startReconScan(consoleIpInput) }, { url, _ -> 
                        val source = if (url.startsWith("rtsp://")) StreamSource.Rtsp(url) else StreamSource.Mjpeg(url)
                        StreamViewerActivity.launch(context, source, consoleIpInput) 
                    }, shodanApiKey = shodanApiKey, onDeepShodan = { ip -> terminalText = "> Initiating Targeted Shodan API Scan on $ip...\n"; scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val module = py.getModule("CamXploit"); val sys = py.getModule("sys"); sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } }); module.callAttr("shodan_search", shodanApiKey, "ip:$ip") } catch (e: Exception) { withContext(Dispatchers.Main) { terminalText += "\n[!] Shodan Error: ${e.message}" } } } })
                    1 -> IntelTab(consoleIpInput, terminalText, publicIntel, shodanApiKey, { terminalText += it }, { scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val b64 = py.getModule("CamXploit").callAttr("manual_snapshot_capture", consoleIpInput, 80, extractCredentials(terminalText).first, extractCredentials(terminalText).second).toString(); if (b64 != "None") { val b = Base64.decode(b64, Base64.DEFAULT); val bmp = BitmapFactory.decodeByteArray(b, 0, b.size); withContext(Dispatchers.Main) { capturedBitmap = bmp; val f = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Snap_${System.currentTimeMillis()}.png"); FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } } } catch (e: Exception) {} } }, { url -> val finalUrl = if (url.contains("shodan.io") || url.contains("censys.io") || url.contains("zoomeye.org")) url else buildAuthUrl(url, extractCredentials(terminalText).first, extractCredentials(terminalText).second); selectedUrl = finalUrl; selectedTab = 3 }, { scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); py.getModule("sys").put("stdout", TerminalOutputStream { t -> scope.launch(Dispatchers.Main) { terminalText += t } }); py.getModule("CamXploit").callAttr("discover_onvif", consoleIpInput) } catch (e: Exception) {} } }, { val targetHost = if (consoleIpInput.contains(":")) consoleIpInput.substringBefore(":") else consoleIpInput; val targetPort = if (consoleIpInput.contains(":")) consoleIpInput.substringAfter(":").toIntOrNull() ?: 80 else 80; val vendorMatch = Regex("""Device:\s*([^\n\r]+)""").find(terminalText)?.groupValues?.get(1); scope.launch { terminalText += "🔍 Probing endpoints on $targetHost:$targetPort ...\n"; CameraScanner().scanEndpoints(host = targetHost, port = targetPort, vendor = vendorMatch, onResult = { result -> val brandTag = if (result.brand != null) "[${result.brand}] " else ""; terminalText += "  🎯 Found $brandTag${result.type}: ${result.url} (HTTP ${result.httpCode})\n" }, onDone = { terminalText += "✅ Endpoint scan complete.\n" }) } }, { ip -> terminalText = "> Initiating Targeted Shodan API Scan on $ip...\n"; scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val module = py.getModule("CamXploit"); val sys = py.getModule("sys"); sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } }); module.callAttr("shodan_search", shodanApiKey, "ip:$ip") } catch (e: Exception) { withContext(Dispatchers.Main) { terminalText += "\n[!] Shodan Error: ${e.message}" } } } }, { showExternalSearchDialog = true }, { showDorksDialog = true })
                    2 -> ArchiveTab(context, selectedTab, terminalText, consoleIpInput) { viewingFile = it }
                    3 -> StreamTab(
                        terminalText = terminalText,
                        selectedUrl = selectedUrl,
                        onUrlSelected = { url -> 
                            selectedUrl = url
                            val source = if (url.startsWith("rtsp://")) StreamSource.Rtsp(url) else StreamSource.Mjpeg(url)
                            streamViewModel.startStream(source)
                        },
                        isRecording = isRecording,
                        recordingDuration = recordingDuration,
                        onToggleRecording = { streamViewModel.toggleRecording() }
                    )
                    4 -> LanScanTab(
                        lanScanResults, 
                        lanIsScanning, 
                        lanProgress, 
                        networkSummary, 
                        lanNmapMode, 
                        { lanNmapMode = it }, 
                        lanScanOutput, 
                        isMonitorRunning = isMonitorServiceRunning,
                        onToggleMonitor = toggleMonitorService,
                        onScanStart = { 
                            lanScanOutput = "> Initiating Multi-Layer Discovery...\n"
                            
                            // Get network summary if not already there
                            val discoveryHelper = NetworkDiscoveryHelper(context)
                            networkSummary = discoveryHelper.getNetworkSummary()
                            
                            lanViewModel.startScan()
                        }, 
                        { selectedTab = it }, 
                        { consoleIpInput = it; selectedTab = 0 }, 
                        { url -> 
                            val source = if (url.startsWith("rtsp://")) StreamSource.Rtsp(url) else StreamSource.Mjpeg(url)
                            StreamViewerActivity.launch(context, source, consoleIpInput.ifBlank { "Unknown" })
                        },
                        onHostClick = { selectedHostForDetail = it }
                    )
                    5 -> StormBreakerScreen(stormViewModel)
                    6 -> SavedCamerasTab({ cam -> StreamViewerActivity.launch(context, cam.toStreamSource(), cam.ip) }, { consoleIpInput = it; selectedTab = 0 })
                    7 -> SentinelTab(savedCameras = cameras)
                }
                capturedBitmap?.let { bmp ->
                    Spacer(Modifier.height(20.dp)); Text(text = "LAST SNAPSHOT", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth().height(200.dp).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).background(Color.Black), Alignment.Center) { Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize()); IconButton(onClick = { capturedBitmap = null }, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White) } }
                }
            }
        }
        if (showExternalSearchDialog) { ExternalSearchDialog(ip = consoleIpInput, onDismiss = { showExternalSearchDialog = false }, onSearch = { url: String -> selectedUrl = url; selectedTab = 3 }, onOpenBrowser = { engine: String, query: String -> openBrowserSearch(context, engine, query) }) }
        if (showDorksDialog) { GoogleDorksDialog(ip = consoleIpInput, onDismiss = { showDorksDialog = false }, onOpenBrowser = { query: String -> openBrowserSearch(context, "GOOGLE", query) }) }
        
        selectedHostForDetail?.let { host ->
            CameraDetailBottomSheet(
                host = host,
                onDismiss = { selectedHostForDetail = null },
                onViewStream = { url ->
                    selectedHostForDetail = null
                    selectedUrl = url
                    selectedTab = 3
                    val source = if (url.startsWith("rtsp://")) StreamSource.Rtsp(url) else StreamSource.Mjpeg(url)
                    StreamViewerActivity.launch(context, source, host.ip)
                },
                onTestCredentials = { ip ->
                    selectedHostForDetail = null
                    consoleIpInput = ip
                    selectedTab = 0
                    startReconScan(ip)
                },
                onOpenWebUi = { url ->
                    selectedHostForDetail = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    } catch (e: Exception) {
                        Toast.makeText(context, "Cannot open browser", Toast.LENGTH_SHORT).show()
                    }
                },
                onProbe = { ip, brand ->
                    lanViewModel.probeStream(ip, brand)
                }
            )
        }
    }
}

@Composable
fun ConsoleTab(consoleIpInput: String, onIpChange: (String) -> Unit, terminalText: String, onTerminalClear: () -> Unit, isScanning: Boolean, scrollState: ScrollState, onStartScan: () -> Unit, onStreamSelect: (String, String) -> Unit, shodanApiKey: String = "", onDeepShodan: (String) -> Unit = {}) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp)).background(Color(0xFF080808)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "root@camxploit:~#", color = Color.Green.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                TextField(value = consoleIpInput, onValueChange = onIpChange, textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.Green), placeholder = { Text("target_ip", color = Color.DarkGray, fontSize = 14.sp, fontFamily = FontFamily.Monospace) })
                IconButton(onClick = onStartScan, modifier = Modifier.size(32.dp)) { Icon(imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.Search, contentDescription = null, tint = if (isScanning) Color.Yellow else Color.Green, modifier = Modifier.size(18.dp)) }
                if (shodanApiKey.isNotBlank()) { IconButton(onClick = { if (consoleIpInput.isBlank()) Toast.makeText(context, "⚠️ Set target host", Toast.LENGTH_SHORT).show() else onDeepShodan(consoleIpInput) }, modifier = Modifier.size(32.dp)) { Icon(Icons.Default.Analytics, null, tint = Color.Magenta, modifier = Modifier.size(18.dp)) } }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF112211), RoundedCornerShape(4.dp)), color = Color(0xFF020202)) { Box { SelectionContainer { Text(text = terminalText, color = Color(0xFF00FF41), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState)) } } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val actionModifier = Modifier.weight(1f).height(36.dp)
            val actionShape = RoundedCornerShape(4.dp)
            Button(onClick = onTerminalClear, modifier = actionModifier, colors = ButtonDefaults.buttonColors(Color(0xFF110000)), border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)), shape = actionShape, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("CLEAR", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = { }, modifier = actionModifier, colors = ButtonDefaults.buttonColors(Color(0xFF001122)), border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f)), shape = actionShape, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.ContentCopy, null, tint = Color.Cyan, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("COPY", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = { }, modifier = actionModifier, colors = ButtonDefaults.buttonColors(Color(0xFF002200)), border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.3f)), shape = actionShape, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Share, null, tint = Color.Green, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("EXPORT", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
        LaunchedEffect(scrollState.maxValue) { if (scrollState.value > scrollState.maxValue - 1000 || terminalText.length < 1000) scrollState.animateScrollTo(scrollState.maxValue) }
        val detectedLinks = remember(terminalText) { if (terminalText.contains("===LINKS_START===")) terminalText.substringAfter("===LINKS_START===").substringBefore("===LINKS_END===").lines().filter { it.contains("|") } else emptyList() }
        if (detectedLinks.isNotEmpty()) {
            Text(text = "🎯 Detected Links", color = Color.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.fillMaxWidth().height(350.dp).verticalScroll(rememberScrollState())) { 
                detectedLinks.forEach { line -> 
                    val p = line.split("|")
                    if (p.size >= 2) Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)), colors = CardDefaults.cardColors(Color.Transparent)) { 
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { 
                            Column(Modifier.weight(1f)) { Text(text = p[0].trim(), color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(text = p[1].trim().take(50), color = Color.Gray, fontSize = 11.sp) }
                            Button(onClick = { onStreamSelect(p[1].trim(), p[0].trim()) }, colors = ButtonDefaults.buttonColors(Color.Green)) { Text(text = "LIVE", color = Color.Black) } 
                        } 
                    } 
                } 
            }
        }
    }
}

@Composable
fun IntelTab(consoleIpInput: String, terminalText: String, publicIntel: String, shodanApiKey: String, onTerminalUpdate: (String) -> Unit, onManualSnapshot: () -> Unit, onStreamSelect: (String) -> Unit, onDiscoverOnvif: () -> Unit, onProbeEndpoints: () -> Unit, onDeepShodan: (String) -> Unit, onExternalSearchClick: () -> Unit, onShowDorks: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = "🛡️ INTELLIGENCE GATHERING", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        if (publicIntel.isNotBlank()) {
            val json = try { JSONObject(publicIntel) } catch (e: Exception) { null }
            if (json != null) {
                Card(Modifier.fillMaxWidth().padding(bottom = 16.dp), colors = CardDefaults.cardColors(Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color.Magenta.copy(0.4f))) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Public, null, tint = Color.Magenta, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text("PUBLIC HOST INTEL", color = Color.Magenta, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        Spacer(Modifier.height(8.dp))
                        json.optJSONArray("ports")?.let { p -> Text("PORTS: " + List(p.length()) { p.getInt(it).toString() }.joinToString(", "), color = Color.Green, fontSize = 11.sp) }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            Button(onClick = { if (consoleIpInput.isEmpty()) return@Button; onDiscoverOnvif() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("ONVIF", fontSize = 10.sp) }
            Button(onClick = { if (consoleIpInput.isEmpty()) return@Button; onProbeEndpoints() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("PROBE", fontSize = 10.sp) }
            Button(onClick = { if (consoleIpInput.isEmpty()) return@Button; onManualSnapshot() }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(8.dp)) { Text("SNAP", fontSize = 10.sp) }
        }
        Spacer(Modifier.height(20.dp))
        IntelSection("OSINT", listOf("Global OSINT Search", "View Shodan Report", "Censys Host Discovery"), Color.Magenta, Icons.Default.Public) {
            when(it) {
                "Global OSINT Search" -> onExternalSearchClick()
                "View Shodan Report" -> onStreamSelect("https://www.shodan.io/host/$consoleIpInput")
                "Censys Host Discovery" -> { /* Open browser */ }
            }
        }
        Spacer(Modifier.height(16.dp))
        IntelSection("SYSTEM AUDIT", listOf("Firmware Check", "Generate Google Dorks"), Color.Yellow, Icons.Default.Dns) { if (it.contains("Dorks")) onShowDorks() else onTerminalUpdate("> Audit: $it...\n") }
    }
}

@Composable
fun IntelSection(title: String, items: List<String>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onItemClick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color(0xFF1A1A1A))) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(text = title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp)); items.forEach { item -> Text(text = item, color = Color.Gray, fontSize = 11.sp, modifier = Modifier.fillMaxWidth().clickable { onItemClick(item) }.padding(vertical = 4.dp)) }
        }
    }
}

@Composable
fun StreamTab(
    terminalText: String, 
    selectedUrl: String, 
    onUrlSelected: (String) -> Unit, 
    isRecording: Boolean, 
    recordingDuration: Long, 
    onToggleRecording: () -> Unit
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var currentExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }; var currentWebView by remember { mutableStateOf<WebView?>(null) }; var currentTextureView by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(selectedUrl) { onDispose { currentExoPlayer?.release(); currentExoPlayer = null } }
    val streamUrls = remember(terminalText) { val start = terminalText.indexOf("===LINKS_START==="); val end = terminalText.indexOf("===LINKS_END==="); if (start != -1 && end != -1) terminalText.substring(start, end).lines().filter { it.contains("|") }.mapNotNull { val p = it.split("|"); if (p.size >= 2) p[1].trim() to p[0].trim() else null }.distinctBy { it.first } else emptyList() }
    LaunchedEffect(streamUrls) { if (selectedUrl.isEmpty() && streamUrls.isNotEmpty()) onUrlSelected(streamUrls.first().first) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
            Text(text = "STREAM VIEWER", color = Color.Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (isRecording) { Row(verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(8.dp).background(Color.Red, CircleShape)); Spacer(Modifier.width(4.dp)); Text(text = "REC ${String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60)}", color = Color.Red, fontSize = 12.sp) } }
        }
        if (selectedUrl.isNotEmpty()) {
            val auth = buildAuthUrl(selectedUrl, extractCredentials(terminalText).first, extractCredentials(terminalText).second); Column(Modifier.weight(1f)) {
                if (selectedUrl.startsWith("rtsp")) { AndroidView(factory = { ctx -> ExoPlayer.Builder(ctx).build().apply { setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).setDebugLoggingEnabled(true).createMediaSource(MediaItem.fromUri(auth))); prepare(); playWhenReady = true; currentExoPlayer = this }.let { PlayerView(ctx).apply { player = it; currentTextureView = TextureView(ctx); try { this.javaClass.getMethod("setVideoSurfaceView", android.view.View::class.java).invoke(this, currentTextureView) } catch (e: Exception) {} } } }, Modifier.fillMaxWidth().weight(1f)) }
                else { AndroidView(factory = { ctx -> WebView(ctx).apply { currentWebView = this; settings.javaScriptEnabled = true; loadUrl(auth) } }, Modifier.fillMaxSize().weight(1f)) }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(8.dp), Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = { scope.launch { val b = if (selectedUrl.startsWith("rtsp")) currentTextureView?.getBitmap() else currentWebView?.let { val bmp = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888); it.draw(Canvas(bmp)); bmp }; b?.let { if (saveBitmapToGallery(context, it)) Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } } }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan) }
                    IconButton(onClick = { onToggleRecording() }) { Icon(if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null, tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(url: String, user: String, pass: String) {
    val auth = buildAuthUrl(url, user, pass); if (url.startsWith("rtsp")) AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; player = ExoPlayer.Builder(ctx).build().apply { setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(auth))); prepare(); play() } } }, Modifier.fillMaxSize())
    else AndroidView(factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; loadUrl(auth) } }, Modifier.fillMaxSize())
}

@Composable
fun ArchiveTab(context: Context, selectedTab: Int, terminalText: String, targetIp: String, onFileClick: (File) -> Unit) {
    var refresh by remember { mutableIntStateOf(0) }; LaunchedEffect(selectedTab) { if (selectedTab == 2) refresh++ }
    val files = remember(refresh) { val all = mutableListOf<File>(); context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.listFiles()?.filter { it.isFile }?.let { all.addAll(it) }; all.sortedByDescending { it.lastModified() } }
    Column {
        if (terminalText.length > 50) Button(onClick = { generateDetailedPdfReport(context, terminalText, targetIp) }, Modifier.fillMaxWidth()) { Text("GENERATE FULL REPORT") }
        LazyColumn(Modifier.fillMaxSize()) { items(files) { f -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onFileClick(f) }) { Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null); Spacer(Modifier.width(10.dp)); Text(text = f.name, modifier = Modifier.weight(1f)); IconButton(onClick = { openFile(context, f) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null) } } } } }
    }
}

@Composable
fun LanScanTab(scanResults: List<LanHost>, isScanning: Boolean, progress: Float, networkSummary: NetworkDiscoveryHelper.NetworkSummary?, nmapMode: Boolean, onNmapModeChange: (Boolean) -> Unit, nmapOutput: String, isMonitorRunning: Boolean, onToggleMonitor: () -> Unit, onScanStart: () -> Unit, onTabSwitch: (Int) -> Unit, onIpSelected: (String) -> Unit, onViewStream: (String) -> Unit, onHostClick: (LanHost) -> Unit) {
    LaunchedEffect(Unit) {
        if (scanResults.isEmpty() && !isScanning) {
            onScanStart()
        }
    }
    Column(Modifier.fillMaxSize()) {
        Text(text = "NETWORK AUDIT", color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Black)
        networkSummary?.let { Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color(0xFF1A1A1A))) { Column(Modifier.padding(16.dp)) { Text(text = it.ssid, color = Color.White, fontWeight = FontWeight.Bold); Text("Local IP: ${it.localIp}", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 12.sp) } } }
        
        Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)), border = BorderStroke(1.dp, if (isMonitorRunning) Color.Green.copy(0.3f) else Color(0xFF1A1A1A))) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Background Monitor", color = Color.White, fontWeight = FontWeight.Bold)
                    Text(if (isMonitorRunning) "Service Active" else "Service Idle", color = if (isMonitorRunning) Color.Green else Color.Gray, fontSize = 11.sp)
                }
                Switch(checked = isMonitorRunning, onCheckedChange = { onToggleMonitor() }, colors = SwitchDefaults.colors(checkedThumbColor = Color.Green, checkedTrackColor = Color.Green.copy(0.3f)))
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
            Text("DEVICES: ${scanResults.size}", color = Color.Green, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Button(onClick = onScanStart, enabled = !isScanning, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)), shape = RoundedCornerShape(4.dp)) { 
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color(0xFF00FF88),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Search, null, Modifier.size(16.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (isScanning) "SCANNING..." else "SCAN") 
            }
        }
        if (isScanning) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp), color = Color.Green, trackColor = Color.Transparent)
        LazyColumn(Modifier.weight(1f)) { 
            items(scanResults.sortedBy { it.ip }, key = { it.ip }) { host -> 
                LanHostCard(host = host, onClick = { onHostClick(host) }) 
            } 
        }
    }
}

@Composable
fun LanHostCard(host: LanHost, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        border = BorderStroke(1.dp, if (host.isYourDevice) Color.Green else Color(0xFF111111))
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (host.isCamera) Icons.Default.Videocam else Icons.Default.Devices,
                null,
                tint = if (host.isCamera) Color(0xFF00FF88) else Color.Gray,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val primaryName = when {
                        host.isYourDevice -> "This Device"
                        host.vendor != null -> host.vendor
                        host.hostname != null && host.hostname != host.ip -> host.hostname
                        else -> "Network Device"
                    }
                    Text(text = primaryName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    if (host.isYourDevice) {
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            color = Color(0xFF00FF88),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                "YOU",
                                color = Color.Black,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
                
                val subtitle = when {
                    host.isYourDevice -> android.os.Build.MODEL
                    host.vendor != null && host.hostname != null && host.hostname != host.ip -> host.hostname
                    host.mac != null && host.mac != "Unknown" -> "MAC: ${host.mac}"
                    else -> host.ip
                }
                
                Text(
                    text = subtitle,
                    color = if (host.isCamera) Color(0xFF00FF88) else Color.Gray,
                    fontSize = 13.sp
                )
                
                if (host.openPorts.isNotEmpty()) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.padding(top = 4.dp)
                    ) {
                        host.openPorts.take(4).forEach { port ->
                            val label = when (port) {
                                80 -> "HTTP"
                                443 -> "HTTPS"
                                554 -> "RTSP"
                                8080 -> "WEB"
                                else -> "$port"
                            }
                            AssistChip(
                                onClick = {},
                                label = { Text(label, fontSize = 9.sp) },
                                colors = AssistChipDefaults.assistChipColors(
                                    labelColor = Color(0xFF00FF88),
                                    containerColor = Color(0xFF0D3B1E)
                                ),
                                border = null,
                                modifier = Modifier.height(20.dp)
                            )
                        }
                    }
                }
            }
            if (host.streamUrl != null) {
                Button(
                    onClick = onClick,
                    modifier = Modifier.height(28.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00FF88), contentColor = Color.Black)
                ) {
                    Text("LIVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            } else if (host.isCamera) {
                TextButton(onClick = onClick, modifier = Modifier.height(28.dp)) {
                    Text("UNLOCK", color = Color(0xFFFFAA00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

fun openFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; context.startActivity(intent) } catch (e: Exception) {} }
fun openBrowserSearch(context: Context, engine: String, query: String) { val url = when (engine) { "GOOGLE" -> "https://www.google.com/search?q=${Uri.encode(query)}"; "SHODAN" -> "https://www.shodan.io/search?query=${Uri.encode(query)}"; else -> "" }; try { if (url.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {} }
fun shareFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); val intent = Intent(Intent.ACTION_SEND).apply { type = "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, "Share Report")) } catch (e: Exception) {} }
fun saveContentToFile(context: Context, content: String, name: String, ext: String) { try { val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS); val file = File(dir, "${name}_${System.currentTimeMillis()}.$ext"); file.writeText(content) } catch (e: Exception) {} }
fun captureScreenshot(context: Context, view: android.view.View) { val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap)); if (saveBitmapToGallery(context, bitmap)) Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() }
fun generateHtmlReport(context: Context, content: String) { saveContentToFile(context, "<html><body><pre>$content</pre></body></html>", "Report", "html") }
fun generatePdfReport(context: Context, content: String) { val pdf = PdfDocument(); val page = pdf.startPage(PageInfo.Builder(595, 842, 1).create()); page.canvas.drawText(content.take(1000), 40f, 50f, Paint()); pdf.finishPage(page); try { val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Report_${System.currentTimeMillis()}.pdf"); pdf.writeTo(FileOutputStream(f)) } catch (e: Exception) {} finally { pdf.close() } }
fun generateDetailedPdfReport(context: Context, terminalText: String, targetIp: String) { generatePdfReport(context, "Target: $targetIp\n\n$terminalText") }
fun saveJsonReport(context: Context, content: String, ip: String) { try { val json = JSONObject().apply { put("target", ip); put("log", content) }; saveContentToFile(context, json.toString(), "Report_$ip", "json") } catch (e: Exception) {} }


fun extractCredentials(text: String): Pair<String, String> { val u = Regex("""User:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: "admin"; val p = Regex("""Pass:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: "admin"; return u to p }
fun buildAuthUrl(u: String, user: String, pass: String): String { if (user.isBlank() || pass.isBlank() || u.contains("@")) return u; return try { if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@") else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@") else u } catch (e: Exception) { u } }

@Composable
fun SavedCamerasTab(onPlay: (SavedCamera) -> Unit, onIpSelected: (String) -> Unit) {
    val context = LocalContext.current; val cameras by CameraDatabase.getDatabase(context).cameraDao().getAllCameras().collectAsState(initial = emptyList())
    LazyColumn(Modifier.fillMaxSize()) { items(cameras) { cam -> Card(Modifier.fillMaxWidth().padding(6.dp).clickable { onIpSelected(cam.ip) }) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(cam.nickname, fontWeight = FontWeight.Bold); Text(cam.ip, fontSize = 12.sp) }; IconButton(onClick = { onPlay(cam) }) { Icon(Icons.Default.PlayArrow, null) } } } } }
}

@Composable
fun SentinelTab(savedCameras: List<SavedCamera>) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); val dao = CameraDatabase.getDatabase(context).sentinelDao(); val detections by dao.getAll().collectAsState(initial = emptyList()); var isRunning by remember { mutableStateOf(false) }; var selectedCamera by remember { mutableStateOf<SavedCamera?>(null) }; val processor = remember { SentinelProcessor(context) }
    Column(Modifier.fillMaxSize()) {
        Text("🛡️ SENTINEL", color = Color.Cyan, fontSize = 20.sp, fontWeight = FontWeight.Black)
        if (savedCameras.isNotEmpty()) { LazyColumn(Modifier.heightIn(max = 140.dp)) { items(savedCameras) { cam -> Card(Modifier.fillMaxWidth().padding(3.dp).clickable { selectedCamera = cam }) { Text(cam.nickname, modifier = Modifier.padding(10.dp)) } } } }
        Button(onClick = { isRunning = !isRunning }, enabled = selectedCamera != null) { Text(if (isRunning) "STOP" else "START") }
        LazyColumn(Modifier.fillMaxSize()) { items(detections) { d -> Text("⚠️ ${d.label} at ${d.cameraIp}", color = Color.Red, modifier = Modifier.padding(8.dp)) } }
    }
}

@Composable
fun GoogleDorksDialog(ip: String, onDismiss: () -> Unit, onOpenBrowser: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("GOOGLE DORKS") }, text = { Column { listOf("view/index.shtml", "Live View / - AXIS").forEach { d -> Text(d, modifier = Modifier.fillMaxWidth().clickable { onOpenBrowser("$d $ip") }.padding(8.dp)) } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } })
}

@Composable
fun ExternalSearchDialog(ip: String, onDismiss: () -> Unit, onSearch: (String) -> Unit, onOpenBrowser: (String, String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("OSINT") }, text = { Column { listOf("SHODAN", "CENSYS", "GOOGLE").forEach { engine -> Button(onClick = { if (engine == "GOOGLE") onOpenBrowser("GOOGLE", ip) else onSearch("https://www.shodan.io/host/$ip"); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(engine) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } })
}

@Composable
fun CameraDetailBottomSheet(
    host: LanHost,
    onDismiss: () -> Unit,
    onViewStream: (String) -> Unit,
    onTestCredentials: (String) -> Unit,
    onOpenWebUi: (String) -> Unit,
    onProbe: (String, String?) -> Unit
) {
    val context = LocalContext.current
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isFetchingPreview by remember { mutableStateOf(false) }

    LaunchedEffect(host.ip) {
        isFetchingPreview = true
        withContext(Dispatchers.IO) {
            try {
                val py = Python.getInstance()
                val module = py.getModule("CamXploit")
                val b64 = module.callAttr("manual_snapshot_capture", host.ip, 80).toString()
                if (b64 != "None") {
                    val bytes = Base64.decode(b64, Base64.DEFAULT)
                    previewBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                isFetchingPreview = false
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        scrimColor = Color.Black.copy(alpha = 0.5f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black)
                    .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isFetchingPreview) {
                    CircularProgressIndicator(color = Color.Green)
                } else if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap!!.asImageBitmap(),
                        contentDescription = "Preview",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        Icons.Default.VideocamOff,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "No Preview Available",
                        color = Color.Gray,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            val primaryName = when {
                host.isYourDevice -> "This Device"
                host.vendor != null -> host.vendor
                host.hostname != null && host.hostname != host.ip -> host.hostname
                else -> "Network Device"
            }
            Text(
                text = primaryName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            val subtitle = when {
                host.vendor != null && host.hostname != null && host.hostname != host.ip -> host.hostname
                host.mac != null && host.mac != "Unknown" -> "MAC: ${host.mac}"
                else -> host.ip
            }
            Text(
                text = subtitle,
                color = Color.Gray,
                fontSize = 14.sp
            )
            if (host.mac != null && host.mac != "Unknown") {
                Text(
                    text = "MAC: ${host.mac}",
                    color = Color.DarkGray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(Modifier.height(16.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (host.openPorts.contains(80) || host.openPorts.contains(443)) {
                    ProtocolBadge("HTTP", Color.Blue)
                }
                if (host.openPorts.contains(554) || host.openPorts.contains(8554)) {
                    ProtocolBadge("RTSP", Color.Magenta)
                }
                if (host.isOnvif) {
                    ProtocolBadge("ONVIF", Color.Cyan)
                }
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DetailActionItem(
                    icon = Icons.Default.PlayArrow,
                    label = if (host.streamUrls.size > 1) "View Streams (${host.streamUrls.size})" else "View Stream",
                    color = Color.Green,
                    modifier = Modifier.weight(1f),
                    enabled = host.streamUrls.isNotEmpty() || host.streamUrl != null || host.isCamera
                ) {
                    if (host.streamUrl != null) {
                        onViewStream(host.streamUrl!!)
                    } else if (host.streamUrls.isNotEmpty()) {
                        onViewStream(host.streamUrls.first())
                    } else if (host.isCamera) {
                        onProbe(host.ip, host.brand)
                        Toast.makeText(context, "Probing RTSP paths...", Toast.LENGTH_SHORT).show()
                    } else {
                        onViewStream("http://${host.ip}")
                    }
                }
                DetailActionItem(
                    icon = Icons.Default.Search,
                    label = "Test Creds",
                    color = Color.Yellow,
                    modifier = Modifier.weight(1f)
                ) {
                    onTestCredentials(host.ip)
                }
            }
            
            if (host.streamUrls.size > 1) {
                Spacer(Modifier.height(16.dp))
                Text("Available Streams:", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.align(Alignment.Start))
                Spacer(Modifier.height(8.dp))
                host.streamUrls.forEachIndexed { index, url ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onViewStream(url) },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                        border = BorderStroke(1.dp, Color(0xFF222222))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayCircle, null, tint = Color.Green, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(12.dp))
                            Text(
                                text = "Stream #${index + 1}: ${url.substringAfterLast("/")}",
                                color = Color.White,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            DetailActionItem(
                icon = Icons.Default.OpenInBrowser,
                label = "Open Web UI",
                color = Color.Cyan,
                modifier = Modifier.fillMaxWidth()
            ) {
                onOpenWebUi("http://${host.ip}")
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun ProtocolBadge(name: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.1f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = name,
            color = color,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
fun DetailActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(48.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) color.copy(alpha = 0.1f) else Color.DarkGray.copy(alpha = 0.1f),
            contentColor = if (enabled) color else Color.Gray
        ),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, if (enabled) color.copy(alpha = 0.3f) else Color.DarkGray.copy(alpha = 0.3f)),
        enabled = enabled
    ) {
        Icon(icon, null, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
