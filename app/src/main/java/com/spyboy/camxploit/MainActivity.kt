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

@OptIn(UnstableApi::class)
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
    terminalText: String,
    onTabSwitch: (Int) -> Unit,
    onIpSelected: (String) -> Unit
) {
    var nmapIp by remember { mutableStateOf("") }
    var scanType by remember { mutableStateOf("QUICK") }
    var output by remember { mutableStateOf("> Nmap Module Ready.\n") }
    var isScanning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    LaunchedEffect(Unit) {
        val lastIp = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""").find(terminalText)?.value
        if (lastIp != null) nmapIp = lastIp
    }

    LaunchedEffect(scanType) {
        if (scanType == "SUBNET" && !nmapIp.contains("/")) {
            nmapIp = getLocalSubnet()
        }
    }

    Column(Modifier.fillMaxSize()) {
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
        Text("SCAN TYPE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), Arrangement.spacedBy(8.dp)) {
            listOf("QUICK", "SUBNET", "CAMERA", "AGGRESSIVE", "VULN").forEach { type ->
                Button(
                    onClick = { scanType = type },
                    colors = ButtonDefaults.buttonColors(if (scanType == type) Color.Green else Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(4.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(type, color = if (scanType == type) Color.Black else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (nmapIp.isNotBlank()) {
                    isScanning = true
                    output = "> Initializing $scanType scan on $nmapIp...\n"
                    val onOutput: (String) -> Unit = { line -> output += "$line\n" }
                    val onComplete: () -> Unit = { isScanning = false }
                    when (scanType) {
                        "QUICK" -> quickScan(context, nmapIp, onOutput, onComplete)
                        "SUBNET" -> subnetScan(context, nmapIp, onOutput, onComplete)
                        "CAMERA" -> cameraScan(context, nmapIp, onOutput, onComplete)
                        "AGGRESSIVE" -> aggressiveScan(context, nmapIp, onOutput, onComplete)
                        "VULN" -> vulnScan(context, nmapIp, onOutput, onComplete)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().height(50.dp),
            enabled = !isScanning,
            colors = ButtonDefaults.buttonColors(Color.Green),
            shape = RoundedCornerShape(8.dp)
        ) {
            if (isScanning) CircularProgressIndicator(Modifier.size(24.dp), Color.Black)
            else Text("START NMAP SCAN", color = Color.Black, fontWeight = FontWeight.Black)
        }

        Spacer(Modifier.height(16.dp))
        Surface(modifier = Modifier.weight(1f).fillMaxWidth().border(1.dp, Color(0xFF1A1A1A)), color = Color(0xFF050505)) {
            Box {
                Column(Modifier.fillMaxSize().padding(10.dp).verticalScroll(scrollState)) {
                    output.lines().forEach { line ->
                        NmapAnnotatedText(line)
                    }
                }
                LaunchedEffect(output) { scrollState.animateScrollTo(scrollState.maxValue) }
            }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val ipMatch = Regex("""\b\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}\b""").find(output)?.value
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
            Icon(Icons.Default.Send, null, tint = Color.White, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text("SEND TO CONSOLE", color = Color.White, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun NmapAnnotatedText(line: String) {
    val annotatedString = buildAnnotatedString {
        when {
            line.contains("open", ignoreCase = true) && line.contains("/") -> {
                withStyle(style = SpanStyle(color = Color(0xFF00FF41), fontWeight = FontWeight.Bold)) { append(line) }
            }
            line.contains("OS details:", ignoreCase = true) || line.contains("Running:") || line.contains("OS scan results") -> {
                withStyle(style = SpanStyle(color = Color.Cyan)) { append(line) }
            }
            line.contains("VULNERABLE", ignoreCase = true) || line.contains("Exploit") || line.contains("state: VULNERABLE") -> {
                withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) { append(line) }
            }
            line.contains("Service Info:", ignoreCase = true) || (line.contains("VERSION") && !line.contains("Nmap")) || (line.contains(":") && line.contains("(") && line.contains(")")) -> {
                withStyle(style = SpanStyle(color = Color.Yellow)) { append(line) }
            }
            else -> {
                append(line)
            }
        }
    }
    Text(
        text = annotatedString,
        color = Color(0xFF00FF41),
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        lineHeight = 14.sp
    )
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val filename = "CamVigil_${System.currentTimeMillis()}.png"
    try {
        val contentResolver = context.contentResolver
        val fos: OutputStream? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CamVigil")
            }
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            imageUri?.let { contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/CamVigil"
            val dir = File(imagesDir); if (!dir.exists()) dir.mkdirs()
            FileOutputStream(File(dir, filename))
        }
        fos?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it); return true }
    } catch (e: Exception) { e.printStackTrace() }
    return false
}

class TerminalOutputStream(
    private val onText: (String) -> Unit
) : OutputStream() {
    
    private val buffer = StringBuilder()

    fun write(s: String) {
        s.forEach { char ->
            buffer.append(char)
            if (char == '\n') {
                onText(buffer.toString())
                buffer.clear()
            }
        }
    }
    
    override fun write(b: Int) {
        val char = b.toChar()
        buffer.append(char)
        if (char == '\n') {
            onText(buffer.toString())
            buffer.clear()
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
    var ipInput by remember { mutableStateOf("") }
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
            ipInput = targetIp; selectedTab = 0; isScanning = true; terminalText = "> Starting Reconnaissance on $targetIp...\n"
            scope.launch(Dispatchers.IO) {
                try {
                    val py = Python.getInstance()
                    val module = py.getModule("CamXploit")
                    
                    // Set up Python-side output capture
                    val sys = py.getModule("sys")
                    val io = py.getModule("io")
                    
                    // Create a StringIO to capture output
                    val stringIO = io.callAttr("StringIO")
                    sys.put("stdout", stringIO)
                    sys.put("stderr", stringIO)
                    
                    // Run the scan
                    module.callAttr("main", targetIp)
                    
                    // Get all output at once
                    stringIO.callAttr("seek", 0)
                    val output = stringIO.callAttr("read").toString()
                    
                    // Display output line by line
                    output.lines().forEach { line ->
                        withContext(Dispatchers.Main) {
                            terminalText += "$line\n"
                        }
                    }

                    withContext(Dispatchers.Main) { 
                        isScanning = false; saveJsonReport(context, terminalText, targetIp); saveContentToFile(context, terminalText, "Scan_Log", "txt")
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
                } catch (e: Exception) { withContext(Dispatchers.Main) { terminalText += "\n[!] ERROR: ${e.message}"; isScanning = false } }
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED) else context.registerReceiver(receiver, filter)
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
                    TextButton(onClick = { viewingFile = null }) {
                        Text("CLOSE", color = Color.Cyan)
                    }
                }
            },
            title = {
                Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                    Text(
                        text = viewingFile?.name ?: "Viewer",
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
                        val bitmap = BitmapFactory.decodeFile(viewingFile?.absolutePath)
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            Text(
                                text = "Error loading image",
                                color = Color.Red,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    } else {
                        val content = try {
                            viewingFile?.readText() ?: ""
                        } catch (e: Exception) {
                            "Error reading file"
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
            NavigationBar(containerColor = Color(0xFF121212), contentColor = Color.Cyan) {
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Info, "Console") }, label = { Text("CONSOLE") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Home, "Intel") }, label = { Text("INTEL") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.AutoMirrored.Filled.List, "Archive") }, label = { Text("ARCHIVE") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Videocam, "Stream") }, label = { Text("STREAM") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Magenta, indicatorColor = Color(0xFF1E1E1E)))
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, icon = { Icon(Icons.Default.Search, "LAN Scan") }, label = { Text("LAN SCAN") })
                NavigationBarItem(selected = selectedTab == 5, onClick = { selectedTab = 5 }, icon = { Icon(Icons.Default.FlashOn, "Storm") }, label = { Text("STORM") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFFBF00), indicatorColor = Color(0xFF1E1E1E)))
                NavigationBarItem(selected = selectedTab == 6, onClick = { selectedTab = 6 }, icon = { Icon(Icons.Default.Bookmark, "Saved") }, label = { Text("SAVED") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Green, indicatorColor = Color(0xFF1E1E1E)))
                NavigationBarItem(selected = selectedTab == 7, onClick = { selectedTab = 7 }, icon = { Icon(Icons.Default.Radar, "Nmap") }, label = { Text("NMAP") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Green, indicatorColor = Color(0xFF1E1E1E)))
            }
        }, containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
                Column { Text(text = "CAM VIGIL", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp); Text(text = "NETWORK RECONNAISSANCE UNIT", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                Row { IconButton(onClick = { showShodanDialog = true }) { Icon(Icons.Default.Public, null, tint = Color.Magenta) }; IconButton(onClick = { captureScreenshot(context, view) }) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan) }; IconButton(onClick = { generatePdfReport(context, terminalText); generateHtmlReport(context, terminalText) }) { Icon(Icons.Default.CheckCircle, null, tint = Color.Green) } }
            }
            Spacer(Modifier.height(20.dp)); when (selectedTab) {
                0 -> ConsoleTab(ipInput, { ipInput = it }, terminalText, { terminalText = "> Console Reset.\n" }, isScanning, scrollState, { startReconScan(ipInput) }, { url, _ -> selectedUrl = buildAuthUrl(url, extractCredentials(terminalText).first, extractCredentials(terminalText).second); selectedTab = 3 })
                1 -> IntelTab(terminalText, { terminalText += it }, { scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val b64 = py.getModule("CamXploit").callAttr("manual_snapshot_capture", ipInput, 80, extractCredentials(terminalText).first, extractCredentials(terminalText).second).toString(); if (b64 != "None") { val b = Base64.decode(b64, Base64.DEFAULT); val bmp = BitmapFactory.decodeByteArray(b, 0, b.size); withContext(Dispatchers.Main) { capturedBitmap = bmp; val f = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Snap_${System.currentTimeMillis()}.png"); FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } } } catch (e: Exception) {} } }, { selectedUrl = buildAuthUrl(it, extractCredentials(terminalText).first, extractCredentials(terminalText).second); selectedTab = 3 }, { scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); py.getModule("sys").put("stdout", TerminalOutputStream { t -> scope.launch(Dispatchers.Main) { terminalText += t } }); py.getModule("CamXploit").callAttr("discover_onvif", ipInput) } catch (e: Exception) {} } })
                2 -> ArchiveTab(context, selectedTab, terminalText, ipInput) { viewingFile = it }
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
                    onIpSelected = { ipInput = it; selectedTab = 0 })
                5 -> StormTab(onAutoRescan = { startReconScan(it); Toast.makeText(context, "Running post-stress scan...", Toast.LENGTH_SHORT).show() }, onSaveResults = { ip, out -> saveContentToFile(context, out, "[STORM] ${SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())} - $ip", "txt"); Toast.makeText(context, "Saved to Archive", Toast.LENGTH_SHORT).show() })
                6 -> SavedCamerasTab({ selectedUrl = it; selectedTab = 3 }, { ipInput = it; selectedTab = 0 })
                7 -> NmapTab(context, terminalText,
                    onTabSwitch = { selectedTab = it },
                    onIpSelected = { ipInput = it })
            }
            capturedBitmap?.let { bmp ->
                Spacer(Modifier.height(20.dp)); Text(text = "LAST SNAPSHOT", color = Color.Yellow, fontSize = 14.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(200.dp).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).background(Color.Black), Alignment.Center) { Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize()); IconButton(onClick = { capturedBitmap = null }, Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(0.5f), CircleShape)) { Icon(Icons.Default.Close, null, tint = Color.White) } }
            }
        }
    }
}

@Composable
fun ConsoleTab(ipInput: String, onIpChange: (String) -> Unit, terminalText: String, onTerminalClear: () -> Unit, isScanning: Boolean, scrollState: ScrollState, onStartScan: () -> Unit, onStreamSelect: (String, String) -> Unit) {
    Column {
        Box(Modifier.fillMaxWidth().border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(12.dp)) { Column { Text(text = "TARGET HOST", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold); Row(verticalAlignment = Alignment.CenterVertically) { BasicTextField(ipInput, onIpChange, textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f), singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Green)); IconButton(onClick = onStartScan) { Icon(if (isScanning) Icons.Default.Refresh else Icons.Default.Search, null, tint = if (isScanning) Color.Yellow else Color.Green) } } } }
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
fun IntelTab(terminalText: String, onLogUpdate: (String) -> Unit, onCaptureSnapshot: () -> Unit, onPreviewStream: (String) -> Unit, onTestOnvif: () -> Unit) {
    val scope = rememberCoroutineScope(); var isAuto by remember { mutableStateOf(false) }
    Column(Modifier.verticalScroll(rememberScrollState())) {
        Button(onClick = { isAuto = true; scope.launch(Dispatchers.IO) { listOf("192.168.1", "192.168.0").forEach { s -> (1..254).map { i -> async { val ip = "$s.$i"; try { if (InetAddress.getByName(ip).isReachable(300)) { if (listOf(80, 554, 8000, 37777).any { try { Socket().use { it.connect(InetSocketAddress(ip, 80), 200); true } } catch (e: Exception) { false } }) { val py = Python.getInstance(); py.getModule("sys").put("stdout", TerminalOutputStream { onLogUpdate(it) }); py.getModule("CamXploit").callAttr("main", ip) } } } catch (e: Exception) {} } }.awaitAll() }; isAuto = false } }, Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(Color(0xFF00E5FF)), shape = RoundedCornerShape(8.dp), enabled = !isAuto) { if (isAuto) CircularProgressIndicator(Modifier.size(24.dp), Color.Black) else Text(text = "AUTO DISCOVER ALL CAMERAS", color = Color.Black, fontWeight = FontWeight.Black) }
        Spacer(Modifier.height(20.dp)); IntelSection("STREAMS FOUND", terminalText.lines().filter { it.contains("http") || it.contains("rtsp") }, Color.Green, Icons.Default.Videocam, onPreviewStream); IntelSection("SECURITY VULNERABILITIES", terminalText.lines().filter { it.contains("VULNERABILITY") || it.contains("CRITICAL") }, Color.Red, Icons.Default.ReportProblem, onPreviewStream); IntelSection("HARDWARE INFO", terminalText.lines().filter { it.contains("Model:") || it.contains("Manufacturer:") }, Color.Cyan, Icons.Default.Info, onPreviewStream)
        Spacer(Modifier.height(20.dp)); Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) { Button(onClick = onTestOnvif, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFF333333))) { Text("TEST ONVIF", fontSize = 10.sp) }; Button(onClick = onCaptureSnapshot, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color(0xFF1B5E20))) { Text("CAPTURE SNAP", fontSize = 10.sp) } }
    }
}

@Composable
fun IntelSection(title: String, items: List<String>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onPreviewStream: (String) -> Unit) {
    if (items.isNotEmpty()) Card(Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color(0xFF1A1A1A))) {
        Column(Modifier.padding(12.dp)) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(text = title, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }; items.forEach { item -> Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(text = item.trim(), color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f)); if (title == "STREAMS FOUND") { val url = Regex("""(rtsp://\S+|http://\S+)""").find(item)?.value ?: ""; if (url.isNotEmpty()) TextButton(onClick = { onPreviewStream(url) }) { Text(text = "[VIEW]", color = Color.Magenta) } } } } }
    }
}

