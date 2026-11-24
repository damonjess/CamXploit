package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

@androidx.annotation.OptIn(UnstableApi::class)
@OptIn(ExperimentalMaterialApi::class)
@UnstableApi
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PublicCamsPanel(
    viewModel: OsintViewModel,
    neonGreen: Color = IntelPalette.Green,
    darkCard: Color = IntelPalette.Card
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
    val selectedCountryInfo = remember(countries, selectedCountry) {
        countries.firstOrNull { it.code == selectedCountry }
    }

    val filteredPublicCameras = remember(
        publicCameras,
        searchQuery,
        verificationFilter,
        sortAlphabetically
    ) {
        val query = searchQuery.trim()
        val textFiltered = if (query.isBlank()) {
            publicCameras
        } else {
            publicCameras.filter {
                it.title.contains(query, ignoreCase = true) ||
                    it.location.contains(query, ignoreCase = true) ||
                    it.sourceLabel.contains(query, ignoreCase = true) ||
                    it.url.contains(query, ignoreCase = true)
            }
        }
        val verificationFiltered = textFiltered.filter {
            val verification = it.verification.lowercase()
            when (verificationFilter) {
                "VERIFIED" -> verification in listOf("mjpeg", "snapshot", "rtsp")
                "MJPEG" -> verification == "mjpeg"
                "SNAPSHOT" -> verification == "snapshot"
                else -> true
            }
        }
        if (sortAlphabetically) {
            verificationFiltered.sortedBy { it.title.lowercase() }
        } else {
            verificationFiltered
        }
    }

    val filteredInsecamCameras = remember(cameras, searchQuery) {
        val query = searchQuery.trim()
        if (query.isBlank()) {
            cameras
        } else {
            cameras.filter {
                (it.location ?: "").contains(query, ignoreCase = true) ||
                    (it.ip ?: "").contains(query, ignoreCase = true)
            }
        }
    }

    val filteredCountries = remember(countries, searchQuery) {
        val query = searchQuery.trim()
        val matches = if (query.isBlank()) {
            countries
        } else {
            countries.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.code.contains(query, ignoreCase = true)
            }
        }
        matches.sortedByDescending { it.count }
    }

    LaunchedEffect(Unit) {
        if (countries.isEmpty()) viewModel.loadCountries()
    }

    val pullRefreshState = rememberPullRefreshState(
        refreshing = genericLoading || scraperLoading,
        onRefresh = { viewModel.refreshCurrentSource() }
    )

    val listState = rememberLazyListState()

    LaunchedEffect(selectedCountry, source) {
        listState.scrollToItem(0)
    }

    val shouldLoadMore = remember {
        derivedStateOf {
            val totalItemsCount = listState.layoutInfo.totalItemsCount
            val lastVisibleItemIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
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
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                IntelSearchField(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    placeholder = when {
                        selectedCountry != null -> "Filter cameras in this region…"
                        source is OsintViewModel.Source.Opentopia ||
                            source is OsintViewModel.Source.GitHub ||
                            source is OsintViewModel.Source.MyCameras -> "Search loaded feeds…"
                        else -> "Search countries…"
                    },
                    accent = neonGreen
                )
            }

            if (activeHealth != null && activeHealth.status != SourceHealthStatus.IDLE) {
                item {
                    SourceStatusBar(
                        health = activeHealth,
                        accent = neonGreen,
                        onRefresh = { viewModel.refreshCurrentSource() }
                    )
                }
            }

            if (recentlyViewed.isNotEmpty()) {
                item {
                    RecentSignalsRail(
                        cameras = recentlyViewed,
                        cardColor = darkCard,
                        onClick = { cam ->
                            viewModel.addToRecentlyViewed(cam)
                            StreamViewerActivity.launch(context, cam, cam.location)
                        }
                    )
                }
            }

            when {
                source is OsintViewModel.Source.Opentopia -> {
                    cameraResultsSection(
                        heading = "OPENTOPIA DIRECTORY",
                        eyebrow = "LIVE PUBLIC INDEX",
                        cameras = filteredPublicCameras,
                        totalCount = publicCameras.size,
                        loading = genericLoading,
                        error = genericError,
                        filter = verificationFilter,
                        onFilterChange = { verificationFilter = it },
                        sortAlphabetically = sortAlphabetically,
                        onToggleSort = { sortAlphabetically = !sortAlphabetically },
                        accent = neonGreen,
                        cardColor = darkCard,
                        backLabel = "BROWSE PUBLIC",
                        onBack = {
                            searchQuery = ""
                            verificationFilter = "ALL"
                            viewModel.selectSource(OsintViewModel.Source.PublicCams)
                        },
                        onRefresh = { viewModel.refreshCurrentSource() },
                        onResetFilters = {
                            searchQuery = ""
                            verificationFilter = "ALL"
                            sortAlphabetically = false
                        },
                        onViewClick = { cam ->
                            viewModel.addToRecentlyViewed(cam)
                            StreamViewerActivity.launch(context, cam, cam.location)
                        },
                        onSaveClick = { viewModel.saveCamera(it) },
                        onVerify = { viewModel.verifyCamera(it) },
                        onDiagnostics = { selectedDiagnosticCamera = it }
                    )
                }

                source is OsintViewModel.Source.GitHub -> {
                    cameraResultsSection(
                        heading = "MOTIONJPEG SOURCES",
                        eyebrow = "GITHUB CURATED LIST",
                        cameras = filteredPublicCameras,
                        totalCount = publicCameras.size,
                        loading = genericLoading,
                        error = genericError,
                        filter = verificationFilter,
                        onFilterChange = { verificationFilter = it },
                        sortAlphabetically = sortAlphabetically,
                        onToggleSort = { sortAlphabetically = !sortAlphabetically },
                        accent = neonGreen,
                        cardColor = darkCard,
                        backLabel = "BROWSE PUBLIC",
                        onBack = {
                            searchQuery = ""
                            verificationFilter = "ALL"
                            viewModel.selectSource(OsintViewModel.Source.PublicCams)
                        },
                        onRefresh = { viewModel.refreshCurrentSource() },
                        onResetFilters = {
                            searchQuery = ""
                            verificationFilter = "ALL"
                            sortAlphabetically = false
                        },
                        onViewClick = { cam ->
                            viewModel.addToRecentlyViewed(cam)
                            StreamViewerActivity.launch(context, cam, cam.location)
                        },
                        onSaveClick = { viewModel.saveCamera(it) },
                        onVerify = { viewModel.verifyCamera(it) },
                        onDiagnostics = { selectedDiagnosticCamera = it }
                    )
                }

                source is OsintViewModel.Source.MyCameras -> {
                    cameraResultsSection(
                        heading = "SAVED SIGNALS",
                        eyebrow = "LOCAL ARCHIVE",
                        cameras = filteredPublicCameras,
                        totalCount = publicCameras.size,
                        loading = genericLoading,
                        error = genericError,
                        filter = verificationFilter,
                        onFilterChange = { verificationFilter = it },
                        sortAlphabetically = sortAlphabetically,
                        onToggleSort = { sortAlphabetically = !sortAlphabetically },
                        accent = IntelPalette.Cyan,
                        cardColor = darkCard,
                        backLabel = "BROWSE PUBLIC",
                        onBack = {
                            searchQuery = ""
                            verificationFilter = "ALL"
                            viewModel.selectSource(OsintViewModel.Source.PublicCams)
                        },
                        onRefresh = { viewModel.loadMyCameras() },
                        onResetFilters = {
                            searchQuery = ""
                            verificationFilter = "ALL"
                            sortAlphabetically = false
                        },
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
                    countryResultsSection(
                        countryName = selectedCountryInfo?.name
                            ?: selectedCountry.orEmpty().uppercase(),
                        cameras = filteredInsecamCameras,
                        totalCount = cameras.size,
                        loading = scraperLoading,
                        error = scraperError,
                        hasMore = hasMore,
                        page = currentCountryPage,
                        accent = neonGreen,
                        cardColor = darkCard,
                        onBack = {
                            searchQuery = ""
                            viewModel.clearCountrySelection()
                        },
                        onRetry = { selectedCountry?.let { viewModel.loadInsecamCountry(it) } },
                        onLoadMore = { viewModel.loadNextInsecamPage() },
                        onManualBrowse = { showManualBrowser = true },
                        onViewClick = { cam ->
                            val pageUrl = cam.toStreamSource().url
                            scope.launch {
                                val result = InsecamScraper.scrapePage(pageUrl)
                                val streamUrl = result.streamUrl.ifBlank { pageUrl }
                                val source = cam.toStreamSource().copy(
                                    url = streamUrl,
                                    title = cam.location ?: cam.ip ?: "Camera",
                                    thumbnailUrl = cam.imageUrl,
                                    protocol = "mjpeg"
                                )
                                viewModel.addToRecentlyViewed(source)
                                StreamViewerActivity.launch(
                                    context,
                                    source,
                                    cam.location ?: cam.ip ?: "Public"
                                )
                            }
                        },
                        onSaveCamera = { viewModel.saveCamera(it) },
                        onVerifyCamera = { cam ->
                            viewModel.verifyCamera(cam.toStreamSource())
                        },
                        onDiagnostics = { cam ->
                            selectedDiagnosticCamera = cam.toStreamSource()
                        }
                    )
                }

                else -> {
                    publicLandingSection(
                        countriesCount = countries.size,
                        filteredCountries = filteredCountries,
                        query = searchQuery,
                        accent = neonGreen,
                        cardColor = darkCard,
                        onScan = { viewModel.loadOpentopiaCameras(it) },
                        onLoadGitHub = { viewModel.loadGitHubMotionJpegSources() },
                        onSelectCountry = { code ->
                            searchQuery = ""
                            viewModel.selectCountry(code)
                        }
                    )
                }
            }

            item { Spacer(Modifier.height(8.dp)) }
        }

        PullRefreshIndicator(
            refreshing = genericLoading || scraperLoading,
            state = pullRefreshState,
            modifier = Modifier.align(Alignment.TopCenter),
            contentColor = neonGreen,
            backgroundColor = IntelPalette.CardRaised
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
private fun IntelSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    accent: Color
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier.fillMaxWidth(),
        placeholder = {
            Text(
                text = placeholder,
                color = IntelPalette.Dim,
                fontSize = 11.sp
            )
        },
        leadingIcon = {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = accent.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp)
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear search",
                        tint = IntelPalette.Muted,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        },
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = IntelPalette.Surface,
            unfocusedContainerColor = IntelPalette.Surface,
            focusedBorderColor = accent.copy(alpha = 0.6f),
            unfocusedBorderColor = IntelPalette.Border,
            focusedTextColor = IntelPalette.Text,
            unfocusedTextColor = IntelPalette.Text,
            cursorColor = accent
        ),
        textStyle = MaterialTheme.typography.bodySmall.copy(fontSize = 11.5.sp),
        singleLine = true,
        shape = RoundedCornerShape(14.dp)
    )
}

