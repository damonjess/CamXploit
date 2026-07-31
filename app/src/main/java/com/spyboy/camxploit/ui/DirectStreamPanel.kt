package com.spyboy.camxploit.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spyboy.camxploit.StreamActivity

@androidx.media3.common.util.UnstableApi
@Composable
fun DirectStreamPanel() {
    val context = LocalContext.current
    val neonGreen = Color(0xFF39FF14)
    val darkCard = Color(0xFF1A1A1A)
    val cyan = Color(0xFF00FFFF)

    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    Column {
        Text("DIRECT STREAM", fontSize = 14.sp, color = neonGreen, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            placeholder = { Text("rtsp:// or http:// URL", color = Color.DarkGray) },
            label = { Text("Stream URL", color = Color.Gray, fontSize = 11.sp) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = cyan,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(6.dp))
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            placeholder = { Text("Label (optional)", color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = Color.DarkGray,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))
        Button(
            onClick = {
                if (url.isBlank()) return@Button
                val mode = if (url.startsWith("rtsp://")) "exo" else "webview"
                context.startActivity(
                    Intent(context, StreamActivity::class.java).apply {
                        putExtra(StreamActivity.EXTRA_MODE, mode)
                        putExtra(StreamActivity.EXTRA_URL, url)
                        putExtra(StreamActivity.EXTRA_TITLE, label.ifBlank { url })
                    }
                )
            },
            colors = ButtonDefaults.buttonColors(containerColor = cyan),
            modifier = Modifier.fillMaxWidth(),
            enabled = url.isNotBlank()
        ) {
            Text("▶ PLAY STREAM", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(16.dp))
        Text("COMMON PATHS", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))

        val commonPaths = listOf(
            "rtsp://admin:admin@IP:554/live/ch00_0" to "Hikvision default",
            "rtsp://admin:admin@IP:554/cam/realmonitor?channel=1&subtype=0" to "Dahua default",
            "rtsp://root:pass@IP:554/axis-media/media.amp" to "Axis default",
            "http://IP:80/video.cgi" to "Generic MJPEG",
            "http://IP:8080/video" to "IP Webcam app",
            "rtsp://IP:554/user=admin&password=&channel=1&stream=0.sdp?" to "XMEye default"
        )

        commonPaths.forEach { (path, name) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = darkCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                onClick = { url = path }
            ) {
                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(name, color = Color.White, fontSize = 12.sp)
                    Text("USE", color = cyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
