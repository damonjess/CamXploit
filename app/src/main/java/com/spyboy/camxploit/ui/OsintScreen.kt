package com.spyboy.camxploit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.spyboy.camxploit.StreamSource
import com.spyboy.camxploit.StreamViewerActivity
import com.spyboy.camxploit.osint.OsintViewModel

@UnstableApi
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
    ) {
        // 1. STATIC HEADER & TABS
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Text("INTEL", fontSize = 22.sp, fontWeight = FontWeight.Black, color = magenta)
            Text("GLOBAL RECONNAISSANCE", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.sp)
            Spacer(Modifier.height(14.dp))

            // Source selector
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SourceChip("CENSYS", source is OsintViewModel.Source.Censys, purple) {
                    viewModel.selectSource(OsintViewModel.Source.Censys)
                }
                SourceChip("PUBLIC CAMS", source is OsintViewModel.Source.PublicCams, Color(0xFF00CED1)) {
                    viewModel.selectSource(OsintViewModel.Source.PublicCams)
                }
                SourceChip("DORKS", source is OsintViewModel.Source.Dorks, Color(0xFFFFA500)) {
                    viewModel.selectSource(OsintViewModel.Source.Dorks)
                }
            }
        }

        // 2. SCROLLABLE CONTENT AREA
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (source) {
                is OsintViewModel.Source.Censys -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        CensysPanel(viewModel, neonGreen, purple, darkCard, red)
                        Spacer(Modifier.height(100.dp))
                    }
                }
                is OsintViewModel.Source.PublicCams -> {
                    PublicCamsPanel(viewModel, neonGreen, darkCard)
                }
                is OsintViewModel.Source.Dorks -> {
                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                        DorksPanel(viewModel, neonGreen, darkCard)
                        Spacer(Modifier.height(100.dp))
                    }
                }
            }

            if (isLoading && source !is OsintViewModel.Source.PublicCams) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = purple)
                }
            }

            error?.let {
                Surface(
                    color = red.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, red.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 20.dp)
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚠", fontSize = 16.sp)
                        Spacer(Modifier.width(8.dp))
                        Text(it, color = red, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// CENSYS PANEL
// ═══════════════════════════════════════════════════════════════════
@OptIn(ExperimentalLayoutApi::class)
@UnstableApi
@Composable
fun CensysPanel(
    vm: OsintViewModel,
    neonGreen: Color,
    purple: Color,
    darkCard: Color,
    red: Color
) {
    val context = LocalContext.current
    val apiToken by vm.censysToken.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val publicCameras by vm.publicCameras.collectAsStateWithLifecycle()
    val errorMessage by vm.errorMessage.collectAsStateWithLifecycle()

    Column {
        OutlinedTextField(
            value = apiToken,
            onValueChange = vm::setCensysToken,
            label = { Text("Censys API Token (Bearer)", color = Color.Gray, fontSize = 11.sp) },
            colors = textFieldColors(darkCard, purple),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = vm::setQuery,
            label = { Text("Query (e.g. services.service_name: \"rtsp\")", color = Color.Gray, fontSize = 11.sp) },
            colors = textFieldColors(darkCard, Color(0xFFFF00FF)),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            PresetChip("Webcams") { vm.applyPreset(OsintViewModel.WEBCAM_QUERIES[0]) }
            PresetChip("IP Camera") { vm.applyPreset(OsintViewModel.WEBCAM_QUERIES[1]) }
            PresetChip("Live View") { vm.applyPreset(OsintViewModel.WEBCAM_QUERIES[2]) }
            PresetChip("Hikvision") { vm.applyPreset("services.software.vendor: \"Hikvision\"") }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.searchCensysCameras(query) },
            colors = ButtonDefaults.buttonColors(containerColor = purple),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Search, null, tint = Color.White)
            Spacer(Modifier.width(8.dp))
            Text("RUN SCAN", color = Color.White, fontWeight = FontWeight.Bold)
        }

        if (errorMessage != null) {
            Text(errorMessage!!, color = red, fontSize = 11.sp, modifier = Modifier.padding(top = 8.dp))
        }

        if (publicCameras.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            Text("${publicCameras.size} HOSTS", color = neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            
            publicCameras.forEach { camera ->
                PublicCameraCard(
                    camera = camera,
                    onViewClick = {
                        StreamViewerActivity.launch(
                            context = context,
                            source = camera,
                            ip = camera.location.ifBlank { "Censys Host" }
                        )
                    },
                    onSaveClick = { vm.saveCamera(camera) }
                )
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════════
// DORKS PANEL
// ═══════════════════════════════════════════════════════════════════
@Composable
fun DorksPanel(vm: OsintViewModel, neonGreen: Color, darkCard: Color) {
    val query by vm.query.collectAsStateWithLifecycle()
    val dorkQuery by vm.dorkQuery.collectAsStateWithLifecycle()

    OutlinedTextField(
        value = query,
        onValueChange = vm::setQuery,
        label = { Text("Keyword", color = Color.Gray, fontSize = 11.sp) },
        colors = textFieldColors(darkCard, Color(0xFFFFA500)),
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Card(colors = CardDefaults.cardColors(containerColor = darkCard)) {
        Column(Modifier.padding(12.dp)) {
            val clipboard = LocalClipboardManager.current
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("DORK PRESET", color = neonGreen, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = { clipboard.setText(AnnotatedString(dorkQuery)) }) {
                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray)
                }
            }
            SelectionContainer {
                Text(dorkQuery, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
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