@Composable
fun PipelineStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: String, isComplete: Boolean, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) { Icon(icon, null, tint = if (isComplete) Color(0xFF00E5FF) else if (isActive) Color.Yellow else Color.DarkGray, modifier = Modifier.size(24.dp)); Text(text = label, color = Color(if (isComplete) 0xFF00E5FF else if (isActive) 0xFFFFFF00 else 0xFF444444), fontSize = 8.sp, fontWeight = FontWeight.Black); Text(text = count, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
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
                Row(Modifier.align(Alignment.CenterHorizontally).padding(8.dp), Arrangement.spacedBy(16.dp)) { IconButton(onClick = { scope.launch { val b = if (selectedUrl.startsWith("rtsp")) currentTextureView?.getBitmap() else currentWebView?.let { val p = it.capturePicture(); val bmp = Bitmap.createBitmap(p.width, p.height, Bitmap.Config.ARGB_8888); p.draw(Canvas(bmp)); bmp }; b?.let { if (saveBitmapToGallery(context, it)) Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } } }, Modifier.background(Color(0xFF003333), CircleShape)) { Icon(Icons.Default.PhotoCamera, null, tint = Color.Cyan) }; IconButton(onClick = { if (isRecording) onStopRecording() else onStartRecording() }, Modifier.background(if (isRecording) Color.Red else Color(0xFF330000), CircleShape)) { Icon(if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord, null, tint = Color.White) } }
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
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "🔍 LAN Scanner", color = Color.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)

            Surface(
                onClick = { onNmapModeChange(!nmapMode) },
                color = if (nmapMode) Color(0xFF1B5E20) else Color(0xFF333333),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (nmapMode) "NMAP" else "KOTLIN",
                        color = if (nmapMode) Color.Green else Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = nmapMode,
                        onCheckedChange = onNmapModeChange,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.Green,
                            checkedTrackColor = Color.Green.copy(alpha = 0.5f)
                        ),
                        modifier = Modifier.scale(0.7f)
                    )
                }
            }
        }

        if (subnet.isNotEmpty()) Text(text = "Subnet: $subnet.0/24", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))

        if (isScanning) LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = Color.Cyan, trackColor = Color.DarkGray)

        Button(onClick = onScanStart, Modifier.fillMaxWidth(), enabled = !isScanning) {
            Text(if (isScanning) "SCANNING... ${(progress * 100).toInt()}%" else "START SCAN")
        }

        Spacer(Modifier.height(16.dp))

        if (nmapMode && nmapOutput.length > 20) {
            Surface(modifier = Modifier.fillMaxWidth().height(150.dp).padding(bottom = 16.dp).border(1.dp, Color(0xFF1A1A1A)), color = Color.Black) {
                SelectionContainer {
                    Text(
                        text = nmapOutput,
                        color = Color.Green,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(8.dp).verticalScroll(rememberScrollState())
                    )
                }
            }
        }

        LazyColumn(Modifier.fillMaxSize()) {
            items(scanResults) { d ->
                Card(Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onIpSelected(d.ip) }, colors = CardDefaults.cardColors(Color(0xFF0A0A0A))) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Router, null, tint = Color.Cyan)
                        Spacer(Modifier.width(12.dp))
                        Text(text = d.ip, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun openFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension)); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open with")) } catch (e: Exception) {} }
