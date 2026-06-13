@file:OptIn(UnstableApi::class)

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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
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
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
        if (!Python.isStarted()) Python.start(AndroidPlatform(this))
        setContent { CamGuardianApp() }
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
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

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
                        state = androidx.compose.foundation.lazy.rememberLazyListState()
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

class TerminalOutputStream(val onText: (String) -> Unit) : OutputStream() {
    private val buffer = StringBuilder()

    fun write(text: String) {
        buffer.append(text)
        if (text.contains("\n")) {
            flush()
        }
    }

    override fun write(b: Int) {
        val c = b.toChar()
        buffer.append(c)
        if (c == '\n') {
            flush()
        }
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(
        b: ByteArray, 
        off: Int, 
        len: Int
    ) {
        val text = String(b, off, len, 
            Charsets.UTF_8)
        write(text)
    }
    
    override fun flush() {
        if (buffer.isNotEmpty()) {
            onText(buffer.toString())
            buffer.clear()
        }
    }
}

@Composable
fun CamGuardianApp() {
    var terminalText by remember { mutableStateOf("> System Initialized. Awaiting Target...\n") }
    val appendToConsole: (String) -> Unit = { text -> terminalText += text }
    var consoleIpInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var lanScanResults by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var lanIsScanning by remember { mutableStateOf(false) }
    var lanProgress by remember { mutableStateOf(0f) }
    var lanSubnet by remember { mutableStateOf("") }
    var showDisclaimer by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showShodanDialog by remember { mutableStateOf(false) }
    var shodanApiKey by remember { mutableStateOf("") }
    var shodanQuery by remember { mutableStateOf("webcam") }
    var selectedUrl by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    var lanNmapMode by remember { mutableStateOf(false) }
    var lanScanOutput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    val startReconScan = { targetIp: String ->
        if (targetIp.isNotEmpty() && !isScanning) {
            consoleIpInput = targetIp; selectedTab = 0; isScanning = true; terminalText = "> Starting Reconnaissance on $targetIp...\n"
            scope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("CamXploit")
                    val sys = py.getModule("sys")
                    
                    val outputQueue = java.util.concurrent.LinkedBlockingQueue<String>()
                    val pyOutputStream = TerminalOutputStream { outputQueue.offer(it) }
                    
                    // Start output consumer
                    val consumerJob = launch(Dispatchers.Main) {
                        while (isActive) {
                            val line = outputQueue.poll()
                            if (line != null) {
                                terminalText += line
                            } else {
                                delay(100)
                            }
                        }
                    }
                    
                    // Redirect Python output
                    sys.put("stdout", pyOutputStream)
                    sys.put("stderr", pyOutputStream)
                    
                    // Run scan
                    val heartbeat = launch(Dispatchers.Main) {
                        val dots = listOf("⠋","⠙","⠹","⠸","⠼","⠴","⠦","⠧","⠇","⠏")
                        var i = 0
                        while (isActive) {
                            delay(500)
                            // pulse indicator — replace last line if it was a pulse
                            i = (i + 1) % dots.size
                        }
                    }

                    try {
                        withTimeout(60_000) {
                            module.callAttr("main", consoleIpInput)
                        }
                    } finally {
                        heartbeat.cancel()
                    }
                    
                    // Flush remaining output
                    pyOutputStream.flush()
                    consumerJob.cancel()
                    
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
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        terminalText += "\n[!] ERROR: ${e.message}\n"
                    }
                } finally {
                    withContext(Dispatchers.Main) {
                        isScanning = false
                    }
                }
            }
        }
    }

    val recordLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply { action = "ACTION_START"; putExtra("RESULT_CODE", result.resultCode); putExtra("DATA", result.data) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(serviceIntent) else context.startService(serviceIntent)
            isRecording = true
        }
    }

    val receiver = remember { object : BroadcastReceiver() { override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == "COM_SPYBOY_CAMXPLOIT_RECORDING_STOPPED") { isRecording = false; val path = intent.getStringExtra("FILE_PATH") ?: ""; Toast.makeText(context, "Recording saved: $path", Toast.LENGTH_LONG).show() } } } }

    DisposableEffect(Unit) {
        val filter = IntentFilter("COM_SPYBOY_CAMXPLOIT_RECORDING_STOPPED")
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        onDispose { context.unregisterReceiver(receiver) }
    }

    LaunchedEffect(isRecording) { if (isRecording) { recordingDuration = 0L; while (isRecording) { delay(1000); recordingDuration++ } } }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
        if (!(permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false) && !(permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false)) Toast.makeText(context, "Location permission required for scanning", Toast.LENGTH_LONG).show()
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        if (permissions.any { ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED }) permissionLauncher.launch(permissions.toTypedArray())
    }

    if (showDisclaimer) {
        AlertDialog(onDismissRequest = { }, title = { Text("LEGAL DISCLAIMER", color = Color.Red, fontWeight = FontWeight.Black) }, text = { Text("This tool is for educational and authorized security testing purposes only. Unauthorized access is illegal. The authors are not responsible for any misuse.", color = Color.White) }, confirmButton = { Button(onClick = { showDisclaimer = false }) { Text("I AGREE & UNDERSTAND") } }, containerColor = Color(0xFF111111), shape = RoundedCornerShape(8.dp))
    }

    if (showShodanDialog) {
        AlertDialog(onDismissRequest = { showShodanDialog = false }, title = { Text("GLOBAL SHODAN SEARCH", color = Color.Magenta, fontWeight = FontWeight.Bold) }, text = {
            Column {
                Text("Search for exposed cameras globally via Shodan API.", color = Color.Gray, fontSize = 12.sp); Spacer(Modifier.height(16.dp))
                OutlinedTextField(value = shodanApiKey, onValueChange = { shodanApiKey = it }, label = { Text("Shodan API Key") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White)); Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = shodanQuery, onValueChange = { shodanQuery = it }, label = { Text("Search Query") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(color = Color.White))
            }
        }, confirmButton = {
            Button(onClick = {
                showShodanDialog = false; if (shodanApiKey.isNotBlank()) { terminalText = "> Initiating Global Shodan Search...\n"; scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val module = py.getModule("CamXploit"); val sys = py.getModule("sys"); sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } }); module.callAttr("shodan_search", shodanApiKey, shodanQuery) } catch (e: Exception) { withContext(Dispatchers.Main) { terminalText += "\n[!] Shodan Error: ${e.message}" } } } } else Toast.makeText(context, "API Key Required", Toast.LENGTH_SHORT).show()
            }) { Text("SEARCH") }
        }, dismissButton = { TextButton(onClick = { showShodanDialog = false }) { Text("CANCEL") } }, containerColor = Color(0xFF111111))
    }

    if (viewingFile != null) {
        AlertDialog(
            onDismissRequest = { viewingFile = null },
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier.fillMaxSize().padding(16.dp),
            confirmButton = {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), Arrangement.End) {
                    TextButton(onClick = { viewingFile?.let { openFile(context, it) } }) {
                        Text("OPEN EXTERNAL", color = Color.Green)
                    }
                    TextButton(onClick = { viewingFile?.let { shareFile(context, it) } }) {
                        Text("SHARE", color = Color.Cyan)
                    }
                    TextButton(onClick = { viewingFile = null }) {
                        Text("CLOSE", color = Color.Red)
                    }
                }
            },
            text = {
                Column(Modifier.fillMaxSize()) {
                    Text(text = viewingFile?.name ?: "", color = Color.Yellow, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            text = viewingFile?.readText() ?: "",
                            color = Color.LightGray,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            modifier = Modifier.verticalScroll(rememberScrollState())
                        )
                    }
                }
            },
            containerColor = Color(0xFF050505)
        )
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            ScrollableTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF0A0A0A),
                contentColor = Color.Green,
                edgePadding = 16.dp,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = Color.Green
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
                    Icons.Default.Radar to "NMAP"
                )
                tabs.forEachIndexed { index, (icon, label) ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(label, fontSize = 10.sp, fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal) },
                        icon = { Icon(icon, null, modifier = Modifier.size(20.dp)) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column {
                    Text(text = "CAMXPLOIT", color = Color.Green, fontSize = 24.sp, fontWeight = FontWeight.Black, style = TextStyle(letterSpacing = 4.sp))
                    Text(text = "ADVANCED CAMERA AUDIT TOOLKIT", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                }
                Row { IconButton(onClick = { showShodanDialog = true }) { Icon(Icons.Default.Public, null, tint = Color.Magenta) }; IconButton(onClick = { captureScreenshot(context, view) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan) }; IconButton(onClick = { generatePdfReport(context, terminalText); generateHtmlReport(context, terminalText) }) { Icon(Icons.Default.CheckCircle, null, tint = Color.Green) } }
            }
            Spacer(Modifier.height(20.dp)); when (selectedTab) {
                0 -> ConsoleTab(consoleIpInput, { consoleIpInput = it }, terminalText, { terminalText = "> Console Reset.\n" }, isScanning, scrollState, { startReconScan(consoleIpInput) }, { url, _ -> selectedUrl = buildAuthUrl(url, extractCredentials(terminalText).first, extractCredentials(terminalText).second); selectedTab = 3 })
                1 -> IntelTab(
                    consoleIpInput = consoleIpInput,
                    terminalText = terminalText,
                    onTerminalUpdate = { terminalText += it },
                    onManualSnapshot = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                val b64 = py.getModule("CamXploit").callAttr("manual_snapshot_capture", consoleIpInput, 80, extractCredentials(terminalText).first, extractCredentials(terminalText).second).toString()
                                if (b64 != "None") {
                                    val b = Base64.decode(b64, Base64.DEFAULT)
                                    val bmp = BitmapFactory.decodeByteArray(b, 0, b.size)
                                    withContext(Dispatchers.Main) {
                                        capturedBitmap = bmp
                                        val f = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Snap_${System.currentTimeMillis()}.png")
                                        FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                        Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            } catch (e: Exception) {
                            }
                        }
                    },
                    onStreamSelect = { url ->
                        selectedUrl = buildAuthUrl(url, extractCredentials(terminalText).first, extractCredentials(terminalText).second)
                        selectedTab = 3
                    },
                    onDiscoverOnvif = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                py.getModule("sys").put("stdout", TerminalOutputStream { t -> scope.launch(Dispatchers.Main) { terminalText += t } })
                                py.getModule("CamXploit").callAttr("discover_onvif", consoleIpInput)
                            } catch (e: Exception) {
                            }
                        }
                    },
                    onProbeEndpoints = {
                        val targetHost = if (consoleIpInput.contains(":")) consoleIpInput.substringBefore(":") else consoleIpInput
                        val targetPort = if (consoleIpInput.contains(":")) consoleIpInput.substringAfter(":").toIntOrNull() ?: 80 else 80
                        scope.launch {
                            terminalText += "🔍 Probing endpoints on $targetHost:$targetPort ...\n"
                            val scanner = CameraScanner()
                            scanner.scanEndpoints(
                                host = targetHost,
                                port = targetPort,
                                onResult = { result ->
                                    terminalText += "  🎯 Found ${result.type}: ${result.url} (HTTP ${result.httpCode})\n"
                                },
                                onDone = {
                                    terminalText += "✅ Endpoint scan complete.\n"
                                }
                            )
                        }
                    }
                )
                2 -> ArchiveTab(context, selectedTab, terminalText, consoleIpInput) { viewingFile = it }
                3 -> StreamTab(terminalText, selectedUrl, { selectedUrl = it }, isRecording, recordingDuration, { recordLauncher.launch(projectionManager.createScreenCaptureIntent()) }, { context.startService(Intent(context, ScreenCaptureService::class.java).apply { action = "ACTION_STOP" }) })
                4 -> LanScanTab(
                    scanResults = lanScanResults,
                    isScanning = lanIsScanning,
                    progress = lanProgress,
                    subnet = lanSubnet,
                    nmapMode = lanNmapMode,
                    onNmapModeChange = { lanNmapMode = it },
                    nmapOutput = lanScanOutput,
                    onScanStart = {
                        lanIsScanning = true
                        lanScanResults = emptyList()
                        lanProgress = 0f
                        lanScanOutput = "> Initiating scan...\n"
                        scope.launch(Dispatchers.IO) {
                            val subnet = getLocalSubnet().substringBeforeLast(".0/24")
                            withContext(Dispatchers.Main) { lanSubnet = subnet }

                            if (lanNmapMode) {
                                subnetScan(
                                    context = context,
                                    subnet = "$subnet.0/24",
                                    onOutput = { line ->
                                        if (line.contains("Nmap scan report")) {
                                            val ip = line.substringAfter("for ").trim().split(" ").first()
                                            if (lanScanResults.none { it.ip == ip }) {
                                                lanScanResults = lanScanResults + DeviceInfo(ip, null, emptyList())
                                            }
                                        }
                                        lanScanOutput += "$line\n"
                                    },
                                    onComplete = {
                                        lanIsScanning = false
                                    }
                                )
                            } else {
                                (1..254).map { i ->
                                    async {
                                        val ip = "$subnet.$i"
                                        try {
                                            if (InetAddress.getByName(ip).isReachable(300)) {
                                                withContext(Dispatchers.Main) {
                                                    if (lanScanResults.none { it.ip == ip }) {
                                                        lanScanResults = lanScanResults + DeviceInfo(ip, null, emptyList())
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                        }
                                        withContext(Dispatchers.Main) { lanProgress = i / 254f }
                                    }
                                }.awaitAll()
                                withContext(Dispatchers.Main) { lanIsScanning = false }
                            }
                        }
                    },
                    onResultFound = { },
                    onScanComplete = { },
                    onTabSwitch = { selectedTab = it },
                    onIpSelected = { consoleIpInput = it; selectedTab = 0 })
                5 -> StormTab(onAutoRescan = { startReconScan(it); Toast.makeText(context, "Running post-stress scan...", Toast.LENGTH_SHORT).show() }, onSaveResults = { ip, out -> saveContentToFile(context, out, "[STORM] ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())} - $ip", "txt"); Toast.makeText(context, "Saved to Archive", Toast.LENGTH_SHORT).show() })
                6 -> SavedCamerasTab({ selectedUrl = it; selectedTab = 3 }, { consoleIpInput = it; selectedTab = 0 })
                7 -> NmapTab(context, consoleIpInput,
                    onTabSwitch = { selectedTab = it },
                    onIpSelected = { consoleIpInput = it })
            }
            capturedBitmap?.let { bmp ->
                Spacer(Modifier.height(20.dp)); Text(text = "LAST SNAPSHOT", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(200.dp).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).background(Color.Black), Alignment.Center) { Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize()); IconButton(onClick = { capturedBitmap = null }, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            }
        }
    }
}

