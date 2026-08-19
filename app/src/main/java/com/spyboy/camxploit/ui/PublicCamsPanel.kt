package com.spyboy.camxploit.ui

import android.webkit.WebResourceRequest
import android.webkit.WebViewClient
import android.annotation.SuppressLint
import android.webkit.WebView
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.spyboy.camxploit.osint.CameraDiagnostics
import com.spyboy.camxploit.osint.InsecamClient
import com.spyboy.camxploit.osint.InsecamScraper
import com.spyboy.camxploit.osint.IntelSourceId
import com.spyboy.camxploit.osint.OsintViewModel
import com.spyboy.camxploit.osint.SourceHealth
import com.spyboy.camxploit.osint.SourceHealthStatus
import com.spyboy.camxploit.osint.ThumbnailResolver
import kotlinx.coroutines.launch
import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.material.icons.filled.Videocam

@androidx.annotation.OptIn(UnstableApi::class)

@OptIn(ExperimentalMaterialApi::class)
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
    val sourceHealth by viewModel.sourceHealth.collectAsStateWithLifecycle()
    val diagnostics by viewModel.cameraDiagnostics.collectAsStateWithLifecycle()

    val recentlyViewed by viewModel.recentlyViewed.collectAsStateWithLifecycle()

    val scraperError by viewModel.insecamError.collectAsStateWithLifecycle()
    val scraperLoading by viewModel.insecamLoading.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMorePages.collectAsStateWithLifecycle()
    val currentCountryPage by viewModel.currentCountryPage.collectAsStateWithLifecycle()

    var showManualBrowser by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var verificationFilter by remember { mutableStateOf("ALL") }
    var sortAlphabetically by remember { mutableStateOf(false) }
    var selectedDiagnosticCamera by remember { mutableStateOf<StreamSource?>(null) }

    val activeHealth = when (source) {
        is OsintViewModel.Source.Opentopia -> sourceHealth[IntelSourceId.OPENTOPIA]
        is OsintViewModel.Source.GitHub -> sourceHealth[IntelSourceId.GITHUB]
        is OsintViewModel.Source.MyCameras -> sourceHealth[IntelSourceId.MY_CAMERAS]
        else -> if (selectedCountry != null) sourceHealth[IntelSourceId.COUNTRY_DIRECTORY] else null
    }

    val filteredPublicCameras = remember(publicCameras, searchQuery, verificationFilter, sortAlphabetically) {
        val textFiltered = if (searchQuery.isBlank()) publicCameras else publicCameras.filter {
            it.title.contains(searchQuery, ignoreCase = true) ||
                it.location.contains(searchQuery, ignoreCase = true) ||
                it.sourceLabel.contains(searchQuery, ignoreCase = true)
        }
        val verificationFiltered = when (verificationFilter) {
            "VERIFIED" -> textFiltered.filter { it.verification !in listOf("Unchecked", "Verifying", "Unavailable") }
            "MJPEG" -> textFiltered.filter { it.verification == "MJPEG" }
            "SNAPSHOT" -> textFiltered.filter { it.verification == "Snapshot" }
            else -> textFiltered
        }
        if (sortAlphabetically) verificationFiltered.sortedBy { it.title.lowercase() } else verificationFiltered
    }

    val filteredInsecamCameras = remember(cameras, searchQuery) {
        if (searchQuery.isBlank()) cameras
        else cameras.filter {
            (it.location ?: "").contains(searchQuery, ignoreCase = true) ||
                    (it.ip ?: "").contains(searchQuery, ignoreCase = true)
        }
    }

    LaunchedEffect(Unit) {
        if (countries.isEmpty()) viewModel.loadCountries()
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = genericLoading || scraperLoading,
        onRefresh = { viewModel.refreshCurrentSource() }
    )

    val gridState = rememberLazyListState()

    // Pagination logic for Insecam
    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = gridState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 5
        }
    }

    LaunchedEffect(shouldLoadMore.value, hasMore, selectedCountry) {
        if (shouldLoadMore.value && !scraperLoading && hasMore && selectedCountry != null) {
            viewModel.loadNextInsecamPage()
        }
    }

    Box(modifier = Modifier.fillMaxSize().pullRefresh(pullRefreshState)) {
        LazyColumn(
            state = gridState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 100.dp)
        ) {
            // 1. Search Bar
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    placeholder = { Text("Search location or title...", color = Color.Gray) },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = neonGreen) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = Color.Gray)
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = neonGreen,
                        unfocusedBorderColor = Color.DarkGray,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
            }

            item {
                IntelSourceStatus(activeHealth)
                if (source is OsintViewModel.Source.Opentopia || source is OsintViewModel.Source.GitHub || source is OsintViewModel.Source.MyCameras) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("ALL", "VERIFIED", "MJPEG", "SNAPSHOT").forEach { filter ->
                            FilterChip(
                                selected = verificationFilter == filter,
                                onClick = { verificationFilter = filter },
                                label = { Text(filter, fontSize = 9.sp) }
                            )
                        }
                    }
                    TextButton(
                        onClick = { sortAlphabetically = !sortAlphabetically },
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Text(if (sortAlphabetically) "SORT: A–Z" else "SORT: SOURCE ORDER", fontSize = 10.sp)
                    }
                }
            }

            // 2. Recently Viewed
            if (recentlyViewed.isNotEmpty()) {
                item {
                    Text(
                        "RECENTLY VIEWED",
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentlyViewed) { cam ->
                            RecentCamItem(cam) {
                                viewModel.addToRecentlyViewed(cam)
                                StreamViewerActivity.launch(context, cam, cam.location)
                            }
                        }
                    }
                }
            }

            // 3. Header (Opentopia/GitHub)
            if (selectedCountry == null && source == OsintViewModel.Source.PublicCams) {
                item {
                    OpentopiaHeader(
                        onLoad = { viewModel.loadOpentopiaCameras(it) },
                        onLoadGitHub = { viewModel.loadGitHubMotionJpegSources() },
                        neonGreen = neonGreen
                    )
                    Spacer(Modifier.height(16.dp))
                }
            }

            // 4. Results Section
            when {
                source == OsintViewModel.Source.Opentopia -> {
                    opentopiaResults(
                        cameras = filteredPublicCameras,
                        loading = genericLoading,
                        error = genericError,
                        neonGreen = neonGreen,
                        onBack = { viewModel.selectSource(OsintViewModel.Source.PublicCams) },
                        onViewClick = { cam ->
                            viewModel.addToRecentlyViewed(cam)
                            StreamViewerActivity.launch(context, cam, cam.location)
                        },
                        onSaveClick = { viewModel.saveCamera(it) },
                        onVerify = { viewModel.verifyCamera(it) },
                        onDiagnostics = { selectedDiagnosticCamera = it }
                    )
                }
                source == OsintViewModel.Source.GitHub -> {
                    gitHubResults(
                        cameras = filteredPublicCameras,
                        loading = genericLoading,
                        error = genericError,
                        neonGreen = neonGreen,
                        onBack = { viewModel.selectSource(OsintViewModel.Source.PublicCams) },
                        onViewClick = { cam ->
                            viewModel.addToRecentlyViewed(cam)
                            StreamViewerActivity.launch(context, cam, cam.location)
                        },
                        onSaveClick = { viewModel.saveCamera(it) },
                        onVerify = { viewModel.verifyCamera(it) },
                        onDiagnostics = { selectedDiagnosticCamera = it }
                    )
                }
                source == OsintViewModel.Source.MyCameras -> {
                    opentopiaResults(
                        cameras = filteredPublicCameras,
                        loading = genericLoading,
                        error = genericError,
                        neonGreen = neonGreen,
                        heading = "MY CAMERAS",
                        onBack = { viewModel.selectSource(OsintViewModel.Source.PublicCams) },
                        onViewClick = { cam ->
                            viewModel.addToRecentlyViewed(cam)
                            StreamViewerActivity.launch(context, cam, cam.location)
                        },
                        onSaveClick = { viewModel.saveCamera(it) },
                        onVerify = { viewModel.verifyCamera(it) },
                        onDiagnostics = { selectedDiagnosticCamera = it }
                    )
                }
                selectedCountry != null -> {
                    countryCameraResults(
                        cameras = filteredInsecamCameras,
                        loading = scraperLoading,
                        error = scraperError,
                        neonGreen = neonGreen,
                        hasMore = hasMore,
                        page = currentCountryPage,
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
                                viewModel.addToRecentlyViewed(source)
                                StreamViewerActivity.launch(context, source, cam.location ?: cam.ip ?: "Public")
                            }
                        },
                        onSaveCamera = { viewModel.saveCamera(it) },
                        onVerifyCamera = { cam ->
                            viewModel.verifyCamera(
                                StreamSource(
                                    id = cam.id,
                                    url = "http://www.insecam.org/en/view/${cam.id}/",
                                    title = cam.location ?: cam.ip ?: "Camera",
                                    location = cam.location ?: "Unknown",
                                    thumbnailUrl = cam.imageUrl,
                                    protocol = "mjpeg",
                                    sourceLabel = "Country directory"
                                )
                            )
                        },
                        onDiagnostics = { cam ->
                            selectedDiagnosticCamera = StreamSource(
                                id = cam.id,
                                url = "http://www.insecam.org/en/view/${cam.id}/",
                                title = cam.location ?: cam.ip ?: "Camera",
                                location = cam.location ?: "Unknown",
                                thumbnailUrl = cam.imageUrl,
                                protocol = "mjpeg",
                                sourceLabel = "Country directory"
                            )
                        }
                    )
                }
                else -> {
                    countryGridResults(
                        countries = countries,
                        darkCard = darkCard,
                        neonGreen = neonGreen,
                        onSelect = { code ->
                            viewModel.selectCountry(code)
                        }
                    )
                }
            }

            // Extra padding for bottom nav
            item { Spacer(Modifier.height(80.dp)) }
        }

        PullRefreshIndicator(
            refreshing = genericLoading || scraperLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = neonGreen,
            backgroundColor = Color(0xFF1A1A1A)
        )
    }

    selectedDiagnosticCamera?.let { camera ->
        CameraDiagnosticsDialog(
            camera = camera,
            diagnostics = diagnostics[camera.id],
            onDismiss = { selectedDiagnosticCamera = null }
        )
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
private fun IntelSourceStatus(health: SourceHealth?) {
    if (health == null || health.status == SourceHealthStatus.IDLE) return

    val color = when (health.status) {
        SourceHealthStatus.HEALTHY -> Color(0xFF39FF14)
        SourceHealthStatus.LOADING -> Color(0xFF00FFFF)
        SourceHealthStatus.PARTIAL -> Color(0xFFFFA500)
        SourceHealthStatus.ERROR -> Color(0xFFFF6B6B)
        SourceHealthStatus.IDLE -> Color.Gray
    }
    val label = when (health.status) {
        SourceHealthStatus.LOADING -> "SOURCE LOADING"
        SourceHealthStatus.HEALTHY -> "SOURCE READY"
        SourceHealthStatus.PARTIAL -> "SOURCE PARTIAL"
        SourceHealthStatus.ERROR -> "SOURCE ERROR"
        SourceHealthStatus.IDLE -> "SOURCE IDLE"
    }

    Surface(
        color = Color(0xFF151515),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 7.dp)) {
            Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Text(health.message, color = Color.LightGray, fontSize = 10.sp, maxLines = 2)
        }
    }
}

