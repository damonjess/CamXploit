package com.spyboy.camxploit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.spyboy.camxploit.osint.OsintViewModel
import com.spyboy.camxploit.osint.ZoomEyeClient

@androidx.media3.common.util.UnstableApi
@Composable
fun OsintScreen(viewModel: OsintViewModel = viewModel()) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val neonGreen = Color(0xFF39FF14)
    val magenta = Color(0xFFFF00FF)
    val purple = Color(0xFF8B5CF6)
    val darkCard = Color(0xFF1A1A1A)
    val red = Color(0xFFFF4444)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        Text("INTEL", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = magenta)
        Text("GLOBAL OSINT RECON", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        // Source selector
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SourceChip("ZOOMEYE", source is OsintViewModel.Source.ZoomEye, purple) {
                viewModel.selectSource(OsintViewModel.Source.ZoomEye)
            }
            SourceChip("PUBLIC CAMS", source is OsintViewModel.Source.PublicCams, Color(0xFF00CED1)) {
                viewModel.selectSource(OsintViewModel.Source.PublicCams)
            }
            SourceChip("DORKS", source is OsintViewModel.Source.Dorks, Color(0xFFFFA500)) {
                viewModel.selectSource(OsintViewModel.Source.Dorks)
            }
        }

        Spacer(Modifier.height(16.dp))

        when (source) {
            is OsintViewModel.Source.ZoomEye -> ZoomEyePanel(viewModel, neonGreen, purple, darkCard, red)
            is OsintViewModel.Source.PublicCams -> PublicCamsPanel(viewModel, neonGreen, darkCard)
            is OsintViewModel.Source.Dorks -> DorksPanel(viewModel, neonGreen, darkCard)
        }

        if (isLoading) {
            Spacer(Modifier.height(20.dp))
            CircularProgressIndicator(color = purple, modifier = Modifier.align(Alignment.CenterHorizontally))
        }

        error?.let {
            Spacer(Modifier.height(12.dp))
            Surface(
                color = red.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
                border = BorderStroke(1.dp, red.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth().clickable { 
                    if (it.contains("credits", true)) {
                        viewModel.apiKey.value.let { key -> if (key.isNotBlank()) viewModel.setApiKey(key) }
                    } else if (source is OsintViewModel.Source.PublicCams) {
                        viewModel.loadCountries()
                    }
                }
            ) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚠", fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(it, color = red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

// ═══════════════════════════════════════════════════════════════════
// ZOOMEYE PANEL
// ═══════════════════════════════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZoomEyePanel(
    vm: OsintViewModel,
    neonGreen: Color,
    purple: Color,
    darkCard: Color,
    red: Color
) {
    val apiKey by vm.apiKey.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.zoomEyeResults.collectAsStateWithLifecycle()
    val credits by vm.credits.collectAsStateWithLifecycle()

    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = vm::setApiKey,
            label = { Text("ZoomEye API Key", color = Color.Gray, fontSize = 11.sp) },
            placeholder = { Text("Free key from zoomeye.org", color = Color.DarkGray) },
            colors = textFieldColors(darkCard, purple),
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        credits?.let {
            Spacer(Modifier.width(8.dp))
            Column(
                horizontalAlignment = Alignment.End,
                modifier = Modifier.clickable { vm.apiKey.value.let { k -> if (k.isNotBlank()) vm.setApiKey(k) } }
            ) {
                Text("GENERAL POINTS", color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Refresh, null, tint = neonGreen.copy(0.5f), modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("${it.basicPoints + it.extraPoints}", color = neonGreen, fontSize = 14.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }

    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = query,
        onValueChange = vm::setQuery,
        label = { Text("Query", color = Color.Gray, fontSize = 11.sp) },
        colors = textFieldColors(darkCard, Color(0xFFFF00FF)),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )

    Spacer(Modifier.height(10.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        PresetChip("webcam") { vm.applyPreset("app:\"webcam\"") }
        PresetChip("Dahua") { vm.applyPreset("app:\"Dahua-DVR\"") }
        PresetChip("Hikvision") { vm.applyPreset("app:\"Hikvision-IP-Camera\"") }
        PresetChip("RTSP") { vm.applyPreset("service:\"rtsp\"") }
        PresetChip("HTTP") { vm.applyPreset("port:80") }
        PresetChip("RDP") { vm.applyPreset("port:3389") }
    }

    Spacer(Modifier.height(12.dp))
    Button(
        onClick = vm::runZoomEye,
        colors = ButtonDefaults.buttonColors(containerColor = purple),
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(Icons.Default.Search, null, tint = Color.White)
        Spacer(Modifier.width(8.dp))
        Text("RUN SCAN", color = Color.White, fontWeight = FontWeight.Bold)
    }

    Spacer(Modifier.height(12.dp))
    if (results.isNotEmpty()) {
        Text("${results.size} HOSTS", color = neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        results.forEach { host ->
            ZoomEyeCard(host, neonGreen, darkCard)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
fun ZoomEyeCard(host: ZoomEyeClient.Host, neonGreen: Color, darkCard: Color) {
    val clipboard = LocalClipboardManager.current
    val purple = Color(0xFF8B5CF6)
    Card(colors = CardDefaults.cardColors(containerColor = darkCard), shape = RoundedCornerShape(10.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(host.ip, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                host.port.let { if (it > 0) Surface(color = neonGreen, shape = RoundedCornerShape(4.dp)) { Text(" $it ", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold) } }
            }
            val loc = listOfNotNull(host.city, host.country).joinToString(", ")
            if (loc.isNotBlank()) Text(loc, color = Color.Gray, fontSize = 11.sp)
            host.title?.let { Text(it, color = Color.LightGray, fontSize = 12.sp) }
            host.banner?.let { Text(it, color = Color.Gray, fontSize = 10.sp, maxLines = 2) }
            Spacer(Modifier.height(4.dp))
            Text("Copy IP", color = purple, fontSize = 11.sp, modifier = Modifier.clickable {
                clipboard.setText(AnnotatedString(host.ip))
            })
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// PUBLIC CAMS PANEL
// ═══════════════════════════════════════════════════════════════════
@androidx.media3.common.util.UnstableApi
@Composable
fun PublicCamsPanel(vm: OsintViewModel, neonGreen: Color, darkCard: Color) {
    val context = LocalContext.current
    var showBrowser by remember { mutableStateOf(false) }

    if (showBrowser) {
        InsecamBrowserScreen(
            onClose = { showBrowser = false },
            onStreamUrl = { url, title ->
                showBrowser = false
                com.spyboy.camxploit.StreamActivity.launch(context, url, title)
            }
        )
    } else {
        Column {
            Text(
                "PUBLIC CAMERA SOURCES",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))

            // Insecam Browser button
            Card(
                colors = CardDefaults.cardColors(containerColor = darkCard),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showBrowser = true }
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Insecam Browser", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("Browse insecam.org directly", color = Color.Gray, fontSize = 11.sp)
                    }
                    Text("OPEN", color = neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))
            DirectStreamPanel()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// DORKS PANEL
// ═══════════════════════════════════════════════════════════════════
@Composable
fun DorksPanel(vm: OsintViewModel, neonGreen: Color, darkCard: Color) {
    val query by vm.query.collectAsStateWithLifecycle()
    val dork by vm.dork.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = query,
        onValueChange = vm::setQuery,
        label = { Text("Keyword", color = Color.Gray, fontSize = 11.sp) },
        colors = textFieldColors(darkCard, Color(0xFFFFA500)),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(10.dp))
    Button(onClick = vm::generateDork, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFA500)), modifier = Modifier.fillMaxWidth()) {
        Text("GENERATE DORK", color = Color.Black, fontWeight = FontWeight.Bold)
    }
    if (dork.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Card(colors = CardDefaults.cardColors(containerColor = darkCard)) {
            Column(Modifier.padding(12.dp)) {
                val clipboard = LocalClipboardManager.current
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("GOOGLE DORK", color = neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { clipboard.setText(AnnotatedString(dork)) }) {
                        Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray)
                    }
                }
                SelectionContainer {
                    Text(dork, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// SHARED COMPONENTS
// ═══════════════════════════════════════════════════════════════════
@Composable
private fun SourceChip(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        color = if (active) activeColor else Color(0xFF2A2A2A),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = if (active) Color.Black else Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

@Composable
private fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(color = Color(0xFF2A2A2A), shape = RoundedCornerShape(14.dp), modifier = Modifier.clickable { onClick() }) {
        Text(label, color = Color.White, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
    }
}

@Composable
private fun textFieldColors(bg: Color, focus: Color) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = bg,
    unfocusedContainerColor = bg,
    focusedTextColor = Color.White,
    unfocusedTextColor = Color.White,
    focusedBorderColor = focus,
    unfocusedBorderColor = Color.DarkGray
)