@Composable
fun ConsoleTab(consoleIpInput: String, onIpChange: (String) -> Unit, terminalText: String, onTerminalClear: () -> Unit, isScanning: Boolean, scrollState: ScrollState, onStartScan: () -> Unit, onStreamSelect: (String, String) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(12.dp)) {
            Column {
                Text(text = "TARGET HOST", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = consoleIpInput,
                        onValueChange = onIpChange,
                        textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            cursorColor = Color.Green
                        ),
                        placeholder = {
                            Text(
                                "Enter IP eg 192.168.1.1",
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    )
                    IconButton(onClick = onStartScan) {
                        Icon(if (isScanning) Icons.Default.Refresh else Icons.Default.Search, null, tint = if (isScanning) Color.Yellow else Color.Green)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp)); Surface(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp)), color = Color(0xFF050505)) { Box { SelectionContainer { Text(text = terminalText, color = Color(0xFF00FF41), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(scrollState)) }; IconButton(onClick = onTerminalClear, Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)) { Icon(Icons.Default.Delete, null, tint = Color.DarkGray, modifier = Modifier.size(16.dp)) } } }
        LaunchedEffect(terminalText) { scrollState.animateScrollTo(scrollState.maxValue) }
        if (terminalText.contains("===LINKS_START===")) {
            val lines = terminalText.substringAfter("===LINKS_START===").substringBefore("===LINKS_END===").lines().filter { it.contains("|") }
            if (lines.isNotEmpty()) {
                Text(text = "🎯 Detected Links", color = Color.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
                Column(Modifier.fillMaxWidth().height(350.dp).verticalScroll(rememberScrollState())) { lines.forEach { line -> val p = line.split("|"); if (p.size >= 2) Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)), colors = CardDefaults.cardColors(Color.Transparent)) { Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(text = p[0].trim(), color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold); Text(text = p[1].trim().take(50), color = Color.Gray, fontSize = 11.sp) }; Button(onClick = { onStreamSelect(p[1].trim(), p[0].trim()) }, colors = ButtonDefaults.buttonColors(Color.Green)) { Text(text = "LIVE", color = Color.Black) } } } } }
            }
        }
    }
}