@Composable
private fun CameraDiagnosticsDialog(
    camera: StreamSource,
    diagnostics: CameraDiagnostics?,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("CAMERA DIAGNOSTICS") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(camera.title, fontWeight = FontWeight.Bold)
                Text("Source: ${camera.sourceLabel.ifBlank { "Manual / unknown" }}", fontSize = 12.sp)
                Text("Status: ${diagnostics?.verification?.label ?: camera.verification}", fontSize = 12.sp)
                Text("Content type: ${diagnostics?.contentType?.ifBlank { "Not checked" } ?: camera.contentType.ifBlank { "Not checked" }}", fontSize = 12.sp)
                Text("URL: ${diagnostics?.effectiveUrl?.ifBlank { camera.bestPlaybackUrl() } ?: camera.bestPlaybackUrl()}", fontSize = 11.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text(diagnostics?.message ?: "Select CHECK to verify this feed.", fontSize = 12.sp, color = Color.Gray)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("CLOSE") } }
    )
}

private fun LazyListScope.countryGridResults(
    countries: List<OsintViewModel.InsecamCountry>,
    darkCard: Color,
    neonGreen: Color,
    onSelect: (String) -> Unit
) {
    item {
        Text(
            "PUBLIC CAMERA SOURCES • DIRECTORY COUNTS MAY VARY",
            color = Color.Gray,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(10.dp))
    }

    if (countries.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen)
            }
        }
    } else {
        item {
            Text(
                "${countries.size} COUNTRIES",
                color = neonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))
        }

        items(countries.sortedByDescending { it.count }.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { country ->
                    Box(modifier = Modifier.weight(1f)) {
                        CountryRow(
                            country = country,
                            darkCard = darkCard,
                            onClick = { onSelect(country.code) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@OptIn(UnstableApi::class)
private fun LazyListScope.countryCameraResults(
    cameras: List<InsecamClient.PublicCamera>,
    loading: Boolean,
    error: String?,
    neonGreen: Color,
    hasMore: Boolean,
    page: Int,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onManualBrowse: () -> Unit,
    onViewClick: (InsecamClient.PublicCamera) -> Unit,
    onSaveCamera: (StreamSource) -> Unit,
    onVerifyCamera: (InsecamClient.PublicCamera) -> Unit,
    onDiagnostics: (InsecamClient.PublicCamera) -> Unit
) {
    item {
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
            if (loading && cameras.isEmpty()) "LOADING CAMERAS..." else "${cameras.size} CAMERAS • PAGE $page",
            color = neonGreen,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
    }

    if (error != null && !loading) {
        item {
            Spacer(Modifier.height(10.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A))
            ) {
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
    }

    item { Spacer(Modifier.height(10.dp)) }

    items(cameras.chunked(2)) { row ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            row.forEach { cam ->
                Box(modifier = Modifier.weight(1f)) {
                    PublicCameraCard(
                        camera = StreamSource(
                            id = cam.id,
                            url = "http://www.insecam.org/en/view/${cam.id}/",
                            title = cam.location ?: cam.ip ?: "Camera",
                            location = cam.location ?: "Unknown",
                            thumbnailUrl = cam.imageUrl,
                            protocol = "mjpeg",
                            sourceLabel = "Country directory"
                        ),
                        onViewClick = { onViewClick(cam) },
                        onSaveClick = {
                            onSaveCamera(StreamSource(
                                id = cam.id,
                                url = "http://www.insecam.org/en/view/${cam.id}/",
                                title = cam.location ?: cam.ip ?: "Camera",
                                location = cam.location ?: "Unknown",
                                thumbnailUrl = cam.imageUrl,
                                protocol = "mjpeg",
                                sourceLabel = "Country directory"
                            ))
                        },
                        onVerify = { onVerifyCamera(cam) },
                        onDiagnostics = { onDiagnostics(cam) }
                    )
                }
            }
            if (row.size == 1) Spacer(Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
    }

    // Make pagination discoverable even if the automatic scroll listener does not fire.
    if (hasMore && !loading && error == null) {
        item {
            OutlinedButton(
                onClick = onLoadMore,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                border = BorderStroke(1.dp, neonGreen),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = neonGreen)
            ) {
                Text("LOAD 6 MORE CAMERAS", fontWeight = FontWeight.Bold)
            }
        }
    }

    if (loading) {
        item {
            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen, modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }
        }
    }
}

@Composable
fun PublicCameraCard(
    camera: StreamSource,
    onViewClick: () -> Unit,
    onSaveClick: () -> Unit,
    onVerify: () -> Unit = {},
    onDiagnostics: () -> Unit = {}
) {
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val thumbUrl = camera.bestThumbnailUrl()

    LaunchedEffect(camera.id, camera.url) {
        if (thumbUrl.isBlank()) {
            thumbnailBitmap = ThumbnailResolver.resolve(camera)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A))
    ) {
        Column {
            Box(modifier = Modifier.fillMaxWidth().height(100.dp).background(Color.DarkGray)) {
                when {
                    thumbnailBitmap != null -> {
                        Image(
                            bitmap = thumbnailBitmap!!.asImageBitmap(),
                            contentDescription = camera.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    thumbUrl.isNotBlank() -> {
                        AsyncImage(
                            model = thumbUrl,
                            contentDescription = "Thumbnail",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                    else -> {
                        // Placeholder
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Videocam,
                                    contentDescription = null,
                                    tint = Color.Cyan.copy(alpha = 0.3f),
                                    modifier = Modifier.size(32.dp)
                                )
                                Text(
                                    text = camera.location.take(15),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color.Gray.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }

                val statusBadge = when {
                    camera.url.contains("mjpg", ignoreCase = true) || camera.protocol == "mjpeg" -> "LIVE"
                    camera.url.contains("rtsp", ignoreCase = true) || camera.protocol == "rtsp" -> "RTSP"
                    camera.url.contains("http") -> "SNAP"
                    else -> "VIEW"
                }

                Surface(
                    color = when (statusBadge) {
                        "LIVE" -> Color(0xFF4CAF50)
                        "RTSP" -> Color(0xFF2196F3)
                        "SNAP" -> Color(0xFFFF9800)
                        else -> Color.Black.copy(alpha = 0.6f)
                    },
                    shape = RoundedCornerShape(bottomEnd = 4.dp),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        statusBadge,
                        color = Color.White,
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
                if (camera.sourceLabel.isNotBlank() || camera.verification != "Unchecked") {
                    Text(
                        text = listOf(camera.sourceLabel, camera.verification).filter { it.isNotBlank() }.joinToString(" • "),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (camera.verification == "Unavailable") Color(0xFFFF6B6B) else Color(0xFF39FF14),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TextButton(
                        onClick = onSaveClick,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("SAVE", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    TextButton(
                        onClick = onVerify,
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(0.dp)
                    ) { Text("CHECK", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
                    IconButton(onClick = onViewClick, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "View camera", tint = Color(0xFF39FF14))
                    }
                }
                TextButton(
                    onClick = onDiagnostics,
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(0.dp)
                ) { Text("INFO / DIAGNOSTICS", fontSize = 9.sp, fontWeight = FontWeight.Bold) }
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
    onLoadGitHub: () -> Unit,
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
            
            Spacer(Modifier.height(8.dp))
            
            AssistChip(
                onClick = onLoadGitHub,
                label = { Text("MotionJPEG", color = Color.White, fontSize = 10.sp) },
                leadingIcon = { Icon(Icons.Default.Search, null, Modifier.size(14.dp), tint = neonGreen) },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = Color(0xFF1A1A1A),
                    labelColor = Color.White
                ),
                border = BorderStroke(1.dp, Color.DarkGray)
            )
        }
    }
}

@OptIn(UnstableApi::class)
private fun LazyListScope.gitHubResults(
    cameras: List<StreamSource>,
    loading: Boolean,
    error: String?,
    neonGreen: Color,
    onBack: () -> Unit,
    onViewClick: (StreamSource) -> Unit,
    onSaveClick: (StreamSource) -> Unit,
    onVerify: (StreamSource) -> Unit,
    onDiagnostics: (StreamSource) -> Unit
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White)
            Text("BACK TO SOURCES", color = Color.White, fontSize = 12.sp)
        }
        
        Spacer(Modifier.height(10.dp))
    }

    if (loading && cameras.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen)
            }
        }
    } else if (error != null && cameras.isEmpty()) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠ $error", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("DISMISS", color = neonGreen)
                    }
                }
            }
        }
    } else {
        item {
            Text(
                "GITHUB MJPEG RESULTS (${cameras.size})",
                color = neonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
        }

        items(cameras.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { cam ->
                    Box(modifier = Modifier.weight(1f)) {
                        PublicCameraCard(
                            camera = cam,
                            onViewClick = { onViewClick(cam) },
                            onSaveClick = { onSaveClick(cam) },
                            onVerify = { onVerify(cam) },
                            onDiagnostics = { onDiagnostics(cam) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@OptIn(UnstableApi::class)
private fun LazyListScope.opentopiaResults(
    cameras: List<StreamSource>,
    loading: Boolean,
    error: String?,
    neonGreen: Color,
    heading: String = "OPENTOPIA RESULTS",
    onBack: () -> Unit,
    onViewClick: (StreamSource) -> Unit,
    onSaveClick: (StreamSource) -> Unit,
    onVerify: (StreamSource) -> Unit,
    onDiagnostics: (StreamSource) -> Unit
) {
    item {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Back", tint = Color.White)
            Text("BACK TO SOURCES", color = Color.White, fontSize = 12.sp)
        }
        
        Spacer(Modifier.height(10.dp))
    }

    if (loading && cameras.isEmpty()) {
        item {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen)
            }
        }
    } else if (error != null && cameras.isEmpty()) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF2A0A0A))
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text("⚠ $error", color = Color(0xFFFF6B6B), fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                        Text("DISMISS", color = neonGreen)
                    }
                }
            }
        }
    } else {
        item {
            Text(
                "$heading (${cameras.size})",
                color = neonGreen,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
            Spacer(Modifier.height(10.dp))
        }

        items(cameras.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { cam ->
                    Box(modifier = Modifier.weight(1f)) {
                        PublicCameraCard(
                            camera = cam,
                            onViewClick = { onViewClick(cam) },
                            onSaveClick = { onSaveClick(cam) },
                            onVerify = { onVerify(cam) },
                            onDiagnostics = { onDiagnostics(cam) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
fun RecentCamItem(
    camera: StreamSource,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .size(width = 120.dp, height = 70.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        border = BorderStroke(1.dp, Color.DarkGray)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumbUrl = camera.bestThumbnailUrl()
            if (thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.6f
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
            ) {
                Text(
                    text = camera.location.uppercase(),
                    color = Color.White,
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
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
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
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
