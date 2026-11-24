@file:OptIn(UnstableApi::class)

package com.spyboy.camxploit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.spyboy.camxploit.StreamSource
import com.spyboy.camxploit.StreamViewerActivity

@Composable
fun DirectStreamPanel() {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    var label by remember { mutableStateOf("") }

    val protocol = when {
        url.startsWith("rtsp://", ignoreCase = true) -> "RTSP"
        url.startsWith("rtmp://", ignoreCase = true) -> "RTMP"
        url.contains("mjpeg", ignoreCase = true) ||
            url.contains("mjpg", ignoreCase = true) -> "MJPEG"
        else -> "HTTP"
    }
    val protocolColor = when (protocol) {
        "RTSP" -> Color(0xFF4CA6FF)
        "RTMP" -> IntelPalette.Magenta
        "MJPEG" -> IntelPalette.Green
        else -> IntelPalette.Cyan
    }

    val commonPaths = listOf(
        "rtsp://admin:admin@IP:554/live/ch00_0" to "Hikvision default",
        "rtsp://admin:admin@IP:554/cam/realmonitor?channel=1&subtype=0" to "Dahua default",
        "rtsp://root:pass@IP:554/axis-media/media.amp" to "Axis default",
        "http://IP:80/video.cgi" to "Generic MJPEG",
        "http://IP:8080/video" to "IP Webcam app",
        "rtsp://IP:554/user=admin&password=&channel=1&stream=0.sdp?" to "XMEye default"
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = IntelPalette.Card,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, IntelPalette.Border)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(IntelPalette.Cyan.copy(alpha = 0.1f))
                                .border(
                                    1.dp,
                                    IntelPalette.Cyan.copy(alpha = 0.22f),
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                tint = IntelPalette.Cyan,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(Modifier.width(11.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "DIRECT UPLINK",
                                color = IntelPalette.Text,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "RTSP · RTMP · MJPEG · SNAPSHOT",
                                color = IntelPalette.Muted,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        }
                        Surface(
                            color = protocolColor.copy(alpha = 0.09f),
                            shape = RoundedCornerShape(50),
                            border = BorderStroke(1.dp, protocolColor.copy(alpha = 0.28f))
                        ) {
                            Text(
                                text = protocol,
                                color = protocolColor,
                                fontSize = 7.5.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.7.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        placeholder = {
                            Text(
                                "rtsp:// or http:// stream URL",
                                color = IntelPalette.Dim,
                                fontSize = 11.sp
                            )
                        },
                        label = {
                            Text(
                                "STREAM URL",
                                color = IntelPalette.Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = IntelPalette.Surface,
                            unfocusedContainerColor = IntelPalette.Surface,
                            focusedTextColor = IntelPalette.Text,
                            unfocusedTextColor = IntelPalette.Text,
                            focusedBorderColor = IntelPalette.Cyan.copy(alpha = 0.7f),
                            unfocusedBorderColor = IntelPalette.Border,
                            cursorColor = IntelPalette.Cyan
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(9.dp))
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        placeholder = {
                            Text(
                                "Label (optional)",
                                color = IntelPalette.Dim,
                                fontSize = 11.sp
                            )
                        },
                        label = {
                            Text(
                                "DISPLAY NAME",
                                color = IntelPalette.Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = IntelPalette.Surface,
                            unfocusedContainerColor = IntelPalette.Surface,
                            focusedTextColor = IntelPalette.Text,
                            unfocusedTextColor = IntelPalette.Text,
                            focusedBorderColor = IntelPalette.Border,
                            unfocusedBorderColor = IntelPalette.Border,
                            cursorColor = IntelPalette.Cyan
                        ),
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            if (url.isBlank()) return@Button
                            val host = url.substringAfter("//", "")
                                .substringBefore("/")
                                .substringBefore("?")
                                .ifBlank { "Unknown" }
                            val streamProtocol = when {
                                protocol == "RTSP" -> "rtsp"
                                protocol == "RTMP" -> "rtmp"
                                protocol == "MJPEG" -> "mjpeg"
                                else -> "http"
                            }
                            val source = StreamSource(
                                url = url,
                                title = label.ifBlank { "Direct Feed" },
                                protocol = streamProtocol,
                                location = host
                            )
                            StreamViewerActivity.launch(context, source, host)
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IntelPalette.Cyan,
                            contentColor = IntelPalette.Background
                        ),
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        enabled = url.isNotBlank()
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "OPEN STREAM",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(12.dp)
                        .background(IntelPalette.Cyan, RoundedCornerShape(1.dp))
                )
                Spacer(Modifier.width(7.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "QUICK TEMPLATES",
                        color = IntelPalette.Text,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Tap to prefill a common stream path",
                        color = IntelPalette.Muted,
                        fontSize = 8.sp
                    )
                }
            }
        }

        items(commonPaths, key = { it.first }) { (path, name) ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { url = path },
                color = IntelPalette.Card,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, IntelPalette.Border)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(IntelPalette.Cyan.copy(alpha = 0.07f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = name.take(1),
                            color = IntelPalette.Cyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                    Spacer(Modifier.width(9.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name,
                            color = IntelPalette.Text,
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = path,
                            color = IntelPalette.Muted,
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Use template",
                        tint = IntelPalette.Cyan.copy(alpha = 0.7f),
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = IntelPalette.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, IntelPalette.Border)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = IntelPalette.Amber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Replace IP and credential placeholders before opening. Only connect to devices you own or are authorized to test.",
                        color = IntelPalette.Muted,
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}