@Composable
fun IntelTab(consoleIpInput: String, terminalText: String, onTerminalUpdate: (String) -> Unit, onManualSnapshot: () -> Unit, onStreamSelect: (String) -> Unit, onDiscoverOnvif: () -> Unit, onProbeEndpoints: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = "🛡️ INTELLIGENCE GATHERING", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(text = "DEEP SCAN & VULNERABILITY ANALYSIS", color = Color.Gray, fontSize = 10.sp)
        
        Spacer(Modifier.height(16.dp))
        
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = { 
                    if (consoleIpInput.isEmpty()) Toast.makeText(context, "Set target in CONSOLE", Toast.LENGTH_SHORT).show()
                    else onDiscoverOnvif() 
                }, 
                modifier = Modifier.weight(1f), 
                colors = ButtonDefaults.buttonColors(Color(0xFF002233)), 
                shape = RoundedCornerShape(8.dp), 
                border = BorderStroke(1.dp, Color.Cyan)
            ) {
                Icon(Icons.Default.Radar, null, tint = Color.Cyan, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("ONVIF PROBE", fontSize = 10.sp, color = Color.Cyan)
            }

            Button(
                onClick = { 
                    if (consoleIpInput.isEmpty()) Toast.makeText(context, "Set target in CONSOLE", Toast.LENGTH_SHORT).show()
                    else onProbeEndpoints() 
                }, 
                modifier = Modifier.weight(1f), 
                colors = ButtonDefaults.buttonColors(Color(0xFF003300)), 
                shape = RoundedCornerShape(8.dp), 
                border = BorderStroke(1.dp, Color.Green)
            ) {
                Icon(Icons.Default.Search, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("PROBE PATHS", fontSize = 10.sp, color = Color.Green)
            }
            
            Button(
                onClick = { 
                    if (consoleIpInput.isEmpty()) Toast.makeText(context, "Set target in CONSOLE", Toast.LENGTH_SHORT).show()
                    else onManualSnapshot() 
                }, 
                modifier = Modifier.weight(1f), 
                colors = ButtonDefaults.buttonColors(Color(0xFF220033)), 
                shape = RoundedCornerShape(8.dp), 
                border = BorderStroke(1.dp, Color.Magenta)
            ) {
                Icon(Icons.Default.PhotoCamera, null, tint = Color.Magenta, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("SNAPSHOT", fontSize = 10.sp, color = Color.Magenta)
            }
        }
        
        Spacer(Modifier.height(20.dp))
        
        IntelSection("EXPLOITATION VECTORS", listOf("CamOver Info Disclosure", "GoAhead Auth Bypass", "RTSP Credential Sniffing", "ADB Screen Capture (Port 5555)", "Intel AMT Auth Bypass"), Color.Red, Icons.Default.Gavel) {
            onTerminalUpdate("> Testing Vector: $it...\n")
            if (it.contains("ADB")) {
                onTerminalUpdate("  [📡] Attempting unauthenticated ADB connection to $consoleIpInput...\n")
                onTerminalUpdate("  [⚠️] Result: Device must have 'Wireless Debugging' enabled in Developer Options.\n")
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        IntelSection("SYSTEM AUDIT", listOf("Firmware Version Check", "Hardware ID Recovery", "Service Banner Grabbing"), Color.Yellow, Icons.Default.Dns) {
            onTerminalUpdate("> Running Audit: $it...\n")
        }

        Spacer(Modifier.height(16.dp))
        
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(Color(0xFF111111)),
            border = BorderStroke(1.dp, Color.DarkGray)
        ) {
            Column(Modifier.padding(16.dp)) {
                Text("TARGET CONTEXT", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text(if (consoleIpInput.isEmpty()) "NO TARGET SET" else "ACTIVE TARGET: $consoleIpInput", color = Color.White, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
fun IntelSection(title: String, items: List<String>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onItemClick: (String) -> Unit) {
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color(0xFF1A1A1A))) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(text = title, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(8.dp)); items.forEach { item -> Row(Modifier.fillMaxWidth().clickable { onItemClick(item) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(4.dp).background(color, CircleShape)); Spacer(Modifier.width(8.dp)); Text(text = item, color = Color.Gray, fontSize = 11.sp) } }
        }
    }
}

fun PipelineStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: String, isComplete: Boolean, isActive: Boolean) {
    // PipelineStep Implementation
}

@Composable
fun StreamTab(terminalText: String, selectedUrl: String, onUrlSelected: (String) -> Unit, isRecording: Boolean, recordingDuration: Long, onStartRecording: () -> Unit, onStopRecording: () -> Unit) {
    val context = LocalContext.current; val scope = rememberCoroutineScope(); var isGridView by remember { mutableStateOf(false) }; var currentExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }; var currentWebView by remember { mutableStateOf<WebView?>(null) }; var currentTextureView by remember { mutableStateOf<TextureView?>(null) }
    DisposableEffect(selectedUrl) { onDispose { currentExoPlayer?.release(); currentExoPlayer = null } }
    val streamUrls = remember(terminalText) { val start = terminalText.indexOf("===LINKS_START==="); val end = terminalText.indexOf("===LINKS_END==="); if (start != -1 && end != -1) terminalText.substring(start, end).lines().filter { it.contains("|") }.mapNotNull { val p = it.split("|"); if (p.size >= 2) p[1].trim() to p[0].trim() else null }.distinctBy { it.first } else emptyList() }
    LaunchedEffect(streamUrls) { if (selectedUrl.isEmpty() && streamUrls.isNotEmpty()) onUrlSelected(streamUrls.first().first) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Row(verticalAlignment = Alignment.CenterVertically) { Text(text = "STREAM VIEWER", color = Color.Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold); if (isRecording) { Spacer(Modifier.width(12.dp)); Box(Modifier.size(8.dp).background(Color.Red, CircleShape)); Spacer(Modifier.width(4.dp)); Text(text = "REC ${String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60)}", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold) } }; IconButton(onClick = { isGridView = !isGridView }) { Icon(if (isGridView) Icons.Default.ViewStream else Icons.Default.GridView, null, tint = Color.Magenta) } }
        if (!isGridView && selectedUrl.isNotEmpty()) {
            val auth = buildAuthUrl(selectedUrl, extractCredentials(terminalText).first, extractCredentials(terminalText).second); Column(Modifier.weight(1f)) {
                if (selectedUrl.startsWith("rtsp")) key(selectedUrl) { AndroidView(factory = { ctx -> ExoPlayer.Builder(ctx).build().apply { setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(auth))); prepare(); playWhenReady = true; currentExoPlayer = this }.let { PlayerView(ctx).apply { player = it; useController = true; currentTextureView = TextureView(ctx); try { this.javaClass.getMethod("setVideoSurfaceView", android.view.View::class.java).invoke(this, currentTextureView) } catch (e: Exception) {} } } }, Modifier.fillMaxWidth().weight(1f)) }
                else key(selectedUrl) { AndroidView(factory = { ctx -> WebView(ctx).apply { currentWebView = this; settings.javaScriptEnabled = true; webViewClient = object : WebViewClient() { override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) { h?.proceed() } }; loadUrl(auth) } }, Modifier.fillMaxSize().weight(1f)) }
                Row(Modifier.align(Alignment.CenterHorizontally).padding(8.dp), Arrangement.spacedBy(16.dp)) {
                    IconButton(onClick = {
                        scope.launch {
                            val b = if (selectedUrl.startsWith("rtsp")) {
                                currentTextureView?.getBitmap()
                            } else {
                                currentWebView?.let { webView ->
                                    val bmp = Bitmap.createBitmap(webView.width, webView.height, Bitmap.Config.ARGB_8888)
                                    webView.draw(Canvas(bmp))
                                    bmp
                                }
                            }
                            b?.let {
                                if (saveBitmapToGallery(context, it)) Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }, Modifier.background(Color(0xFF003333), CircleShape)) {
                        Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan)
                    }
                    IconButton(onClick = { if (isRecording) onStopRecording() else onStartRecording() }, Modifier.background(if (isRecording) Color.Red else Color(0xFF330000), CircleShape)) { Icon(if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null, tint = Color.White) }
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
    val files = remember(refresh) { val all = mutableListOf<File>(); context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)?.listFiles()?.filter { it.isFile }?.let { all.addAll(it) }; File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CamVigil").listFiles()?.let { all.addAll(it) }; context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.listFiles()?.let { all.addAll(it) }; all.filter { it.isFile }.sortedByDescending { it.lastModified() } }
    Column {
        if (terminalText.length > 50) Button(onClick = { generateDetailedPdfReport(context, terminalText, targetIp) }, Modifier.fillMaxWidth().padding(bottom = 12.dp), colors = ButtonDefaults.buttonColors(Color.Green)) { Icon(Icons.Default.PictureAsPdf, null, tint = Color.Black); Text(text = "GENERATE FULL AUDIT REPORT", color = Color.Black, fontWeight = FontWeight.Black) }
        Text(text = "SAVED REPORTS & LOGS", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold); LazyColumn(Modifier.fillMaxSize()) { items(files) { f -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onFileClick(f) }, colors = CardDefaults.cardColors(Color(0xFF080808)), border = BorderStroke(1.dp, Color(0xFF111111))) { Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Description, null, tint = Color.Gray); Spacer(Modifier.width(10.dp)); Text(text = f.name, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f)); IconButton(onClick = { openFile(context, f) }) { Icon(Icons.AutoMirrored.Filled.OpenInNew, null, tint = Color.Green) } } } } }
    }
}

