package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.webkit.WebView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.SubcomposeAsyncImage
import com.spyboy.camxploit.StreamActivity
import com.spyboy.camxploit.osint.InsecamScraper
import com.spyboy.camxploit.osint.OsintViewModel

@androidx.media3.common.util.UnstableApi
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PublicCamsPanel(vm: OsintViewModel, neonGreen: Color, darkCard: Color) {
    val context = LocalContext.current
    val countries by vm.countries.collectAsStateWithLifecycle()
    val cameras by vm.insecamCameras.collectAsStateWithLifecycle()
    val loading by vm.insecamLoading.collectAsStateWithLifecycle()
    var selectedCountry by remember { mutableStateOf<String?>(null) }

    // Create scraper with ACTIVITY context (not Application context)
    val scraper = remember { InsecamScraper(context) }

    // Collect scraper results and push to ViewModel
    LaunchedEffect(scraper) {
        scraper.cameras.collect { vm.setInsecamCameras(it) }
    }
    LaunchedEffect(scraper) {
        scraper.isLoading.collect { vm.setInsecamLoading(it) }
    }

    // Hidden WebView that does the scraping — MUST be in UI layer for proper context
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    // Invisible WebView for scraping - matched to parent but transparent
    // Some WebView versions stop execution if size is 0 or 1.dp
    Box(modifier = Modifier.size(2.dp).background(Color.Transparent)) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).also { wv ->
                    wv.alpha = 0f // Invisible
                    scraper.attachWebView(wv)
                    webViewRef = wv
                }
            }
        )
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
        // ── CAMERA GRID ──
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { selectedCountry = null }
                    .padding(vertical = 4.dp)
            ) {
                Icon(Icons.Default.KeyboardArrowLeft, "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("BACK TO COUNTRIES", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            
            if (loading && cameras.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = neonGreen, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("SCRAPING LIVE FEEDS...", color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            } else {
                Text(
                    if (loading) "REFRESHING: ${cameras.size} CAMERAS" else "${cameras.size} CAMERAS DETECTED",
                    color = neonGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(cameras, key = { it.id }) { cam ->
                        CameraThumbnailCard(cam, darkCard) {
                            val viewerUrl = "http://www.insecam.org/en/view/${cam.id}/"
                            context.startActivity(
                                Intent(context, StreamActivity::class.java).apply {
                                    putExtra(StreamActivity.EXTRA_MODE, "webview")
                                    putExtra(StreamActivity.EXTRA_URL, viewerUrl)
                                    putExtra(StreamActivity.EXTRA_TITLE, cam.location)
                                }
                            )
                        }
                    }
                }
            }
        }
    } else {
        // ── COUNTRY LIST ──
        if (countries.isEmpty()) {
            Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = neonGreen)
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    Text(
                        "${countries.size} COUNTRIES",
                        color = neonGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(10.dp))
                }
                items(countries.sortedByDescending { it.count }) { country ->
                    CountryRow(country, darkCard) {
                        selectedCountry = country.code
                        scraper.loadCountry(country.code)
                    }
                    Spacer(Modifier.height(6.dp))
                }
                item {
                    Spacer(Modifier.height(100.dp))
                }
            }
        }
    }
}

@Composable
private fun CameraThumbnailCard(
    cam: InsecamScraper.Camera,
    darkCard: Color,
    onClick: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = darkCard),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.3f)
            .clickable { onClick() }
    ) {
        Column {
            SubcomposeAsyncImage(
                model = cam.imageUrl,
                contentDescription = cam.location,
                contentScale = ContentScale.Crop,
                loading = {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFF39FF14), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    }
                },
                error = {
                    Box(modifier = Modifier.fillMaxSize().background(Color(0xFF111111)), contentAlignment = Alignment.Center) {
                        Text("NO SIGNAL", color = Color.Gray, fontSize = 10.sp)
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Text(
                cam.location,
                color = Color.White,
                fontSize = 11.sp,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
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
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = country.name.uppercase(),
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "CODE: ${country.code.uppercase()}",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
            Surface(
                color = Color(0xFF252525),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "${country.count}",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    }
}
