package com.spyboy.camxploit

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.media.ImageReader
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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContent {
            CamGuardianApp()
        }
    }
}

fun saveBitmapToGallery(context: Context, bitmap: Bitmap): Boolean {
    val filename = "CamVigil_${System.currentTimeMillis()}.png"
    var fos: OutputStream? = null
    val contentResolver = context.contentResolver

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/CamVigil")
            }
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
            fos = imageUri?.let { contentResolver.openOutputStream(it) }
        } else {
            val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).toString() + "/CamVigil"
            val dir = File(imagesDir)
            if (!dir.exists()) dir.mkdirs()
            val image = File(dir, filename)
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

class TerminalOutputStream(val onUpdate: (String) -> Unit) : OutputStream() {
    override fun write(b: Int) {
        onUpdate(b.toChar().toString())
    }
    override fun write(b: ByteArray, off: Int, len: Int) {
        onUpdate(String(b, off, len))
    }
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
    var lanScanResults by remember { mutableStateOf<List<DeviceInfo>>(emptyList()) }
    var lanIsScanning by remember { mutableStateOf(false) }
    var lanProgress by remember { mutableStateOf(0f) }
    var lanSubnet by remember { mutableStateOf("") }
    var showDisclaimer by remember { mutableStateOf(true) }
    var capturedBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var showLiveView by remember { mutableStateOf(false) }
    var selectedStreamUrl by remember { mutableStateOf("") }
    var selectedStreamType by remember { mutableStateOf("") }
    var shodanApiKey by remember { mutableStateOf("") }
    var shodanQuery by remember { mutableStateOf("webcam") }
    var showShodanDialog by remember { mutableStateOf(false) }
    var selectedUrl by remember { mutableStateOf("") }

    var isRecording by remember { mutableStateOf(false) }
    var recordingDuration by remember { mutableLongStateOf(0L) }

    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    val projectionManager = remember { context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager }

