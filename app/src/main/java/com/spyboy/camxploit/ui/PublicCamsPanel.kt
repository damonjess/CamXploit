package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import coil.compose.AsyncImage
import com.spyboy.camxploit.StreamSource
import com.spyboy.camxploit.StreamViewerActivity
import com.spyboy.camxploit.osint.InsecamClient
import com.spyboy.camxploit.osint.InsecamScraper
import com.spyboy.camxploit.osint.OsintViewModel
import kotlinx.coroutines.launch

@UnstableApi
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PublicCamsPanel(
    viewModel: OsintViewModel,
    neonGreen: Color = Color(0xFF39FF14),
    darkCard: Color = Color(0xFF1A1A1A)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val countries by viewModel.countries.collectAsStateWithLifecycle()
    val cameras by viewModel.cameras.collectAsStateWithLifecycle()
    val selectedCountry by viewModel.selectedCountry.collectAsStateWithLifecycle()
    
    val source by viewModel.source.collectAsStateWithLifecycle()
    val publicCameras by viewModel.publicCameras.collectAsStateWithLifecycle()
    val genericLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val genericError by viewModel.error.collectAsStateWithLifecycle()

    val scraperError by viewModel.insecamError.collectAsStateWithLifecycle()
    val scraperLoading by viewModel.insecamLoading.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMorePages.collectAsStateWithLifecycle()

    var showManualBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (countries.isEmpty()) viewModel.loadCountries()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        if (selectedCountry == null && source == OsintViewModel.Source.PublicCams) {
            OpentopiaHeader(
                onLoad = { viewModel.loadOpentopiaCameras(it) },
                neonGreen = neonGreen
            )
            Spacer(Modifier.height(16.dp))
        }

        when {
            source == OsintViewModel.Source.Opentopia -> {
                OpentopiaResultView(
                    cameras = publicCameras,
                    loading = genericLoading,
                    error = genericError,
                    neonGreen = neonGreen,
                    onBack = { viewModel.selectSource(OsintViewModel.Source.PublicCams) },
                    onViewClick = { cam ->
                        // Launch immediately with pageUrl, resolve inside activity
                        StreamViewerActivity.launch(context, cam, cam.location)
                    },
                    onSaveClick = { viewModel.saveCamera(it) }
                )
            }
            selectedCountry != null -> {
                CountryCameraView(
                    cameras = cameras,
                    loading = scraperLoading,
                    hasMore = hasMore,
                    error = scraperError,
                    darkCard = darkCard,
                    neonGreen = neonGreen,
                    onBack = { viewModel.clearCountrySelection() },
                    onRetry = { viewModel.loadInsecamCountry(selectedCountry!!) },
                    onLoadMore = { viewModel.loadNextInsecamPage() },
                    onManualBrowse = { showManualBrowser = true },
                    onViewClick = { cam ->
                        val viewUrl = "http://www.insecam.org/en/view/${cam.id}/"
                        scope.launch {
                            val result = InsecamScraper.scrapePage(viewUrl)
                            val streamUrl = result.streamUrl.ifBlank { viewUrl }
                            val source = StreamSource(
                                id = cam.id,
                                url = streamUrl,
                                title = cam.location ?: cam.ip ?: "Camera",
                                location = cam.location ?: "Unknown",
                                thumbnailUrl = cam.imageUrl,
                                protocol = "mjpeg"
                            )
                            StreamViewerActivity.launch(context, source, cam.location ?: cam.ip ?: "Public")
                        }
                    },
                    onSaveCamera = { viewModel.saveCamera(it) }
                )
            }
            else -> {
                CountryGridView(
                    countries = countries,
                    darkCard = darkCard,
                    neonGreen = neonGreen,
                    onSelect = { code ->
                        viewModel.selectCountry(code)
                    }
                )
            }
        }
    }

    if (showManualBrowser && selectedCountry != null) {
        ManualBrowserOverlay(
            url = "http://www.insecam.org/en/bycountry/$selectedCountry/",
            title = "Insecam",
            onClose = { showManualBrowser = false },
            onCameraTap = { url, title ->
                val source = StreamSource(url = url, title = title, protocol = "mjpeg")
                StreamViewerActivity.launch(context, source, "Public")
            }
        )
    }
}

@Composable
private fun CountryGridView(
    countries: List<OsintViewModel.InsecamCountry>,
    darkCard: Color,
    neonGreen: Color,
    onSelect: (String) -> Unit
) {
    Column {
        Text(
            "PUBLIC CAMERA SOURCES",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))

        if (countries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen)
            }
        } else {
            Text(
                "${countries.size} COUNTRIES",
                color = neonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(countries.sortedByDescending { it.count }) { country ->
                    CountryRow(
                        country = country,
                        darkCard = darkCard,
                        onClick = { onSelect(country.code) }
                    )
                }
            }
        }
    }
}

