package com.spyboy.camxploit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyboy.camxploit.osint.OsintViewModel
import com.spyboy.camxploit.osint.ShodanClient

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OsintScreen(
    viewModel: OsintViewModel
) {
    val activeTab by viewModel.activeTab.collectAsStateWithLifecycle()
    val apiKey by viewModel.apiKey.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val dork by viewModel.dorkResult.collectAsStateWithLifecycle()
    val results = viewModel.results

    val neonGreen = Color(0xFF39FF14)
    val magenta = Color(0xFFFF00FF)
    val darkCard = Color(0xFF1A1A1A)
    val purple = Color(0xFF8B5CF6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(16.dp)
    ) {
        Text("GLOBAL OSINT RECON", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = magenta)
        Text("EXTERNAL THREAT INTELLIGENCE", fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))

        // Tabs
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TabChip("SHODAN API", activeTab is OsintViewModel.OsintTab.Shodan, purple) {
                viewModel.setTab(OsintViewModel.OsintTab.Shodan)
            }
            TabChip("WEB DORKS", activeTab is OsintViewModel.OsintTab.WebDork, purple) {
                viewModel.setTab(OsintViewModel.OsintTab.WebDork)
            }
        }

        Spacer(Modifier.height(16.dp))

        // Content
        Box(modifier = Modifier.weight(1f)) {
            if (activeTab is OsintViewModel.OsintTab.Shodan) {
                ShodanTabContent(
                    apiKey = apiKey,
                    onApiKeyChange = viewModel::setApiKey,
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    onApplyPreset = viewModel::applyPreset,
                    onRunScan = viewModel::runShodanScan,
                    isLoading = isLoading,
                    error = error,
                    results = results,
                    neonGreen = neonGreen,
                    magenta = magenta,
                    darkCard = darkCard,
                    purple = purple
                )
            } else {
                WebDorkTabContent(
                    query = query,
                    onQueryChange = viewModel::setQuery,
                    onGenerateDork = viewModel::generateDork,
                    dork = dork,
                    neonGreen = neonGreen,
                    magenta = magenta,
                    darkCard = darkCard
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ShodanTabContent(
    apiKey: String,
    onApiKeyChange: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onApplyPreset: (String) -> Unit,
    onRunScan: () -> Unit,
    isLoading: Boolean,
    error: String?,
    results: List<ShodanClient.ShodanHost>,
    neonGreen: Color,
    magenta: Color,
    darkCard: Color,
    purple: Color
) {
    Column {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            placeholder = { Text("Shodan API Key", color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = purple,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search Query", color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = magenta,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(10.dp))

        // Presets
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            PresetChip("Hikvision") { onApplyPreset("Hikvision") }
            PresetChip("Dahua") { onApplyPreset("Dahua") }
            PresetChip("Axis") { onApplyPreset("Axis") }
            PresetChip("Exposed RTSP") { onApplyPreset("Exposed RTSP") }
            PresetChip("RDP") { onApplyPreset("port:3389") }
            PresetChip("SSH") { onApplyPreset("port:22") }
        }

        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRunScan,
            colors = ButtonDefaults.buttonColors(containerColor = purple),
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.Search, null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("RUN SCAN", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text("⚠ $it", color = Color(0xFFFF6B6B), fontSize = 12.sp)
        }

        Spacer(Modifier.height(12.dp))

        // Results
        if (results.isNotEmpty()) {
            Text("${results.size} HOSTS FOUND", color = neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                items(results, key = { it.ip }) { host ->
                    ShodanResultCard(host, neonGreen, darkCard)
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
fun WebDorkTabContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onGenerateDork: () -> Unit,
    dork: String,
    neonGreen: Color,
    magenta: Color,
    darkCard: Color
) {
    Column {
        Text("ZERO-API GOOGLE DORKS", color = Color.Gray, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Target keyword…", color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = magenta,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onGenerateDork,
            colors = ButtonDefaults.buttonColors(containerColor = magenta),
            modifier = Modifier.fillMaxWidth()
        ) {
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
                            Icon(Icons.Default.Share, "Copy", tint = Color.Gray)
                        }
                    }
                    SelectionContainer {
                        Text(dork, color = Color.White, fontFamily = FontFamily.Monospace, fontSize = 13.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Paste into Google Search", color = Color.Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun ShodanResultCard(host: ShodanClient.ShodanHost, neonGreen: Color, darkCard: Color) {
    val clipboard = LocalClipboardManager.current
    val purple = Color(0xFF8B5CF6)
    Card(colors = CardDefaults.cardColors(containerColor = darkCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(host.ip, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Row {
                    host.vulns.take(1).forEach { _ ->
                        Surface(color = Color(0xFFFF4444), shape = RoundedCornerShape(4.dp)) {
                            Text(" CVE ", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            Text("${host.city ?: "?"}, ${host.country ?: "?"}  •  ${host.org ?: host.isp ?: "Unknown org"}", color = Color.Gray, fontSize = 11.sp)
            Spacer(Modifier.height(6.dp))
            Text("Ports: ${host.ports.joinToString()}", color = neonGreen, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            if (host.banners.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    host.banners.first().product ?: host.banners.first().data ?: "",
                    color = Color.LightGray, fontSize = 11.sp, maxLines = 2
                )
            }
            if (host.vulns.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text("Vulns: ${host.vulns.take(3).joinToString()}", color = Color(0xFFFF6B6B), fontSize = 11.sp)
            }
            Spacer(Modifier.height(4.dp))
            Text("Copy IP", color = purple, fontSize = 11.sp, modifier = Modifier.clickable {
                clipboard.setText(AnnotatedString(host.ip))
            })
        }
    }
}

@Composable
fun TabChip(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    val bg = if (active) activeColor else Color(0xFF2A2A2A)
    val text = if (active) Color.Black else Color.White
    Surface(
        color = bg,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = text, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
    }
}

@Composable
fun PresetChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Color(0xFF2A2A2A),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
