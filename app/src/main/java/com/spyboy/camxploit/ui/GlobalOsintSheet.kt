package com.spyboy.camxploit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
    importedIp: String? = null, // pass an IP from LAN scan
    viewModel: OsintViewModel = viewModel()
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

    // Auto-import LAN IP if passed
    LaunchedEffect(importedIp) {
        importedIp?.let { viewModel.setQuery(it); viewModel.lookupIp(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("GLOBAL OSINT RECON", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = magenta)
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Close", tint = Color.Gray)
            }
        }
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
