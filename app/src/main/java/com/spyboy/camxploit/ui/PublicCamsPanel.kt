package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.spyboy.camxploit.StreamActivity
import com.spyboy.camxploit.osint.InsecamClient
import com.spyboy.camxploit.osint.InsecamScraper
import com.spyboy.camxploit.osint.OsintViewModel

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PublicCamsPanel(
    vm: OsintViewModel,
    neonGreen: Color = Color(0xFF39FF14),
    darkCard: Color = Color(0xFF1A1A1A)
) {
    val context = LocalContext.current
    val countries by vm.countries.collectAsStateWithLifecycle()
    val cameras by vm.cameras.collectAsStateWithLifecycle()
    val selectedCountry by vm.selectedCountry.collectAsStateWithLifecycle()
    
    // Scraper state
    val scraper = remember { InsecamScraper() }
    val scraperError by scraper.error.collectAsStateWithLifecycle()
    val scraperLoading by scraper.isLoading.collectAsStateWithLifecycle()
    val hasMore by scraper.hasMorePages.collectAsStateWithLifecycle()

    var showManualBrowser by remember { mutableStateOf(false) }

    // Sync scraper state to ViewModel so outer UI can see it if needed
    LaunchedEffect(scraper) {
        scraper.cameras.collect { vm.setInsecamCameras(it) }
    }
    LaunchedEffect(scraper) {
        scraper.isLoading.collect { vm.setInsecamLoading(it) }
    }

    // Hidden scraper WebView
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    Box(modifier = Modifier.size(1.dp)) {
        AndroidView(factory = { ctx ->
            WebView(ctx).also { wv ->
                scraper.attachWebView(wv)
                webViewRef = wv
            }
        })
    }

    DisposableEffect(Unit) {
        onDispose {
            scraper.detach()
            webViewRef?.destroy()
        }
    }

    LaunchedEffect(Unit) {
        if (countries.isEmpty()) vm.loadCountries()
    }

    if (selectedCountry != null) {
        CountryCameraView(
            cameras = cameras,
            loading = scraperLoading,
            hasMore = hasMore,
            error = scraperError,
            scraper = scraper,
            darkCard = darkCard,
            neonGreen = neonGreen,
            onBack = { vm.clearCountrySelection() },
            onManualBrowse = { showManualBrowser = true }
        )
    } else {
        CountryGridView(
            countries = countries,
            darkCard = darkCard,
            neonGreen = neonGreen,
            onSelect = { code ->
                vm.selectCountry(code)
                scraper.loadCountry(code)
            }
        )
    }

    // Manual fallback browser overlay
    if (showManualBrowser && selectedCountry != null) {
        ManualBrowserOverlay(
            url = "http://www.insecam.org/en/bycountry/$selectedCountry/",
            title = "Insecam",
            onClose = { showManualBrowser = false },
            onCameraTap = { url, title ->
                StreamActivity.launch(context, url, title)
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

@OptIn(ExperimentalMaterialApi::class)
@Composable
private fun CountryCameraView(
    cameras: List<InsecamClient.PublicCamera>,
    loading: Boolean,
    hasMore: Boolean,
    error: String?,
    scraper: InsecamScraper,
    darkCard: Color,
    neonGreen: Color,
    onBack: () -> Unit,
    onManualBrowse: () -> Unit
) {
    val context = LocalContext.current
    val pullRefreshState = rememberPullRefreshState(
        refreshing = loading,
        onRefresh = { scraper.retry() }
    )

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { onBack() }
        ) {
            Icon(Icons.Default.KeyboardArrowLeft, "Back", tint = Color.White)
            Text("BACK", color = Color.White, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { scraper.retry() }) {
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

        // Error card with fallback option
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
                    TextButton(onClick = { scraper.retry() }, modifier = Modifier.fillMaxWidth()) {
                        Text("RETRY SCRAPE", color = neonGreen)
                    }
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        val gridState = rememberLazyGridState()

        // Detect scroll to end for pagination
        val shouldLoadMore = remember {
            derivedStateOf {
                val totalItemsCount = gridState.layoutInfo.totalItemsCount
                val lastVisibleItemIndex = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                totalItemsCount > 0 && lastVisibleItemIndex >= totalItemsCount - 2
            }
        }

        LaunchedEffect(shouldLoadMore.value, hasMore) {
            if (shouldLoadMore.value && !loading && hasMore) {
                scraper.loadNextPage()
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
                    CameraCard(
                        cam = cam,
                        darkCard = darkCard,
                        onViewStream = {
                            val viewerUrl = "http://www.insecam.org/en/view/${cam.id}/"
                            // Using StreamActivity.launch to maintain consistency
                            StreamActivity.launch(context, viewerUrl, cam.location ?: cam.ip ?: "Camera")
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun ManualBrowserOverlay(
    url: String,
    title: String,
    onClose: () -> Unit,
    onCameraTap: (String, String) -> Unit
) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.0"
                    webViewClient = object : WebViewClient() {
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
            modifier = Modifier.fillMaxWidth().background(Color(0xCC000000)).padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title.uppercase(), color = Color(0xFF39FF14), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onClose) { Text("CLOSE", color = Color.White) }
        }
    }
}
