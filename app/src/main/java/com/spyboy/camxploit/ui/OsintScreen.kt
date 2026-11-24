@file:OptIn(UnstableApi::class)

package com.spyboy.camxploit.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.util.UnstableApi
import com.spyboy.camxploit.StreamSource
import com.spyboy.camxploit.StreamViewerActivity
import com.spyboy.camxploit.osint.IntelSourceId
import com.spyboy.camxploit.osint.OsintViewModel
import com.spyboy.camxploit.osint.SourceHealthStatus

@Composable
fun OsintScreen(viewModel: OsintViewModel = viewModel()) {
    val context = LocalContext.current
    val source by viewModel.source.collectAsStateWithLifecycle()
    val sourceHealth by viewModel.sourceHealth.collectAsStateWithLifecycle()
    var showBrowser by remember { mutableStateOf(false) }

    val selectedTab = when (source) {
        is OsintViewModel.Source.PublicCams,
        is OsintViewModel.Source.Opentopia,
        is OsintViewModel.Source.GitHub -> 0
        is OsintViewModel.Source.MyCameras -> 1
        is OsintViewModel.Source.DirectStream -> 2
        is OsintViewModel.Source.Browser -> 3
    }

    val activeHealth = when (source) {
        is OsintViewModel.Source.Opentopia -> sourceHealth[IntelSourceId.OPENTOPIA]
        is OsintViewModel.Source.GitHub -> sourceHealth[IntelSourceId.GITHUB]
        is OsintViewModel.Source.MyCameras -> sourceHealth[IntelSourceId.MY_CAMERAS]
        is OsintViewModel.Source.PublicCams -> sourceHealth[IntelSourceId.COUNTRY_DIRECTORY]
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF071016), IntelPalette.Background, Color(0xFF030506))
                )
            )
    ) {
        IntelHeader(activeHealth?.status)
        IntelTabBar(
            selectedTab = selectedTab,
            onSelect = { index ->
                when (index) {
                    0 -> viewModel.selectSource(OsintViewModel.Source.PublicCams)
                    1 -> viewModel.loadMyCameras()
                    2 -> viewModel.selectSource(OsintViewModel.Source.DirectStream)
                    3 -> viewModel.selectSource(OsintViewModel.Source.Browser)
                }
            }
        )

        Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
            key(selectedTab) {
                when (source) {
                    is OsintViewModel.Source.PublicCams,
                    is OsintViewModel.Source.Opentopia,
                    is OsintViewModel.Source.GitHub,
                    is OsintViewModel.Source.MyCameras -> PublicCamsPanel(
                        viewModel = viewModel,
                        neonGreen = IntelPalette.Green,
                        darkCard = IntelPalette.Card
                    )
                    is OsintViewModel.Source.DirectStream -> DirectStreamPanel()
                    is OsintViewModel.Source.Browser -> BrowserLaunchPanel(
                        onLaunch = { showBrowser = true }
                    )
                }
            }
        }
    }

    if (showBrowser) {
        Dialog(
            onDismissRequest = { showBrowser = false },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = IntelPalette.Background
            ) {
                InsecamBrowserScreen(
                    onClose = { showBrowser = false },
                    onStreamUrl = { source ->
                        StreamViewerActivity.launch(context, source, "Public")
                    }
                )
            }
        }
    }
}

@Composable
private fun IntelHeader(healthStatus: SourceHealthStatus?) {
    val statusColor = when (healthStatus) {
        SourceHealthStatus.LOADING -> IntelPalette.Cyan
        SourceHealthStatus.HEALTHY -> IntelPalette.Green
        SourceHealthStatus.PARTIAL -> IntelPalette.Amber
        SourceHealthStatus.ERROR -> IntelPalette.Red
        else -> IntelPalette.Cyan
    }
    val statusLabel = when (healthStatus) {
        SourceHealthStatus.LOADING -> "SYNCING"
        SourceHealthStatus.HEALTHY -> "READY"
        SourceHealthStatus.PARTIAL -> "PARTIAL"
        SourceHealthStatus.ERROR -> "ERROR"
        else -> "READY"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    listOf(Color(0xFF0B151A), Color(0xFF070D11), IntelPalette.Background)
                )
            )
            .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            RadarMark()
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "INTEL",
                        color = IntelPalette.Text,
                        fontSize = 23.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp
                    )
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .width(18.dp)
                            .height(2.dp)
                            .background(IntelPalette.Magenta, RoundedCornerShape(1.dp))
                    )
                }
                Text(
                    text = "GLOBAL SIGNAL INTELLIGENCE",
                    color = IntelPalette.Muted,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.3.sp
                )
            }
            Surface(
                color = statusColor.copy(alpha = 0.08f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, statusColor.copy(alpha = 0.28f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .background(statusColor, CircleShape)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            IntelPalette.Green.copy(alpha = 0.35f),
                            IntelPalette.Cyan.copy(alpha = 0.12f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}

@Composable
private fun RadarMark() {
    val transition = rememberInfiniteTransition(label = "intelRadar")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(3600, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "intelRadarSweep"
    )

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        IntelPalette.Green.copy(alpha = 0.14f),
                        Color(0xFF07130D),
                        Color(0xFF040806)
                    )
                )
            )
            .border(1.dp, IntelPalette.Green.copy(alpha = 0.32f), CircleShape)
            .padding(4.dp)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val radius = size.minDimension / 2f
            val center = this.center

            drawCircle(
                color = IntelPalette.Green.copy(alpha = 0.23f),
                radius = radius,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = IntelPalette.Green.copy(alpha = 0.14f),
                radius = radius * 0.66f,
                style = Stroke(width = 1f)
            )
            drawCircle(
                color = IntelPalette.Green.copy(alpha = 0.09f),
                radius = radius * 0.33f,
                style = Stroke(width = 1f)
            )
            drawLine(
                color = IntelPalette.Green.copy(alpha = 0.12f),
                start = Offset(center.x - radius, center.y),
                end = Offset(center.x + radius, center.y),
                strokeWidth = 1f
            )
            drawLine(
                color = IntelPalette.Green.copy(alpha = 0.12f),
                start = Offset(center.x, center.y - radius),
                end = Offset(center.x, center.y + radius),
                strokeWidth = 1f
            )
            rotate(degrees = sweep) {
                drawArc(
                    color = IntelPalette.Green.copy(alpha = 0.18f),
                    startAngle = 0f,
                    sweepAngle = 52f,
                    useCenter = true
                )
                drawLine(
                    color = IntelPalette.Green.copy(alpha = 0.9f),
                    start = center,
                    end = Offset(center.x + radius * 0.9f, center.y),
                    strokeWidth = 1.5f
                )
            }
            drawCircle(
                color = IntelPalette.Green,
                radius = 2f,
                center = center
            )
            drawCircle(
                color = IntelPalette.Green.copy(alpha = 0.85f),
                radius = 1.8f,
                center = Offset(center.x + radius * 0.42f, center.y - radius * 0.35f)
            )
        }
    }
}

