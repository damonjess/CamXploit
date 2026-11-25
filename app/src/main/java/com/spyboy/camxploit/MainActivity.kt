@file:OptIn(UnstableApi::class, androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spyboy.camxploit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.net.Uri
import android.os.*
import android.provider.MediaStore
import android.media.projection.MediaProjectionManager
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfDocument.PageInfo
import android.util.Base64
import android.view.TextureView
import android.view.View
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
import com.spyboy.camxploit.pentest.TlsAuditor
import com.spyboy.camxploit.osint.OsintViewModel
import com.spyboy.camxploit.ui.GlobalOsintSheet
import com.spyboy.camxploit.ui.OsintScreen
import com.spyboy.camxploit.ui.SentinelScreen
import com.spyboy.camxploit.ui.SentinelViewModel
import com.spyboy.camxploit.ui.SavedFeedLibrary
import com.spyboy.camxploit.ui.ToolsScreen
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
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CamGuardianApp()
            }
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
    
    val rawDevices by DiscoveryCoordinator.getInstance(context).devices.collectAsState()
    val sentinelViewModel: SentinelViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

    LaunchedEffect(rawDevices) {
        sentinelViewModel.setDevices(rawDevices)
    }

    var networkSummary by remember { mutableStateOf<NetworkDiscoveryHelper.NetworkSummary?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var publicIntel by remember { mutableStateOf("") }
    var selectedUrl by remember { mutableStateOf("") }
    
    val streamViewModel: StreamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    val isRecording by streamViewModel.isRecording.collectAsState()
    val recordingDuration by streamViewModel.recordingDuration.collectAsState()

    val stormViewModel: StormViewModel = androidx.lifecycle.viewmodel.compose.viewModel(
        factory = StormViewModel.Factory(context)
    )
    
    val osintViewModel: OsintViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
    
    var lanNmapMode by remember { mutableStateOf(false) }
    var lanScanOutput by remember { mutableStateOf("") }
    var showExternalSearchDialog by remember { mutableStateOf(false) }
    var showDorksDialog by remember { mutableStateOf(false) }
    var selectedHostForDetail by remember { mutableStateOf<LanHost?>(null) }
    
    var showOsint by remember { mutableStateOf(false) }
    var importedOsintIp by remember { mutableStateOf<String?>(null) }
    
    var isMonitorServiceRunning by remember { mutableStateOf(CameraMonitorService.isRunning) }

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
                        Icons.Default.Build to "TOOLS",
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
            Surface(modifier = Modifier.fillMaxWidth().statusBarsPadding(), color = Color(0xFF0A0A0A)) {
                Column(Modifier.padding(16.dp)) {
                    Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                        Column {
                            Text(text = "CAMXPLOIT", color = Color.Green, fontSize = 28.sp, fontWeight = FontWeight.Black, style = TextStyle(letterSpacing = 4.sp))
                            Text(text = "ADVANCED AUDIT DASHBOARD", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(onClick = { 
                                importedOsintIp = null
                                showOsint = true 
                            }, modifier = Modifier.background(Color.Magenta.copy(0.1f), CircleShape).size(36.dp)) { Icon(Icons.Default.Public, null, tint = Color.Magenta, modifier = Modifier.size(18.dp)) }
                            IconButton(onClick = { captureScreenshot(context, view) { capturedBitmap = it } }, modifier = Modifier.background(Color.Cyan.copy(0.1f), CircleShape).size(36.dp)) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan, modifier = Modifier.size(18.dp)) }
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
                    0 -> ConsoleTab(
                        consoleIpInput, 
                        { consoleIpInput = it }, 
                        terminalText + currentPulse, 
                        { terminalText = "> Console Reset.\n" }, 
                        isScanning, 
                        scrollState, 
                        { startReconScan(consoleIpInput) }, 
                        { url, title -> 
                            val protocol = protocolForUrl(url)
                            val source = StreamSource(url = url, title = title, protocol = protocol)
                            StreamViewerActivity.launch(context, source, consoleIpInput.ifBlank { "Unknown" }) 
                        }
                    )
                    1 -> OsintScreen(viewModel = osintViewModel)
                    2 -> ArchiveTab(context, selectedTab, terminalText, consoleIpInput) { viewingFile = it }
                    3 -> StreamTab(
                        terminalText = terminalText,
                        selectedUrl = selectedUrl,
                        onUrlSelected = { url -> 
                            selectedUrl = url
                            val protocol = protocolForUrl(url)
                            val source = StreamSource(url = url, protocol = protocol)
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
                            val protocol = protocolForUrl(url)
                            val source = StreamSource(url = url, protocol = protocol)
                            StreamViewerActivity.launch(context, source, consoleIpInput.ifBlank { "Unknown" })
                        },
                        onHostClick = { selectedHostForDetail = it }
                    )
                    5 -> StormBreakerScreen(stormViewModel, consoleIpInput)
                    6 -> SavedFeedLibrary(
                        onPlay = { cam -> StreamViewerActivity.launch(context, cam.toStreamSource(), cam.ip) },
                        onOpenConsole = { cam -> consoleIpInput = cam.ip; selectedTab = 0 }
                    )
                    7 -> ToolsScreen()
                    8 -> SentinelScreen(viewModel = sentinelViewModel)
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
                    val protocol = protocolForUrl(url)
                    val source = StreamSource(url = url, protocol = protocol)
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
        if (showOsint) {
            GlobalOsintSheet(
                onDismiss = { showOsint = false },
                importedIp = importedOsintIp,
                viewModel = osintViewModel
            )
        }
    }
}

@Composable
fun ConsoleTab(
    consoleIpInput: String, 
    onIpChange: (String) -> Unit, 
    terminalText: String, 
    onTerminalClear: () -> Unit, 
    isScanning: Boolean, 
    scrollState: ScrollState, 
    onStartScan: () -> Unit, 
    onStreamSelect: (String, String) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current

    val detectedLinks = remember(terminalText) { CameraLinkVerifier.parse(terminalText) }
    var linkResults by remember(detectedLinks) { mutableStateOf<List<CameraLinkVerifier.Result>?>(null) }
    var verifyingLinks by remember(detectedLinks) { mutableStateOf(false) }
    var showLinksSheet by remember { mutableStateOf(false) }
    var showRejectedLinks by remember { mutableStateOf(false) }

    LaunchedEffect(detectedLinks, isScanning) {
        if (detectedLinks.isEmpty() || isScanning) {
            linkResults = null
            verifyingLinks = false
            showLinksSheet = false
            return@LaunchedEffect
        }
        verifyingLinks = true
        linkResults = CameraLinkVerifier.verifyAll(detectedLinks)
        verifyingLinks = false
    }

    val verifiedLinks = linkResults.orEmpty().filter {
        it.verdict == CameraLinkVerifier.Verdict.LIVE || it.verdict == CameraLinkVerifier.Verdict.LOGIN
    }
    val rejectedLinks = linkResults.orEmpty().filter {
        it.verdict == CameraLinkVerifier.Verdict.REJECTED || it.verdict == CameraLinkVerifier.Verdict.UNREACHABLE
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp)).background(Color(0xFF080808)).padding(horizontal = 12.dp, vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "root@camxploit:~#", color = Color.Green.copy(alpha = 0.7f), fontSize = 12.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
                TextField(value = consoleIpInput, onValueChange = onIpChange, textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f), singleLine = true, colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = Color.Green), placeholder = { Text("target_ip", color = Color.DarkGray, fontSize = 14.sp, fontFamily = FontFamily.Monospace) })
                IconButton(onClick = onStartScan, modifier = Modifier.size(32.dp)) { Icon(imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.Search, contentDescription = null, tint = if (isScanning) Color.Yellow else Color.Green, modifier = Modifier.size(18.dp)) }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF112211), RoundedCornerShape(4.dp)), color = Color(0xFF020202)) { Box { SelectionContainer { Text(text = terminalText, color = Color(0xFF00FF41), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxSize().padding(12.dp).verticalScroll(scrollState)) } } }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val actionModifier = Modifier.weight(1f).height(36.dp)
            val actionShape = RoundedCornerShape(4.dp)
            Button(onClick = onTerminalClear, modifier = actionModifier, colors = ButtonDefaults.buttonColors(Color(0xFF110000)), border = BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)), shape = actionShape, contentPadding = PaddingValues(0.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(6.dp)); Text("CLEAR", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Button(
                onClick = {
                    if (terminalText.isNotBlank()) {
                        clipboardManager.setText(AnnotatedString(terminalText))
                        Toast.makeText(context, "Terminal log copied to clipboard", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(context, "Terminal is empty", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = actionModifier,
                colors = ButtonDefaults.buttonColors(Color(0xFF001122)),
                border = BorderStroke(1.dp, Color.Cyan.copy(alpha = 0.3f)),
                shape = actionShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.ContentCopy, null, tint = Color.Cyan, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("COPY", color = Color.Cyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = {
                    if (terminalText.isNotBlank()) {
                        exportTerminalText(context, terminalText)
                    } else {
                        Toast.makeText(context, "Terminal is empty", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = actionModifier,
                colors = ButtonDefaults.buttonColors(Color(0xFF002200)),
                border = BorderStroke(1.dp, Color.Green.copy(alpha = 0.3f)),
                shape = actionShape,
                contentPadding = PaddingValues(0.dp)
            ) {
                Icon(Icons.Default.Share, null, tint = Color.Green, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text("EXPORT", color = Color.Green, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
        LaunchedEffect(scrollState.maxValue) { if (scrollState.value > scrollState.maxValue - 1000 || terminalText.length < 1000) scrollState.animateScrollTo(scrollState.maxValue) }

        if (detectedLinks.isNotEmpty()) {
            val verifiedCount = verifiedLinks.size
            val rejectedCount = rejectedLinks.size
            val accent = if (verifiedCount > 0) Color.Green else Color(0xFFFFAA00)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { showLinksSheet = true },
                color = if (verifiedCount > 0) Color(0xFF071607) else Color(0xFF1A1005),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.45f)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (verifiedCount > 0) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "SCAN FINDINGS",
                            color = Color.Cyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = when {
                                verifyingLinks -> "Checking ${detectedLinks.size} detected links..."
                                linkResults == null -> "Tap to inspect ${detectedLinks.size} detected links"
                                verifiedCount == 0 -> "No verified feeds · $rejectedCount rejected"
                                else -> "$verifiedCount verified · $rejectedCount rejected"
                            },
                            color = Color.Gray,
                            fontSize = 11.sp
                        )
                    }
                    if (verifyingLinks) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.Cyan,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("VIEW", color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showLinksSheet) {
        ScanFindingsSheet(
            detectedCount = detectedLinks.size,
            verifying = verifyingLinks,
            verified = verifiedLinks,
            rejected = rejectedLinks,
            showRejected = showRejectedLinks,
            onToggleRejected = { showRejectedLinks = !showRejectedLinks },
            onDismiss = { showLinksSheet = false },
            onOpenLink = { result ->
                showLinksSheet = false
                onStreamSelect(result.link.url, result.link.type)
            }
        )
    }
}

@Composable
private fun ScanFindingsSheet(
    detectedCount: Int,
    verifying: Boolean,
    verified: List<CameraLinkVerifier.Result>,
    rejected: List<CameraLinkVerifier.Result>,
    showRejected: Boolean,
    onToggleRejected: () -> Unit,
    onDismiss: () -> Unit,
    onOpenLink: (CameraLinkVerifier.Result) -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF0A0A0A),
        scrimColor = Color.Black.copy(alpha = 0.65f),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Text(
                    "DETECTED LINKS",
                    color = Color.Cyan,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )
                Text(
                    "Live HTTP check: HTML/login pages and redirects are excluded.",
                    color = Color.Gray,
                    fontSize = 10.sp
                )
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FindingStat("DETECTED", detectedCount, Color.Gray, Modifier.weight(1f))
                    FindingStat("VERIFIED", verified.size, Color.Green, Modifier.weight(1f))
                    FindingStat("REJECTED", rejected.size, Color(0xFFFF5555), Modifier.weight(1f))
                }
            }
            if (verifying) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.Cyan, strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text("Verifying endpoints...", color = Color.Gray, fontSize = 12.sp)
                    }
                }
            } else {
                if (verified.isEmpty()) {
                    item {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF130D0D),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFF3A1A1A))
                        ) {
                            Column(Modifier.padding(14.dp)) {
                                Text(
                                    "No verified camera feeds",
                                    color = Color(0xFFFFAA00),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "The scanner found HTTP 200 endpoints, but none returned image or MJPEG content. Raw findings are still in the terminal log.",
                                    color = Color.Gray,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
                items(verified, key = { it.link.url }) { result ->
                    VerifiedLinkCard(result, onOpenLink)
                }
                if (verified.isNotEmpty()) {
                    item {
                        TextButton(
                            onClick = {
                                clipboardManager.setText(AnnotatedString(verified.joinToString("\n") { it.link.url }))
                                Toast.makeText(context, "Verified links copied", Toast.LENGTH_SHORT).show()
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("COPY VERIFIED URLS", color = Color.Cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                if (rejected.isNotEmpty()) {
                    item {
                        TextButton(onClick = onToggleRejected, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                if (showRejected) "HIDE ${rejected.size} REJECTED" else "SHOW ${rejected.size} REJECTED",
                                color = Color(0xFFFFAA00),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    if (showRejected) {
                        items(rejected, key = { it.link.url }) { result ->
                            RejectedLinkCard(result)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FindingStat(label: String, value: Int, color: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        color = Color(0xFF111111),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, Color(0xFF222222))
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            Text(value.toString(), color = color, fontSize = 18.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
private fun VerifiedLinkCard(
    result: CameraLinkVerifier.Result,
    onOpen: (CameraLinkVerifier.Result) -> Unit
) {
    val isLogin = result.verdict == CameraLinkVerifier.Verdict.LOGIN
    val accent = if (isLogin) Color(0xFFFFAA00) else Color.Green
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A140A)),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(result.link.type, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        color = accent.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.4f))
                    ) {
                        Text(
                            if (isLogin) "LOGIN" else "VERIFIED",
                            color = accent,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    result.link.url,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    result.detail,
                    color = Color(0xFF6A9A6A),
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = { onOpen(result) },
                colors = ButtonDefaults.buttonColors(accent),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text(if (isLogin) "AUTH" else "LIVE", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun RejectedLinkCard(result: CameraLinkVerifier.Result) {
    val unreachable = result.verdict == CameraLinkVerifier.Verdict.UNREACHABLE
    val accent = if (unreachable) Color(0xFF8A8A8A) else Color(0xFFFF5555)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF120C0C)),
        border = BorderStroke(1.dp, Color(0xFF3A1A1A))
    ) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(result.link.type, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(8.dp))
                Text(
                    if (unreachable) "NO RESPONSE" else "REJECTED",
                    color = accent,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(3.dp))
            Text(
                result.link.url,
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                result.detail,
                color = Color(0xFF9A6A6A),
                fontSize = 10.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
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
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    var currentTextureView by remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(selectedUrl) { onDispose { currentExoPlayer?.release(); currentExoPlayer = null } }

    val streamUrls = remember(terminalText) {
        val start = terminalText.indexOf("===LINKS_START===")
        val end = terminalText.indexOf("===LINKS_END===")
        if (start != -1 && end != -1) {
            terminalText.substring(start, end).lines().filter { it.contains("|") }.mapNotNull {
                val p = it.split("|")
                if (p.size >= 2) p[1].trim() to p[0].trim() else null
            }.distinctBy { it.first }
        } else emptyList()
    }

    LaunchedEffect(streamUrls) {
        if (selectedUrl.isEmpty() && streamUrls.isNotEmpty()) {
            onUrlSelected(streamUrls.first().first)
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
            Text(text = "STREAM VIEWER", color = Color.Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            if (isRecording) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(Color.Red, CircleShape))
                    Spacer(Modifier.width(4.dp))
                    Text(text = "REC ${String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60)}", color = Color.Red, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        if (streamUrls.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                streamUrls.forEach { (url, label) ->
                    val isSelected = url == selectedUrl
                    FilterChip(
                        selected = isSelected,
                        onClick = { onUrlSelected(url) },
                        label = { Text(label, fontSize = 11.sp, color = if (isSelected) Color.Black else Color.Green) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color.Green,
                            containerColor = Color(0xFF111111)
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            borderColor = Color(0xFF333333),
                            selectedBorderColor = Color.Green,
                            enabled = true,
                            selected = isSelected
                        )
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        if (selectedUrl.isNotEmpty()) {
            val (user, pass) = extractCredentials(terminalText)
            val auth = buildAuthUrl(selectedUrl, user, pass)
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black, RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
                    .clip(RoundedCornerShape(8.dp))
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color.Black)
                ) {
                    if (selectedUrl.startsWith("rtsp")) {
                        AndroidView(
                            factory = { ctx ->
                                ExoPlayer.Builder(ctx).build().apply {
                                    setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).setDebugLoggingEnabled(false).createMediaSource(MediaItem.fromUri(auth)))
                                    prepare()
                                    playWhenReady = true
                                    currentExoPlayer = this
                                }.let { player ->
                                    PlayerView(ctx).apply {
                                        this.player = player
                                        currentTextureView = TextureView(ctx)
                                        try {
                                            this.javaClass.getMethod("setVideoSurfaceView", View::class.java).invoke(this, currentTextureView)
                                        } catch (e: Exception) {}
                                    }
                                }
                            },
                            Modifier.fillMaxSize()
                        )
                    } else {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    currentWebView = this
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        useWideViewPort = true
                                        loadWithOverviewMode = true
                                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                                    }
                                    loadUrl(auth)
                                }
                            },
                            Modifier.fillMaxSize()
                        )
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D0D))
                        .padding(12.dp),
                    Arrangement.SpaceEvenly,
                    Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            scope.launch {
                                val b = if (selectedUrl.startsWith("rtsp")) currentTextureView?.getBitmap() else currentWebView?.let {
                                    val bmp = Bitmap.createBitmap(it.width, it.height, Bitmap.Config.ARGB_8888)
                                    it.draw(Canvas(bmp))
                                    bmp
                                }
                                b?.let { if (saveBitmapToGallery(context, it)) Toast.makeText(context, "📷 Snapshot Saved", Toast.LENGTH_SHORT).show() }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color.Green, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("SNAP", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = { onToggleRecording() },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isRecording) Color(0xFF8B0000) else Color(0xFF222222)),
                        shape = RoundedCornerShape(4.dp),
                        border = BorderStroke(1.dp, if (isRecording) Color.Red else Color(0xFF444444))
                    ) {
                        Icon(
                            if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            null,
                            tint = if (isRecording) Color.White else Color.Red,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(if (isRecording) "STOP REC" else "RECORD", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        } else {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp))
                    .border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Videocam, null, tint = Color.DarkGray, modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("NO ACTIVE STREAM FEED", color = Color.Gray, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(4.dp))
                    Text("Run target scan in CONSOLE or LAN tab to discover streams", color = Color.DarkGray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun MiniPlayer(url: String, user: String, pass: String) {
    val auth = buildAuthUrl(url, user, pass)
    if (url.startsWith("rtsp")) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = ExoPlayer.Builder(ctx).build().apply {
                        setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).setDebugLoggingEnabled(false).createMediaSource(MediaItem.fromUri(auth)))
                        prepare()
                        play()
                    }
                }
            },
            Modifier.fillMaxSize()
        )
    } else {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    settings.javaScriptEnabled = true
                    loadUrl(auth)
                }
            },
            Modifier.fillMaxSize()
        )
    }
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
fun LanScanTab(
    scanResults: List<LanHost>,
    isScanning: Boolean,
    progress: Float,
    networkSummary: NetworkDiscoveryHelper.NetworkSummary?,
    nmapMode: Boolean,
    onNmapModeChange: (Boolean) -> Unit,
    nmapOutput: String,
    isMonitorRunning: Boolean,
    onToggleMonitor: () -> Unit,
    onScanStart: () -> Unit,
    onTabSwitch: (Int) -> Unit,
    onIpSelected: (String) -> Unit,
    onViewStream: (String) -> Unit,
    onHostClick: (LanHost) -> Unit
) {
    LaunchedEffect(Unit) {
        if (scanResults.isEmpty() && !isScanning) {
            onScanStart()
        }
    }

    val neon = Color(0xFF00FF88)
    val cameraCount = scanResults.count { it.isCamera }
    val percent = (progress.coerceIn(0f, 1f) * 100).toInt()
    val statusLine = when {
        isScanning -> "Scanning subnet - $percent%"
        cameraCount > 0 -> "$cameraCount camera${if (cameraCount == 1) "" else "s"} - ${scanResults.size} host${if (scanResults.size == 1) "" else "s"}"
        scanResults.isNotEmpty() -> "${scanResults.size} host${if (scanResults.size == 1) "" else "s"} online"
        else -> "Ready to scan"
    }

    // One scroll container for the whole tab: header, network cards, controls and
    // results all move together instead of leaving only a small list scrollable.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(top = 4.dp, bottom = 12.dp)
    ) {

        // ---- Section header -------------------------------------------------
        item {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.width(3.dp).height(38.dp).background(neon, RoundedCornerShape(2.dp)))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "NETWORK AUDIT",
                        color = neon,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = statusLine,
                        color = Color(0xFF8A8A8A),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = neon,
                        strokeWidth = 2.dp
                    )
                }
            }
        }

        // ---- Connected network ----------------------------------------------
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF0B1210),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, neon.copy(alpha = 0.18f))
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, tint = neon, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "NETWORK",
                            color = neon.copy(alpha = 0.9f),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.weight(1f))
                        Box(Modifier.size(6.dp).background(neon, CircleShape))
                    }
                    Spacer(Modifier.height(8.dp))
                    NetworkSummaryRow("SSID", networkSummary?.ssid?.takeIf { it.isNotBlank() } ?: "Unknown")
                    NetworkSummaryRow("LOCAL IP", networkSummary?.localIp ?: "Unknown")
                    NetworkSummaryRow("SUBNET", networkSummary?.subnet ?: "Unknown")
                    NetworkSummaryRow("GATEWAY", networkSummary?.gateway ?: "Unknown")
                }
            }
        }

        // ---- Background monitor (visually distinct from the summary) --------
        item {
            val monitorAccent = Color(0xFFFFC107)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = if (isMonitorRunning) Color(0xFF141006) else Color(0xFF0B0B0B),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(
                    1.dp,
                    if (isMonitorRunning) monitorAccent.copy(alpha = 0.35f) else Color(0xFF1E1E1E)
                )
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(36.dp).background(
                            if (isMonitorRunning) monitorAccent.copy(alpha = 0.12f) else Color(0xFF141414),
                            RoundedCornerShape(9.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.NotificationsActive,
                            null,
                            tint = if (isMonitorRunning) monitorAccent else Color(0xFF6A6A6A),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Traffic Monitor",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isMonitorRunning) "Background service active" else "Background service off",
                            color = if (isMonitorRunning) monitorAccent.copy(alpha = 0.85f) else Color(0xFF7A7A7A),
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Switch(
                        checked = isMonitorRunning,
                        onCheckedChange = { onToggleMonitor() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Black,
                            checkedTrackColor = monitorAccent,
                            checkedBorderColor = monitorAccent,
                            uncheckedThumbColor = Color(0xFF8A8A8A),
                            uncheckedTrackColor = Color(0xFF1E1E1E),
                            uncheckedBorderColor = Color(0xFF333333)
                        )
                    )
                }
            }
        }

        // ---- Device count + scan action -------------------------------------
        item {
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = "DEVICES",
                            color = Color(0xFF7A7A7A),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "${scanResults.size}",
                            color = neon,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    if (isScanning) {
                        Text(
                            text = "$percent% complete",
                            color = Color(0xFFFFC107),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
                Button(
                    onClick = { if (!isScanning) onScanStart() },
                    modifier = Modifier.height(42.dp),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 0.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = neon,
                        contentColor = Color.Black
                    )
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Search, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(7.dp))
                    Text(
                        text = if (isScanning) "SCANNING" else "SCAN",
                        color = Color.Black,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }

        if (isScanning) {
            item {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
                    color = neon,
                    trackColor = Color(0xFF14231A),
                    strokeCap = StrokeCap.Round
                )
            }
        }

        if (scanResults.isEmpty()) {
            item {
                LanEmptyState(isScanning = isScanning, onScan = onScanStart)
            }
        }

        items(scanResults.sortedBy { it.ip }, key = { it.ip }) { host ->
            LanHostCard(host = host, onClick = { onHostClick(host) })
        }
    }
}

