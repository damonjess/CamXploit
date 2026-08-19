package com.spyboy.camxploit

import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.spyboy.camxploit.osint.InsecamScraper
import com.spyboy.camxploit.osint.OpentopiaScraper
import com.spyboy.camxploit.osint.CameraUrlProbe
import com.spyboy.camxploit.ui.AutoRefreshImage
import com.spyboy.camxploit.ui.FastMjpegPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private val neonCyan = Color(0xFF00FFFF)
private val neonGreen = Color(0xFF39FF14)
private val neonRed = Color(0xFFFF0033)
private val orange = Color(0xFFFFA500)

fun guessMjpegFromSnapshot(snapshotUrl: String): String? {
    val replacements = listOf(
        "current.jpg" to "video.mjpg",
        "snapshot.jpg" to "video.mjpg",
        "image.jpg" to "video.mjpg",
        "still.jpg" to "video.mjpg",
        "jpg" to "mjpg"
    )
    val lower = snapshotUrl.lowercase()
    for ((old, new) in replacements) {
        if (lower.contains(old)) {
            return snapshotUrl.replace(old, new, ignoreCase = true)
        }
    }
    return null
}

@UnstableApi
class StreamViewerActivity : ComponentActivity() {

    private val viewModel: StreamViewModel by viewModels()
    private var streamSource: StreamSource? = null
    private var targetIp: String = ""

    companion object {
        private const val EXTRA_SOURCE = "stream_source"
        private const val EXTRA_IP     = "target_ip"

        fun launch(context: Context, source: StreamSource, ip: String) {
            val intent = Intent(context, StreamViewerActivity::class.java).apply {
                putExtra(EXTRA_SOURCE, source)
                putExtra(EXTRA_IP,     ip)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        streamSource = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(EXTRA_SOURCE, StreamSource::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(EXTRA_SOURCE)
        }
        targetIp = intent.getStringExtra(EXTRA_IP) ?: "Unknown"

        setContent {
            val status by viewModel.status.collectAsState()
            val mjpegBitmap by viewModel.mjpegBitmap.collectAsState()
            val streamInfo by viewModel.streamInfo.collectAsState()
            val isRecording by viewModel.isRecording.collectAsState()
            val recordingDuration by viewModel.recordingDuration.collectAsState()

            var showAuthDialog by remember { mutableStateOf(false) }

            LaunchedEffect(status) {
                if (status is StreamStatus.Unauthorized) {
                    showAuthDialog = true
                }
            }

            Surface(modifier = Modifier.fillMaxSize(), color = Color.Black) {
                StreamPlayerScreen(
                    source = streamSource,
                    targetIp = targetIp,
                    status = status,
                    mjpegBitmap = mjpegBitmap,
                    streamInfo = streamInfo,
                    isRecording = isRecording,
                    recordingDuration = recordingDuration,
                    onBack = { finish() },
                    onRetry = { streamSource?.let { viewModel.startStream(it) } },
                    onSnapshot = { view -> takeSnapshot(view) },
                    onPip = { enterPipMode() },
                    onRecord = { viewModel.toggleRecording() }
                )

                if (showAuthDialog) {
                    AuthDialog(
                        targetIp = targetIp,
                        onDismiss = { showAuthDialog = false },
                        onConnect = { user, pass ->
                            showAuthDialog = false
                            val brand = when {
                                targetIp.lowercase().contains("hik") -> CameraBrand.Hikvision
                                targetIp.lowercase().contains("dahua") -> CameraBrand.Dahua
                                else -> CameraBrand.Generic
                            }
                            viewModel.probeWithCredentials(targetIp, brand, user, pass)
                        }
                    )
                }
            }
        }

        // HTTP camera URLs are classified by StreamPlayerScreen before playback begins.
        // Starting them here can create a second MJPEG connection or treat HTTP as RTSP.
        if (savedInstanceState == null) {
            streamSource
                ?.takeIf { it.protocol.equals("rtsp", true) || it.protocol.equals("rtmp", true) || it.protocol.equals("onvif", true) }
                ?.let { viewModel.startStream(it) }
        }
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(16, 9))
                .build()
            enterPictureInPictureMode(params)
        } else {
            @Suppress("DEPRECATION")
            enterPictureInPictureMode()
        }
    }