@Composable
private fun SourceStatusBar(
    health: SourceHealth,
    accent: Color,
    onRefresh: () -> Unit
) {
    val statusColor = when (health.status) {
        SourceHealthStatus.HEALTHY -> IntelPalette.Green
        SourceHealthStatus.LOADING -> IntelPalette.Cyan
        SourceHealthStatus.PARTIAL -> IntelPalette.Amber
        SourceHealthStatus.ERROR -> IntelPalette.Red
        SourceHealthStatus.IDLE -> IntelPalette.Muted
    }
    val label = when (health.status) {
        SourceHealthStatus.LOADING -> "SOURCE SYNCING"
        SourceHealthStatus.HEALTHY -> "SOURCE READY"
        SourceHealthStatus.PARTIAL -> "SOURCE PARTIAL"
        SourceHealthStatus.ERROR -> "SOURCE ERROR"
        SourceHealthStatus.IDLE -> "SOURCE IDLE"
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = statusColor.copy(alpha = 0.05f),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(start = 11.dp, end = 5.dp, top = 7.dp, bottom = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(6.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    color = statusColor,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.9.sp
                )
                Text(
                    text = health.message,
                    color = IntelPalette.Muted,
                    fontSize = 9.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (health.itemCount > 0) {
                Text(
                    text = compactCount(health.itemCount),
                    color = statusColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
            if (health.status == SourceHealthStatus.LOADING) {
                Box(modifier = Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = IntelPalette.Cyan,
                        strokeWidth = 1.8.dp
                    )
                }
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh source",
                        tint = accent.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentSignalsRail(
    cameras: List<StreamSource>,
    cardColor: Color,
    onClick: (StreamSource) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "RECENT SIGNALS",
                color = IntelPalette.Muted,
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = cameras.size.toString().padStart(2, '0'),
                color = IntelPalette.Dim,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace
            )
        }
        Spacer(Modifier.height(6.dp))
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            items(cameras, key = { it.id }) { cam ->
                RecentCamItem(
                    camera = cam,
                    cardColor = cardColor,
                    onClick = { onClick(cam) }
                )
            }
        }
    }
}

@Composable
private fun SectionHeading(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .width(2.dp)
                .height(if (subtitle == null) 11.dp else 22.dp)
                .background(accent, RoundedCornerShape(1.dp))
        )
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = IntelPalette.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 1.1.sp
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    color = IntelPalette.Muted,
                    fontSize = 8.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Surface(
                color = accent.copy(alpha = 0.07f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
            ) {
                Text(
                    text = trailing,
                    color = accent,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun MetricTile(
    label: String,
    value: String,
    caption: String,
    color: Color,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
            Text(
                text = label,
                color = IntelPalette.Dim,
                fontSize = 7.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = value,
                color = color,
                fontSize = 18.sp,
                fontWeight = FontWeight.Black
            )
            Text(
                text = caption,
                color = IntelPalette.Muted,
                fontSize = 7.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun DiscoverySourceCard(
    eyebrow: String,
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color,
    primaryLabel: String,
    onPrimary: () -> Unit,
    cardColor: Color,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(accent.copy(alpha = 0.09f))
                        .border(
                            1.dp,
                            accent.copy(alpha = 0.2f),
                            RoundedCornerShape(11.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = eyebrow,
                        color = accent.copy(alpha = 0.8f),
                        fontSize = 7.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = title,
                        color = IntelPalette.Text,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IntelPalette.Dim,
                    modifier = Modifier.size(16.dp)
                )
            }

            Spacer(Modifier.height(9.dp))
            Text(
                text = description,
                color = IntelPalette.Muted,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Button(
                    onClick = onPrimary,
                    modifier = Modifier.weight(1f).height(34.dp),
                    shape = RoundedCornerShape(9.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent.copy(alpha = 0.13f),
                        contentColor = accent
                    ),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                    contentPadding = PaddingValues(horizontal = 6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = primaryLabel,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp
                    )
                }
                if (secondaryLabel != null && onSecondary != null) {
                    OutlinedButton(
                        onClick = onSecondary,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(9.dp),
                        border = BorderStroke(1.dp, IntelPalette.Border),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = IntelPalette.Muted
                        ),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = secondaryLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CountryRow(
    country: OsintViewModel.InsecamCountry,
    accent: Color,
    cardColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .clickable { onClick() },
        color = cardColor,
        shape = RoundedCornerShape(13.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Column(Modifier.padding(11.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.08f))
                        .border(
                            1.dp,
                            accent.copy(alpha = 0.18f),
                            RoundedCornerShape(8.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = country.code.uppercase(),
                        color = accent,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.4.sp
                    )
                }
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = IntelPalette.Dim,
                    modifier = Modifier.size(14.dp)
                )
            }
            Spacer(Modifier.height(9.dp))
            Text(
                text = country.name.uppercase(),
                color = IntelPalette.Text,
                fontSize = 10.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.3.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "${compactCount(country.count)} indexed feeds",
                color = IntelPalette.Muted,
                fontSize = 7.5.sp,
                maxLines = 1
            )
        }
    }
}

private fun LazyListScope.publicLandingSection(
    countriesCount: Int,
    filteredCountries: List<OsintViewModel.InsecamCountry>,
    query: String,
    accent: Color,
    cardColor: Color,
    onScan: (Int) -> Unit,
    onLoadGitHub: () -> Unit,
    onSelectCountry: (String) -> Unit
) {
    if (query.isBlank()) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                MetricTile(
                    label = "COUNTRIES",
                    value = countriesCount.toString(),
                    caption = "INDEXED",
                    color = accent,
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "SOURCES",
                    value = "3",
                    caption = "PUBLIC",
                    color = IntelPalette.Cyan,
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
                MetricTile(
                    label = "CHECKS",
                    value = "HTTP",
                    caption = "ON DEMAND",
                    color = IntelPalette.Magenta,
                    cardColor = cardColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Column {
                SectionHeading(
                    title = "DISCOVERY INDEXES",
                    subtitle = "Pull live listings from public camera directories",
                    accent = accent
                )
                Spacer(Modifier.height(8.dp))
                DiscoverySourceCard(
                    eyebrow = "LIVE PUBLIC DIRECTORY",
                    title = "OPENTOPIA",
                    description = "Crawl public camera listings with thumbnails, locations and per-feed verification.",
                    icon = Icons.Default.Public,
                    accent = accent,
                    primaryLabel = "SCAN 50",
                    onPrimary = { onScan(50) },
                    cardColor = cardColor,
                    secondaryLabel = "SCAN 100",
                    onSecondary = { onScan(100) }
                )
                Spacer(Modifier.height(8.dp))
                DiscoverySourceCard(
                    eyebrow = "CURATED FEED LIST",
                    title = "GITHUB MOTIONJPEG",
                    description = "Load a maintained list of direct MotionJPEG stream URLs, ready for probing and playback.",
                    icon = Icons.Default.Code,
                    accent = IntelPalette.Cyan,
                    primaryLabel = "LOAD FEEDS",
                    onPrimary = onLoadGitHub,
                    cardColor = cardColor
                )
            }
        }
    }

    item {
        SectionHeading(
            title = if (query.isBlank()) "COUNTRY DIRECTORY" else "SEARCH RESULTS",
            subtitle = if (query.isBlank()) {
                "Tap a region to load its public camera index"
            } else {
                "${filteredCountries.size} matching countries"
            },
            trailing = filteredCountries.size.toString(),
            accent = IntelPalette.Cyan
        )
    }

    if (filteredCountries.isEmpty()) {
        item {
            NoticeCard(
                title = "NO REGIONS FOUND",
                message = if (query.isBlank()) {
                    "The country directory is empty. Pull down to refresh."
                } else {
                    "No country matches \"$query\". Try a shorter search."
                },
                accent = IntelPalette.Amber,
                icon = Icons.Default.Search
            )
        }
    } else {
        items(filteredCountries.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { country ->
                    Box(modifier = Modifier.weight(1f)) {
                        CountryRow(
                            country = country,
                            accent = accent,
                            cardColor = cardColor,
                            onClick = { onSelectCountry(country.code) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

private fun LazyListScope.cameraResultsSection(
    heading: String,
    eyebrow: String,
    cameras: List<StreamSource>,
    totalCount: Int,
    loading: Boolean,
    error: String?,
    filter: String,
    onFilterChange: (String) -> Unit,
    sortAlphabetically: Boolean,
    onToggleSort: () -> Unit,
    accent: Color,
    cardColor: Color,
    backLabel: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onResetFilters: () -> Unit,
    onViewClick: (StreamSource) -> Unit,
    onSaveClick: (StreamSource) -> Unit,
    onVerify: (StreamSource) -> Unit,
    onDiagnostics: (StreamSource) -> Unit
) {
    item {
        ResultsHeader(
            eyebrow = eyebrow,
            title = heading,
            countLabel = if (cameras.size == totalCount) {
                "$totalCount FEEDS"
            } else {
                "${cameras.size} / $totalCount"
            },
            loading = loading,
            accent = accent,
            onBack = onBack,
            onRefresh = onRefresh
        )
    }

    if (totalCount > 0) {
        item {
            CameraFilterRow(
                filter = filter,
                onFilterChange = onFilterChange,
                sortAlphabetically = sortAlphabetically,
                onToggleSort = onToggleSort,
                accent = accent
            )
        }
    }

    if (loading && totalCount == 0) {
        item { LoadingCard(label = "SCANNING $heading…", accent = accent, cardColor = cardColor) }
    } else if (error != null && totalCount == 0) {
        item {
            NoticeCard(
                title = "SOURCE UNAVAILABLE",
                message = error,
                accent = IntelPalette.Red,
                actionLabel = "TRY AGAIN",
                onAction = onRefresh
            )
        }
    } else if (cameras.isEmpty()) {
        item {
            NoticeCard(
                title = "NO MATCHING SIGNALS",
                message = if (totalCount == 0) {
                    "This source returned no usable cameras. Refresh to retry."
                } else {
                    "No feeds match the active search or verification filter."
                },
                accent = IntelPalette.Amber,
                icon = Icons.Default.Videocam,
                actionLabel = if (totalCount > 0) "RESET FILTERS" else "REFRESH",
                onAction = if (totalCount > 0) onResetFilters else onRefresh
            )
        }
    } else {
        if (error != null) {
            item {
                NoticeCard(
                    title = "PARTIAL SOURCE",
                    message = error,
                    accent = IntelPalette.Amber,
                    actionLabel = "RETRY",
                    onAction = onRefresh
                )
            }
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
                            cardColor = cardColor,
                            onViewClick = { onViewClick(cam) },
                            onSaveClick = { onSaveClick(cam) },
                            onVerify = { onVerify(cam) },
                            onDiagnostics = { onDiagnostics(cam) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }

    if (loading && totalCount > 0) {
        item { LoadingFooter(accent = accent) }
    }
}

private fun LazyListScope.countryResultsSection(
    countryName: String,
    cameras: List<InsecamClient.PublicCamera>,
    totalCount: Int,
    loading: Boolean,
    error: String?,
    hasMore: Boolean,
    page: Int,
    accent: Color,
    cardColor: Color,
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
        ResultsHeader(
            eyebrow = "COUNTRY DIRECTORY · PAGE $page",
            title = countryName.uppercase(),
            countLabel = if (cameras.size == totalCount) {
                "$totalCount FEEDS"
            } else {
                "${cameras.size} / $totalCount"
            },
            loading = loading,
            accent = accent,
            onBack = onBack,
            onRefresh = onRetry
        )
    }

    if (loading && totalCount == 0) {
        item { LoadingCard(label = "LOADING REGIONAL INDEX…", accent = accent, cardColor = cardColor) }
    } else if (error != null && totalCount == 0) {
        item {
            NoticeCard(
                title = "REGION UNREACHABLE",
                message = error,
                accent = IntelPalette.Red,
                actionLabel = "RETRY",
                onAction = onRetry,
                secondaryLabel = "OPEN IN BROWSER",
                onSecondary = onManualBrowse
            )
        }
    } else if (cameras.isEmpty()) {
        item {
            NoticeCard(
                title = "NO CAMERAS FOUND",
                message = "No indexed cameras matched this region. The source may have rotated its listing.",
                accent = IntelPalette.Amber,
                icon = Icons.Default.Videocam,
                actionLabel = "OPEN IN BROWSER",
                onAction = onManualBrowse,
                secondaryLabel = "RETRY",
                onSecondary = onRetry
            )
        }
    } else {
        if (error != null) {
            item {
                NoticeCard(
                    title = "PARTIAL REGION",
                    message = error,
                    accent = IntelPalette.Amber,
                    actionLabel = "RETRY",
                    onAction = onRetry
                )
            }
        }

        items(cameras.chunked(2)) { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                row.forEach { cam ->
                    val streamSource = cam.toStreamSource()
                    Box(modifier = Modifier.weight(1f)) {
                        PublicCameraCard(
                            camera = streamSource,
                            cardColor = cardColor,
                            onViewClick = { onViewClick(cam) },
                            onSaveClick = { onSaveCamera(streamSource) },
                            onVerify = { onVerifyCamera(cam) },
                            onDiagnostics = { onDiagnostics(cam) }
                        )
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        if (hasMore && !loading) {
            item {
                OutlinedButton(
                    onClick = onLoadMore,
                    modifier = Modifier.fillMaxWidth().height(38.dp),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, accent.copy(alpha = 0.4f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = accent)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = "LOAD MORE CAMERAS",
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.7.sp
                    )
                }
            }
        }
    }

    if (loading && totalCount > 0) {
        item { LoadingFooter(accent = accent) }
    }
}

@Composable
private fun ResultsHeader(
    eyebrow: String,
    title: String,
    countLabel: String,
    loading: Boolean,
    accent: Color,
    onBack: () -> Unit,
    onRefresh: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = IntelPalette.Surface,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Row(
            modifier = Modifier.padding(start = 4.dp, end = 5.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Back",
                    tint = IntelPalette.Text,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = eyebrow,
                    color = accent.copy(alpha = 0.85f),
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = title,
                    color = IntelPalette.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                color = accent.copy(alpha = 0.07f),
                shape = RoundedCornerShape(50),
                border = BorderStroke(1.dp, accent.copy(alpha = 0.2f))
            ) {
                Text(
                    text = countLabel,
                    color = accent,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.4.sp,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 4.dp)
                )
            }
            Spacer(Modifier.width(4.dp))
            if (loading) {
                Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = accent,
                        strokeWidth = 1.8.dp
                    )
                }
            } else {
                IconButton(onClick = onRefresh, modifier = Modifier.size(30.dp)) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = accent.copy(alpha = 0.8f),
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraFilterRow(
    filter: String,
    onFilterChange: (String) -> Unit,
    sortAlphabetically: Boolean,
    onToggleSort: () -> Unit,
    accent: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            listOf("ALL", "VERIFIED", "MJPEG", "SNAPSHOT").forEach { label ->
                val selected = filter == label
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable { onFilterChange(label) },
                    color = if (selected) accent.copy(alpha = 0.1f) else IntelPalette.Card,
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(
                        1.dp,
                        if (selected) accent.copy(alpha = 0.35f) else IntelPalette.Border
                    )
                ) {
                    Text(
                        text = label,
                        color = if (selected) accent else IntelPalette.Muted,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp,
                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(IntelPalette.Card)
                .border(1.dp, IntelPalette.Border, RoundedCornerShape(8.dp))
                .clickable(onClick = onToggleSort),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.SwapVert,
                contentDescription = if (sortAlphabetically) "Sort by source order" else "Sort A to Z",
                tint = if (sortAlphabetically) accent else IntelPalette.Muted,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

@Composable
private fun NoticeCard(
    title: String,
    message: String,
    accent: Color,
    icon: ImageVector = Icons.Default.Warning,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = accent.copy(alpha = 0.045f),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .background(accent.copy(alpha = 0.1f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = message,
                        color = IntelPalette.Muted,
                        fontSize = 9.5.sp,
                        lineHeight = 13.sp
                    )
                }
            }

            if (actionLabel != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Button(
                        onClick = onAction,
                        modifier = Modifier.weight(1f).height(34.dp),
                        shape = RoundedCornerShape(9.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = accent.copy(alpha = 0.13f),
                            contentColor = accent
                        ),
                        border = BorderStroke(1.dp, accent.copy(alpha = 0.28f)),
                        contentPadding = PaddingValues(horizontal = 6.dp)
                    ) {
                        Text(
                            text = actionLabel,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                    if (secondaryLabel != null && onSecondary != null) {
                        OutlinedButton(
                            onClick = onSecondary,
                            modifier = Modifier.weight(1f).height(34.dp),
                            shape = RoundedCornerShape(9.dp),
                            border = BorderStroke(1.dp, IntelPalette.Border),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = IntelPalette.Muted
                            ),
                            contentPadding = PaddingValues(horizontal = 6.dp)
                        ) {
                            Text(
                                text = secondaryLabel,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingCard(label: String, accent: Color, cardColor: Color) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 26.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = accent,
                strokeWidth = 2.dp
            )
            Spacer(Modifier.height(9.dp))
            Text(
                text = label,
                color = IntelPalette.Muted,
                fontSize = 8.5.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun LoadingFooter(accent: Color) {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(18.dp),
            color = accent,
            strokeWidth = 1.8.dp
        )
    }
}

@Composable
fun PublicCameraCard(
    camera: StreamSource,
    onViewClick: () -> Unit,
    onSaveClick: () -> Unit,
    onVerify: () -> Unit = {},
    onDiagnostics: () -> Unit = {},
    cardColor: Color = IntelPalette.Card
) {
    val context = LocalContext.current
    var thumbnailBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val thumbUrl = camera.bestThumbnailUrl()
    val (statusLabel, statusColor) = cameraStatus(camera)
    val (verificationLabel, verificationColor) = verificationMeta(camera.verification)

    LaunchedEffect(camera.id, camera.url) {
        thumbnailBitmap = if (thumbUrl.isBlank()) ThumbnailResolver.resolve(camera) else null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(104.dp)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF101B24), Color(0xFF080D12))
                        )
                    )
            ) {
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
                        CameraPlaceholder()
                    }
                }

                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))
                            )
                        )
                )

                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(7.dp),
                    color = Color.Black.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(50),
                    border = BorderStroke(1.dp, statusColor.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(4.dp)
                                .background(statusColor, CircleShape)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = statusLabel,
                            color = statusColor,
                            fontSize = 6.5.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(26.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onDiagnostics),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Camera diagnostics",
                        tint = Color.White.copy(alpha = 0.8f),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }

            Column(Modifier.padding(9.dp)) {
                Text(
                    text = camera.title.ifBlank { "Unnamed camera" },
                    color = IntelPalette.Text,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = IntelPalette.Dim,
                        modifier = Modifier.size(9.dp)
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        text = camera.location.ifBlank { "Unknown location" },
                        color = IntelPalette.Muted,
                        fontSize = 8.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = camera.sourceLabel.ifBlank { "PUBLIC INDEX" }.uppercase(),
                        color = statusColor.copy(alpha = 0.8f),
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.3.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = verificationLabel,
                        color = verificationColor,
                        fontSize = 6.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.2.sp,
                        maxLines = 1
                    )
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onViewClick,
                        modifier = Modifier.weight(1f).height(30.dp),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = statusColor.copy(alpha = 0.12f),
                            contentColor = statusColor
                        ),
                        border = BorderStroke(1.dp, statusColor.copy(alpha = 0.25f)),
                        contentPadding = PaddingValues(horizontal = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "VIEW",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.4.sp
                        )
                    }
                    MiniActionButton(
                        icon = Icons.Default.BookmarkAdd,
                        description = "Save camera",
                        tint = IntelPalette.Cyan,
                        onClick = {
                            onSaveClick()
                            Toast.makeText(context, "Saved to archive", Toast.LENGTH_SHORT).show()
                        }
                    )
                    MiniActionButton(
                        icon = Icons.Default.CheckCircle,
                        description = "Verify feed",
                        tint = IntelPalette.Amber,
                        onClick = onVerify
                    )
                }
            }
        }
    }
}

@Composable
private fun CameraPlaceholder() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0D1921), Color(0xFF070B0F))
                )
            )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val step = 18.dp.toPx()
            var x = 0f
            while (x < size.width) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1f
                )
                x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(
                    color = Color.White.copy(alpha = 0.025f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f
                )
                y += step
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Videocam,
                contentDescription = null,
                tint = IntelPalette.Cyan.copy(alpha = 0.4f),
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = "NO PREVIEW",
                color = IntelPalette.Dim,
                fontSize = 6.5.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 0.8.sp
            )
        }
    }
}

