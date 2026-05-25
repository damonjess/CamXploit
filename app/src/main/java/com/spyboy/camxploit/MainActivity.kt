package com.spyboy.camxploit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.os.Environment
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.*

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
    var selectedTab by remember { mutableIntStateOf(0) }
    var terminalText by remember { mutableStateOf("> System Initialized. Awaiting Target...\n") }
    var ipInput by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

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
                    label = { Text("CONSOLE") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Green,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color.Green,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Home, "Intel") },
                    label = { Text("INTEL") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Cyan,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color.Cyan,
                        indicatorColor = Color(0xFF1E1E1E)
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.List, "Files") },
                    label = { Text("ARCHIVE") },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.Yellow,
                        unselectedIconColor = Color.Gray,
                        selectedTextColor = Color.Yellow,
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
                                    withContext(Dispatchers.Main) { isScanning = false }
                                } catch (e: Exception) {
                                    withContext(Dispatchers.Main) {
                                        terminalText += "\n[!] ERROR: ${e.message}"
                                        isScanning = false
                                    }
                                }
                            }
                        }
                    }
                )
                1 -> IntelTab(terminalText)
                2 -> ArchiveTab(context)
            }
        }
    }
}

@Composable
fun ConsoleTab(
    ipInput: String,
    onIpChange: (String) -> Unit,
    terminalText: String,
    onTerminalClear: () -> Unit,
    isScanning: Boolean,
    scrollState: androidx.compose.foundation.ScrollState,
    onStartScan: () -> Unit
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
    }
}

@Composable
fun IntelTab(terminalText: String) {
    val camerasFound = terminalText.split("Potential Stream").size - 1
    val portsOpen = terminalText.split("[OPEN]").size - 1
    val isVulnerable = terminalText.contains("Success!") || terminalText.contains("Potential Stream")

    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("RECON SUMMARY", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            InfoCard("DEVICES FOUND", camerasFound.toString(), Color.Green, Modifier.weight(1f))
            InfoCard("PORTS OPEN", portsOpen.toString(), Color.Cyan, Modifier.weight(1f))
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        InfoCard(
            "THREAT LEVEL",
            if (isVulnerable) "VULNERABLE" else if (portsOpen > 0) "SUSPICIOUS" else "SECURE",
            if (isVulnerable) Color.Red else if (portsOpen > 0) Color.Yellow else Color.Green,
            Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(20.dp))
        Text("EXTRACTED INTEL", color = Color.Cyan, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(8.dp))
        
        // Simple list of found URLs
        terminalText.lines().filter { it.contains("http") || it.contains("rtsp") }.forEach { line ->
            Text(
                text = line.trim(),
                color = Color.LightGray,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }
    }
}

@Composable
fun InfoCard(label: String, value: String, color: Color, modifier: Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, Color(0xFF222222), RoundedCornerShape(8.dp))
            .background(Color(0xFF0F0F0F))
            .padding(16.dp)
    ) {
        Column {
            Text(label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(value, color = color, fontSize = 20.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun ArchiveTab(context: Context) {
    val docDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
    val files = docDir?.listFiles()?.sortedByDescending { it.lastModified() } ?: emptyList()

    Column {
        Text("SAVED REPORTS & LOGS", color = Color.Yellow, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        Spacer(modifier = Modifier.height(12.dp))
        
        if (files.isEmpty()) {
            Text("No reports found in archive.", color = Color.DarkGray, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(20.dp))
        } else {
            files.forEach { file ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp)
                        .border(1.dp, Color(0xFF111111), RoundedCornerShape(4.dp))
                        .background(Color(0xFF080808))
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(file.name, color = Color.White, fontSize = 13.sp, maxLines = 1)
                        Text(Date(file.lastModified()).toString(), color = Color.Gray, fontSize = 10.sp)
                    }
                    Icon(
                        imageVector = if (file.name.endsWith("html")) Icons.Default.List else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (file.name.endsWith("html")) Color.Green else Color.Cyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

// Global functions
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