@UnstableApi
@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun CountryCameraView(
    cameras: List<InsecamClient.PublicCamera>,
    loading: Boolean,
    hasMore: Boolean,
    error: String?,
    darkCard: Color,
    neonGreen: Color,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onManualBrowse: () -> Unit,
    onViewClick: (InsecamClient.PublicCamera) -> Unit,
    onSaveCamera: (StreamSource) -> Unit
) {
    val pullRefreshState = rememberPullRefreshState(
        refreshing = loading,
        onRefresh = onRetry
    )

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White)
            Text("BACK", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onRetry) {
                Icon(Icons.Default.Refresh, "Retry", tint = neonGreen)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            if (loading && cameras.isEmpty()) "LOADING CAMERAS..." else "${cameras.size} CAMERAS",
            color = neonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )

        if (error != null && !loading) {
            Spacer(Modifier.height(10.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A))) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠ $error", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onManualBrowse,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("OPEN IN BROWSER", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    TextButton(onClick = onRetry, modifier = Modifier.fillMaxWidth()) {
                        Text("RETRY SCRAPE", color = neonGreen)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val gridState = rememberLazyGridState()
        val shouldLoadMore = remember {
            derivedStateOf {
                val totalItemsCount = gridState.layoutInfo.totalItemsCount
                val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 2
            }
        }

        LaunchedEffect(shouldLoadMore.value, hasMore) {
            if (shouldLoadMore.value && !loading && hasMore) {
                onLoadMore()
            }
        }

        Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(cameras, key = { it.id }) { cam ->
                    PublicCameraCard(
                        camera = StreamSource(
                            id = cam.id,
                            url = "http://www.insecam.org/en/view/${cam.id}/",
                            title = cam.location ?: cam.ip ?: "Camera",
                            location = cam.location ?: "Unknown",
                            thumbnailUrl = cam.imageUrl,
                            protocol = "mjpeg"
                        ),
                        onViewClick = { onViewClick(cam) },
                        onSaveClick = {
                            onSaveCamera(StreamSource(
                                id = cam.id,
                                url = "http://www.insecam.org/en/view/${cam.id}/",
                                title = cam.location ?: cam.ip ?: "Camera",
                                location = cam.location ?: "Unknown",
                                thumbnailUrl = cam.imageUrl,
                                protocol = "mjpeg"
                            ))
                        }
                    )
                }
                if (loading) {
                    item(span = { GridItemSpan(2) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = neonGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                        }
                    }
                }
            }

            PullRefreshIndicator(
                refreshing = loading,
                state = pullRefreshState,
                modifier = Modifier.align(Alignment.TopCenter),
                contentColor = neonGreen,
                backgroundColor = Color(0xFF1A1A1A)
            )
        }
    }
}

@Composable
fun PublicCameraCard(
    camera: StreamSource,
    onViewClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.DarkGray)) {
                val thumbUrl = camera.bestThumbnailUrl()
                if (thumbUrl.isNotBlank()) {
                    AsyncImage(
                        model = thumbUrl,
                        contentDescription = "Thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFF00FFFF).copy(alpha = 0.3f),
                        modifier = Modifier.align(Alignment.Center).size(32.dp)
                    )
                }
                
                Surface(
                    color = Color.Black.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        "LIVE",
                        color = Color(0xFF39FF14),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }

            Column(modifier = Modifier.padding(8.dp)) {
                Text(
                    text = camera.title.ifBlank { "Unnamed Camera" },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = camera.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(onClick = onSaveClick, contentPadding = PaddingValues(0.dp)) {
                        Text("SAVE", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    IconButton(onClick = onViewClick, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "View", tint = Color(0xFF39FF14))
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: OsintViewModel.InsecamCountry,
    darkCard: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = darkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(country.name.uppercase(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                Text("CODE: ${country.code.uppercase()}", color = Color.Gray, fontSize = 10.sp)
            }
            Surface(color = Color(0xFF252525), shape = RoundedCornerShape(6.dp)) {
                Text("${country.count}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
            }
        }
    }
}

@Composable
fun OpentopiaHeader(
    onLoad: (Int) -> Unit,
    neonGreen: Color
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                text = "OPENTOPIA LIVE DIRECTORY",
                color = Color.Gray,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = { onLoad(50) },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color.DarkGray)
                ) {
                    Icon(Icons.Default.Search, null, tint = neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN 50", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = { onLoad(100) },
                    modifier = Modifier.weight(1f).height(40.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, Color.DarkGray)
                ) {
                    Icon(Icons.Default.Search, null, tint = neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("SCAN 100", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@UnstableApi
@Composable
fun OpentopiaResultView(
    cameras: List<StreamSource>,
    loading: Boolean,
    error: String?,
    neonGreen: Color,
    onBack: () -> Unit,
    onViewClick: (StreamSource) -> Unit,
    onSaveClick: (StreamSource) -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White)
            Text("BACK TO SOURCES", color = Color.White, fontSize = 12.sp)
        }
        
        Spacer(Modifier.height(10.dp))
        
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen)
            }
        } else if (error != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A))) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠ $error", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("DISMISS", color = neonGreen)
                    }
                }
            }
        } else {
            Text(
                "OPENTOPIA RESULTS (${cameras.size})",
                color = neonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 100.dp)
            ) {
                items(cameras, key = { it.id }) { cam ->
                    PublicCameraCard(
                        camera = cam,
                        onViewClick = { onViewClick(cam) },
                        onSaveClick = { onSaveClick(cam) }
                    )
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ManualBrowserOverlay(
    url: String,
    title: String,
    onClose: () -> Unit,
    onCameraTap: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).navigationBarsPadding()) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.0"
                    webViewClient = object : android.webkit.WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: android.webkit.WebResourceRequest?): Boolean {
                            val u = request?.url?.toString() ?: return false
                            if (u.contains("/en/view/")) {
                                val id = u.substringAfter("/en/view/").takeWhile { it.isDigit() }
                                onCameraTap(u, "Camera $id")
                                return true
                            }
                            return false
                        }
                    }
                    loadUrl(url)
                }
            },
            modifier = Modifier.fillMaxSize().padding(top = 48.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth().background(Color(0xCC000000)).statusBarsPadding().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title.uppercase(), color = Color(0xFF39FF14), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("CLOSE", color = Color.White) }
        }
    }
}
