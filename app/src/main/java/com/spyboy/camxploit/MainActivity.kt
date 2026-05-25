package com.spyboy.camxploit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight

import java.io.OutputStream

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!Python.isStarted()) {
            Python.start(AndroidPlatform(this))
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                CamXploitUI()
            }
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
    fun write(s: String) {
        onUpdate(s)
    }

    override fun flush() {}
}

@Composable
fun CamXploitUI() {
    var ipInput by remember { mutableStateOf("") }
    var terminalText by remember { mutableStateOf("> CamXploit Mobile Console Ready\n") }
    var isScanning by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Δ AI SCANNER",
                color = Color.Green,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = "Reset",
                tint = Color.Cyan,
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "CAM-XPLOIT CONSOLE",
            color = Color.Red,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Custom Input Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Color.Red, RoundedCornerShape(4.dp))
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Column {
                Text(
                    text = "TARGET IP / HOST",
                    color = Color.Red,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BasicTextField(
                        value = ipInput,
                        onValueChange = { ipInput = it },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 18.sp,
                            fontFamily = FontFamily.Monospace
                        ),
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Color.White)
                    )
                    IconButton(
                        onClick = {
                            if (ipInput.isNotEmpty() && !isScanning) {
                                isScanning = true
                                terminalText = "> Initiating CamXploit Reconnaissance on $ipInput...\n"
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        val py = Python.getInstance()
                                        val module = py.getModule("CamXploit")
                                        
                                        val sys = py.getModule("sys")
                                        val outputStream = TerminalOutputStream { text ->
                                            scope.launch(Dispatchers.Main) {
                                                terminalText += text
                                            }
                                        }
                                        sys.put("stdout", outputStream)

                                        module.callAttr("main", ipInput)
                                        
                                        withContext(Dispatchers.Main) {
                                            isScanning = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            terminalText += "\n[!] Error: ${e.message}"
                                            isScanning = false
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "Scan",
                            tint = Color.Green
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Console Output
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .border(1.dp, Color.Gray, RoundedCornerShape(4.dp)),
            color = Color.Black
        ) {
            Text(
                text = terminalText,
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                modifier = Modifier
                    .padding(8.dp)
                    .verticalScroll(scrollState)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Bottom Status Box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .border(1.dp, Color(0xFF333333), RoundedCornerShape(8.dp))
                .padding(16.dp)
        ) {
            Text(
                text = if (isScanning) "SCANNING TARGET..." else if (terminalText.contains("Potential Stream")) "TARGET VULNERABLE" else "TARGET SECURE",
                color = if (isScanning) Color.Yellow else if (terminalText.contains("Potential Stream")) Color.Red else Color(0xFF00FF00),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
        
        LaunchedEffect(terminalText) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}