@Composable
fun LanScanTab(
    scanResults: List<DeviceInfo>,
    isScanning: Boolean,
    progress: Float,
    subnet: String,
    nmapMode: Boolean,
    onNmapModeChange: (Boolean) -> Unit,
    nmapOutput: String,
    onScanStart: () -> Unit,
    onResultFound: (DeviceInfo) -> Unit,
    onScanComplete: () -> Unit,
    onTabSwitch: (Int) -> Unit,
    onIpSelected: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(text = "LOCAL NETWORK SCANNER", color = Color.Green, fontSize = 16.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth().background(Color(0xFF0A0A0A), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text("SUBNET: $subnet.0/24", color = Color.White, fontSize = 14.sp); Text(if (isScanning) "Scanning..." else "Ready", color = Color.Gray, fontSize = 11.sp) }
            Button(onClick = onScanStart, enabled = !isScanning, colors = ButtonDefaults.buttonColors(Color(0xFF004400))) { Text("SCAN", color = Color.Green) }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Switch(checked = nmapMode, onCheckedChange = onNmapModeChange, colors = SwitchDefaults.colors(checkedThumbColor = Color.Green)); Spacer(Modifier.width(8.dp)); Text("Enhanced Nmap Discovery", color = Color.LightGray, fontSize = 12.sp) }
        Spacer(Modifier.height(16.dp))
        if (isScanning && !nmapMode) LinearProgressIndicator(progress = progress, modifier = Modifier.fillMaxWidth().height(2.dp), color = Color.Green, trackColor = Color(0xFF111111))
        if (nmapMode && nmapOutput.isNotEmpty()) {
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF050505)).border(1.dp, Color(0xFF111111)).padding(8.dp)) {
                Text(text = nmapOutput, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.verticalScroll(rememberScrollState()))
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(scanResults) { device ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onIpSelected(device.ip); onTabSwitch(0) }, colors = CardDefaults.cardColors(Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color(0xFF111111))) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(32.dp).background(Color(0xFF001100), CircleShape), Alignment.Center) { Icon(Icons.Default.Lan, null, tint = Color.Green, modifier = Modifier.size(16.dp)) }
                            Spacer(Modifier.width(12.dp)); Column { Text(text = device.ip, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold); device.hostname?.let { Text(text = it, color = Color.Gray, fontSize = 11.sp) } }
                        }
                    }
                }
            }
        }
    }
}