    val recordLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK && result.data != null) {
            val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                action = "ACTION_START"
                putExtra("RESULT_CODE", result.resultCode)
                putExtra("DATA", result.data)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
            isRecording = true
        }
    }

    val receiver = remember {
        object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                if (intent?.action == "COM_SPYBOY_CAMXPLOIT_RECORDING_STOPPED") {
                    isRecording = false
                    val path = intent.getStringExtra("FILE_PATH") ?: ""
                    Toast.makeText(context, "Recording saved: $path", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    DisposableEffect(Unit) {
        val filter = IntentFilter("COM_SPYBOY_CAMXPLOIT_RECORDING_STOPPED")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            context.unregisterReceiver(receiver)
        }
    }

    LaunchedEffect(isRecording) {
        if (isRecording) {
            recordingDuration = 0L
            while (isRecording) {
                delay(1000)
                recordingDuration++
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineLocationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] ?: false
        val coarseLocationGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] ?: false
        val notificationsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions[Manifest.permission.POST_NOTIFICATIONS] ?: false
        } else true

        if (!fineLocationGranted && !coarseLocationGranted) {
            Toast.makeText(context, "Location permission is required for LAN scanning", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        
        val needsRequest = permissions.any {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        
        if (needsRequest) {
            permissionLauncher.launch(permissions.toTypedArray())
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
                    TextButton(onClick = { viewingFile?.let { openFile(context, it) } }) {
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
                        val bitmap = remember(viewingFile) { BitmapFactory.decodeFile(viewingFile?.absolutePath) }
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
                NavigationBarItem(selected = selectedTab == 0, onClick = { selectedTab = 0 }, icon = { Icon(Icons.Default.Info, "Console") }, label = { Text("CONSOLE") })
                NavigationBarItem(selected = selectedTab == 1, onClick = { selectedTab = 1 }, icon = { Icon(Icons.Default.Home, "Intel") }, label = { Text("INTEL") })
                NavigationBarItem(selected = selectedTab == 2, onClick = { selectedTab = 2 }, icon = { Icon(Icons.Default.List, "Archive") }, label = { Text("ARCHIVE") })
                NavigationBarItem(selected = selectedTab == 3, onClick = { selectedTab = 3 }, icon = { Icon(Icons.Default.Videocam, "Stream") }, label = { Text("STREAM") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Magenta, unselectedIconColor = Color.Gray, selectedTextColor = Color.Magenta, indicatorColor = Color(0xFF1E1E1E)))
                NavigationBarItem(selected = selectedTab == 4, onClick = { selectedTab = 4 }, icon = { Icon(Icons.Default.Search, "LAN Scan") }, label = { Text("LAN SCAN") })
                NavigationBarItem(selected = selectedTab == 5, onClick = { selectedTab = 5 }, icon = { Icon(Icons.Default.FlashOn, "Storm") }, label = { Text("STORM") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color(0xFFFFBF00), selectedTextColor = Color(0xFFFFBF00), indicatorColor = Color(0xFF1E1E1E)))
                NavigationBarItem(selected = selectedTab == 6, onClick = { selectedTab = 6 }, icon = { Icon(Icons.Default.Bookmark, "Saved") }, label = { Text("SAVED") }, colors = NavigationBarItemDefaults.colors(selectedIconColor = Color.Green, selectedTextColor = Color.Green, indicatorColor = Color(0xFF1E1E1E)))
            }
        },
        containerColor = Color.Black
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text = "CAM VIGIL", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text(text = "NETWORK RECONNAISSANCE UNIT", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                Row {
                    IconButton(onClick = { showShodanDialog = true }) { Icon(Icons.Default.Public, "Shodan Search", tint = Color.Magenta) }
                    IconButton(onClick = { captureScreenshot(context, view) }) { Icon(Icons.Default.PhotoCamera, "Screenshot", tint = Color.Cyan) }
                    IconButton(onClick = { 
                        generatePdfReport(context, terminalText)
                        generateHtmlReport(context, terminalText)
                    }) { Icon(Icons.Default.CheckCircle, "Save Report", tint = Color.Green) }
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
                                    val outputStream = TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } }
                                    sys.put("stdout", outputStream)
                                    module.callAttr("main", ipInput)
                                    withContext(Dispatchers.Main) { 
                                        isScanning = false 
                                        saveJsonReport(context, terminalText, ipInput)
                                        saveContentToFile(context, terminalText, "Scan_Log", "txt")
                                        if (terminalText.contains("CRACKED")) {
                                            val (user, pass) = extractCredentials(terminalText)
                                            val ip = ipInput
                                            val brandMatch = Regex("""Brand:\s*(\w+)""").find(terminalText)
                                            val brand = brandMatch?.groupValues?.get(1) ?: "Unknown"
                                            val streamUrl = Regex("""(rtsp://[^\s]+|http://[^\s]+)""").find(terminalText)?.value ?: ""
                                            scope.launch(Dispatchers.IO) {
                                                val dao = CameraDatabase.getDatabase(context).cameraDao()
                                                if (dao.getCameraByIp(ip) == null) {
                                                    dao.insertCamera(SavedCamera(nickname = "Auto $brand", ip = ip, username = user, password = pass, brand = brand, streamUrl = streamUrl, isOnline = true))
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) { terminalText += "\n[!] ERROR: ${e.message}"; isScanning = false }
                                }
                            }
                        }
                    },
                    onStreamSelect = { detectedStreamUrl, type ->
                        val (user, pass) = extractCredentials(terminalText)
                        selectedUrl = buildAuthUrl(detectedStreamUrl, user, pass)
                        selectedTab = 3
                    }
                )
                1 -> IntelTab(
                    terminalText = terminalText,
                    onLogUpdate = { terminalText += it },
                    onCaptureSnapshot = {
                        scope.launch(Dispatchers.IO) {
                            try {
                                val py = Python.getInstance()
                                val module = py.getModule("CamXploit")
                                val ipMatch = Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").find(terminalText)
                                val portMatch = Regex("""Port (\d+)""").find(terminalText)
                                if (ipMatch != null) {
                                    val targetIp = ipMatch.value
                                    val targetPort = portMatch?.groupValues?.get(1)?.toInt() ?: 80
                                    val credMatch = Regex("""CRACKED \(HTTP\): (\w+):(\w+)""").find(terminalText)
                                    val user = credMatch?.groupValues?.get(1)
                                    val pass = credMatch?.groupValues?.get(2)
                                    val b64Data = module.callAttr("manual_snapshot_capture", targetIp, targetPort, user, pass).toString()
                                    if (b64Data != "None") {
                                        val decodedString = Base64.decode(b64Data, Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(decodedString, 0, decodedString.size)
                                        withContext(Dispatchers.Main) {
                                            capturedBitmap = bitmap
                                            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                                            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Manual_Snap_$timeStamp.png")
                                            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                            Toast.makeText(context, "Snapshot Captured & Saved", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            } catch (e: Exception) { withContext(Dispatchers.Main) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
                        }
                    },
                    onPreviewStream = { url ->
                        val (user, pass) = extractCredentials(terminalText)
                        selectedUrl = buildAuthUrl(url, user, pass)
                        selectedTab = 3
                    },
                    onTestOnvif = {
                        val targetIp = ipInput.ifEmpty { Regex("""\d{1,3}\.\d{1,3}\.\d{1,3}\.\d{1,3}""").findAll(terminalText).lastOrNull()?.value ?: "" }
                        if (targetIp.isNotBlank()) {
                            scope.launch(Dispatchers.IO) {
                                try {
                                    val py = Python.getInstance()
                                    val module = py.getModule("CamXploit")
                                    val sys = py.getModule("sys")
                                    sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { terminalText += text } })
                                    module.callAttr("discover_onvif", targetIp)
                                } catch (e: Exception) { withContext(Dispatchers.Main) { terminalText += "\n[!] ONVIF Error: ${e.message}\n" } }
                            }
                        }
                    }
                )
                2 -> ArchiveTab(context, selectedTab, terminalText, ipInput) { file -> viewingFile = file }
                3 -> StreamTab(
                    terminalText,
                    selectedUrl,
                    { selectedUrl = it },
                    isRecording,
                    recordingDuration,
                    onStartRecording = {
                        val intent = projectionManager.createScreenCaptureIntent()
                        recordLauncher.launch(intent)
                    },
                    onStopRecording = {
                        val serviceIntent = Intent(context, ScreenCaptureService::class.java).apply {
                            action = "ACTION_STOP"
                        }
                        context.startService(serviceIntent)
                    }
                )
                4 -> LanScanTab(
                    scanResults = lanScanResults,
                    isScanning = lanIsScanning,
                    progress = lanProgress,
                    subnet = lanSubnet,
                    onScanStart = {
                        lanIsScanning = true
                        lanScanResults = emptyList()
                        lanProgress = 0f
                        scope.launch(Dispatchers.IO) {
                            val currentSubnet = getLocalSubnet().substringBeforeLast(".0/24")
                            withContext(Dispatchers.Main) { lanSubnet = currentSubnet }
                            val total = 254
                            (1..total).map { i ->
                                async {
                                    val ip = "$currentSubnet.$i"
                                    if (InetAddress.getByName(ip).isReachable(300)) {
                                        withContext(Dispatchers.Main) {
                                            val device = DeviceInfo(ip, null, emptyList())
                                            lanScanResults = lanScanResults + device
                                        }
                                    }
                                    withContext(Dispatchers.Main) {
                                        lanProgress = i.toFloat() / total
                                    }
                                }
                            }.awaitAll()
                            withContext(Dispatchers.Main) {
                                lanIsScanning = false
                            }
                        }
                    },
                    onResultFound = { },
                    onScanComplete = { },
                    onTabSwitch = { selectedTab = it },
                    onIpSelected = { ip ->
                        ipInput = ip
                        selectedTab = 0
                        Toast.makeText(context, "Target set to $ip", Toast.LENGTH_SHORT).show()
                    }
                )
                5 -> StormTab(terminalText) { text: String -> terminalText += text }
                6 -> SavedCamerasTab(onStream = { url -> selectedUrl = url; selectedTab = 3 }, onScan = { ip -> ipInput = ip; selectedTab = 0 })
            }

            capturedBitmap?.let { bitmap ->
                Spacer(modifier = Modifier.height(20.dp))
                Text("LAST CAPTURED SNAPSHOT", color = Color.Yellow, fontWeight = FontWeight.Black, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(200.dp).border(1.dp, Color.Gray, RoundedCornerShape(8.dp)).background(Color.Black), contentAlignment = Alignment.Center) {
                    Image(bitmap = bitmap.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize())
                    IconButton(onClick = { capturedBitmap = null }, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.Black.copy(alpha = 0.5f), CircleShape)) { Icon(Icons.Default.Close, "Close", tint = Color.White) }
                }
            }
        }
    }
}

@Composable
fun ConsoleTab(context: Context, ipInput: String, onIpChange: (String) -> Unit, terminalText: String, onTerminalClear: () -> Unit, isScanning: Boolean, scrollState: ScrollState, onStartScan: () -> Unit, onStreamSelect: (String, String) -> Unit) {
    Column {
        Box(modifier = Modifier.fillMaxWidth().border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).background(Color(0xFF0A0A0A)).padding(12.dp)) {
            Column {
                Text("TARGET HOST / RANGE", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(value = ipInput, onValueChange = onIpChange, textStyle = TextStyle(color = Color.White, fontSize = 18.sp, fontFamily = FontFamily.Monospace), modifier = Modifier.weight(1f), singleLine = true, cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.Green))
                    IconButton(onClick = onStartScan) { Icon(imageVector = if (isScanning) Icons.Default.Refresh else Icons.Default.Search, contentDescription = "Scan", tint = if (isScanning) Color.Yellow else Color.Green) }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Surface(modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF1A1A1A), RoundedCornerShape(4.dp)), color = Color(0xFF050505)) {
            Box {
                SelectionContainer { Text(text = terminalText, color = Color(0xFF00FF41), fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.fillMaxSize().padding(10.dp).verticalScroll(scrollState)) }
                IconButton(onClick = onTerminalClear, modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(24.dp)) { Icon(Icons.Default.Delete, "Clear", tint = Color.DarkGray, modifier = Modifier.size(16.dp)) }
            }
        }
        LaunchedEffect(terminalText) { scrollState.animateScrollTo(scrollState.maxValue) }
        if (terminalText.contains("===LINKS_START===")) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("🎯 Auto-Detected Links", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            val lines = terminalText.lines()
            val start = lines.indexOfFirst { it.contains("===LINKS_START===") }
            val end = lines.indexOfFirst { it.contains("===LINKS_END===") }
            if (start != -1 && end != -1) {
                Column(modifier = Modifier.fillMaxWidth().height(350.dp).verticalScroll(rememberScrollState())) {
                    for (i in start + 1 until end) {
                        val line = lines[i].trim()
                        if (line.contains("|")) {
                            val parts = line.split("|")
                            if (parts.size >= 2) {
                                val linkType = parts[0]
                                val url = parts[1]
                                val status = if (parts.size > 2) parts[2] else ""
                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(linkType, color = if (linkType.contains("SNAPSHOT")) Color.Magenta else Color.Yellow, fontWeight = FontWeight.Bold)
                                        Text(url.take(52) + "...", color = Color.LightGray, fontSize = 12.sp)
                                        Text("Status: $status", color = Color.Green, fontSize = 11.sp)
                                    }
                                    Button(onClick = { onStreamSelect(url, linkType) }, colors = ButtonDefaults.buttonColors(containerColor = if (linkType.contains("SNAPSHOT")) Color.Magenta else Color.Green)) { Text("LIVE") }
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
fun IntelTab(terminalText: String, onLogUpdate: (String) -> Unit, onCaptureSnapshot: () -> Unit, onPreviewStream: (String) -> Unit, onTestOnvif: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var autoDiscoverOutput by remember { mutableStateOf("") }
    var isAutoDiscovering by remember { mutableStateOf(false) }
    var currentPipelineStep by remember { mutableIntStateOf(0) }
    var hostCount by remember { mutableIntStateOf(0) }
    var cameraCount by remember { mutableIntStateOf(0) }

    val streams = terminalText.lines().filter { it.contains("http") || it.contains("rtsp") }
    val vulns = terminalText.lines().filter { it.contains("VULNERABILITY") || it.contains("CRITICAL") || it.contains("FIRE") }
    val deviceInfo = terminalText.lines().filter { it.contains("Model:") || it.contains("Firmware:") || it.contains("Manufacturer:") }

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Button(
            onClick = {
                isAutoDiscovering = true
                autoDiscoverOutput = "> INITIALIZING AUTO-DISCOVERY...\n"
                scope.launch(Dispatchers.IO) {
                    val subnets = listOf("192.168.1", "192.168.0")
                    subnets.forEach { subnet ->
                        (1..254).map { i ->
                            async {
                                val ip = "$subnet.$i"
                                try {
                                    if (InetAddress.getByName(ip).isReachable(300)) {
                                        withContext(Dispatchers.Main) { hostCount++; autoDiscoverOutput += "Found: $ip\n" }
                                        val openPorts = listOf(80, 554, 8000, 37777).filter { port -> try { Socket().use { s -> s.connect(InetSocketAddress(ip, port), 200); true } } catch (e: Exception) { false } }
                                        if (openPorts.isNotEmpty()) {
                                            withContext(Dispatchers.Main) { cameraCount++; autoDiscoverOutput += "Camera at $ip\n" }
                                            try {
                                                val py = Python.getInstance()
                                                val module = py.getModule("CamXploit")
                                                val sys = py.getModule("sys")
                                                sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { onLogUpdate(text) } })
                                                module.callAttr("main", ip)
                                            } catch (e: Exception) {}
                                        }
                                    }
                                } catch (e: Exception) {}
                            }
                        }.awaitAll()
                    }
                    isAutoDiscovering = false
                }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
            shape = RoundedCornerShape(8.dp),
            enabled = !isAutoDiscovering
        ) {
            if (isAutoDiscovering) CircularProgressIndicator(color = Color.Black, modifier = Modifier.size(24.dp))
            else Text("AUTO DISCOVER ALL CAMERAS", color = Color.Black, fontWeight = FontWeight.Black)
        }
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            PipelineStep(Icons.Default.Radar, "PING", "$hostCount", currentPipelineStep >= 1, isAutoDiscovering && currentPipelineStep == 1)
            PipelineStep(Icons.Default.SettingsInputComponent, "PORT", "$cameraCount", currentPipelineStep >= 2, isAutoDiscovering && currentPipelineStep == 2)
        }
        Spacer(modifier = Modifier.height(12.dp))
        IntelSection("STREAMS FOUND", streams, Color.Green, Icons.Default.Videocam, onPreviewStream)
        IntelSection("SECURITY VULNERABILITIES", vulns, Color.Red, Icons.Default.ReportProblem, onPreviewStream)
        IntelSection("DEVICE HARDWARE INFO", deviceInfo, Color.Cyan, Icons.Default.Info, onPreviewStream)
        Spacer(modifier = Modifier.height(20.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = onTestOnvif, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF333333)), shape = RoundedCornerShape(4.dp)) { Text("TEST ONVIF", fontSize = 10.sp) }
            Button(onClick = onCaptureSnapshot, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1B5E20)), shape = RoundedCornerShape(4.dp)) { Text("CAPTURE SNAP", fontSize = 10.sp) }
        }
    }
}

@Composable
fun IntelSection(title: String, items: List<String>, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector, onPreviewStream: (String) -> Unit) {
    if (items.isNotEmpty()) {
        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A)), border = BorderStroke(1.dp, Color(0xFF1A1A1A))) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = color, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp)); Text(title, color = color, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                items.forEach { item ->
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = item.trim(), color = Color.LightGray, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                        if (title == "STREAMS FOUND") {
                            val url = Regex("""(rtsp://[^\s]+|http://[^\s]+)""").find(item)?.value ?: ""
                            if (url.isNotEmpty()) TextButton(onClick = { onPreviewStream(url) }) { Text("[VIEW]", color = Color.Magenta) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PipelineStep(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, count: String, isComplete: Boolean, isActive: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(60.dp)) {
        Icon(imageVector = icon, contentDescription = label, tint = if (isComplete) Color(0xFF00E5FF) else if (isActive) Color.Yellow else Color.DarkGray, modifier = Modifier.size(24.dp))
        Text(label, color = if (isComplete) Color(0xFF00E5FF) else if (isActive) Color.Yellow else Color.DarkGray, fontSize = 8.sp, fontWeight = FontWeight.Black)
        Text(count, color = if (isComplete || isActive) Color.White else Color.DarkGray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StreamTab(
    terminalText: String,
    selectedUrl: String,
    onUrlSelected: (String) -> Unit,
    isRecording: Boolean,
    recordingDuration: Long,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentials = remember(terminalText) {
        val credMatch = Regex("(?:CRACKED|Success)[^\\n]*?(\\w+):(\\w+)\\s*@").find(terminalText)
        (credMatch?.groupValues?.get(1) ?: "admin") to (credMatch?.groupValues?.get(2) ?: "admin")
    }

    var isGridView by remember { mutableStateOf(false) }
    var gridUrls by remember { mutableStateOf(List(4) { "" }) }
    var activeSlotIndex by remember { mutableIntStateOf(0) }
    var screenshotPreview by remember { mutableStateOf<Bitmap?>(null) }
    var showScreenshotPreview by remember { mutableStateOf(false) }

    var currentExoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var currentWebView by remember { mutableStateOf<WebView?>(null) }
    var currentTextureView by remember { mutableStateOf<TextureView?>(null) }

    DisposableEffect(selectedUrl) {
        onDispose {
            // player cleanup handled by AndroidView
            currentExoPlayer?.release()
            currentExoPlayer = null
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    val streamUrls = remember(terminalText) {
        val start = terminalText.indexOf("===LINKS_START===")
        val end = terminalText.indexOf("===LINKS_END===")
        if (start != -1 && end != -1) {
            terminalText.substring(start, end).lines().filter { it.contains("|") }.mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size >= 2) { val url = parts[1].trim(); if (url.startsWith("http") || url.startsWith("rtsp")) url to parts[0].trim() else null } else null
            }.distinctBy { it.first }
        } else emptyList()
    }

    LaunchedEffect(streamUrls) { if (selectedUrl.isEmpty() && streamUrls.isNotEmpty()) onUrlSelected(streamUrls.first().first) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("STREAM VIEWER", color = Color.Magenta, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (isRecording) {
                    Spacer(modifier = Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(Color.Red.copy(alpha = pulseAlpha), CircleShape)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "REC ${String.format("%02d:%02d", recordingDuration / 60, recordingDuration % 60)}",
                        color = Color.Red,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            IconButton(onClick = { isGridView = !isGridView }) { Icon(if (isGridView) Icons.Default.ViewStream else Icons.Default.GridView, null, tint = Color.Magenta) }
        }
        
        if (isGridView) {
            LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                itemsIndexed(gridUrls) { index, url ->
                    Box(modifier = Modifier.aspectRatio(16/9f).border(2.dp, if (index == activeSlotIndex) Color.Magenta else Color(0xFF1A1A1A), RoundedCornerShape(4.dp)).clickable { activeSlotIndex = index }) {
                        if (url.isNotEmpty()) MiniPlayer(url, credentials.first, credentials.second)
                        else Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("SLOT ${index + 1}", color = Color.DarkGray, fontSize = 10.sp) }
                    }
                }
            }
        } else if (selectedUrl.isNotEmpty()) {
            val authenticatedUrl = buildAuthUrl(selectedUrl, credentials.first, credentials.second)
            val isRtsp = selectedUrl.startsWith("rtsp")
            Column(modifier = Modifier.weight(1f)) {
                if (isRtsp) {
                    key(selectedUrl) {
                        AndroidView(
                            factory = { ctx ->
                                val player = ExoPlayer.Builder(ctx).build().apply {
                                    setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(authenticatedUrl)))
                                    prepare()
                                    playWhenReady = true
                                }
                                currentExoPlayer = player
                                val textureView = TextureView(ctx)
                                currentTextureView = textureView
                                PlayerView(ctx).apply {
                                    this.player = player
                                    useController = true
                                    // Use reflection or alternative if direct call fails
                                    try {
                                        val method = this.javaClass.getMethod("setVideoSurfaceView", android.view.View::class.java)
                                        method.invoke(this, textureView)
                                    } catch (e: Exception) {
                                        // Fallback or ignore if not available in this version
                                    }
                                }
                            },
                            update = { playerView ->
                                playerView.player?.play()
                            },
                            modifier = Modifier.fillMaxWidth().weight(1f).border(1.dp, Color(0xFF333333))
                        )
                    }
                } else {
                    key(selectedUrl) {
                        AndroidView(
                            factory = { ctx ->
                                WebView(ctx).apply {
                                    currentWebView = this
                                    settings.javaScriptEnabled = true
                                    webViewClient = object : WebViewClient() { override fun onReceivedSslError(v: WebView?, h: SslErrorHandler?, e: android.net.http.SslError?) { h?.proceed() } }
                                    loadUrl(authenticatedUrl)
                                }
                            },
                            modifier = Modifier.fillMaxSize().weight(1f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    IconButton(
                        onClick = {
                            scope.launch {
                                val bitmap: Bitmap? = if (isRtsp) {
                                    currentTextureView?.let { tv ->
                                        val size = currentExoPlayer?.videoSize
                                        if (size != null && size.width > 0) tv.getBitmap(size.width, size.height) else tv.getBitmap()
                                    }
                                } else {
                                    currentWebView?.let { wv ->
                                        val picture = wv.capturePicture()
                                        val b = Bitmap.createBitmap(picture.width, picture.height, Bitmap.Config.ARGB_8888)
                                        picture.draw(Canvas(b))
                                        b
                                    }
                                }
                                bitmap?.let { b ->
                                    if (saveBitmapToGallery(context, b)) {
                                        screenshotPreview = b
                                        showScreenshotPreview = true
                                        Toast.makeText(context, "Screenshot saved", Toast.LENGTH_SHORT).show()
                                        delay(3000)
                                        showScreenshotPreview = false
                                    }
                                }
                            }
                        },
                        modifier = Modifier.background(Color(0xFF003333), CircleShape).size(48.dp)
                    ) { Icon(Icons.Default.PhotoCamera, "Screenshot", tint = Color.Cyan) }

                    IconButton(
                        onClick = {
                            if (isRecording) onStopRecording() else onStartRecording()
                        },
                        modifier = Modifier.background(if (isRecording) Color.Red else Color(0xFF330000), CircleShape).size(48.dp)
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Default.Stop else Icons.Default.FiberManualRecord,
                            contentDescription = "Record",
                            tint = Color.White
                        )
                    }
                }
            }
        }
    }

    AnimatedVisibility(visible = showScreenshotPreview, enter = fadeIn() + slideInVertically { it }, exit = fadeOut() + slideOutVertically { it }, modifier = Modifier.padding(16.dp)) {
        screenshotPreview?.let {
            Card(modifier = Modifier.size(120.dp, 80.dp).border(1.dp, Color.Cyan, RoundedCornerShape(8.dp)), colors = CardDefaults.cardColors(containerColor = Color.Black)) {
                Image(bitmap = it.asImageBitmap(), contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}

@Composable
fun MiniPlayer(url: String, user: String, pass: String) {
    val authUrl = buildAuthUrl(url, user, pass)
    if (url.startsWith("rtsp")) {
        AndroidView(factory = { ctx -> PlayerView(ctx).apply { useController = false; player = ExoPlayer.Builder(ctx).build().apply { setMediaSource(RtspMediaSource.Factory().setForceUseRtpTcp(true).createMediaSource(MediaItem.fromUri(authUrl))); prepare(); play() } } }, modifier = Modifier.fillMaxSize())
    } else {
        AndroidView(factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; loadUrl(authUrl) } }, modifier = Modifier.fillMaxSize())
    }
}

@Composable
fun ArchiveTab(context: Context, selectedTab: Int, terminalText: String, targetIp: String, onFileClick: (File) -> Unit) {
    var refreshTrigger by remember { mutableIntStateOf(0) }
    LaunchedEffect(selectedTab) { if (selectedTab == 2) refreshTrigger++ }
    val files = remember(refreshTrigger) {
        val allFiles = mutableListOf<File>()
        val docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        docDir?.listFiles()?.let { allFiles.addAll(it.filter { it.isFile }) }
        File(docDir, "CamVigil").listFiles()?.let { allFiles.addAll(it) }
        context.getExternalFilesDir(Environment.DIRECTORY_PICTURES)?.listFiles()?.let { allFiles.addAll(it) }
        allFiles.filter { it.isFile }.sortedByDescending { it.lastModified() }
    }
    Column {
        if (terminalText.length > 50) {
            Button(
                onClick = { generateDetailedPdfReport(context, terminalText, targetIp) },
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Green),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Default.PictureAsPdf, "PDF", tint = Color.Black)
                Spacer(Modifier.width(8.dp))
                Text("GENERATE FULL AUDIT REPORT", color = Color.Black, fontWeight = FontWeight.Black)
            }
        }
        Text("SAVED REPORTS & LOGS", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(files) { file ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onFileClick(file) }, colors = CardDefaults.cardColors(containerColor = Color(0xFF080808)), border = BorderStroke(1.dp, Color(0xFF111111))) {
                    Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Description, null, tint = Color.Gray, modifier = Modifier.size(28.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(file.name, color = Color.White, fontSize = 12.sp, maxLines = 1, modifier = Modifier.weight(1f))
                        IconButton(onClick = { openFile(context, file) }) { Icon(Icons.Default.OpenInNew, null, tint = Color.Green) }
                    }
                }
            }
        }
    }
}

@Composable
fun LanScanTab(
    scanResults: List<DeviceInfo>,
    isScanning: Boolean,
    progress: Float,
    subnet: String,
    onScanStart: () -> Unit,
    onResultFound: (DeviceInfo) -> Unit,
    onScanComplete: () -> Unit,
    onTabSwitch: (Int) -> Unit,
    onIpSelected: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("🔍 LAN Scanner", color = Color.Cyan, fontSize = 22.sp, fontWeight = FontWeight.Black)
        
                    if (subnet.isNotEmpty()) {
                        Text("Subnet: $subnet.0/24", color = Color.Gray, fontSize = 12.sp)
                    }

        Spacer(modifier = Modifier.height(8.dp))

        if (isScanning) {
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth(),
                color = Color.Cyan,
                trackColor = Color.DarkGray
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(onClick = onScanStart, modifier = Modifier.fillMaxWidth(), enabled = !isScanning) {
            Text(if (isScanning) "SCANNING... ${(progress * 100).toInt()}%" else "START SCAN")
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(scanResults) { device ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable { onIpSelected(device.ip) },
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))
                ) {
                    Row(modifier = Modifier.padding(12.dp)) {
                        Icon(Icons.Default.Router, null, tint = Color.Cyan)
                        Spacer(Modifier.width(12.dp))
                        Text(device.ip, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

fun openFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW).apply { setDataAndType(uri, mime); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Open with")) } catch (e: Exception) { Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show() } }
fun shareFile(context: Context, file: File) { try { val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file); context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension) ?: "*/*"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Share via")) } catch (e: Exception) { Toast.makeText(context, "Share Failed", Toast.LENGTH_SHORT).show() } }
fun saveContentToFile(context: Context, content: String, prefix: String, extension: String) { try { val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "${prefix}_${System.currentTimeMillis()}.$extension"); FileOutputStream(file).use { it.write(content.toByteArray()) } } catch (e: Exception) {} }
fun captureScreenshot(context: Context, view: android.view.View) { try { val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888); view.draw(Canvas(bitmap)); val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "Vigil_Capture_${System.currentTimeMillis()}.png"); FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; Toast.makeText(context, "Screenshot Saved", Toast.LENGTH_SHORT).show() } catch (e: Exception) {} }
fun generateHtmlReport(context: Context, terminalText: String) { val report = "<html><body><pre>${terminalText.replace("<", "\&lt;").replace(">", "\&gt;")}</pre></body></html>"; saveContentToFile(context, report, "Vigil_Report", "html") }

fun generatePdfReport(context: Context, terminalText: String) {
    val pdfDocument = PdfDocument()
    val paint = Paint().apply {
        textSize = 10f
        color = android.graphics.Color.BLACK
        typeface = Typeface.MONOSPACE
    }
    
    val lines = terminalText.split("\n")
    val pageWidth = 595 // A4 width in points
    val pageHeight = 842 // A4 height in points
    val margin = 40f
    val contentWidth = pageWidth - 2 * margin
    val lineHeight = 14f
    val linesPerPage = ((pageHeight - 2 * margin) / lineHeight).toInt()
    
    var currentLine = 0
    var pageCount = 1
    
    while (currentLine < lines.size) {
        val pageInfo = PageInfo.Builder(pageWidth, pageHeight, pageCount).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas = page.canvas
        
        canvas.drawText("CAM VIGIL - RECONNAISSANCE REPORT", margin, margin - 10f, Paint().apply {
            textSize = 12f
            isFakeBoldText = true
            typeface = Typeface.DEFAULT_BOLD
        })
        
        var y = margin + lineHeight
        for (i in 0 until linesPerPage) {
            if (currentLine >= lines.size) break
            
            val line = lines[currentLine]
            // Simple text wrapping if line is too long
            if (paint.measureText(line) > contentWidth) {
                var subLine = line
                while (subLine.isNotEmpty()) {
                    val count = paint.breakText(subLine, true, contentWidth, null)
                    canvas.drawText(subLine.substring(0, count), margin, y, paint)
                    y += lineHeight
                    subLine = subLine.substring(count)
                    if (y > pageHeight - margin) break 
                }
            } else {
                canvas.drawText(line, margin, y, paint)
                y += lineHeight
            }
            
            currentLine++
            if (y > pageHeight - margin) break
        }
        
        pdfDocument.finishPage(page)
        pageCount++
    }
    
    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Vigil_Report_${System.currentTimeMillis()}.pdf")
    try {
        pdfDocument.writeTo(FileOutputStream(file))
        Toast.makeText(context, "PDF Report Saved: ${file.name}", Toast.LENGTH_LONG).show()
    } catch (e: Exception) {
        Toast.makeText(context, "Failed to save PDF: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        pdfDocument.close()
    }
}

fun generateDetailedPdfReport(context: Context, terminalText: String, targetIp: String) {
    val pdfDocument = PdfDocument()
    val pageWidth = 595
    val pageHeight = 842
    val margin = 50f
    val paint = Paint()

    // Page 1: Cover Page
    val page1Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 1).create()
    val page1 = pdfDocument.startPage(page1Info)
    val canvas1 = page1.canvas
    
    paint.color = android.graphics.Color.RED
    canvas1.drawRect(margin, 100f, margin + 60f, 160f, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = 40f
    paint.typeface = Typeface.DEFAULT_BOLD
    canvas1.drawText("CV", margin + 5f, 145f, paint)
    
    paint.color = android.graphics.Color.BLACK
    paint.textSize = 28f
    canvas1.drawText("CAMVIGIL AUDIT REPORT", margin + 80f, 140f, paint)
    
    paint.textSize = 14f
    paint.typeface = Typeface.DEFAULT
    canvas1.drawText("Generated on: ${SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()).format(Date())}", margin, 250f, paint)
    canvas1.drawText("Target Host: ${targetIp.ifBlank { "N/A" }}", margin, 275f, paint)
    
    paint.textSize = 16f
    paint.isFakeBoldText = true
    canvas1.drawText("EXECUTIVE SUMMARY", margin, 350f, paint)
    paint.isFakeBoldText = false
    paint.textSize = 12f
    val summary = if (terminalText.contains("CRACKED")) 
        "CRITICAL: Unauthorized access was successfully gained during the scan. Default or weak credentials were discovered, allowing full stream access."
        else "NORMAL: Scan completed. Network mapping and service discovery performed. No immediate authentication bypass found."
    
    val textPaint = Paint(paint).apply { textSize = 12f }
    var ySum = 380f
    val words = summary.split(" ")
    var line = ""
    for (word in words) {
        if (textPaint.measureText("$line $word") < pageWidth - 2 * margin) {
            line += "$word "
        } else {
            canvas1.drawText(line, margin, ySum, textPaint)
            ySum += 20f
            line = "$word "
        }
    }
    canvas1.drawText(line, margin, ySum, textPaint)
    pdfDocument.finishPage(page1)

    // Page 2: Open Ports Table
    val page2Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 2).create()
    val page2 = pdfDocument.startPage(page2Info)
    val canvas2 = page2.canvas
    paint.textSize = 20f
    paint.isFakeBoldText = true
    canvas2.drawText("NETWORK SERVICES & PORTS", margin, 80f, paint)
    
    paint.textSize = 12f
    canvas2.drawRect(margin, 110f, pageWidth - margin, 135f, Paint().apply { color = android.graphics.Color.LTGRAY })
    canvas2.drawText("PORT", margin + 10f, 127f, paint)
    canvas2.drawText("SERVICE", margin + 100f, 127f, paint)
    canvas2.drawText("STATUS", margin + 300f, 127f, paint)
    
    val ports = Regex("""Port (\d+)""").findAll(terminalText).map { it.groupValues[1] }.distinct().toList()
    var yPort = 155f
    paint.isFakeBoldText = false
    ports.forEach { port ->
        val service = when(port) {
            "80", "8080" -> "HTTP / Web"
            "554" -> "RTSP Streaming"
            "8000", "37777" -> "Media SDK"
            else -> "Other Service"
        }
        canvas2.drawText(port, margin + 10f, yPort, paint)
        canvas2.drawText(service, margin + 100f, yPort, paint)
        canvas2.drawText("OPEN", margin + 300f, yPort, paint)
        canvas2.drawLine(margin, yPort + 5f, pageWidth - margin, yPort + 5f, Paint().apply { color = android.graphics.Color.LTGRAY; strokeWidth = 1f })
        yPort += 25f
    }
    pdfDocument.finishPage(page2)

    // Page 3: Credentials Found
    val page3Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 3).create()
    val page3 = pdfDocument.startPage(page3Info)
    val canvas3 = page3.canvas
    paint.textSize = 20f
    paint.isFakeBoldText = true
    canvas3.drawText("AUTHENTICATION BYPASS", margin, 80f, paint)
    
    val (user, pass) = extractCredentials(terminalText)
    paint.textSize = 14f
    paint.isFakeBoldText = false
    if (user.isNotEmpty()) {
        paint.color = android.graphics.Color.RED
        canvas3.drawText("STATUS: SUCCESSFUL ATTACK", margin, 120f, paint)
        paint.color = android.graphics.Color.BLACK
        canvas3.drawRect(margin, 150f, pageWidth - margin, 230f, Paint().apply { color = android.graphics.Color.rgb(240, 240, 240) })
        canvas3.drawText("Username: $user", margin + 20f, 185f, paint)
        canvas3.drawText("Password: $pass", margin + 20f, 210f, paint)
    } else {
        canvas3.drawText("No cracked credentials found in this session.", margin, 120f, paint)
    }
    pdfDocument.finishPage(page3)

    // Page 4: Stream URLs
    val page4Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 4).create()
    val page4 = pdfDocument.startPage(page4Info)
    val canvas4 = page4.canvas
    paint.textSize = 20f
    paint.isFakeBoldText = true
    canvas4.drawText("DISCOVERED STREAMS", margin, 80f, paint)
    
    paint.textSize = 10f
    paint.isFakeBoldText = false
    paint.typeface = Typeface.MONOSPACE
    val streams = terminalText.lines().filter { it.contains("http") || it.contains("rtsp") }.distinct()
    var yStream = 120f
    streams.take(30).forEach { stream ->
        val url = Regex("""(rtsp://[^\s]+|http://[^\s]+)""").find(stream)?.value ?: stream
        canvas4.drawText("• $url", margin, yStream, paint)
        yStream += 18f
    }
    pdfDocument.finishPage(page4)

    // Page 5: Vulnerabilities
    val page5Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 5).create()
    val page5 = pdfDocument.startPage(page5Info)
    val canvas5 = page5.canvas
    paint.typeface = Typeface.DEFAULT
    paint.textSize = 20f
    paint.isFakeBoldText = true
    canvas5.drawText("SECURITY VULNERABILITIES", margin, 80f, paint)
    
    val vulns = terminalText.lines().filter { it.contains("VULNERABILITY") || it.contains("CRITICAL") || it.contains("FIRE") || it.contains("CVE") }.distinct()
    var yVuln = 120f
    paint.textSize = 12f
    paint.isFakeBoldText = false
    if (vulns.isEmpty()) {
        canvas5.drawText("No specific vulnerabilities identified via automated analysis.", margin, yVuln, paint)
    } else {
        vulns.forEach { vuln ->
            canvas5.drawText("⚠ $vuln", margin, yVuln, paint)
            yVuln += 22f
        }
    }
    pdfDocument.finishPage(page5)

    // Page 6: Recommendations
    val page6Info = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, 6).create()
    val page6 = pdfDocument.startPage(page6Info)
    val canvas6 = page6.canvas
    paint.textSize = 20f
    paint.isFakeBoldText = true
    canvas6.drawText("MITIGATION STRATEGIES", margin, 80f, paint)
    
    val recs = listOf(
        "• REMOVE DEFAULT CREDENTIALS: Set a strong, unique password for all administrative accounts.",
        "• DISABLE UPNP: Turn off Universal Plug and Play on the edge router to prevent automatic port forwarding.",
        "• UPDATE FIRMWARE: Apply latest manufacturer patches to remediate known CVE exploits.",
        "• NETWORK ISOLATION: Place cameras on a separate VLAN with restricted outbound internet access.",
        "• ENCRYPT TRAFFIC: Use HTTPS for web management and SRTP/RTSPS if supported by the hardware.",
        "• AUDIT ACCESS LOGS: Regularly review device logs for unauthorized login attempts."
    )
    var yRec = 130f
    paint.textSize = 11f
    paint.isFakeBoldText = false
    recs.forEach { rec ->
        var line = ""
        val recWords = rec.split(" ")
        for (word in recWords) {
            if (paint.measureText("$line $word") < pageWidth - 2 * margin) {
                line += "$word "
            } else {
                canvas6.drawText(line, margin, yRec, paint)
                yRec += 20f
                line = "  $word "
            }
        }
        canvas6.drawText(line, margin, yRec, paint)
        yRec += 35f
    }
    pdfDocument.finishPage(page6)

    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "CamVigil")
    if (!dir.exists()) dir.mkdirs()
    val file = File(dir, "CamVigil_Audit_${System.currentTimeMillis()}.pdf")
    
    try {
        pdfDocument.writeTo(FileOutputStream(file))
        Toast.makeText(context, "Full Audit Report Ready", Toast.LENGTH_SHORT).show()
        shareFile(context, file)
    } catch (e: Exception) {
        Toast.makeText(context, "Generation Failed: ${e.message}", Toast.LENGTH_SHORT).show()
    } finally {
        pdfDocument.close()
    }
}

