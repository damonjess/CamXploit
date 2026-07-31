package com.spyboy.camxploit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import com.spyboy.camxploit.osint.OsintViewModel

@Composable
fun GlobalOsintSheet(
    onDismiss: () -> Unit,
    importedIp: String? = null,
    viewModel: OsintViewModel = viewModel()
) {
    val source by viewModel.source.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val neonGreen = Color(0xFF39FF14)
    val magenta = Color(0xFFFF00FF)
    val purple = Color(0xFF8B5CF6)
    val darkCard = Color(0xFF1A1A1A)
    val red = Color(0xFFFF4444)

    // Auto-import LAN IP if passed
    LaunchedEffect(importedIp) {
        importedIp?.let { 
            viewModel.setQuery(it)
            viewModel.lookupIp(it) 
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // 1. STATIC HEADER & TABS
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("INTEL RECON", fontSize = 20.sp, fontWeight = FontWeight.Black, color = magenta)
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(20.dp))
                }
            }
            Text("EXTERNAL THREAT INTELLIGENCE", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.sp)
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

        Spacer(Modifier.height(8.dp))

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
                Text("⚠ $it", color = red, fontSize = 12.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 20.dp))
            }
        }
    }
}

@Composable
private fun SourceChip(label: String, active: Boolean, activeColor: Color, onClick: () -> Unit) {
    Surface(
        color = if (active) activeColor else Color(0xFF2A2A2A),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.clickable { onClick() }
    ) {
        Text(label, color = if (active) Color.Black else Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
    }
}
