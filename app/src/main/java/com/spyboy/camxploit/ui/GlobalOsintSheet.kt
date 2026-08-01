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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.spyboy.camxploit.osint.OsintViewModel

@UnstableApi
@Composable
fun GlobalOsintSheet(
    onDismiss: () -> Unit,
    importedIp: String? = null,
    viewModel: OsintViewModel = viewModel()
) {
    val neonGreen = Color(0xFF39FF14)
    val magenta = Color(0xFFFF00FF)
    val darkCard = Color(0xFF1A1A1A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
    ) {
        // 1. HEADER
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
        }

        Spacer(Modifier.height(8.dp))

        // 2. CONTENT AREA
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            PublicCamsPanel(viewModel, neonGreen, darkCard)
        }
    }
}