    private fun takeSnapshot(view: View) {
        // We'll handle snapshotting based on whether it's MJPEG (ImageView/Bitmap) or ExoPlayer (SurfaceView)
        if (streamSource?.protocol?.lowercase() == "mjpeg") {
            val bitmap = viewModel.mjpegBitmap.value
            if (bitmap != null) {
                saveSnapshotToArchive(bitmap)
            } else {
                Toast.makeText(this, "No frame available", Toast.LENGTH_SHORT).show()
            }
            return
        }

        // For ExoPlayer, we find the PlayerView in the hierarchy
        val playerView = findViewByType(view, PlayerView::class.java)
        val surfaceView = playerView?.videoSurfaceView as? SurfaceView
        
        if (surfaceView == null) {
            Toast.makeText(this, "Stream surface not ready", Toast.LENGTH_SHORT).show()
            return
        }

        val bitmap = Bitmap.createBitmap(surfaceView.width, surfaceView.height, Bitmap.Config.ARGB_8888)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            PixelCopy.request(surfaceView, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) {
                    saveSnapshotToArchive(bitmap)
                } else {
                    runOnUiThread { Toast.makeText(this, "Capture failed", Toast.LENGTH_SHORT).show() }
                }
            }, Handler(Looper.getMainLooper()))
        } else {
            val canvas = Canvas(bitmap)
            surfaceView.draw(canvas)
            saveSnapshotToArchive(bitmap)
        }
    }

    private fun <T> findViewByType(root: View, type: Class<T>): T? {
        if (type.isInstance(root)) return type.cast(root)
        if (root is android.view.ViewGroup) {
            for (i in 0 until root.childCount) {
                val found = findViewByType(root.getChildAt(i), type)
                if (found != null) return found
            }
        }
        return null
    }

    private fun saveSnapshotToArchive(bitmap: Bitmap) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val safIp     = targetIp.replace(".", "_")
            val filename  = "Vigil_Snap_${safIp}_$timestamp.jpg"

            val picDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
            val imgFile = File(picDir, filename)
            FileOutputStream(imgFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
            }

            // Metadata JSON
            val meta = org.json.JSONObject().apply {
                put("type",      "snapshot")
                put("target_ip", targetIp)
                put("stream_url", streamSource?.url ?: "")
                put("timestamp", System.currentTimeMillis())
                put("filename",  filename)
                put("width",     bitmap.width)
                put("height",    bitmap.height)
            }
            val docDir   = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val metaFile = File(docDir, "Vigil_Snap_${safIp}_$timestamp.json")
            FileOutputStream(metaFile).use { it.write(meta.toString(2).toByteArray()) }

            runOnUiThread {
                Toast.makeText(this, "📷 Snapshot saved to Archive", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            runOnUiThread {
                Toast.makeText(this, "Save failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onPause()   { super.onPause(); if (!isInPictureInPictureMode) viewModel.getPlayer().playWhenReady = false }
    override fun onResume()  { super.onResume(); viewModel.getPlayer().playWhenReady = true }
}

@OptIn(ExperimentalMaterial3Api::class)
@androidx.media3.common.util.UnstableApi
@Composable
fun StreamPlayerScreen(
    source: StreamSource?,
    targetIp: String,
    status: StreamStatus,
    mjpegBitmap: Bitmap?,
    streamInfo: String,
    isRecording: Boolean,
    recordingDuration: Long,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onSnapshot: (View) -> Unit,
    onPip: () -> Unit,
    onRecord: () -> Unit,
    viewModel: StreamViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
) {
    val context = LocalContext.current
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }
    var resolvedUrl by remember { mutableStateOf(source?.url ?: "") }
    var isScraping by remember { mutableStateOf(false) }
    var probeResult by remember { mutableStateOf<CameraUrlProbe.Result?>(null) }

    val rawUrl = source?.url ?: ""
    val title = source?.title ?: targetIp

    val isWrapper = rawUrl.contains("insecam.org", ignoreCase = true) ||
                    rawUrl.contains("opentopia.com", ignoreCase = true)

    // Probe the URL to determine what kind of feed it actually is
    if (!isScraping && probeResult == null && rawUrl.isNotBlank()) {
        isScraping = true
        LaunchedEffect(rawUrl) {
            val result = withContext(Dispatchers.IO) {
                when {
                    isWrapper -> {
                        val direct = if (rawUrl.contains("insecam")) {
                            InsecamScraper.scrapePage(rawUrl).streamUrl
                        } else {
                            OpentopiaScraper.scrapeDetailPage(rawUrl)?.first
                        }
                        if (!direct.isNullOrBlank() && direct != rawUrl) {
                            CameraUrlProbe.probe(direct)
                        } else {
                            CameraUrlProbe.probe(rawUrl)
                        }
                    }
                    else -> CameraUrlProbe.probe(rawUrl)
                }
            }
            probeResult = result
            resolvedUrl = result.url
            isScraping = false
            isLoading = false

            // Use one effective protocol for both playback state and renderer selection.
            // A declared MJPEG source is retained when the probe is inconclusive, but an
            // explicit snapshot response always takes precedence.
            if (source != null) {
                val declaredMjpeg = source.protocol.equals("mjpeg", ignoreCase = true)
                val effectiveProtocol = when {
                    result.isMjpeg || (declaredMjpeg && !result.isSnapshot) -> "mjpeg"
                    result.isSnapshot -> "snapshot"
                    else -> source.protocol
                }
                val effectiveSource = source.copy(url = result.url, protocol = effectiveProtocol)

                if (effectiveProtocol.equals("mjpeg", ignoreCase = true)) {
                    viewModel.prepareMjpegPlayback(effectiveSource)
                } else {
                    viewModel.startStream(effectiveSource)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(resolvedUrl, color = Color.Gray, fontSize = 10.sp, maxLines = 1)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back", tint = Color.White)
                    }
                },
                actions = {
                    StatusIndicator(status, isRecording, recordingDuration)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF0D0D0D)),
                modifier = Modifier.statusBarsPadding()
            )
        },
        bottomBar = {
            Surface(
                color = Color(0xFF0D0D0D),
                modifier = Modifier.navigationBarsPadding()
            ) {
                BottomControls(
                    isRecording = isRecording,
                    onRetry = {
                        probeResult = null
                        onRetry()
                    },
                    onSnapshot = { onSnapshot(it) },
                    onPip = onPip,
                    onRecord = onRecord
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black)
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                when {
                    rawUrl.isBlank() -> {
                        Text(
                            "No stream URL provided",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    isScraping -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = neonCyan)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Analyzing camera feed...",
                                color = Color.Gray,
                                fontSize = 12.sp
                            )
                        }
                    }
                    hasError -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Failed to load stream.\nThe camera may be offline.",
                                color = Color.Red,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(16.dp)
                            )
                            Button(onClick = {
                                val useNativeMjpeg = source?.protocol.equals("mjpeg", ignoreCase = true) || probeResult?.isMjpeg == true
                                hasError = false
                                probeResult = null
                                // The native player reconnects when it re-enters composition. Avoid
                                // restarting the ViewModel MJPEG decoder in parallel.
                                if (!useNativeMjpeg) onRetry()
                            }) {
                                Text("Retry")
                            }
                        }
                    }
                    else -> {
                        val result = probeResult
                        val lowerUrl = resolvedUrl.lowercase()
                        
                        when {
                            // True MJPEG stream → native decoder
                            result?.isMjpeg == true ||
                                (source?.protocol.equals("mjpeg", ignoreCase = true) && result?.isSnapshot != true) ||
                                lowerUrl.contains(".mjpg") -> {
                                FastMjpegPlayer(
                                    url = resolvedUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    onFrame = viewModel::onMjpegFrame,
                                    onError = { hasError = true }
                                )
                            }

                            // Snapshot JPEG → auto-refresh every 1.5s
                            result?.isSnapshot == true -> {
                                AutoRefreshImage(
                                    url = resolvedUrl,
                                    modifier = Modifier.fillMaxSize(),
                                    refreshMs = 1500,
                                    onError = { hasError = true }
                                )
                            }
                            
                            // RTSP / RTMP feeds
                            lowerUrl.startsWith("rtsp://") || lowerUrl.startsWith("rtmp://") -> {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = viewModel.getPlayer()
                                            useController = true
                                            setBackgroundColor(0xFF000000.toInt())
                                        }
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // HTML wrapper or unknown → WebView fallback
                            else -> {
                                WebPlayer(
                                    url = resolvedUrl,
                                    onLoadingChange = { isLoading = it },
                                    onError = { hasError = true }
                                )
                            }
                        }
                    }
                }
                
                if (isLoading && !isScraping && rawUrl.isNotBlank() && probeResult != null) {
                    CircularProgressIndicator(color = neonCyan)
                }
            }

            // Info Panel
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF121212))
                    .padding(8.dp)
            ) {
                Text(
                    text = streamInfo,
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun WebPlayer(
    url: String,
    autoRefresh: Boolean = false,
    onLoadingChange: (Boolean) -> Unit,
    onError: () -> Unit
) {
    val context = LocalContext.current
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    if (autoRefresh) {
        LaunchedEffect(url) {
            while (true) {
                kotlinx.coroutines.delay(15_000) // 15s refresh for Opentopia fallback
                withContext(Dispatchers.Main) {
                    webViewRef?.reload()
                }
            }
        }
    }

    AndroidView(
        factory = {
            WebView(context).apply {
                webViewRef = this
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                // Force hardware acceleration — critical for smooth WebView
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    cacheMode = WebSettings.LOAD_DEFAULT
                    useWideViewPort = true
                    loadWithOverviewMode = true
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                }
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        onLoadingChange(false)
                    }
                    override fun onReceivedError(
                        view: WebView?,
                        request: android.webkit.WebResourceRequest?,
                        error: android.webkit.WebResourceError?
                    ) {
                        onLoadingChange(false)
                        onError()
                    }
                }
                loadUrl(url)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
fun StatusIndicator(status: StreamStatus, isRecording: Boolean, duration: Long) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
        if (isRecording) {
            val mins = duration / 60
            val secs = duration % 60
            Text(
                "● RECORDING ${String.format("%02d:%02d", mins, secs)}",
                color = neonRed,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        } else {
            val (text, color) = when (status) {
                is StreamStatus.Live -> "● LIVE" to neonGreen
                is StreamStatus.Snapshot -> "● LIVE (Snapshot)" to neonGreen
                is StreamStatus.Web -> "● WEB" to neonCyan
                is StreamStatus.Connecting, is StreamStatus.Buffering -> "● SYNCING" to orange
                is StreamStatus.Unauthorized -> "● LOCKED" to neonRed
                is StreamStatus.Error -> "● OFFLINE" to Color.Red
                else -> "● IDLE" to Color.Gray
            }
            Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun BottomControls(
    isRecording: Boolean,
    onRetry: () -> Unit,
    onSnapshot: (View) -> Unit,
    onPip: () -> Unit,
    onRecord: () -> Unit
) {
    val context = LocalContext.current
    val activity = remember(context) { 
        var c = context
        while (c is android.content.ContextWrapper) {
            if (c is android.app.Activity) break
            c = c.baseContext
        }
        c as? android.app.Activity
    }
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0D0D0D))
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ControlBtn("↺", "RETRY", Modifier.weight(1f), onClick = onRetry)
        ControlBtn("📷", "SNAP", Modifier.weight(1f), color = Color(0xFF00AA00)) {
            activity?.window?.decorView?.let { onSnapshot(it) }
        }
        ControlBtn("📺", "PIP", Modifier.weight(1f), color = Color(0xFF0000AA), onClick = onPip)
        ControlBtn(
            if (isRecording) "⏹" else "⏺",
            if (isRecording) "STOP" else "RECORD",
            Modifier.weight(1.2f),
            color = if (isRecording) Color.Gray else Color(0xFFAA0000),
            onClick = onRecord
        )
    }
}

@Composable
fun ControlBtn(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF333333),
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(44.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        shape = RoundedCornerShape(4.dp),
        contentPadding = PaddingValues(0.dp)
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(icon, fontSize = 14.sp)
            Text(label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun AuthDialog(targetIp: String, onDismiss: () -> Unit, onConnect: (String, String) -> Unit) {
    var user by remember { mutableStateOf("admin") }
    var pass by remember { mutableStateOf("") }
    val neonCyan = Color(0xFF00FFFF)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("AUTHENTICATION", color = neonCyan, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Target: $targetIp", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
                TextField(
                    value = user,
                    onValueChange = { user = it },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                )
                TextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConnect(user, pass) }) {
                Text("CONNECT", color = neonCyan)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("CANCEL", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1A1A1A),
        textContentColor = Color.White
    )
}
