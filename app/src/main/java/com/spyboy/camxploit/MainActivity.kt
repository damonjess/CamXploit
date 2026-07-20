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
import androidx.compose.material.icons.automirrored.filled.*
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
        // Extract assets before starting Python to ensure files are available
        extractNmap(this)
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
    var currentPulse by remember { mutableStateOf("") }
    val appendToConsole: (String) -> Unit = { text -> terminalText += text }
    var consoleIpInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    var viewingFile by remember { mutableStateOf<File?>(null) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var lanScanResults by remember { mutableStateOf<List<LanHost>>(emptyList()) }
    var lanIsScanning by remember { mutableStateOf(false) }
    var lanProgress by remember { mutableStateOf(0f) }
    var lanSubnet by remember { mutableStateOf("") }
    var networkSummary by remember { mutableStateOf<NetworkDiscoveryHelper.NetworkSummary?>(null) }
    var showDisclaimer by remember { mutableStateOf(false) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var publicIntel by remember { mutableStateOf("") }
    var showShodanDialog by remember { mutableStateOf(false) }
    var shodanApiKey by remember { mutableStateOf("") }
    var shodanQuery by remember { mutableStateOf("webcam") }
    var selectedUrl by remember { mutableStateOf("") }
    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }
    var lanNmapMode by remember { mutableStateOf(false) }
    var lanScanOutput by remember { mutableStateOf("") }

    val context = LocalContext.current
    val cameras by CameraDatabase.getDatabase(context).cameraDao()
        .getAllCameras().collectAsState(initial = emptyList())
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()
    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    val startReconScan = { targetIp: String ->
        if (targetIp.isNotEmpty() && !isScanning) {
            consoleIpInput = targetIp; selectedTab = 0; isScanning = true; terminalText = "> Starting Reconnaissance on $targetIp...\n"; publicIntel = ""
            scope.launch(Dispatchers.IO) {
                // Fetch Public Intel (InternetDB)
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
                    
                    // Start output consumer with batching to prevent UI thread saturation
                    val consumerJob = launch(Dispatchers.Main) {
                        while (isActive) {
                            val batch = mutableListOf<String>()
                            outputQueue.drainTo(batch)
                            if (batch.isNotEmpty()) {
                                val newText = terminalText + batch.joinToString("")
                                // Keep only the last 3000 lines to prevent UI lag
                                val lines = newText.lines()
                                terminalText = if (lines.size > 3000) {
                                    lines.takeLast(3000).joinToString("\n")
                                } else {
                                    newText
                                }
                            }
                            delay(150) // Slightly faster updates
                        }
                    }
                    
                    // Redirect Python output
                    sys.put("stdout", pyOutputStream)
                    sys.put("stderr", pyOutputStream)
                    
                    // Run scan
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
                        withTimeout(360_000) { // 6 minutes
                            module.callAttr("main", consoleIpInput)
                        }
                    } finally {
                        heartbeat.cancel()
                        currentPulse = ""
                        
                        // Final flush and drain to ensure no output is lost
                        pyOutputStream.flush()
                        delay(200) // Wait for queue to fill
                        val finalBatch = mutableListOf<String>()
                        outputQueue.drainTo(finalBatch)
                        if (finalBatch.isNotEmpty()) {
                            terminalText += finalBatch.joinToString("")
                        }
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
                } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
                    withContext(Dispatchers.Main) {
                        terminalText += "\n[!] SCAN ABORTED: Operation timed out (exceeded 5-minute limit).\n"
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
        var osintTab by remember { mutableStateOf(0) }
        val shodanFilters = listOf(
            "Hikvision" to "product:\"Hikvision IP Camera\"",
            "Dahua" to "http.title:\"WEB VIEW\" Dahua",
            "Axis" to "product:\"Axis Communications AB\"",
            "Exposed RTSP" to "port:554 has_screenshot:true",
            "Blue Iris" to "title:\"ui3 -\"",
            "Mobotix" to "http.title:MOBOTIX"
        )
        val googleDorks = listOf(
            "Hikvision Login" to "intitle:\"Hikvision\" inurl:/doc/page/login.asp",
            "Axis View" to "intitle:\"Live View / - AXIS\"",
            "Mobotix PDA" to "intitle:MOBOTIX inurl:/pda/index.html",
            "Public Webcams" to "inurl:/view/view.shtml",
            "ViewerFrame" to "inurl:\"ViewerFrame?Mode=\""
        )
        val currentDorks = if (osintTab == 0) shodanFilters else googleDorks

        AlertDialog(
            onDismissRequest = { showShodanDialog = false },
            title = { Text("GLOBAL OSINT RECON", color = Color.Magenta, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    TabRow(
                        selectedTabIndex = osintTab,
                        containerColor = Color.Transparent,
                        contentColor = Color.Magenta,
                        indicator = { tabPositions ->
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[osintTab]),
                                color = Color.Magenta
                            )
                        }
                    ) {
                        Tab(selected = osintTab == 0, onClick = { osintTab = 0 }) {
                            Text("SHODAN API", modifier = Modifier.padding(8.dp), fontSize = 10.sp)
                        }
                        Tab(selected = osintTab == 1, onClick = { osintTab = 1 }) {
                            Text("WEB SEARCH", modifier = Modifier.padding(8.dp), fontSize = 10.sp)
                        }
                    }
                    
                    Spacer(Modifier.height(16.dp))

                    if (osintTab == 0) {
                        Text("Search for exposed cameras globally via Shodan API.", color = Color.Gray, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = shodanApiKey,
                            onValueChange = { shodanApiKey = it },
                            label = { Text("Shodan API Key") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = shodanQuery,
                            onValueChange = { shodanQuery = it },
                            label = { Text("Search Query") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White),
                            singleLine = true
                        )
                        Spacer(Modifier.height(12.dp))
                        Text("QUICK FILTERS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.height(140.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(shodanFilters.size) { index ->
                                Button(
                                    onClick = { shodanQuery = shodanFilters[index].second },
                                    colors = ButtonDefaults.buttonColors(Color(0xFF111111)),
                                    border = BorderStroke(1.dp, Color.Magenta.copy(0.3f)),
                                    contentPadding = PaddingValues(4.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(shodanFilters[index].first, fontSize = 9.sp, color = Color.LightGray)
                                }
                            }
                        }
                    } else {
                        Text("Perform API-less recon using search engines.", color = Color.Gray, fontSize = 11.sp)
                        Spacer(Modifier.height(12.dp))
                        
                        Text("QUICK DORKS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier.height(140.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(googleDorks.size) { index ->
                                Button(
                                    onClick = { shodanQuery = googleDorks[index].second },
                                    colors = ButtonDefaults.buttonColors(Color(0xFF111111)),
                                    border = BorderStroke(1.dp, Color.Magenta.copy(0.3f)),
                                    contentPadding = PaddingValues(4.dp),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(googleDorks[index].first, fontSize = 9.sp, color = Color.LightGray)
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = shodanQuery,
                            onValueChange = { shodanQuery = it },
                            label = { Text("Current Query") },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(color = Color.White, fontSize = 12.sp)
                        )
                        
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { openBrowserSearch(context, "GOOGLE", shodanQuery) }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFF1B5E20)), shape = RoundedCornerShape(4.dp)) {
                                Text("GOOGLE", fontSize = 9.sp)
                            }
                            Button(onClick = { 
                                selectedUrl = "https://search.censys.io/search?q=${Uri.encode(shodanQuery)}"
                                selectedTab = 3
                                showShodanDialog = false
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFF311B92)), shape = RoundedCornerShape(4.dp)) {
                                Text("CENSYS", fontSize = 9.sp)
                            }
                            Button(onClick = { 
                                selectedUrl = "https://www.zoomeye.org/searchResult?q=${Uri.encode(shodanQuery)}"
                                selectedTab = 3
                                showShodanDialog = false
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFFE65100)), shape = RoundedCornerShape(4.dp)) {
                                Text("ZOOMEYE", fontSize = 9.sp)
                            }
                            Button(onClick = { 
                                selectedUrl = "https://www.shodan.io/search?query=${Uri.encode(shodanQuery)}"
                                selectedTab = 3
                                showShodanDialog = false
                            }, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFF003366)), shape = RoundedCornerShape(4.dp)) {
                                Text("SHODAN", fontSize = 9.sp)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (osintTab == 0) {
                    Button(onClick = {
                        showShodanDialog = false
                        if (shodanApiKey.isNotBlank()) {
                            terminalText = "> Initiating Global Shodan Search...\n"
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val py = Python.getInstance()
                                    val module = py.getModule("CamXploit")
                                    val sys = py.getModule("sys")
                                    sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } })
                                    module.callAttr("shodan_search", shodanApiKey, shodanQuery)
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { terminalText += "\n[!] Shodan Error: ${e.message}" }
                                }
                            }
                        } else Toast.makeText(context, "API Key Required", Toast.LENGTH_SHORT).show()
                    }) { Text("RUN API SCAN") }
                }
            },
            dismissButton = {
                TextButton(onClick = { showShodanDialog = false }) { Text("CLOSE") }
            },
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
                    Icons.Default.Shield to "SENTINEL"
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
                0 -> ConsoleTab(consoleIpInput, { consoleIpInput = it }, terminalText + currentPulse, { terminalText = "> Console Reset.\n" }, isScanning, scrollState, { startReconScan(consoleIpInput) }, { url, _ -> selectedUrl = buildAuthUrl(url, extractCredentials(terminalText).first, extractCredentials(terminalText).second); selectedTab = 3 })
                1 -> IntelTab(
                    consoleIpInput = consoleIpInput,
                    terminalText = terminalText,
                    publicIntel = publicIntel,
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
                        val finalUrl = if (url.contains("shodan.io") || url.contains("censys.io") || url.contains("zoomeye.org")) {
                            url
                        } else {
                            buildAuthUrl(url, extractCredentials(terminalText).first, extractCredentials(terminalText).second)
                        }
                        selectedUrl = finalUrl
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
                        
                        // Try to find vendor from terminal text if available
                        val vendorMatch = Regex("""Device:\s*([^\n\r]+)""").find(terminalText)?.groupValues?.get(1)
                        
                        scope.launch {
                            terminalText += "🔍 Probing endpoints on $targetHost:$targetPort ...\n"
                            val scanner = CameraScanner()
                            scanner.scanEndpoints(
                                host = targetHost,
                                port = targetPort,
                                vendor = vendorMatch,
                                onResult = { result ->
                                    val brandTag = if (result.brand != null) "[${result.brand}] " else ""
                                    terminalText += "  🎯 Found $brandTag${result.type}: ${result.url} (HTTP ${result.httpCode})\n"
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
                    networkSummary = networkSummary,
                    nmapMode = lanNmapMode,
                    onNmapModeChange = { lanNmapMode = it },
                    nmapOutput = lanScanOutput,
                    onScanStart = {
                        lanIsScanning = true
                        lanScanResults = emptyList()
                        lanProgress = 0f
                        lanScanOutput = "> Initiating scan...\n"
                        scope.launch(Dispatchers.IO) {
                            val discoveryHelper = NetworkDiscoveryHelper(context)
                            val summary = discoveryHelper.getNetworkSummary()
                            withContext(Dispatchers.Main) { networkSummary = summary }
                            
                            launch {
                                val publicIp = discoveryHelper.getPublicIp()
                                withContext(Dispatchers.Main) {
                                    networkSummary = networkSummary?.copy(publicIp = publicIp)
                                }
                            }

                            val subnet = summary.localIp.substringBeforeLast(".")
                            withContext(Dispatchers.Main) { lanSubnet = subnet }
                            val scanner = LanScanner(context)

                            if (lanNmapMode) {
                                subnetScan(
                                    context = context,
                                    subnet = "$subnet.0/24",
                                    onOutput = { line ->
                                        if (line.contains("Nmap scan report")) {
                                            val ip = line.substringAfter("for ").trim().split(" ").first()
                                            if (lanScanResults.none { it.ip == ip }) {
                                                val arp = scanner.readArpTable()
                                                val mac = arp[ip] ?: "Unknown"
                                                val vendor = scanner.getVendor(mac)
                                                val deviceType = scanner.guessDeviceType(vendor, "Unknown", emptyList())
                                                lanScanResults = lanScanResults + LanHost(ip = ip, mac = mac, vendor = vendor, deviceType = deviceType, isYourDevice = ip == summary.localIp)
                                            }
                                        }
                                        lanScanOutput += "$line\n"
                                    },
                                    onComplete = {
                                        lanIsScanning = false
                                    }
                                )
                            } else {
                                val arp = scanner.readArpTable()
                                coroutineScope {
                                    (1..254).map { i ->
                                        async {
                                            val ip = "$subnet.$i"
                                            try {
                                                if (InetAddress.getByName(ip).isReachable(300)) {
                                                    val mac = arp[ip] ?: "Unknown"
                                                    val vendor = scanner.getVendor(mac)
                                                    
                                                    // Quick port check for camera indicators
                                                    val openPorts = mutableListOf<Int>()
                                                    listOf(80, 443, 554, 8000, 37777, 34567, 8080).forEach { port ->
                                                        try {
                                                            java.net.Socket().use { s ->
                                                                s.connect(java.net.InetSocketAddress(ip, port), 150)
                                                                openPorts.add(port)
                                                            }
                                                        } catch (_: Exception) { }
                                                    }
                                                    
                                                    val isCam = openPorts.any { it in listOf(554, 8000, 37777, 34567) } || 
                                                               vendor.lowercase().contains("camera") || 
                                                               vendor.lowercase().contains("hikvision") || 
                                                               vendor.lowercase().contains("dahua") || 
                                                               vendor.lowercase().contains("axis") ||
                                                               vendor.lowercase().contains("reolink")
                                                               
                                                    val deviceType = scanner.guessDeviceType(vendor, "Unknown", openPorts)

                                                    withContext(Dispatchers.Main) {
                                                        if (lanScanResults.none { it.ip == ip }) {
                                                            val host = LanHost(
                                                                ip = ip, 
                                                                mac = mac, 
                                                                vendor = vendor, 
                                                                isCamera = isCam,
                                                                deviceType = deviceType,
                                                                isYourDevice = ip == summary.localIp,
                                                                openPorts = openPorts
                                                            )
                                                            lanScanResults = lanScanResults + host
                                                            
                                                            if (isCam) {
                                                                scope.launch(Dispatchers.IO) {
                                                                    val camScanner = CameraScanner()
                                                                    camScanner.scanEndpoints(
                                                                        host = ip, 
                                                                        port = if (openPorts.contains(80)) 80 else if (openPorts.contains(8080)) 8080 else 80,
                                                                        vendor = vendor,
                                                                        onResult = { result ->
                                                                            if (result.type == "MJPEG_STREAM" || result.type == "SNAPSHOT") {
                                                                                scope.launch(Dispatchers.Main) {
                                                                                    lanScanResults = lanScanResults.map { 
                                                                                        if (it.ip == ip) it.copy(streamUrl = result.url) else it
                                                                                    }
                                                                                }
                                                                            }
                                                                        }, 
                                                                        onDone = {}
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            } catch (e: Exception) {
                                            }
                                            withContext(Dispatchers.Main) { lanProgress = i / 254f }
                                        }
                                    }.awaitAll()
                                }
                                withContext(Dispatchers.Main) { lanIsScanning = false }
                            }
                        }
                    },
                    onTabSwitch = { selectedTab = it },
                    onIpSelected = { consoleIpInput = it; selectedTab = 0 },
                    onViewStream = { selectedUrl = it; selectedTab = 3 })
                5 -> StormTab(onAutoRescan = { startReconScan(it); Toast.makeText(context, "Running post-stress scan...", Toast.LENGTH_SHORT).show() }, onSaveResults = { ip, out -> saveContentToFile(context, out, "[STORM] ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())} - $ip", "txt"); Toast.makeText(context, "Saved to Archive", Toast.LENGTH_SHORT).show() })
                6 -> SavedCamerasTab({ selectedUrl = it; selectedTab = 3 }, { consoleIpInput = it; selectedTab = 0 })
                7 -> SentinelTab(savedCameras = cameras)
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
        LaunchedEffect(scrollState.maxValue) { 
            // Automatically scroll to bottom if we are already near the bottom
            // Reacting to maxValue ensures we scroll after the new content has been measured
            if (scrollState.value > scrollState.maxValue - 1000 || terminalText.length < 1000) {
                scrollState.animateScrollTo(scrollState.maxValue)
            }
        }
        val detectedLinks = remember(terminalText) {
            if (terminalText.contains("===LINKS_START===")) {
                terminalText.substringAfter("===LINKS_START===")
                    .substringBefore("===LINKS_END===")
                    .lines()
                    .filter { it.contains("|") }
            } else {
                emptyList()
            }
        }
        
        if (detectedLinks.isNotEmpty()) {
            Text(text = "🎯 Detected Links", color = Color.Cyan, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 8.dp))
            Column(Modifier.fillMaxWidth().height(350.dp).verticalScroll(rememberScrollState())) { 
                detectedLinks.forEach { line -> 
                    val p = line.split("|")
                    if (p.size >= 2) Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)), colors = CardDefaults.cardColors(Color.Transparent)) { 
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) { 
                            Column(Modifier.weight(1f)) { 
                                Text(text = p[0].trim(), color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                Text(text = p[1].trim().take(50), color = Color.Gray, fontSize = 11.sp) 
                            }
                            Button(onClick = { onStreamSelect(p[1].trim(), p[0].trim()) }, colors = ButtonDefaults.buttonColors(Color.Green)) { 
                                Text(text = "LIVE", color = Color.Black) 
                            } 
                        } 
                    } 
                } 
            }
        }
    }
}

@Composable
fun IntelTab(consoleIpInput: String, terminalText: String, publicIntel: String, onTerminalUpdate: (String) -> Unit, onManualSnapshot: () -> Unit, onStreamSelect: (String) -> Unit, onDiscoverOnvif: () -> Unit, onProbeEndpoints: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(text = "🛡️ INTELLIGENCE GATHERING", color = Color.Cyan, fontSize = 18.sp, fontWeight = FontWeight.Black)
        Text(text = "DEEP SCAN & VULNERABILITY ANALYSIS", color = Color.Gray, fontSize = 10.sp)
        
        Spacer(Modifier.height(16.dp))
        
        if (publicIntel.isNotBlank()) {
            val json = remember(publicIntel) { 
                try { JSONObject(publicIntel) } catch (e: Exception) { null }
            }
            if (json != null) {
                Card(
                    Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(Color(0xFF0A0A0A)),
                    border = BorderStroke(1.dp, Color.Magenta.copy(0.4f))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Public, null, tint = Color.Magenta, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("PUBLIC HOST INTEL (NO LOGIN)", color = Color.Magenta, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(8.dp))
                        
                        val hostnames = json.optJSONArray("hostnames")
                        if (hostnames != null && hostnames.length() > 0) {
                            Text("HOSTNAMES", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(List(hostnames.length()) { hostnames.getString(it) }.joinToString(", "), color = Color.White, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                        }
                        
                        val ports = json.optJSONArray("ports")
                        if (ports != null && ports.length() > 0) {
                            Text("OPEN PORTS (API)", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            Text(List(ports.length()) { ports.getInt(it).toString() }.joinToString(", "), color = Color.Green, fontSize = 11.sp)
                            Spacer(Modifier.height(8.dp))
                        }

                        val tags = json.optJSONArray("tags")
                        if (tags != null && tags.length() > 0) {
                            Text("TAGS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                List(tags.length()) { tags.getString(it) }.forEach { tag ->
                                    Surface(color = Color.DarkGray, shape = RoundedCornerShape(4.dp)) {
                                        Text(tag, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp), color = Color.Cyan, fontSize = 9.sp)
                                    }
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }

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
        
        IntelSection("OSINT & INTELLIGENCE", listOf("View Shodan Report", "Censys Host Discovery", "ZoomEye IoT Search", "IP Geolocation Map"), Color.Magenta, Icons.Default.Public) {
            val url = when(it) {
                "View Shodan Report" -> "https://www.shodan.io/host/$consoleIpInput"
                "Censys Host Discovery" -> "https://censys.io/ipv4/$consoleIpInput"
                "ZoomEye IoT Search" -> "https://www.zoomeye.org/searchResult?q=$consoleIpInput"
                "IP Geolocation Map" -> "https://viewdns.info/iplocation/?ip=$consoleIpInput"
                else -> ""
            }
            if (url.isNotEmpty()) {
                if (it == "View Shodan Report") {
                    onStreamSelect(url)
                } else {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        
        IntelSection("SYSTEM AUDIT", listOf("Firmware Version Check", "Hardware ID Recovery", "Service Banner Grabbing", "Generate Google Dorks"), Color.Yellow, Icons.Default.Dns) {
            onTerminalUpdate("> Running Audit: $it...\n")
            if (it.contains("Google Dorks")) {
                onTerminalUpdate("  [🔍] Suggestions appearing in Console based on detected brand.\n")
            }
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
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { 
            Row(verticalAlignment = Alignment.CenterVertically) { 
                Text(text = "STREAM VIEWER", color = Color.Magenta, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                if (isRecording) { 
                    Spacer(Modifier.width(12.dp)); Box(Modifier.size(8.dp).background(Color.Red, CircleShape)); Spacer(Modifier.width(4.dp)); Text(text = "REC ${String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60)}", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold) 
                } 
            }
            Row {
                if (!selectedUrl.startsWith("rtsp") && selectedUrl.isNotEmpty()) {
                    IconButton(onClick = { currentWebView?.goBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.LightGray) }
                    IconButton(onClick = { currentWebView?.goForward() }) { Icon(Icons.AutoMirrored.Filled.ArrowForward, null, tint = Color.LightGray) }
                    IconButton(onClick = { currentWebView?.reload() }) { Icon(Icons.Default.Refresh, null, tint = Color.LightGray) }
                }
                IconButton(onClick = { isGridView = !isGridView }) { Icon(if (isGridView) Icons.Default.ViewStream else Icons.Default.GridView, null, tint = Color.Magenta) } 
            }
        }
        if (!isGridView && selectedUrl.isNotEmpty()) {
            val auth = buildAuthUrl(selectedUrl, extractCredentials(terminalText).first, extractCredentials(terminalText).second); Column(Modifier.weight(1f)) {
                if (selectedUrl.startsWith("rtsp")) key(selectedUrl) { AndroidView(factory = { ctx -> ExoPlayer.Builder(ctx).build().apply { setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(auth))); prepare(); playWhenReady = true; currentExoPlayer = this }.let { PlayerView(ctx).apply { player = it; useController = true; currentTextureView = TextureView(ctx); try { this.javaClass.getMethod("setVideoSurfaceView", android.view.View::class.java).invoke(this, currentTextureView) } catch (e: Exception) {} } } }, Modifier.fillMaxWidth().weight(1f)) }
                else key(selectedUrl) { AndroidView(factory = { ctx -> WebView(ctx).apply { 
                    currentWebView = this
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = true
                        displayZoomControls = false
                    }
                    webViewClient = object : WebViewClient() { 
                        override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) { h?.proceed() } 
                    }
                    loadUrl(auth) 
                } }, Modifier.fillMaxSize().weight(1f)) }
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
    scanResults: List<LanHost>,
    isScanning: Boolean,
    progress: Float,
    networkSummary: NetworkDiscoveryHelper.NetworkSummary?,
    nmapMode: Boolean,
    onNmapModeChange: (Boolean) -> Unit,
    nmapOutput: String,
    onScanStart: () -> Unit,
    onTabSwitch: (Int) -> Unit,
    onIpSelected: (String) -> Unit,
    onViewStream: (String) -> Unit
) {
    Column(Modifier.fillMaxSize()) {
        Text(text = "NETWORK AUDIT", color = Color.Green, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text(text = "DEVICE DISCOVERY & FINGERPRINTING", color = Color.Gray, fontSize = 10.sp)
        
        Spacer(Modifier.height(16.dp))

        // Network Summary Header
        networkSummary?.let {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(Color(0xFF0A0A0A)),
                border = BorderStroke(1.dp, Color(0xFF1A1A1A))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Wifi, null, tint = Color.Green, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(text = it.ssid, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("GATEWAY", color = Color.Gray, fontSize = 9.sp)
                            Text(it.gateway, color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("LOCAL IP", color = Color.Gray, fontSize = 9.sp)
                            Text(it.localIp, color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            Text("PUBLIC IP", color = Color.Gray, fontSize = 9.sp)
                            Text(it.publicIp, color = Color.Cyan, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("DNS", color = Color.Gray, fontSize = 9.sp)
                            Text(it.dns.split(",").first(), color = Color.LightGray, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "DEVICES FOUND: ${scanResults.size}",
                color = Color.Green,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onScanStart,
                enabled = !isScanning,
                colors = ButtonDefaults.buttonColors(Color(0xFF003300)),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.height(32.dp),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text(if (isScanning) "SCANNING..." else "SCAN", color = Color.Green, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(12.dp))

        if (isScanning && !nmapMode) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().height(2.dp),
                color = Color.Green,
                trackColor = Color(0xFF111111)
            )
        }

        Spacer(Modifier.height(8.dp))

        if (nmapMode && nmapOutput.isNotEmpty()) {
            val lanNmapScrollState = rememberScrollState()
            LaunchedEffect(lanNmapScrollState.maxValue) {
                if (lanNmapScrollState.value > lanNmapScrollState.maxValue - 800 || nmapOutput.length < 1000) {
                    lanNmapScrollState.animateScrollTo(lanNmapScrollState.maxValue)
                }
            }
            Box(Modifier.fillMaxWidth().weight(1f).background(Color(0xFF050505)).border(1.dp, Color(0xFF111111)).padding(8.dp)) {
                Text(text = nmapOutput, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp, modifier = Modifier.verticalScroll(lanNmapScrollState))
            }
        } else {
            if (scanResults.isEmpty() && !isScanning) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    Text("No devices discovered. Start a scan.", color = Color.DarkGray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(Modifier.weight(1f)) {
                    items(scanResults.sortedBy { it.ip }) { host ->
                        LanHostCard(host = host, onClick = { 
                            if (host.isCamera && host.streamUrl != null) {
                                onViewStream(host.streamUrl)
                                onTabSwitch(3)
                            } else {
                                onIpSelected(host.ip)
                                onTabSwitch(0)
                            }
                        })
                    }
                }
            }
        }
    }
}

@Composable
fun LanHostCard(host: LanHost, onClick: () -> Unit) {
    val icon = when (host.deviceType) {
        "Phone" -> Icons.Default.Smartphone
        "Computer" -> Icons.Default.Laptop
        "Camera" -> Icons.Default.Videocam
        "Router" -> Icons.Default.Router
        "Smart Speaker" -> Icons.Default.Speaker
        "TV" -> Icons.Default.Tv
        "Storage" -> Icons.Default.Storage
        "Printer" -> Icons.Default.Print
        else -> Icons.Default.Devices
    }

    val iconColor = if (host.isCamera) Color.Cyan else if (host.isYourDevice) Color.Green else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)),
        border = BorderStroke(1.dp, if (host.isYourDevice) Color.Green.copy(0.3f) else Color(0xFF111111)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(40.dp).background(iconColor.copy(0.1f), CircleShape),
                Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = host.ip,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    if (host.isYourDevice) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "YOU",
                            color = Color.Green,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.background(Color.Green.copy(0.1f), RoundedCornerShape(2.dp)).padding(horizontal = 4.dp)
                        )
                    }
                }
                
                Text(
                    text = if (host.vendor != "Unknown Device") host.vendor ?: "Unknown Vendor" else host.hostname ?: "Unknown Device",
                    color = Color.LightGray,
                    fontSize = 11.sp
                )
                
                if (host.isCamera) {
                    Text(
                        text = "📷 CAMERA DETECTED",
                        color = Color.Cyan,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }
            
            Column(horizontalAlignment = Alignment.End) {
                if (host.isCamera && !host.streamUrl.isNullOrEmpty()) {
                    Button(
                        onClick = onClick,
                        colors = ButtonDefaults.buttonColors(Color(0xFF003333)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(24.dp),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text("LIVE", color = Color.Cyan, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = host.mac ?: "",
                    color = Color.DarkGray,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

fun openFile(context: Context, file: File) {
    try {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (e: Exception) {
        Toast.makeText(context, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun openBrowserSearch(context: Context, engine: String, query: String) {
    val url = when (engine) {
        "GOOGLE" -> "https://www.google.com/search?q=${Uri.encode(query)}"
        "SHODAN" -> "https://www.shodan.io/search?query=${Uri.encode(query)}"
        "CENSYS" -> "https://search.censys.io/search?q=${Uri.encode(query)}"
        else -> ""
    }
    try {
        if (url.isNotEmpty()) {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not open browser", Toast.LENGTH_SHORT).show()
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
        context.startActivity(Intent.createChooser(intent, "Share Report"))
    } catch (e: Exception) {
        Toast.makeText(context, "Error sharing file: ${e.message}", Toast.LENGTH_SHORT).show()
    }
}

fun saveContentToFile(context: Context, content: String, name: String, ext: String) {
    try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "${name}_${System.currentTimeMillis()}.$ext")
        file.writeText(content)
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

fun captureScreenshot(context: Context, view: android.view.View) {
    val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    view.draw(canvas)
    if (saveBitmapToGallery(context, bitmap)) {
        Toast.makeText(context, "Screenshot saved to gallery", Toast.LENGTH_SHORT).show()
    } else {
        Toast.makeText(context, "Failed to save screenshot", Toast.LENGTH_SHORT).show()
    }
}

fun generateHtmlReport(context: Context, content: String) {
    val htmlContent = """
        <!DOCTYPE html>
        <html>
        <head>
            <style>
                body { background-color: #000; color: #0f0; font-family: monospace; padding: 20px; }
                h1 { color: #00ff00; border-bottom: 1px solid #333; }
                .line { margin-bottom: 4px; white-space: pre-wrap; }
            </style>
        </head>
        <body>
            <h1>CAMXPLOIT AUDIT REPORT</h1>
            <p>Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}</p>
            <hr>
            ${content.lines().joinToString("") { "<div class='line'>$it</div>" }}
        </body>
        </html>
    """.trimIndent()
    saveContentToFile(context, htmlContent, "Audit_Report", "html")
}

fun generatePdfReport(context: Context, content: String) {
    val pdf = PdfDocument(); val paint = Paint(); paint.textSize = 10f; val lines = content.lines(); var pageCount = 1; var current = 0
    while (current < lines.size) {
        val page = pdf.startPage(PageInfo.Builder(595, 842, pageCount).create()); val canvas = page.canvas; var y = 54f; for (i in 0 until 50) { if (current >= lines.size) break; canvas.drawText(lines[current], 40f, y, paint); y += 14f; current++ }; pdf.finishPage(page); pageCount++
    }
    try { 
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val f = File(dir, "Report_${System.currentTimeMillis()}.pdf"); 
        pdf.writeTo(FileOutputStream(f)); 
        Toast.makeText(context, "Report saved to Archive", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        e.printStackTrace()
    } finally {
        pdf.close()
    }
}

fun generateDetailedPdfReport(context: Context, terminalText: String, targetIp: String) {
    val pdf = PdfDocument()
    val paint = Paint()
    val titlePaint = Paint().apply {
        textSize = 18f
        isFakeBoldText = true
        color = android.graphics.Color.BLACK
    }
    val textPaint = Paint().apply {
        textSize = 10f
    }
    
    val lines = terminalText.lines()
    var pageCount = 1
    var currentLine = 0
    val linesPerPage = 55
    
    while (currentLine < lines.size) {
        val pageInfo = PageInfo.Builder(595, 842, pageCount).create()
        val page = pdf.startPage(pageInfo)
        val canvas = page.canvas
        
        var y = 50f
        if (pageCount == 1) {
            canvas.drawText("CAMXPLOIT AUDIT REPORT", 50f, y, titlePaint)
            y += 25f
            canvas.drawText("Target: $targetIp", 50f, y, textPaint)
            y += 15f
            canvas.drawText("Generated: ${SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())}", 50f, y, textPaint)
            y += 30f
        }
        
        for (i in 0 until linesPerPage) {
            if (currentLine >= lines.size) break
            canvas.drawText(lines[currentLine], 50f, y, textPaint)
            y += 12f
            currentLine++
        }
        
        pdf.finishPage(page)
        pageCount++
    }
    
    try {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val file = File(dir, "Detailed_Report_${targetIp}_${System.currentTimeMillis()}.pdf")
        pdf.writeTo(FileOutputStream(file))
        Toast.makeText(context, "PDF Report generated in Archive", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "PDF Error: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        pdf.close()
    }
}

fun saveJsonReport(context: Context, content: String, ip: String) {
    try {
        val json = JSONObject().apply {
            put("target", ip)
            put("timestamp", System.currentTimeMillis())
            put("log", content)
        }
        saveContentToFile(context, json.toString(4), "Report_$ip", "json")
    } catch (e: Exception) {
        e.printStackTrace()
    }
}

@Composable
fun StormTab(onAutoRescan: (String) -> Unit, onSaveResults: (String, String) -> Unit) {
    var targetIp by remember { mutableStateOf("") }
    var stormLog by remember { mutableStateOf("> Storm Module Ready.\n") }
    var isRunning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    LaunchedEffect(scrollState.maxValue) {
        if (scrollState.value > scrollState.maxValue - 1000 || stormLog.length < 1000) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

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
                modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(scrollState)
            )
        }
    }
}


fun extractCredentials(text: String): Pair<String, String> {
    val userMatch = Regex("""User:\s*(\S+)""").find(text)
    val passMatch = Regex("""Pass:\s*(\S+)""").find(text)
    return (userMatch?.groupValues?.get(1) ?: "admin") to (passMatch?.groupValues?.get(1) ?: "admin")
}
fun buildAuthUrl(u: String, user: String, pass: String): String { if (user.isBlank() || pass.isBlank() || u.contains("@")) return u; return try { if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@") else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@") else u } catch (e: Exception) { u } }

data class LanHost(
    val ip: String,
    val mac: String? = null,
    val hostname: String? = null,
    val vendor: String? = null,
    val deviceType: String = "Unknown",
    val isYourDevice: Boolean = false,
    val openPorts: List<Int> = emptyList(),
    val isCamera: Boolean = false,
    val streamUrl: String? = null,
    val lastSeen: Long = System.currentTimeMillis()
)

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

@Composable
fun SentinelTab(savedCameras: List<SavedCamera>) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember { CameraDatabase.getDatabase(context).sentinelDao() }
    val detections by dao.getAll().collectAsState(initial = emptyList())

    var isRunning by remember { mutableStateOf(false) }
    var selectedCamera by remember { mutableStateOf<SavedCamera?>(null) }
    var statusText by remember { mutableStateOf("Select a camera to begin monitoring") }
    var frameCount by remember { mutableIntStateOf(0) }
    var monitorJob by remember { mutableStateOf<Job?>(null) }
    val processor = remember { SentinelProcessor(context) }

    DisposableEffect(Unit) {
        processor.load()
        onDispose { processor.close(); monitorJob?.cancel() }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Text("🛡️ SENTINEL", color = Color(0xFF00FFFF), fontSize = 20.sp, fontWeight = FontWeight.Black)
        Text("AI THREAT MONITOR", color = Color.Gray, fontSize = 10.sp)

        Spacer(Modifier.height(12.dp))

        // Camera selector
        if (savedCameras.isEmpty()) {
            Text("No saved cameras. Save a camera from the CONSOLE tab first.",
                color = Color.Gray, fontSize = 12.sp)
        } else {
            Text("SELECT TARGET", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 140.dp)) {
                items(savedCameras) { cam ->
                    val isSelected = selectedCamera?.ip == cam.ip
                    Card(
                        Modifier.fillMaxWidth().padding(vertical = 3.dp)
                            .clickable { if (!isRunning) selectedCamera = cam },
                        colors = CardDefaults.cardColors(
                            if (isSelected) Color(0xFF002200) else Color(0xFF0A0A0A)
                        ),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF00FF00) else Color(0xFF1A1A1A))
                    ) {
                        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, null,
                                tint = if (isSelected) Color.Green else Color.Gray,
                                modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(cam.nickname, color = Color.White, fontSize = 13.sp)
                                Text(cam.ip, color = Color.Gray, fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // Status bar
        Box(
            Modifier.fillMaxWidth()
                .background(Color(0xFF050505), RoundedCornerShape(4.dp))
                .border(1.dp, Color(0xFF1A3A1A), RoundedCornerShape(4.dp))
                .padding(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isRunning) {
                    Box(Modifier.size(8.dp).background(Color.Green, CircleShape))
                    Spacer(Modifier.width(8.dp))
                }
                Text(statusText, color = if (isRunning) Color.Green else Color.Gray,
                    fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                if (isRunning) {
                    Spacer(Modifier.weight(1f))
                    Text("${frameCount}f", color = Color.DarkGray, fontSize = 10.sp)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        // Start / Stop button
        Button(
            onClick = {
                if (isRunning) {
                    monitorJob?.cancel()
                    isRunning = false
                    statusText = "Monitoring stopped"
                } else {
                    val cam = selectedCamera ?: return@Button
                    val streamUrl = cam.streamUrl.ifBlank {
                        "http://${cam.ip}/videostream.cgi"
                    }
                    isRunning = true
                    frameCount = 0
                    statusText = "Connecting to ${cam.ip}..."

                    monitorJob = scope.launch {
                        val grabber = MjpegFrameGrabber(streamUrl)
                        grabber.stream(
                            onFrame = { bitmap ->
                                frameCount++
                                statusText = "Analysing frame $frameCount..."
                                val results = processor.detect(bitmap)
                                results.filter { it.isThreat }.forEach { result ->
                                    scope.launch {
                                        dao.insert(SentinelDetection(
                                            cameraIp   = cam.ip,
                                            label      = result.label,
                                            confidence = result.confidence,
                                            frameIndex = frameCount
                                        ))
                                    }
                                    statusText = "⚠️ ${result.label.uppercase()} detected (${(result.confidence * 100).toInt()}%)"
                                }
                            },
                            onError = { err ->
                                statusText = "❌ $err"
                                isRunning = false
                            }
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                if (isRunning) Color(0xFF330000) else Color(0xFF003300)
            ),
            border = BorderStroke(1.dp, if (isRunning) Color.Red else Color.Green),
            shape = RoundedCornerShape(4.dp),
            enabled = selectedCamera != null || isRunning
        ) {
            Icon(
                if (isRunning) Icons.Default.Stop else Icons.Default.Radar,
                null,
                tint = if (isRunning) Color.Red else Color.Green
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isRunning) "STOP SENTINEL" else "ACTIVATE SENTINEL",
                color = if (isRunning) Color.Red else Color.Green,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(12.dp))

        // Detection log
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("THREAT LOG", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            if (detections.isNotEmpty()) {
                TextButton(onClick = { scope.launch { dao.clearAll() } }) {
                    Text("CLEAR", color = Color.DarkGray, fontSize = 10.sp)
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            if (detections.isEmpty()) {
                item {
                    Text("No threats logged yet.", color = Color.DarkGray,
                        fontSize = 11.sp, modifier = Modifier.padding(8.dp))
                }
            }
            items(detections) { detection ->
                val time = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                    .format(java.util.Date(detection.timestamp))
                Card(
                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                    colors = CardDefaults.cardColors(Color(0xFF0A0000)),
                    border = BorderStroke(1.dp, Color(0xFF2A0000))
                ) {
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠️", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(detection.label.uppercase(),
                                color = Color(0xFFFF4444),
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp)
                            Text("${detection.cameraIp}  •  frame ${detection.frameIndex}",
                                color = Color.DarkGray, fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${(detection.confidence * 100).toInt()}%",
                                color = Color(0xFFFF4444), fontWeight = FontWeight.Bold)
                            Text(time, color = Color.DarkGray, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}