@Composable
private fun IntelTabBar(selectedTab: Int, onSelect: (Int) -> Unit) {
    val tabs = listOf(
        "PUBLIC" to Icons.Default.Public,
        "MY CAMS" to Icons.Default.VideoLibrary,
        "DIRECT" to Icons.Default.SettingsInputAntenna,
        "BROWSER" to Icons.Default.Language
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 10.dp),
        color = IntelPalette.Surface.copy(alpha = 0.9f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            tabs.forEachIndexed { index, (label, icon) ->
                val selected = selectedTab == index
                val tint = if (selected) IntelPalette.Green else IntelPalette.Muted
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(42.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            if (selected) IntelPalette.Green.copy(alpha = 0.1f)
                            else Color.Transparent
                        )
                        .then(
                            if (selected) {
                                Modifier.border(
                                    1.dp,
                                    IntelPalette.Green.copy(alpha = 0.28f),
                                    RoundedCornerShape(10.dp)
                                )
                            } else {
                                Modifier
                            }
                        )
                        .clickable { onSelect(index) },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            color = tint,
                            fontSize = 7.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.3.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowserLaunchPanel(onLaunch: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = IntelPalette.Card,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, IntelPalette.Cyan.copy(alpha = 0.22f))
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(46.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(IntelPalette.Cyan.copy(alpha = 0.1f))
                                .border(
                                    1.dp,
                                    IntelPalette.Cyan.copy(alpha = 0.24f),
                                    RoundedCornerShape(14.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = IntelPalette.Cyan,
                                modifier = Modifier.size(23.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "WEB RECON",
                                color = IntelPalette.Text,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 1.2.sp
                            )
                            Text(
                                text = "IN-APP CAMERA DIRECTORY",
                                color = IntelPalette.Muted,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.1.sp
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = "Pivot beyond the built-in indexes. Browse public camera directories and sniff page source for playable stream URLs without leaving the app.",
                        color = IntelPalette.Muted,
                        fontSize = 10.5.sp,
                        lineHeight = 15.sp
                    )
                    Spacer(Modifier.height(16.dp))

                    BrowserFeature(
                        icon = Icons.Default.Search,
                        title = "DOM STREAM SNIFFER",
                        description = "Scans loaded pages for MJPEG, snapshot and embedded feed URLs."
                    )
                    Spacer(Modifier.height(10.dp))
                    BrowserFeature(
                        icon = Icons.Default.PlayArrow,
                        title = "ONE-TAP VIEWER",
                        description = "Send any detected feed straight to the stream viewer."
                    )
                    Spacer(Modifier.height(10.dp))
                    BrowserFeature(
                        icon = Icons.Default.Shield,
                        title = "CONTAINED SESSION",
                        description = "Runs in a dedicated in-app WebView with a clean user agent."
                    )

                    Spacer(Modifier.height(18.dp))
                    Button(
                        onClick = onLaunch,
                        modifier = Modifier.fillMaxWidth().height(46.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = IntelPalette.Cyan,
                            contentColor = IntelPalette.Background
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "LAUNCH BROWSER",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.8.sp
                        )
                    }
                }
            }
        }

        item {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = IntelPalette.Surface,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, IntelPalette.Border)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = IntelPalette.Amber,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "External pages are untrusted. Only view feeds you are authorized to access.",
                        color = IntelPalette.Muted,
                        fontSize = 9.sp,
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserFeature(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(28.dp)
                .background(IntelPalette.Cyan.copy(alpha = 0.08f), CircleShape)
                .border(1.dp, IntelPalette.Cyan.copy(alpha = 0.15f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = IntelPalette.Cyan,
                modifier = Modifier.size(14.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = IntelPalette.Text,
                fontSize = 9.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                color = IntelPalette.Muted,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
        }
    }
}