fun openFile(context: Context, file: File) {}
fun shareFile(context: Context, file: File) {}
fun saveContentToFile(context: Context, content: String, name: String, ext: String) {}
fun captureScreenshot(context: Context, view: android.view.View) {}
fun generateHtmlReport(context: Context, content: String) {}
fun generatePdfReport(context: Context, content: String) {
    val pdf = PdfDocument(); val paint = Paint(); val lines = content.lines(); var pageCount = 1; var current = 0
    while (current < lines.size) {
        val page = pdf.startPage(PageInfo.Builder(595, 842, pageCount).create()); val canvas = page.canvas; var y = 54f; for (i in 0 until 50) { if (current >= lines.size) break; canvas.drawText(lines[current], 40f, y, paint); y += 14f; current++ }; pdf.finishPage(page); pageCount++
    }
    try { val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Report_${System.currentTimeMillis()}.pdf"); pdf.writeTo(FileOutputStream(f)); pdf.close() } catch (e: Exception) {}
}
fun generateDetailedPdfReport(context: Context, terminalText: String, targetIp: String) {}
fun saveJsonReport(context: Context, content: String, ip: String) {}

@Composable
fun StormTab(onAutoRescan: (String) -> Unit, onSaveResults: (String, String) -> Unit) {
    var targetIp by remember { mutableStateOf("") }
    var stormLog by remember { mutableStateOf("> Storm Module Ready.\n") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text(text = "⚡ STORM BREAKER", color = Color.Yellow, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(text = "STRESS TESTING & SERVICE RESILIENCE", color = Color.Gray, fontSize = 10.sp)
        
        Spacer(Modifier.height(16.dp))
        
        Box(Modifier.fillMaxWidth().background(Color(0xFF110000), RoundedCornerShape(8.dp)).border(1.dp, Color.Red, RoundedCornerShape(8.dp)).padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    placeholder = { Text("Target IP", color = Color.DarkGray) },
                    modifier = Modifier.weight(1f),
                    textStyle = TextStyle(color = Color.White, fontFamily = FontFamily.Monospace),
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent)
                )
                Button(
                    onClick = {
                        isRunning = true
                        stormLog += "> Initiating stress test on $targetIp...\n"
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                val module = py.getModule("CamXploit")
                                val sys = py.getModule("sys")
                                sys.put("stdout", TerminalOutputStream { line -> stormLog += line })
                                module.callAttr("test_dos_resilience", targetIp, 80)
                                withContext(Dispatchers.Main) { 
                                    isRunning = false
                                    stormLog += "> Test complete.\n"
                                    onSaveResults(targetIp, stormLog)
                                    onAutoRescan(targetIp)
                                }
                            } catch (e: Exception) {
                                withContext(Dispatchers.Main) { 
                                    stormLog += "[!] Error: ${e.message}\n"
                                    isRunning = false 
                                }
                            }
                        }
                    },
                    enabled = !isRunning && targetIp.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(Color.Red)
                ) {
                    Text("START")
                }
            }
        }
        
        Spacer(Modifier.height(16.dp))
        
        Surface(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF220000), RoundedCornerShape(4.dp)), color = Color.Black) {
            Text(
                text = stormLog,
                color = Color.Red,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(rememberScrollState())
            )
        }
    }
}

