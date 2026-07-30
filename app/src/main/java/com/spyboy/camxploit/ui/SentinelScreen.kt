package com.spyboy.camxploit.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SentinelScreen(
    viewModel: SentinelViewModel = viewModel(),
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val selectedDevice by viewModel.selectedDevice.collectAsStateWithLifecycle()
    val tlsReport by viewModel.tlsReport.collectAsStateWithLifecycle()
    val webReport by viewModel.webReport.collectAsStateWithLifecycle()
    val diffReport by viewModel.diffReport.collectAsStateWithLifecycle()

    val neonGreen = Color(0xFF39FF14)
    val darkCard = Color(0xFF1A1A1A)
    val cyan = Color(0xFF00FFFF)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp) // clears your bottom nav
    ) {
        // Header
        Text("SENTINEL", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cyan)
        Text("ACTIVE RECON & DEFENSE", fontSize = 12.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(20.dp))

        // ── TARGET SELECTOR ──
        Text("TARGET", fontSize = 14.sp, color = neonGreen, fontWeight = FontWeight.Bold)
        if (devices.isEmpty()) {
            Text("No devices found. Run LAN scan first.", color = Color.Gray)
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                devices.forEach { dev ->
                    val isSelected = selectedDevice?.ip == dev.ip
                    AssistChip(
                        onClick = { viewModel.selectDevice(dev) },
                        label = { Text(dev.ip, color = if (isSelected) Color.Black else Color.White) },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isSelected) neonGreen else darkCard
                        )
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── ACTION BUTTONS ──
        selectedDevice?.let { target ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { viewModel.scanTls(target.ip, target.openPorts) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                    modifier = Modifier.weight(1f),
                    enabled = scanState != ScanState.Scanning
                ) {
                    Text("AUDIT SSL", color = Color.White)
                }
                Button(
                    onClick = { viewModel.scanWeb(target.ip, target.openPorts) },
                    colors = ButtonDefaults.buttonColors(containerColor = darkCard),
                    modifier = Modifier.weight(1f),
                    enabled = scanState != ScanState.Scanning
                ) {
                    Text("WEB SURFACE", color = neonGreen)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // ── TLS REPORT ──
        tlsReport?.let { r ->
            Card(colors = CardDefaults.cardColors(containerColor = darkCard)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("TLS AUDIT  ", color = Color.White, fontWeight = FontWeight.Bold)
                        Surface(color = gradeColor(r.grade), shape = RoundedCornerShape(4.dp)) {
                            Text("  ${r.grade}  ", color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Protocol: ${r.protocol ?: "N/A"}", color = Color.Gray, fontSize = 12.sp)
                    Text("Cipher: ${r.cipherSuite ?: "N/A"}", color = Color.Gray, fontSize = 12.sp)
                    r.certificate?.let { c ->
                        Text("Subject: ${c.subject}", color = Color.LightGray, fontSize = 11.sp)
                        Text("Expires: ${c.notAfter}", color = if (c.isExpired) Color.Red else Color.Gray, fontSize = 11.sp)
                    }
                    if (r.vulnerabilities.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        r.vulnerabilities.forEach { v ->
                            val displayError = if (v.startsWith("Connection failed:")) {
                                when {
                                    v.contains("ECONNREFUSED", ignoreCase = true) -> "⚠ Service Offline (Port ${r.port} closed)"
                                    v.contains("timeout", ignoreCase = true) -> "⚠ Connection Timeout"
                                    else -> "⚠ Scan Error: ${v.substringAfter("Connection failed: ").take(40)}..."
                                }
                            } else v
                            Text(displayError, color = Color(0xFFFF6B6B), fontSize = 12.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── WEB REPORT ──
        webReport?.let { w ->
            Card(colors = CardDefaults.cardColors(containerColor = darkCard)) {
                Column(Modifier.padding(16.dp)) {
                    Text("WEB SURFACE", color = Color.White, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text("Server: ${w.serverBanner ?: "hidden"}", color = Color.Gray, fontSize = 12.sp)
                    if (w.exposedPanels.isNotEmpty()) {
                        Text("Exposed Panels:", color = neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        w.exposedPanels.take(5).forEach {
                            Text("• ${it.path} → ${it.status}", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                    if (w.missingHeaders.isNotEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        val headerText = if (w.missingHeaders.any { it.contains("Connection failed") }) {
                             w.missingHeaders.first().let { v ->
                                when {
                                    v.contains("ECONNREFUSED", ignoreCase = true) -> "⚠ Web Service Offline"
                                    v.contains("timeout", ignoreCase = true) -> "⚠ Web Timeout"
                                    else -> "⚠ Web Scan Error"
                                }
                             }
                        } else "Analysis: Missing Security Headers (${w.missingHeaders.size})"
                        
                        Text(headerText, color = Color(0xFFFFA500), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (!headerText.startsWith("⚠")) {
                            Text(w.missingHeaders.joinToString(), color = Color(0xFFFFA500).copy(alpha = 0.8f), fontSize = 10.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        // ── NETWORK GUARD ──
        Text("NETWORK GUARD", fontSize = 14.sp, color = neonGreen, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = { viewModel.saveBaseline() },
                colors = ButtonDefaults.buttonColors(containerColor = darkCard),
                modifier = Modifier.weight(1f)
            ) { Text("SET BASELINE", color = Color.White, fontSize = 11.sp) }
            Button(
                onClick = { viewModel.checkBaseline() },
                colors = ButtonDefaults.buttonColors(containerColor = darkCard),
                modifier = Modifier.weight(1f)
            ) { Text("CHECK NOW", color = neonGreen, fontSize = 11.sp) }
        }

        diffReport?.let { diff ->
            Spacer(Modifier.height(8.dp))
            if (diff.newDevices.isEmpty() && diff.missingDevices.isEmpty() && diff.newPorts.isEmpty()) {
                Text("✓ Network unchanged", color = Color.Green)
            } else {
                diff.newDevices.forEach {
                    Text("🚨 NEW DEVICE: ${it.ip}", color = Color.Red, fontSize = 13.sp)
                }
                diff.missingDevices.forEach {
                    Text("⚠ MISSING: ${it.ip}", color = Color(0xFFFFA500), fontSize = 13.sp)
                }
                diff.newPorts.forEach { (ip, ports) ->
                    Text("🔓 NEW PORTS on $ip: ${ports.joinToString()}", color = neonGreen, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

private fun gradeColor(g: String): Color = when (g) {
    "A+", "A" -> Color(0xFF39FF14)
    "B" -> Color(0xFF00FF88)
    "C" -> Color(0xFFFFD700)
    "D" -> Color(0xFFFFA500)
    "F" -> Color(0xFFFF4444)
    else -> Color.Gray
}