@Composable
private fun MiniActionButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(IntelPalette.CardRaised)
            .border(1.dp, IntelPalette.Border, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier.size(14.dp)
        )
    }
}

@Composable
fun RecentCamItem(
    camera: StreamSource,
    cardColor: Color = IntelPalette.Card,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .size(width = 138.dp, height = 76.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() },
        color = cardColor,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, IntelPalette.Border)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val thumbUrl = camera.bestThumbnailUrl()
            if (thumbUrl.isNotBlank()) {
                AsyncImage(
                    model = thumbUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.72f
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF0D1921), Color(0xFF070B0F))
                            )
                        )
                ) {
                    Icon(
                        imageVector = Icons.Default.Videocam,
                        contentDescription = null,
                        tint = IntelPalette.Cyan.copy(alpha = 0.25f),
                        modifier = Modifier.align(Alignment.Center).size(20.dp)
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.82f))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 7.dp, vertical = 6.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .size(4.dp)
                            .background(IntelPalette.Cyan, CircleShape)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "RECENT",
                        color = IntelPalette.Cyan,
                        fontSize = 5.5.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.6.sp
                    )
                }
                Text(
                    text = camera.title.ifBlank { camera.location }.uppercase(),
                    color = Color.White,
                    fontSize = 7.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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
        containerColor = IntelPalette.Surface,
        shape = RoundedCornerShape(18.dp),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .background(IntelPalette.Cyan.copy(alpha = 0.09f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = IntelPalette.Cyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "CAMERA DIAGNOSTICS",
                        color = IntelPalette.Text,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                    Text(
                        text = camera.title,
                        color = IntelPalette.Muted,
                        fontSize = 9.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagnosticRow(
                    label = "SOURCE",
                    value = camera.sourceLabel.ifBlank { "Manual / unknown" }
                )
                DiagnosticRow(
                    label = "STATUS",
                    value = diagnostics?.verification?.label
                        ?: camera.verification.ifBlank { "Unchecked" }
                )
                DiagnosticRow(
                    label = "CONTENT TYPE",
                    value = diagnostics?.contentType?.ifBlank { "Not checked" }
                        ?: camera.contentType.ifBlank { "Not checked" }
                )
                DiagnosticRow(
                    label = "EFFECTIVE URL",
                    value = diagnostics?.effectiveUrl?.ifBlank { camera.bestPlaybackUrl() }
                        ?: camera.bestPlaybackUrl(),
                    monospace = true
                )
                Surface(
                    color = IntelPalette.Card,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, IntelPalette.Border)
                ) {
                    Text(
                        text = diagnostics?.message ?: "Select CHECK to verify this feed.",
                        color = IntelPalette.Muted,
                        fontSize = 10.sp,
                        lineHeight = 14.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "CLOSE",
                    color = IntelPalette.Cyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    )
}

@Composable
private fun DiagnosticRow(label: String, value: String, monospace: Boolean = false) {
    Column {
        Text(
            text = label,
            color = IntelPalette.Dim,
            fontSize = 7.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 0.8.sp
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            color = IntelPalette.Text,
            fontSize = if (monospace) 8.5.sp else 10.sp,
            fontFamily = if (monospace) FontFamily.Monospace else FontFamily.Default,
            maxLines = if (monospace) 3 else 2,
            overflow = TextOverflow.Ellipsis
        )
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
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(IntelPalette.Background)
            .navigationBarsPadding()
    ) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.0"
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?
                        ): Boolean {
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
            modifier = Modifier.fillMaxSize().padding(top = 80.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(IntelPalette.Surface.copy(alpha = 0.97f))
                .statusBarsPadding()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(IntelPalette.Cyan.copy(alpha = 0.09f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        tint = IntelPalette.Cyan,
                        modifier = Modifier.size(14.dp)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title.uppercase(),
                    color = IntelPalette.Text,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
            }
            TextButton(onClick = onClose) {
                Text(
                    text = "CLOSE",
                    color = IntelPalette.Cyan,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

private fun InsecamClient.PublicCamera.toStreamSource(): StreamSource {
    return StreamSource(
        id = id,
        url = "http://www.insecam.org/en/view/$id/",
        title = location ?: ip ?: "Camera",
        location = location ?: "Unknown",
        thumbnailUrl = imageUrl,
        protocol = "mjpeg",
        sourceLabel = "Country directory"
    )
}

private fun cameraStatus(camera: StreamSource): Pair<String, Color> {
    val verification = camera.verification.lowercase()
    return when {
        verification == "mjpeg" -> "MJPEG" to IntelPalette.Green
        verification == "snapshot" -> "SNAP" to IntelPalette.Cyan
        verification == "rtsp" -> "RTSP" to Color(0xFF4CA6FF)
        verification == "web" -> "WEB" to IntelPalette.Amber
        verification == "unavailable" -> "OFFLINE" to IntelPalette.Red
        verification == "verifying" -> "CHECKING" to IntelPalette.Amber
        camera.protocol.equals("rtsp", ignoreCase = true) -> "RTSP" to Color(0xFF4CA6FF)
        camera.url.contains("rtsp", ignoreCase = true) -> "RTSP" to Color(0xFF4CA6FF)
        camera.protocol.equals("mjpeg", ignoreCase = true) -> "MJPEG" to IntelPalette.Cyan
        camera.url.contains("mjpg", ignoreCase = true) ||
            camera.url.contains("mjpeg", ignoreCase = true) -> "MJPEG" to IntelPalette.Cyan
        else -> "HTTP" to IntelPalette.Muted
    }
}

private fun verificationMeta(verification: String): Pair<String, Color> {
    return when (verification.lowercase()) {
        "mjpeg" -> "MJPEG VERIFIED" to IntelPalette.Green
        "snapshot" -> "SNAPSHOT" to IntelPalette.Cyan
        "rtsp" -> "RTSP" to Color(0xFF4CA6FF)
        "web" -> "WEB PAGE" to IntelPalette.Amber
        "unavailable" -> "UNAVAILABLE" to IntelPalette.Red
        "verifying" -> "CHECKING" to IntelPalette.Amber
        "unchecked", "" -> "UNVERIFIED" to IntelPalette.Dim
        else -> verification.uppercase() to IntelPalette.Muted
    }
}

private fun compactCount(count: Int): String {
    return if (count >= 1000) {
        val whole = count / 1000
        val decimal = (count % 1000) / 100
        if (decimal == 0) "${whole}K" else "$whole.${decimal}K"
    } else {
        count.toString()
    }
}
