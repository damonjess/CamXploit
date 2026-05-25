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
import androidx.compose.ui.text.TextStyle

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

@Composable
fun CamXploitUI() {
    var ipInput by remember { mutableStateOf("") }
    var terminalText by remember { mutableStateOf("CamXploit Mobile Ready\n") }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.background(Color.Black).padding(16.dp).fillMaxSize()) {
        OutlinedTextField(
            value = ipInput,
            onValueChange = { ipInput = it },
            label = { Text("Target IP Address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = TextStyle(color = Color.White),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.White,
                unfocusedIndicatorColor = Color.Gray,
                focusedLabelColor = Color.White,
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        Button(
            onClick = {
                terminalText = ">>> Scanning $ipInput...\n"
                scope.launch(Dispatchers.IO) {
                    try {
                        val py = Python.getInstance()
                        val module = py.getModule("CamXploit")
                        val sys = py.getModule("sys")
                        val io = py.getModule("io")
                        val outputStream = io.callAttr("StringIO")
                        sys.put("stdout", outputStream)

                        module.callAttr("main", ipInput)
                        
                        val result = outputStream.callAttr("getvalue").toString()
                        withContext(Dispatchers.Main) {
                            terminalText += result
                        }
                    } catch (e: Exception) {
                        withContext(Dispatchers.Main) {
                            terminalText += "\nError: ${e.message}"
                        }
                    }
                }
            },
            modifier = Modifier.padding(vertical = 8.dp).fillMaxWidth()
        ) {
            Text("START SCAN")
        }

        Surface(
            modifier = Modifier.fillMaxSize().weight(1f),
            color = Color.Black,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = terminalText,
                color = Color.Green,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                modifier = Modifier.padding(8.dp).verticalScroll(scrollState)
            )
        }
        
        LaunchedEffect(terminalText) {
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }
}