fun getLocalSubnet(): String = "192.168.1.0/24"
fun extractCredentials(text: String): Pair<String, String> {
    val userMatch = Regex("""User:\s*(\S+)""").find(text)
    val passMatch = Regex("""Pass:\s*(\S+)""").find(text)
    return (userMatch?.groupValues?.get(1) ?: "admin") to (passMatch?.groupValues?.get(1) ?: "admin")
}
fun buildAuthUrl(u: String, user: String, pass: String): String { if (user.isBlank() || pass.isBlank() || u.contains("@")) return u; return try { if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@") else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@") else u } catch (e: Exception) { u } }

data class DeviceInfo(val ip: String, val hostname: String?, val openPorts: List<Int>)

@Composable
fun SavedCamerasTab(onPlay: (String) -> Unit, onIpSelected: (String) -> Unit) {
    val context = LocalContext.current
    val dao = remember { CameraDatabase.getDatabase(context).cameraDao() }
    val cameras by dao.getAllCameras().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        Text(text = "📑 SAVED AUDIT TARGETS", color = Color.Green, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(16.dp))
        
        if (cameras.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No saved cameras found. Run a scan first.", color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(cameras) { camera ->
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { onIpSelected(camera.ip) },
                        colors = CardDefaults.cardColors(Color(0xFF0A0A0A)),
                        border = BorderStroke(1.dp, Color(0xFF1A1A1A))
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(40.dp).background(if (camera.isOnline) Color(0xFF003300) else Color(0xFF330000), CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Videocam, null, tint = if (camera.isOnline) Color.Green else Color.Red)
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(camera.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(camera.ip, color = Color.Gray, fontSize = 12.sp)
                                Text("Brand: ${camera.brand}", color = Color.DarkGray, fontSize = 10.sp)
                            }
                            IconButton(onClick = { onPlay(camera.remoteUrl ?: camera.streamUrl) }) {
                                Icon(
                                    imageVector = if (camera.remoteUrl != null) Icons.Default.CloudDone else Icons.Default.PlayArrow, 
                                    null, 
                                    tint = if (camera.remoteUrl != null) Color.Magenta else Color.Cyan
                                )
                            }
                            IconButton(onClick = { 
                                scope.launch(Dispatchers.IO) {
                                    val py = Python.getInstance()
                                    val relayUrl = py.getModule("CamXploit").callAttr("start_remote_relay", camera.ip, camera.port).toString()
                                    dao.updateCamera(camera.copy(remoteUrl = relayUrl))
                                    withContext(Dispatchers.Main) {
                                        Toast.makeText(context, "Remote Bridge Active!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Public, null, tint = Color.Yellow.copy(0.8f))
                            }
                            IconButton(onClick = { scope.launch(Dispatchers.IO) { dao.deleteCamera(camera) } }) {
                                Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.7f))
                            }
                        }
                    }
                }
            }
        }
    }
}