fun shareFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share via")) } catch (e: Exception) {} }
fun saveContentToFile(context: Context, content: String, prefix: String, extension: String) { try { val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "${prefix}_${System.currentTimeMillis()}.$extension"); FileOutputStream(f).use { it.write(content.toByteArray()) } } catch (e: Exception) {} }
fun captureScreenshot(context: Context, view: android.view.View) { try { val b = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); Canvas(b).apply { view.draw(this) }; val f = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Vigil_${System.currentTimeMillis()}.png"); FileOutputStream(f).use { b.compress(Bitmap.CompressFormat.PNG, 100, it) }; Toast.makeText(context, "Saved", Toast.LENGTH_SHORT).show() } catch (e: Exception) {} }
fun generateHtmlReport(context: Context, terminalText: String) { saveContentToFile(context, "<html><body><pre>${terminalText.replace("<", "&lt;").replace(">", "&gt;")}</pre></body></html>", "Vigil_Report", "html") }
fun generatePdfReport(context: Context, terminalText: String) {
    val pdf = PdfDocument(); val paint = Paint().apply { textSize = 10f; color = android.graphics.Color.BLACK; typeface = Typeface.MONOSPACE }; val lines = terminalText.split("\n"); var current = 0; var pageCount = 1
    while (current < lines.size) {
        val page = pdf.startPage(PageInfo.Builder(595, 842, pageCount).create()); val canvas = page.canvas; var y = 54f; for (i in 0 until 50) { if (current >= lines.size) break; canvas.drawText(lines[current], 40f, y, paint); y += 14f; current++ }; pdf.finishPage(page); pageCount++
    }
    try { val f = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Vigil_Report_${System.currentTimeMillis()}.pdf"); pdf.writeTo(FileOutputStream(f)) } catch (e: Exception) {} finally { pdf.close() }
}
fun generateDetailedPdfReport(context: Context, terminalText: String, targetIp: String) {
    val pdf = PdfDocument(); val page = pdf.startPage(PageInfo.Builder(595, 842, 1).create()); val canvas = page.canvas; val paint = Paint()
    paint.color = android.graphics.Color.RED; canvas.drawRect(50f, 100f, 110f, 160f, paint); paint.color = android.graphics.Color.BLACK; paint.textSize = 28f; canvas.drawText("CAMVIGIL AUDIT REPORT", 130f, 140f, paint)
    canvas.drawText("Target: $targetIp", 50f, 250f, Paint().apply { textSize = 14f }); pdf.finishPage(page)
    try { val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CamVigil"); if (!dir.exists()) dir.mkdirs(); val f = File(dir, "Audit_${System.currentTimeMillis()}.pdf"); pdf.writeTo(FileOutputStream(f)); shareFile(context, f) } catch (e: Exception) {} finally { pdf.close() }
}
fun saveJsonReport(context: Context, t: String, target: String) { try { saveContentToFile(context, JSONObject().apply { put("target", target); put("raw", t) }.toString(4), "Vigil_Data", "json") } catch (e: Exception) {} }

@Composable
fun StormTab(onAutoRescan: (String) -> Unit, onSaveResults: (String, String) -> Unit) {
    var targetIp by remember { mutableStateOf("") }; var stormOutput by remember { mutableStateOf("> Ready for Tactical Scan...\n") }; var isRunning by remember { mutableStateOf(false) }; var selectedTest by remember { mutableStateOf("SCAN") }
    val scope = rememberCoroutineScope(); val responseTimes = remember { mutableStateListOf<Float>() }
    var minT by remember { mutableFloatStateOf(0f) }; var maxT by remember { mutableFloatStateOf(0f) }; var avgT by remember { mutableFloatStateOf(0f) }; var curT by remember { mutableFloatStateOf(0f) }
    var currentJob by remember { mutableStateOf<Job?>(null) }; var elapsedTime by remember { mutableLongStateOf(0L) }
    val scrollState = rememberScrollState(); val context = LocalContext.current; val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    val portNames = mapOf(21 to "FTP", 22 to "SSH", 23 to "Telnet", 80 to "HTTP", 443 to "HTTPS", 554 to "RTSP Camera Stream", 1883 to "MQTT IoT", 1935 to "RTMP Stream", 3702 to "ONVIF Discovery", 5000 to "UPnP", 8000 to "Hikvision SDK", 8080 to "HTTP Alt", 8443 to "HTTPS Alt", 8554 to "RTSP Alt", 9000 to "Sony/Bosch Camera", 34567 to "XMEye DVR", 37777 to "Dahua Service", 37778 to "Dahua Config", 10554 to "RTSP Alt")
    val allPorts = (1..200).toList() + listOf(554, 1935, 3702, 4747, 5000, 5554, 8000, 8080, 8081, 8082, 8083, 8088, 8090, 8443, 8554, 8888, 9000, 9001, 9090, 9999, 10554, 34567, 37777, 37778, 49152, 60000, 60001)

    LaunchedEffect(isRunning) { if (isRunning) { elapsedTime = 0L; while (isRunning) { delay(1000); elapsedTime++ } } }
    LaunchedEffect(stormOutput) { scrollState.animateScrollTo(scrollState.maxValue) }

    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text(text = "⚡ STORM MODULE", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Black)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), Arrangement.spacedBy(8.dp)) {
            listOf("SCAN" to Color(0xFFFFBF00), "RTSP_FLOOD" to Color.Magenta, "BANDWIDTH" to Color.Cyan).forEach { (t, c) ->
                Button(onClick = { selectedTest = t }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(if (selectedTest == t) c else Color(0xFF1A1A1A)), shape = RoundedCornerShape(4.dp)) { Text(text = t.replace("_", " ").take(6), color = if (selectedTest == t) Color.Black else Color.Gray, fontSize = 9.sp) }
            }
        }
        OutlinedTextField(targetIp, { targetIp = it }, label = { Text("Target IP") }, modifier = Modifier.fillMaxWidth(), textStyle = TextStyle(Color.White))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                if (targetIp.isNotBlank() && !isRunning) {
                    isRunning = true; responseTimes.clear(); minT = 0f; maxT = 0f; avgT = 0f; curT = 0f
                    currentJob = scope.launch(Dispatchers.IO) {
                        try {
                            if (selectedTest == "SCAN") {
                                stormOutput = "> Initiating Tactical Scan on $targetIp...\n"; val open = mutableListOf<Int>(); val sem = Semaphore(50); allPorts.map { p -> launch { sem.withPermit { val start = System.currentTimeMillis(); val isOpen = try { Socket().use { it.connect(InetSocketAddress(targetIp, p), 300); true } } catch (e: Exception) { false }; val dur = (System.currentTimeMillis() - start).toFloat(); withContext(Dispatchers.Main) { curT = dur; if (responseTimes.size >= 30) responseTimes.removeAt(0); responseTimes.add(dur); if (minT == 0f || dur < minT) minT = dur; if (dur > maxT) maxT = dur; avgT = responseTimes.average().toFloat(); if (isOpen) { open.add(p); stormOutput += "> Scanning port $p... OPEN${portNames[p]?.let { " ($it)" } ?: ""}\n" } } } } }.joinAll()
                                withContext(Dispatchers.Main) { stormOutput += "> Found ${open.size} open ports\n> Camera: ${open.filter { it in listOf(554, 8000, 37777, 34567, 37778, 8554, 10554, 9000) }.joinToString(" ")}\n> Management: ${open.filter { it in listOf(21, 22, 23) }.joinToString(" ")}\n"
                                    val vulnChecks = buildString {
                                        if (open.contains(23)) append("> ⚠️ CRITICAL: Telnet (port 23) exposed - unencrypted admin access!\n")
                                        if (open.contains(21)) append("> ⚠️ HIGH: FTP (port 21) exposed - file system may be accessible!\n")
                                        if (open.contains(22)) append("> ℹ️ SSH (port 22) open - try admin:admin or root:root\n")
                                        if (open.contains(554) && open.contains(80)) append("> ℹ️ Both HTTP and RTSP open - run CONSOLE scan for credentials\n")
                                        if (open.contains(3702)) append("> ℹ️ ONVIF Discovery active - run ONVIF probe in CONSOLE tab\n")
                                        if (open.contains(8000)) append("> ℹ️ Hikvision SDK port open - try iVMS-4500 app to connect\n")
                                        if (open.contains(37777)) append("> ℹ️ Dahua service port open - try DMSS app or gDMSS to connect\n")
                                        if (open.size > 20) append("> ⚠️ Many ports exposed - attack surface is large!\n")
                                    }
                                    stormOutput += "\n=== VULNERABILITY ASSESSMENT ===\n$vulnChecks================================\n"
                                }
                            } else if (selectedTest == "BANDWIDTH") {
                                stormOutput = "> Initiating Bandwidth Test on $targetIp:80...\n"; try { val start = System.currentTimeMillis(); var bytes = 0L; Socket().use { it.connect(InetSocketAddress(targetIp, 80), 3000); it.getOutputStream().write("GET / HTTP/1.1\r\nHost: $targetIp\r\nConnection: keep-alive\r\n\r\n".toByteArray()); val buf = ByteArray(4096); val ins = it.getInputStream()
                                    while (isActive && System.currentTimeMillis() - start < 3000L) { val b = ins.read(buf); if (b == -1) break; bytes += b; val s = (bytes * 8).toFloat() / (System.currentTimeMillis() - start).coerceAtLeast(1); withContext(Dispatchers.Main) { curT = s / 10; if (responseTimes.size >= 30) responseTimes.removeAt(0); responseTimes.add(s / 10) } }
                                    val elap = System.currentTimeMillis() - start; val mbps = (bytes * 8) / elap.coerceAtLeast(1) / 1000.0; withContext(Dispatchers.Main) { stormOutput += "> Speed: ${"%.2f".format(mbps)}Mbps\n> Bandwidth: ${if(mbps>5)"GOOD" else if(mbps>1)"OK" else "SLOW"}\n" }
                                } } catch (e: Exception) { withContext(Dispatchers.Main) { stormOutput += "\n[!] Error: ${e.message}\n" } }
                            } else {
                                stormOutput = "> Initiating RTSP Flood on $targetIp...\n"; val p = listOf(554, 8554).firstOrNull { try { Socket().use { s -> s.connect(InetSocketAddress(targetIp, it), 500); true } } catch (e: Exception) { false } }
                                if (p == null) { withContext(Dispatchers.Main) { stormOutput += "[!] RTSP closed.\n" } } else { var ok = 0; (1..20).map { launch { val start = System.currentTimeMillis(); try { Socket().use { s -> s.connect(InetSocketAddress(targetIp, p), 2000); s.getOutputStream().write("OPTIONS rtsp://$targetIp:$p/ RTSP/1.0\r\nCSeq: 1\r\n\r\n".toByteArray()); val r = s.getInputStream().bufferedReader().readLine(); withContext(Dispatchers.Main) { curT = (System.currentTimeMillis()-start).toFloat(); if (responseTimes.size >= 30) responseTimes.removeAt(0); responseTimes.add(curT); avgT = responseTimes.average().toFloat(); ok++; stormOutput += "> Stream: ${r?.take(20)}\n" }; delay(5000) } } catch (e: Exception) {} } }.joinAll(); withContext(Dispatchers.Main) { stormOutput += "> Max streams: $ok\n" } }
                            }
                        } finally { withContext(Dispatchers.Main) { isRunning = false; currentJob = null } }
                    }
                }
            }, Modifier.weight(1f), enabled = !isRunning) { if (isRunning) CircularProgressIndicator(Modifier.size(18.dp), Color.Black) else Text("START ${selectedTest.replace("_", " ")}") }
            if (isRunning) { Button(onClick = { currentJob?.cancel(); isRunning = false; stormOutput += "\n[!] STOPPED BY USER\n" }, colors = ButtonDefaults.buttonColors(Color.Red)) { Text("STOP", color = Color.White) } }
        }
        if (isRunning) Text(text = "Running: ${String.format("%02d:%02d", elapsedTime / 60, elapsedTime % 60)}", color = Color.Yellow, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 4.dp))
        Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { val s = TextStyle(color = Color.White, fontSize = 10.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold); Text(text = "NOW: ${curT.toInt()}ms", style = s); Text(text = "MIN: ${minT.toInt()}ms", style = s); Text(text = "MAX: ${maxT.toInt()}ms", style = s); Text(text = "AVG: ${avgT.toInt()}ms", style = s) }
        Spacer(Modifier.height(4.dp)); Box(Modifier.fillMaxWidth().height(120.dp).background(Color(0xFF0A0A0A)).border(1.dp, Color(0xFF1A1A1A))) {
            Canvas(Modifier.fillMaxSize().padding(8.dp)) {
                val w = size.width; val h = size.height; val max = 600f; val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                drawLine(Color.Gray, Offset(0f, h - (100f/max)*h), Offset(w, h - (100f/max)*h), pathEffect = dash); drawLine(Color.Gray, Offset(0f, h - (500f/max)*h), Offset(w, h - (500f/max)*h), pathEffect = dash)
                if (responseTimes.isNotEmpty()) { val sx = w / 29f; for (i in 0 until responseTimes.size - 1) drawLine(if (responseTimes[i+1]<100f) Color.Green else if (responseTimes[i+1]<500f) Color.Yellow else Color.Red, Offset(i*sx, h - (responseTimes[i].coerceAtMost(max)/max)*h), Offset((i+1)*sx, h - (responseTimes[i+1].coerceAtMost(max)/max)*h), 2.dp.toPx(), StrokeCap.Round) }
            }
        }
        Surface(modifier = Modifier.fillMaxWidth().weight(1f).padding(top = 16.dp).border(1.dp, Color(0xFF1A1A1A)), color = Color.Black) {
            Box {
                Column(Modifier.fillMaxSize().padding(8.dp).verticalScroll(scrollState)) {
                    stormOutput.lines().forEach { line ->
                        val color = when { line.contains("CRITICAL") || line.contains("WARNING") || line.contains("⚠️") -> Color.Red; line.contains("OPEN") -> Color.Green; line.contains("CLOSED") -> Color.Gray; line.contains("ERROR") || line.contains("[!]") -> Color.Yellow; else -> Color(0xFFFFBF00) }
                        Text(text = line, color = color, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
                Row(Modifier.align(Alignment.TopEnd).padding(4.dp), Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(stormOutput)); Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show() }, Modifier.size(24.dp).background(Color.DarkGray.copy(0.5f), CircleShape)) { Icon(Icons.Default.ContentCopy, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                    IconButton(onClick = { stormOutput = "> Console Cleared.\n" }, Modifier.size(24.dp).background(Color.DarkGray.copy(0.5f), CircleShape)) { Icon(Icons.Default.Clear, null, tint = Color.White, modifier = Modifier.size(14.dp)) }
                }
            }
        }
        if (!isRunning && stormOutput.length > 30) Row(Modifier.fillMaxWidth().padding(top = 8.dp), Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onAutoRescan(targetIp) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color.Green), shape = RoundedCornerShape(4.dp)) { Text(text = "AUTO RESCAN", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
            Button(onClick = { onSaveResults(targetIp, stormOutput) }, Modifier.weight(1f), colors = ButtonDefaults.buttonColors(Color.Blue), shape = RoundedCornerShape(4.dp)) { Text(text = "SAVE RESULTS", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
        }
    }
}

fun getLocalSubnet(): String { try { val interfaces = NetworkInterface.getNetworkInterfaces(); for (inf in Collections.list(interfaces)) { if (inf.isLoopback || !inf.isUp) continue; for (addr in Collections.list(inf.inetAddresses)) { if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress?.substringBeforeLast(".") + ".0/24" } } } catch (e: Exception) {}; return "192.168.1.0/24" }
fun extractCredentials(t: String): Pair<String, String> { val m = Regex("""CRACKED \((?:HTTP|RTSP)\): ([^:]+):([^\s\n]+)""").find(t); return if (m != null) m.groupValues[1] to m.groupValues[2] else "admin" to "admin" }
fun buildAuthUrl(u: String, user: String, pass: String): String { if (user.isBlank() || pass.isBlank() || u.contains("@")) return u; return try { if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@") else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@") else u } catch (e: Exception) { u } }
data class DeviceInfo(val ip: String, val hostname: String?, val openPorts: List<Int>)
@Composable fun SavedCamerasTab(onStream: (String) -> Unit, onScan: (String) -> Unit) {
    val context = LocalContext.current; val dao = remember { CameraDatabase.getDatabase(context).cameraDao() }; val cameras by dao.getAllCameras().collectAsState(initial = emptyList())
    var isMon by remember { mutableStateOf(CameraMonitorService.isRunning) }; val pulse by rememberInfiniteTransition().animateFloat(1f, 0.3f, infiniteRepeatable(tween(800), RepeatMode.Reverse))
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) { Text(text = "SAVED TARGETS", color = Color.Green, fontSize = 14.sp, fontWeight = FontWeight.Bold); Switch(isMon, { isMon = it; val intent = Intent(context, CameraMonitorService::class.java); if (it) { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent) } else context.stopService(intent) }) }
        Text(text = if (isMon) "MONITORING ACTIVE" else "DISABLED", color = if (isMon) Color.Green else Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp))
        LazyColumn { items(cameras) { c -> Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(Color(0xFF0A0A0A))) { Column(Modifier.padding(12.dp)) { Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) { Text(text = c.nickname, color = Color.White, fontWeight = FontWeight.Bold); Box(Modifier.size(8.dp).background(if (c.isOnline) Color.Green else Color.Red, CircleShape).align(Alignment.CenterVertically)) }; Text(text = c.ip, color = Color.Cyan); Row { Button(onClick = { onStream(c.streamUrl) }) { Text("STREAM") }; Spacer(Modifier.width(8.dp)); Button(onClick = { onScan(c.ip) }) { Text("SCAN") } } } } } }
    }
}
