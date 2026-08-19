package com.spyboy.camxploit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val neonGreen = Color(0xFF39FF14)
    val magenta = Color(0xFFFF00FF)
    val darkCard = Color(0xFF1A1A1A)
    
    val source by viewModel.source.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        // 1. STATIC HEADER
        Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
            Text("INTEL", fontSize = 22.sp, fontWeight = FontWeight.Black, color = magenta)
            Text("GLOBAL RECONNAISSANCE", fontSize = 10.sp, color = Color.Gray, letterSpacing = 1.sp)
        }

        // 2. TABS
        TabRow(
            selectedTabIndex = when (source) {
                is OsintViewModel.Source.PublicCams,
                is OsintViewModel.Source.Opentopia,
                is OsintViewModel.Source.GitHub -> 0
                is OsintViewModel.Source.MyCameras -> 1
                is OsintViewModel.Source.DirectStream -> 2
                is OsintViewModel.Source.Browser -> 3
            },
            containerColor = Color.Black,
            contentColor = neonGreen,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[when (source) {
                        is OsintViewModel.Source.PublicCams, 
                        is OsintViewModel.Source.Opentopia,
                        is OsintViewModel.Source.GitHub -> 0
                        is OsintViewModel.Source.MyCameras -> 1
                        is OsintViewModel.Source.DirectStream -> 2
                        is OsintViewModel.Source.Browser -> 3
                    }]),
                    color = neonGreen
                )
            },
            divider = {}
        ) {
            Tab(
                selected = source is OsintViewModel.Source.PublicCams || source is OsintViewModel.Source.Opentopia || source is OsintViewModel.Source.GitHub,
                onClick = { viewModel.selectSource(OsintViewModel.Source.PublicCams) },
                text = { Text("PUBLIC CAMS", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Public, null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = source is OsintViewModel.Source.MyCameras,
                onClick = { viewModel.loadMyCameras() },
                text = { Text("MY CAMS", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Public, null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = source is OsintViewModel.Source.DirectStream,
                onClick = { viewModel.selectSource(OsintViewModel.Source.DirectStream) },
                text = { Text("DIRECT", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.SettingsInputAntenna, null, modifier = Modifier.size(18.dp)) }
            )
            Tab(
                selected = source is OsintViewModel.Source.Browser,
                onClick = { viewModel.selectSource(OsintViewModel.Source.Browser) },
                text = { Text("BROWSER", fontSize = 10.sp, fontWeight = FontWeight.Bold) },
                icon = { Icon(Icons.Default.Language, null, modifier = Modifier.size(18.dp)) }
            )
        }

        Spacer(Modifier.height(12.dp))

        // 3. CONTENT AREA
        Box(modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
            when (source) {
                is OsintViewModel.Source.PublicCams,
                is OsintViewModel.Source.Opentopia,
                is OsintViewModel.Source.GitHub,
                is OsintViewModel.Source.MyCameras -> {
                    PublicCamsPanel(viewModel, neonGreen, darkCard)
                }
                is OsintViewModel.Source.DirectStream -> {
                    DirectStreamPanel()
                }
                is OsintViewModel.Source.Browser -> {
                    InsecamBrowserScreen(
                        onClose = { viewModel.selectSource(OsintViewModel.Source.PublicCams) },
                        onStreamUrl = { url, title ->
                            val streamSource = StreamSource(
                                url = url,
                                title = title,
                                protocol = "mjpeg"
                            )
                            StreamViewerActivity.launch(context, streamSource, "Public")
                        }
                    )
                }
            }
        }
    }
}