@Composable
private fun NetworkSummaryRow(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            color = Color(0xFF6F7F78),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.8.sp,
            modifier = Modifier.width(74.dp)
        )
        Text(
            text = value,
            color = Color(0xFFE6FFF2),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun LanEmptyState(isScanning: Boolean, onScan: () -> Unit) {
    val neon = Color(0xFF00FF88)
    Column(
        Modifier.fillMaxWidth().padding(vertical = 30.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier.size(62.dp)
                .background(if (isScanning) neon.copy(alpha = 0.10f) else Color(0xFF121212), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Wifi,
                null,
                tint = if (isScanning) neon else Color(0xFF4A4A4A),
                modifier = Modifier.size(30.dp)
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = "No devices yet",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = if (isScanning) {
                "Scan in progress - devices will appear as they respond."
            } else {
                "Run a scan to find cameras, routers and other hosts on your network."
            },
            color = Color(0xFF7A7A7A),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
        if (!isScanning) {
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onScan,
                shape = RoundedCornerShape(9.dp),
                border = BorderStroke(1.dp, neon.copy(alpha = 0.6f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = neon)
            ) {
                Icon(Icons.Default.Search, null, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text("SCAN NETWORK", fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp)
            }
        }
    }
}

@Composable
fun LanHostCard(host: LanHost, onClick: () -> Unit) {
    val neon = Color(0xFF00FF88)
    val type = host.deviceType.uppercase(Locale.US)
    val typeColor = lanTypeColor(type)
    val title = lanDisplayName(host)
    val subtitle = when {
        host.isYourDevice -> Build.MODEL.takeIf { it.isNotBlank() }
        !host.vendor.isNullOrBlank() && !host.vendor.equals(title, ignoreCase = true) -> host.vendor
        !host.hostname.isNullOrBlank() && !host.hostname.equals(title, ignoreCase = true) -> host.hostname
        else -> null
    }?.takeIf { !it.equals(host.ip, ignoreCase = true) }
    val mac = host.mac?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
    val ports = host.openPorts.distinct()
    val highlighted = host.isCamera || host.isYourDevice

    Surface(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        color = Color(0xFF0A0A0A),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(
            1.dp,
            if (highlighted) neon.copy(alpha = 0.28f) else Color(0xFF1A1A1A)
        )
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.size(42.dp)
                    .background(typeColor.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                    .border(1.dp, typeColor.copy(alpha = 0.28f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = lanDeviceIcon(type),
                    contentDescription = null,
                    tint = typeColor,
                    modifier = Modifier.size(21.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (host.isYourDevice) {
                        Spacer(Modifier.width(6.dp))
                        Box(
                            Modifier.background(neon, RoundedCornerShape(4.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = "YOU",
                                color = Color.Black,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }

                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle,
                        color = if (host.isCamera) neon.copy(alpha = 0.75f) else Color(0xFF8A8A8A),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = host.ip,
                    color = Color(0xFFA8A8A8),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.5.sp
                )

                Spacer(Modifier.height(6.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LanTypeTag(type)
                    ports.take(3).forEach { port ->
                        LanPortTag(lanPortLabel(port))
                    }
                    if (ports.size > 3) {
                        Text(
                            text = "+${ports.size - 3}",
                            color = Color(0xFF5A5A5A),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                if (mac != null) {
                    Spacer(Modifier.height(4.dp))
                    LanMacTag("MAC $mac")
                }
            }

            if (host.streamUrl != null) {
                Spacer(Modifier.width(8.dp))
                LanActionTag("LIVE", neon)
            }
        }
    }
}

/** One display name used by both the card and the detail sheet. */
private fun lanDisplayName(host: LanHost): String {
    if (host.isYourDevice) return "This Device"
    return DeviceIdentifier.cleanName(host.hostname, host.ip)
        ?: host.vendor?.takeIf { it.isNotBlank() }
        ?: "Network Device"
}

private fun lanDeviceIcon(type: String) = when (type) {
    DeviceIdentifier.CAMERA -> Icons.Default.Videocam
    DeviceIdentifier.ROUTER -> Icons.Default.Router
    DeviceIdentifier.PHONE -> Icons.Default.Smartphone
    DeviceIdentifier.PRINTER -> Icons.Default.Print
    DeviceIdentifier.NAS -> Icons.Default.Storage
    DeviceIdentifier.TV -> Icons.Default.Tv
    DeviceIdentifier.IOT -> Icons.Default.Sensors
    DeviceIdentifier.COMPUTER -> Icons.Default.Computer
    else -> Icons.Default.Devices
}

private fun lanTypeColor(type: String): Color = when (type) {
    DeviceIdentifier.CAMERA -> Color(0xFF00FF88)
    DeviceIdentifier.ROUTER -> Color(0xFF4FC3F7)
    DeviceIdentifier.PHONE -> Color(0xFFCE93D8)
    DeviceIdentifier.PRINTER -> Color(0xFFFFB74D)
    DeviceIdentifier.NAS -> Color(0xFF81C784)
    DeviceIdentifier.TV -> Color(0xFF64B5F6)
    DeviceIdentifier.IOT -> Color(0xFFFFD54F)
    DeviceIdentifier.COMPUTER -> Color(0xFF90A4AE)
    else -> Color(0xFF7A7A7A)
}

private fun lanPortLabel(port: Int): String = when (port) {
    80, 8000, 8080 -> "HTTP"
    443, 8443 -> "HTTPS"
    554, 8554 -> "RTSP"
    21 -> "FTP"
    22 -> "SSH"
    23 -> "TELNET"
    445 -> "SMB"
    515, 631 -> "IPP"
    9100 -> "PRINT"
    8009 -> "CAST"
    3389 -> "RDP"
    37777 -> "TCP"
    else -> "$port"
}

@Composable
private fun LanPortTag(label: String) {
    Box(
        Modifier.background(Color(0xFF0F2418), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFF00FF88).copy(alpha = 0.85f),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

@Composable
private fun LanMacTag(label: String) {
    Box(
        Modifier.background(Color(0xFF141414), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFB8B8B8),
            fontSize = 8.sp,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.4.sp
        )
    }
}

@Composable
private fun LanTypeTag(label: String) {
    Box(
        Modifier.background(Color(0xFF151515), RoundedCornerShape(4.dp))
            .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = Color(0xFFB0B0B0),
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun LanActionTag(label: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.12f),
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.45f))
    ) {
        Text(
            text = label,
            color = color,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.5.sp,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

fun openFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); val intent = Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }; context.startActivity(intent) } catch (e: Exception) {} }
fun openBrowserSearch(context: Context, engine: String, query: String) { val url = when (engine) { "GOOGLE" -> "https://www.google.com/search?q=${Uri.encode(query)}"; else -> "" }; try { if (url.isNotEmpty()) context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (e: Exception) {} }
fun shareFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); val intent = Intent(Intent.ACTION_SEND).apply { type = "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }; context.startActivity(Intent.createChooser(intent, "Share Report")) } catch (e: Exception) {} }
fun saveContentToFile(context: Context, content: String, name: String, ext: String) { try { val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS); val file = File(dir, "${name}_${System.currentTimeMillis()}.$ext"); file.writeText(content) } catch (e: Exception) {} }
fun captureScreenshot(context: Context, view: android.view.View, onCapture: (Bitmap) -> Unit) { 
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    view.draw(Canvas(bitmap))
    onCapture(bitmap)
    if (saveBitmapToGallery(context, bitmap)) Toast.makeText(context, "Screenshot Saved", Toast.LENGTH_SHORT).show() 
}
fun exportTerminalText(context: Context, text: String) {
    if (text.isBlank()) {
        Toast.makeText(context, "Terminal is empty", Toast.LENGTH_SHORT).show()
        return
    }
    try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "CamXploit_Log_${System.currentTimeMillis()}.txt")
        file.writeText(text)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "CamXploit Terminal Log")
            putExtra(Intent.EXTRA_TEXT, text)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Export Terminal Log"))
    } catch (e: Exception) {
        Toast.makeText(context, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}
fun generateHtmlReport(context: Context, content: String) { saveContentToFile(context, "<html><body style='background:#000;color:#0F0;font-family:monospace'><pre>$content</pre></body></html>", "Report", "html") }
fun generatePdfReport(context: Context, content: String) { 
    val pdf = PdfDocument()
    val paint = Paint().apply {
        color = android.graphics.Color.BLACK
        textSize = 10f
        typeface = Typeface.MONOSPACE
    }
    val margin = 40f
    val pageWidth = 595
    val pageHeight = 842
    var yPos = margin + 20f
    
    var pageInfo = PageInfo.Builder(pageWidth, pageHeight, 1).create()
    var page = pdf.startPage(pageInfo)
    var canvas = page.canvas

    canvas.drawText("CAMXPLOIT AUDIT REPORT", margin, margin, Paint().apply { 
        textSize = 16f
        isFakeBoldText = true 
    })
    
    content.lines().forEach { line ->
        if (yPos > pageHeight - margin) {
            pdf.finishPage(page)
            pageInfo = PageInfo.Builder(pageWidth, pageHeight, pdf.pages.size + 1).create()
            page = pdf.startPage(pageInfo)
            canvas = page.canvas
            yPos = margin
        }
        canvas.drawText(line.take(100), margin, yPos, paint)
        yPos += 14f
    }
    
    pdf.finishPage(page)
    try {
        val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Report_${System.currentTimeMillis()}.pdf")
        pdf.writeTo(FileOutputStream(f))
        Toast.makeText(context, "PDF Report Generated", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        pdf.close()
    }
}
fun generateDetailedPdfReport(context: Context, terminalText: String, targetIp: String) { generatePdfReport(context, "Target: $targetIp\n\n$terminalText") }
fun saveJsonReport(context: Context, content: String, ip: String) { try { val json = JSONObject().apply { put("target", ip); put("log", content) }; saveContentToFile(context, json.toString(), "Report_$ip", "json") } catch (e: Exception) {} }


fun extractCredentials(text: String): Pair<String, String> { val u = Regex("""User:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: "admin"; val p = Regex("""Pass:\s*(\S+)""").find(text)?.groupValues?.get(1) ?: "admin"; return u to p }
fun buildAuthUrl(u: String, user: String, pass: String): String { if (user.isBlank() || pass.isBlank() || u.contains("@")) return u; return try { if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@") else if (u.startsWith("https://")) u.replace("https://", "https://$user:$pass@") else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@") else u } catch (e: Exception) { u } }

@Composable
fun GoogleDorksDialog(ip: String, onDismiss: () -> Unit, onOpenBrowser: (String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("GOOGLE DORKS") }, text = { Column { listOf("view/index.shtml", "Live View / - AXIS").forEach { d -> Text(d, modifier = Modifier.fillMaxWidth().clickable { onOpenBrowser("$d $ip") }.padding(8.dp)) } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } })
}

@Composable
fun ExternalSearchDialog(ip: String, onDismiss: () -> Unit, onSearch: (String) -> Unit, onOpenBrowser: (String, String) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("OSINT") }, text = { Column { listOf("GOOGLE").forEach { engine -> Button(onClick = { if (engine == "GOOGLE") onOpenBrowser("GOOGLE", ip) else onSearch(""); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text(engine) } } } }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } })
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
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
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

            val primaryName = lanDisplayName(host)
            Text(
                text = primaryName,
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            val subtitle = when {
                host.isYourDevice -> Build.MODEL.takeIf { it.isNotBlank() }
                !host.vendor.isNullOrBlank() && !host.vendor.equals(primaryName, ignoreCase = true) -> host.vendor
                !host.hostname.isNullOrBlank() && !host.hostname.equals(primaryName, ignoreCase = true) -> host.hostname
                else -> null
            }?.takeIf { !it.equals(host.ip, ignoreCase = true) }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = Color.Gray,
                    fontSize = 14.sp
                )
            }
            Spacer(Modifier.height(6.dp))
            val mac = host.mac?.trim()?.takeIf { it.isNotEmpty() && !it.equals("Unknown", ignoreCase = true) }
            if (mac != null) {
                Text(
                    text = "MAC $mac",
                    color = Color(0xFFB0B0B0),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            } else {
                Text(
                    text = "MAC unavailable - Android 10+ does not expose other devices' MAC addresses to apps.",
                    color = Color(0xFF7A7A7A),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 16.dp)
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
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DetailActionItem(
                    icon = Icons.Default.OpenInBrowser,
                    label = "Web UI",
                    color = Color.Cyan,
                    modifier = Modifier.weight(1f)
                ) {
                    onOpenWebUi("http://${host.ip}")
                }
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