fun saveJsonReport(context: Context, terminalText: String, target: String) { try { val report = JSONObject().apply { put("target", target); put("raw", terminalText) }; saveContentToFile(context, report.toString(4), "Vigil_Data", "json") } catch (e: Exception) {} }

@Composable
fun StormTab(terminalText: String, onLogUpdate: (String) -> Unit) {
    var targetIp by remember { mutableStateOf("") }; var stormOutput by remember { mutableStateOf("> Ready for stress testing...\n") }; val scope = rememberCoroutineScope()
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("⚡ STORM MODULE", color = Color(0xFFFFBF00), fontSize = 24.sp, fontWeight = FontWeight.Black)
        OutlinedTextField(value = targetIp, onValueChange = { targetIp = it }, label = { Text("Target IP") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { stormOutput += "> Initiating on $targetIp...\n"; scope.launch(Dispatchers.IO) { try { val py = Python.getInstance(); val module = py.getModule("CamXploit"); val sys = py.getModule("sys"); sys.put("stdout", TerminalOutputStream { text -> scope.launch(Dispatchers.Main) { stormOutput += text } }); module.callAttr("test_dos_resilience", targetIp, 80) } catch (e: Exception) { withContext(Dispatchers.Main) { stormOutput += "\nError: ${e.message}" } } } }, modifier = Modifier.fillMaxWidth()) { Text("START STRESS TEST") }
        Surface(modifier = Modifier.fillMaxWidth().height(200.dp).padding(top = 16.dp), color = Color.Black) { Text(text = stormOutput, color = Color.Yellow, fontFamily = FontFamily.Monospace, fontSize = 12.sp, modifier = Modifier.verticalScroll(rememberScrollState())) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveViewScreen(streamUrl: String, streamType: String, onBack: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("📺 $streamType") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        if (streamUrl.startsWith("rtsp")) { AndroidView(factory = { ctx -> PlayerView(ctx).apply { player = ExoPlayer.Builder(ctx).build().apply { setMediaSource(RtspMediaSource.Factory().createMediaSource(MediaItem.fromUri(streamUrl))); prepare(); play() } } }, modifier = Modifier.fillMaxSize()) }
        else { AndroidView(factory = { ctx -> WebView(ctx).apply { settings.javaScriptEnabled = true; loadUrl(streamUrl) } }, modifier = Modifier.fillMaxSize()) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapshotViewer(imageUrl: String, onBack: () -> Unit) {
    var bitmap by remember { mutableStateOf<ImageBitmap?>(null) }; LaunchedEffect(imageUrl) { withContext(Dispatchers.IO) { try { val b = BitmapFactory.decodeStream(URL(imageUrl).openStream()); bitmap = b.asImageBitmap() } catch (e: Exception) {} } }
    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(title = { Text("📸 Snapshot") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null) } })
        bitmap?.let { Image(bitmap = it, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit) }
    }
}

fun getLocalSubnet(): String { try { val interfaces = NetworkInterface.getNetworkInterfaces(); for (inf in Collections.list(interfaces)) { if (inf.isLoopback || !inf.isUp) continue; for (addr in Collections.list(inf.inetAddresses)) { if (addr is Inet4Address && !addr.isLoopbackAddress) return addr.hostAddress.substringBeforeLast(".") + ".0/24" } } } catch (e: Exception) {}; return "192.168.1.0/24" }

@Composable
fun SavedCamerasTab(onStream: (String) -> Unit, onScan: (String) -> Unit) {
    val context = LocalContext.current; val dao = remember { CameraDatabase.getDatabase(context).cameraDao() }; val cameras by dao.getAllCameras().collectAsState(initial = emptyList())
    var isMonitoring by remember { mutableStateOf(CameraMonitorService.isRunning) }
    
    LaunchedEffect(Unit) {
        isMonitoring = CameraMonitorService.isRunning
    }

    val infiniteTransition = rememberInfiniteTransition(label = "monitorPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("SAVED TARGETS", color = Color.Green, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                if (isMonitoring) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Box(modifier = Modifier.size(8.dp).background(Color.Green.copy(alpha = pulseAlpha), CircleShape))
                }
            }
            
            Switch(
                checked = isMonitoring,
                onCheckedChange = { checked ->
                    val intent = Intent(context, CameraMonitorService::class.java)
                    if (checked) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            context.startForegroundService(intent)
                        } else {
                            context.startService(intent)
                        }
                    } else {
                        context.stopService(intent)
                    }
                    isMonitoring = checked
                },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.Green,
                    checkedTrackColor = Color.Green.copy(alpha = 0.5f),
                    uncheckedThumbColor = Color.Gray,
                    uncheckedTrackColor = Color.DarkGray
                )
            )
        }
        Text(
            text = if (isMonitoring) "BACKGROUND MONITORING ACTIVE" else "MONITORING DISABLED",
            color = if (isMonitoring) Color.Green else Color.Gray,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        LazyColumn {
            items(cameras) { camera ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0A0A))) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(camera.nickname, color = Color.White, fontWeight = FontWeight.Bold)
                            Box(modifier = Modifier.size(8.dp).background(if (camera.isOnline) Color.Green else Color.Red, CircleShape).align(Alignment.CenterVertically))
                        }
                        Text(camera.ip, color = Color.Cyan)
                        Row {
                            Button(onClick = { onStream(camera.streamUrl) }) { Text("STREAM") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = { onScan(camera.ip) }) { Text("SCAN") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AddCameraDialog(onDismiss: () -> Unit, onSave: (SavedCamera) -> Unit) {
    var nick by remember { mutableStateOf("") }; var ip by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, confirmButton = { Button(onClick = { onSave(SavedCamera(nickname = nick, ip = ip, port = 80, username = "admin", password = "admin", streamUrl = "", brand = "Manual")) }) { Text("SAVE") } }, title = { Text("Add Camera") }, text = { Column { OutlinedTextField(nick, { nick = it }, label = { Text("Nickname") }); OutlinedTextField(ip, { ip = it }, label = { Text("IP") }) } })
}

fun extractCredentials(terminalText: String): Pair<String, String> { val match = Regex("""CRACKED \((?:HTTP|RTSP)\): ([^:]+):([^\s\n]+)""").find(terminalText); return if (match != null) match.groupValues[1] to match.groupValues[2] else "" to "" }
fun buildAuthUrl(url: String, user: String, pass: String): String { if (user.isBlank() || pass.isBlank() || url.contains("@")) return url; return try { if (url.startsWith("rtsp://")) url.replace("rtsp://", "rtsp://$user:$pass@") else if (url.startsWith("http://")) url.replace("http://", "http://$user:$pass@") else url } catch (e: Exception) { url } }
data class DeviceInfo(val ip: String, val hostname: String?, val openPorts: List<Int>)
