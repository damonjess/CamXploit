package com.spyboy.camxploit.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spyboy.camxploit.OnvifCapabilities
import com.spyboy.camxploit.OnvifDeviceInfo
import com.spyboy.camxploit.OnvifProber
import com.spyboy.camxploit.OnvifProfile
import com.spyboy.camxploit.StreamSource
import com.spyboy.camxploit.StreamViewerActivity
import com.spyboy.camxploit.buildAuthUrl
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val onvifGreen = Color(0xFF39FF14)
private val onvifCyan = Color(0xFF00FFFF)
private val onvifCard = Color(0xFF171717)

@Composable
fun OnvifToolsCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prober = remember { OnvifProber() }

    var devices by remember { mutableStateOf<List<OnvifDeviceInfo>>(emptyList()) }
    var scanning by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<OnvifDeviceInfo?>(null) }
    var username by remember { mutableStateOf("admin") }
    var password by remember { mutableStateOf("") }
    var connecting by remember { mutableStateOf(false) }
    var profiles by remember { mutableStateOf<List<OnvifProfile>>(emptyList()) }
    var selectedProfile by remember { mutableStateOf<OnvifProfile?>(null) }
    var capabilities by remember { mutableStateOf<OnvifCapabilities?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var ptzBusy by remember { mutableStateOf(false) }

    val deviceService = selected?.deviceService
    val mediaXAddr = capabilities?.mediaXAddr ?: deviceService
    val ptzXAddr = capabilities?.ptzXAddr

    fun scan() {
        if (scanning) return
        scope.launch {
            scanning = true
            error = null
            devices = runCatching { prober.probe(4_000) }.getOrDefault(emptyList())
            scanning = false
            if (devices.isEmpty()) {
                Toast.makeText(context, "No ONVIF devices answered", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun connect() {
        val service = deviceService ?: run {
            error = "This device did not advertise a service URL."
            return
        }
        scope.launch {
            connecting = true
            error = null
            profiles = emptyList()
            selectedProfile = null
            capabilities = null
            val caps = runCatching { prober.getCapabilities(service, username, password) }
                .getOrDefault(OnvifCapabilities())
            capabilities = caps
            val media = caps.mediaXAddr ?: service
            val found = runCatching {
                prober.getProfilesAt(media, username, password).ifEmpty {
                    if (media != service) prober.getProfilesAt(service, username, password)
                    else emptyList()
                }
            }.getOrDefault(emptyList())
            profiles = found
            selectedProfile = found.firstOrNull()
            if (found.isEmpty()) {
                error = "No media profiles. Check the credentials or whether media service is enabled."
            }
            connecting = false
        }
    }

    fun play(profile: OnvifProfile) {
        val media = mediaXAddr ?: return
        val device = selected ?: return
        scope.launch {
            error = null
            val uri = runCatching {
                prober.getStreamUri(media, profile.token, username, password)
            }.getOrNull()
            if (uri.isNullOrBlank()) {
                error = "The device did not return an RTSP stream URI."
            } else {
                val source = StreamSource(
                    url = uri,
                    title = "${device.ip} · ${profile.name.ifBlank { profile.token }}",
                    protocol = "rtsp",
                    username = username,
                    password = password,
                    brand = "ONVIF",
                    sourceLabel = "ONVIF"
                )
                StreamViewerActivity.launch(context, source, device.ip)
            }
        }
    }

    fun snapshot(profile: OnvifProfile) {
        val media = mediaXAddr ?: return
        val device = selected ?: return
        scope.launch {
            error = null
            val uri = runCatching {
                prober.getSnapshotUri(media, profile.token, username, password)
            }.getOrNull()
            if (uri.isNullOrBlank()) {
                error = "The device did not return a snapshot URI."
            } else {
                val authUri = if (username.isNotBlank()) buildAuthUrl(uri, username, password) else uri
                val source = StreamSource(
                    url = authUri,
                    title = "${device.ip} · snapshot",
                    protocol = "snapshot",
                    username = username,
                    password = password,
                    brand = "ONVIF",
                    sourceLabel = "ONVIF snapshot"
                )
                StreamViewerActivity.launch(context, source, device.ip)
            }
        }
    }

    fun move(pan: Float, tilt: Float, zoom: Float) {
        val ptz = ptzXAddr ?: return
        val profile = selectedProfile ?: return
        scope.launch {
            ptzBusy = true
            error = null
            val started = runCatching {
                prober.ptzContinuousMove(
                    ptz, profile.token, pan, tilt, zoom, username, password
                )
            }.getOrDefault(false)
            if (started) {
                delay(350)
                runCatching { prober.ptzStop(ptz, profile.token, username, password) }
            } else {
                error = "PTZ command was rejected by the device."
            }
            ptzBusy = false
        }
    }

    fun stopPtz() {
        val ptz = ptzXAddr ?: return
        val profile = selectedProfile ?: return
        scope.launch {
            ptzBusy = true
            runCatching { prober.ptzStop(ptz, profile.token, username, password) }
            ptzBusy = false
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = onvifCard),
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, null, tint = onvifCyan, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "ONVIF DEVICES",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        "Profiles · WS-Security · RTSP · snapshot · PTZ",
                        color = Color.Gray,
                        fontSize = 9.sp
                    )
                }
                TextButton(onClick = ::scan, enabled = !scanning) {
                    if (scanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = onvifGreen,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Refresh,
                            null,
                            tint = onvifGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        if (scanning) "SCANNING" else "SCAN",
                        color = onvifGreen,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (devices.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                devices.forEach { device ->
                    val isSelected = selected?.ip == device.ip
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                if (isSelected) onvifCyan.copy(alpha = 0.10f) else Color(0xFF111111),
                                RoundedCornerShape(6.dp)
                            )
                            .clickable {
                                selected = device
                                profiles = emptyList()
                                selectedProfile = null
                                capabilities = null
                                error = null
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                device.ip,
                                color = if (isSelected) onvifCyan else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                device.types?.take(80) ?: "NetworkVideoTransmitter",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Text(
                            if (isSelected) "SELECTED" else "CONNECT",
                            color = if (isSelected) onvifCyan else Color.DarkGray,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                }
            } else if (!scanning) {
                Text(
                    "Scan the LAN for ONVIF cameras. The device service is discovered over WS-Discovery.",
                    color = Color.Gray,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            selected?.let { device ->
                Spacer(Modifier.height(10.dp))
                HorizontalDivider(color = Color(0xFF2A2A2A))
                Spacer(Modifier.height(8.dp))
                Text(
                    "CREDENTIALS FOR ${device.ip}",
                    color = onvifCyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = onvifFieldColors()
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth(),
                    colors = onvifFieldColors()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = ::connect,
                    enabled = !connecting,
                    colors = ButtonDefaults.buttonColors(containerColor = onvifCyan),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (connecting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            color = Color.Black,
                            strokeWidth = 2.dp
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        if (connecting) "READING PROFILES…" else "GET PROFILES",
                        color = Color.Black,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }

                if (profiles.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "MEDIA PROFILES",
                        color = Color.Gray,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    profiles.forEach { profile ->
                        val isSelected = selectedProfile?.token == profile.token
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedProfile = profile }
                                .padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { selectedProfile = profile },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = onvifGreen,
                                    unselectedColor = Color.Gray
                                )
                            )
                            Text(
                                profile.label,
                                color = if (isSelected) Color.White else Color.Gray,
                                fontSize = 10.sp
                            )
                        }
                    }

                    selectedProfile?.let { profile ->
                        Spacer(Modifier.height(6.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { play(profile) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = onvifGreen),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("PLAY RTSP", color = Color.Black, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { snapshot(profile) },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text("SNAPSHOT", color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (ptzXAddr != null) {
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "PTZ · ${ptzXAddr.substringAfter("://", "").substringBefore('/')}",
                                color = onvifCyan,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                PtzButton("◀", Icons.Default.ChevronLeft, ptzBusy) { move(-0.7f, 0f, 0f) }
                                PtzButton("▲", Icons.Default.ArrowUpward, ptzBusy) { move(0f, 0.7f, 0f) }
                                PtzButton("▼", Icons.Default.ArrowDownward, ptzBusy) { move(0f, -0.7f, 0f) }
                                PtzButton("▶", Icons.Default.ChevronRight, ptzBusy) { move(0.7f, 0f, 0f) }
                            }
                            Spacer(Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                SmallButton("ZOOM +", ptzBusy) { move(0f, 0f, 0.6f) }
                                SmallButton("ZOOM −", ptzBusy) { move(0f, 0f, -0.6f) }
                                SmallButton("STOP", ptzBusy, accent = true) { stopPtz() }
                            }
                            Text(
                                "Each press moves briefly, then stops. Use STOP to halt immediately.",
                                color = Color.Gray,
                                fontSize = 8.sp,
                                modifier = Modifier.padding(top = 3.dp)
                            )
                        }
                    }
                }

                error?.let {
                    Spacer(Modifier.height(6.dp))
                    Text(it, color = Color(0xFFFF5252), fontSize = 9.sp)
                }

                if (deviceService != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        deviceService,
                        color = Color.DarkGray,
                        fontSize = 7.5.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.PtzButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    busy: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.weight(1f).height(38.dp),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2A2A2A)),
        contentPadding = PaddingValues(0.dp)
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RowScope.SmallButton(
    label: String,
    busy: Boolean,
    accent: Boolean = false,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier.weight(1f).height(34.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (accent) Color(0xFFAA0000) else Color(0xFF2A2A2A)
        ),
        contentPadding = PaddingValues(0.dp)
    ) {
        Text(label, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun onvifFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = Color(0xFF111111),
    unfocusedContainerColor = Color(0xFF111111),
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = onvifCyan,
    unfocusedBorderColor = Color.DarkGray
)
