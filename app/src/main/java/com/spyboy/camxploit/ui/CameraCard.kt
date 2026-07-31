package com.spyboy.camxploit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest
import com.spyboy.camxploit.osint.InsecamClient
import kotlinx.coroutines.delay

@Composable
fun CameraCard(
    cam: InsecamClient.PublicCamera,
    darkCard: Color = Color(0xFF1A1A1A),
    onViewStream: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val neonGreen = Color(0xFF39FF14)
    val context = LocalContext.current

    var imageState by remember { mutableStateOf<AsyncImagePainter.State>(AsyncImagePainter.State.Empty) }
    var forceError by remember { mutableStateOf(false) }

    // Hard timeout: if still loading after 3 seconds, treat as failed
    LaunchedEffect(cam.imageUrl) {
        forceError = false
        if (cam.imageUrl != null) {
            delay(3500)
            if (imageState is AsyncImagePainter.State.Loading) {
                forceError = true
            }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = darkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Image box with fixed height and rounded top corners
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                    .background(Color(0xFF0D0D0D)),
                contentAlignment = Alignment.Center
            ) {
                if (cam.imageUrl != null && !forceError) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(cam.imageUrl)
                            .crossfade(200)
                            // 3s timeout for static images; MJPEG streams will hit the LaunchedEffect timeout above
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                        onState = { state -> imageState = state }
                    )
                }

                // State overlays
                when {
                    (imageState is AsyncImagePainter.State.Loading && !forceError) -> {
                        CircularProgressIndicator(
                            color = neonGreen,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(28.dp) // Explicitly constrained
                        )
                    }
                    (imageState is AsyncImagePainter.State.Error || forceError || cam.imageUrl == null) -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = null,
                                tint = Color(0xFF333333),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "NO PREVIEW",
                                color = Color(0xFF444444),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                    else -> { /* Loaded successfully — image visible, no overlay */ }
                }
            }

            // Info section
            Column(modifier = Modifier.padding(12.dp)) {
                cam.ip?.let {
                    Text(
                        text = it,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        maxLines = 1
                    )
                }
                cam.location?.let {
                    Text(
                        text = it,
                        color = Color.Gray,
                        fontSize = 11.sp,
                        maxLines = 2
                    )
                }

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "COPY IP",
                        color = Color(0xFF8B5CF6),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString(cam.ip ?: ""))
                        }
                    )

                    Text(
                        text = "▶ VIEW",
                        color = neonGreen,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.clickable { onViewStream() }
                    )
                }
            }
        }
    }
}
