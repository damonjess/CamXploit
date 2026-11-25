package com.spyboy.camxploit.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.widget.Toast
import android.webkit.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.spyboy.camxploit.FeedLibrary
import com.spyboy.camxploit.StreamCandidate
import com.spyboy.camxploit.StreamPreflight
import com.spyboy.camxploit.StreamSource
import com.spyboy.camxploit.osint.SkylineResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONTokener
import java.net.URLEncoder

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun InsecamBrowserScreen(onClose: () -> Unit, onStreamUrl: (StreamSource) -> Unit) {
    val neonGreen = Color(0xFF39FF14)
    val orange = Color(0xFFFFA500)
    val neonRed = Color(0xFFFF5252)
    val darkSurface = Color(0xFF141414)
    val darkCard = Color(0xFF1E1E1E)
    
    var currentUrl by remember { mutableStateOf("http://www.insecam.org/en/") }
    var inputUrl by remember { mutableStateOf("http://www.insecam.org/en/") }
    var isLoading by remember { mutableStateOf(true) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var detectedStreamUrl by remember { mutableStateOf<String?>(null) }
    var detectedCandidates by remember { mutableStateOf<List<String>>(emptyList()) }
    var detectedTitle by remember { mutableStateOf("Live Feed") }
    var candidateTitles by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var candidatePageUrls by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var candidateThumbnails by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isSniffing by remember { mutableStateOf(false) }
    var checkedOnce by remember { mutableStateOf(false) }
    var isChecking by remember { mutableStateOf(false) }
    var candidateChecks by remember { mutableStateOf<Map<String, CandidateCheck>>(emptyMap()) }
    var checkJob by remember { mutableStateOf<Job?>(null) }
    var checkToken by remember { mutableStateOf(0) }
    var showFeedPicker by remember { mutableStateOf(false) }
    // Network-level capture catches manifests and MJPEG endpoints the DOM/JS
    // sniffer cannot see, including cross-origin player requests.
    val interceptedMedia = remember {
        java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }

    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val density = LocalDensity.current
    val navigationInset = WindowInsets.navigationBars.getBottom(density)
    val bottomSafePadding = with(density) {
        maxOf(navigationInset, 44.dp.roundToPx()).toDp()
    }

    // Universal JS Sniffer script
    val snifferScript = """
        (function() {
            var candidates = [];
            
            // 1. Direct camera IDs/elements
            var img0 = document.querySelector('#image0, #main-image, #camera-image, img.camera-image, img.webcam');
            if (img0 && img0.src) candidates.push(img0.src);
            
            // 2. Scan image elements for camera stream keywords or direct IPs
            var imgs = document.getElementsByTagName('img');
            for (var i = 0; i < imgs.length; i++) {
                var src = imgs[i].src || imgs[i].getAttribute('data-src') || imgs[i].getAttribute('data-original') || '';
                if (!src) continue;
                var lower = src.toLowerCase();
                if (lower.includes('mjpg') || lower.includes('mjpeg') || lower.includes('cgi-bin') || 
                    lower.includes('stream') || lower.includes('video') || lower.includes('snapshot') ||
                    lower.includes('axis-cgi') || lower.includes('live') || lower.includes('current')) {
                    candidates.push(src);
                } else if (src.match(/^https?:\/\/\d+\.\d+\.\d+\.\d+/)) {
                    candidates.push(src);
                }
            }

            // 3. Check embedded frames/video tags
            var iframes = document.querySelectorAll('iframe, video, embed');
            for (var j = 0; j < iframes.length; j++) {
                var vsrc = iframes[j].src || '';
                if (vsrc && !vsrc.includes('google') && !vsrc.includes('facebook') && !vsrc.includes('youtube')) {
                    candidates.push(vsrc);
                }
            }

            // 4. Player configs often keep the real manifest in JSON/script text
            var pageHtml = '';
            try { pageHtml = document.documentElement.innerHTML || ''; } catch (e) {}
            pageHtml = pageHtml.replace(/\\\//g, '/').replace(/&amp;/g, '&');
            var embedded = pageHtml.match(/https?:\/\/[^\s"'<>]+?\.(?:m3u8|mpd)(?:\?[^\s"'<>]*)?/gi) || [];
            for (var k = 0; k < embedded.length; k++) candidates.push(embedded[k]);

            // SkylineWebcams keeps a relative Clappr source and its player moves
            // it to the hd-auth host (livee.m3u8 -> live.m3u8).
            var skylineSource = pageHtml.match(/["']livee\.m3u8(\?[^"']*)?["']/i);
            if (skylineSource) {
                candidates.push('https://hd-auth.skylinewebcams.com/live.m3u8' + (skylineSource[1] || ''));
            }

            // Other players may use relative manifest paths as well.
            var relativeMedia = pageHtml.match(/["']((?:\.\/|\/)?[^"'\s<>]+?\.(?:m3u8|mpd)(?:\?[^"'\s<>]*)?)["']/gi) || [];
            for (var m = 0; m < relativeMedia.length; m++) {
                var relativeUrl = relativeMedia[m].slice(1, -1);
                if (/^https?:\/\//i.test(relativeUrl) || relativeUrl.toLowerCase().indexOf('livee.m3u8') >= 0) continue;
                try { candidates.push(new URL(relativeUrl, location.href).href); } catch (e) {}
            }

            // 5. Media manifests already fetched by the page player
            try {
                var resources = performance.getEntriesByType('resource');
                for (var r = 0; r < resources.length; r++) {
                    var name = (resources[r].name || '').toLowerCase();
                    if (name.indexOf('.m3u8') >= 0 || name.indexOf('.mpd') >= 0) {
                        candidates.push(resources[r].name);
                    }
                }
            } catch (e) {}

            // 6. Filter out common web assets and duplicate URLs
            var seen = {};
            var valid = candidates.filter(function(url) {
                if (!url.match(/^https?:\/\//)) return false;
                var l = url.toLowerCase();
                if (l.includes('logo') || l.includes('banner') || l.includes('icon') ||
                    l.includes('favicon') || l.includes('avatar') || l.includes('button') ||
                    l.endsWith('.gif') || l.endsWith('.svg')) return false;
                // Keep real WebP snapshot endpoints, but not directory card art.
                if (l.endsWith('.webp') && !(l.includes('snapshot') || l.includes('current') ||
                    l.includes('still') || l.includes('snap.'))) return false;
                if (seen[url]) return false;
                seen[url] = true;
                return true;
            });

            return JSON.stringify(valid);
        })();
    """.trimIndent()

    // SkylineWebcams directory pages only carry .webp preview images; their live
    // manifests sit on the individual camera pages. Collect those camera pages so
    // SNIFF can resolve the real HLS feeds instead of giving up.
    val skylineCamsScript = """
        (function() {
            var out = [];
            var seen = {};
            if (location.hostname.indexOf('skylinewebcams.com') < 0) return JSON.stringify(out);
            var links = document.querySelectorAll('a[href]');
            for (var i = 0; i < links.length && out.length < 20; i++) {
                var a = links[i];
                var href = a.href || '';
                if (!/^https?:\/\/(?:www\.)?skylinewebcams\.com\/[a-z]{2}\/webcam\/(?:[^\/?#]+\/){3,}[^\/?#]+\.html/i.test(href)) continue;
                if (seen[href]) continue;
                seen[href] = true;
                var img = a.querySelector('img');
                var name = (img && img.alt ? img.alt : (a.textContent || '')).replace(/\s+/g, ' ').trim();
                out.push({ u: href, n: name.slice(0, 80) });
            }
            return JSON.stringify(out);
        })();
    """.trimIndent()

    fun playlistFor(chosen: StreamPreflight.Result): List<StreamCandidate> {
        val playable = candidateChecks.values
            .mapNotNull { (it as? CandidateCheck.Done)?.result }
            .filter { it.isPlayable }
            .sortedBy { it.candidateIndex }
        val ordered = buildList {
            add(chosen)
            playable.filter { it.protocol == "mjpeg" && it.url != chosen.url }.forEach { add(it) }
            playable.filter { it.protocol == "hls" && it.url != chosen.url }.forEach { add(it) }
            playable.filter { it.protocol == "snapshot" && it.url != chosen.url }.forEach { add(it) }
        }
        return ordered.map {
            StreamCandidate(
                url = it.url,
                protocol = it.protocol,
                contentType = it.contentType,
                verification = it.label,
                title = candidateTitles[it.candidateUrl]?.ifBlank { null }
                    ?: candidateTitles[it.url]?.ifBlank { null }
                    ?: detectedTitle,
                pageUrl = candidatePageUrls[it.url] ?: candidatePageUrls[it.candidateUrl].orEmpty()
            )
        }
    }

    fun sourceFor(result: StreamPreflight.Result): StreamSource = StreamSource(
        url = result.url,
        title = candidateTitles[result.candidateUrl]?.ifBlank { null }
            ?: candidateTitles[result.url]?.ifBlank { null }
            ?: detectedTitle,
        protocol = result.protocol,
        thumbnailUrl = candidateThumbnails[result.url] ?: candidateThumbnails[result.candidateUrl],
        pageUrl = candidatePageUrls[result.url]
            ?: candidatePageUrls[result.candidateUrl]
            ?: currentUrl,
        streamUrl = result.url,
        sourceLabel = "OSINT browser",
        verification = result.label,
        contentType = result.contentType,
        verifiedAt = System.currentTimeMillis(),
        candidateUrls = detectedCandidates,
        streamCandidates = playlistFor(result)
    )

    fun saveFeed(result: StreamPreflight.Result) {
        val source = sourceFor(result)
        scope.launch {
            runCatching { FeedLibrary.save(context, source, result.thumbnail) }
                .onSuccess {
                    Toast.makeText(context, "Saved to feed library", Toast.LENGTH_SHORT).show()
                }
                .onFailure {
                    Toast.makeText(context, "Could not save feed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    fun launchVerified(result: StreamPreflight.Result) {
        showFeedPicker = false
        onStreamUrl(sourceFor(result))
    }

    fun resetChecks() {
        checkToken += 1
        checkJob?.cancel()
        checkJob = null
        isChecking = false
        checkedOnce = false
        candidateChecks = emptyMap()
    }

    fun checkCandidates(urls: List<String>, restart: Boolean = false) {
        val normalized = urls.map { it.trim() }
            .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
            .distinct()
        if (normalized.isEmpty()) return
        if (isChecking && !restart) return

        checkJob?.cancel()
        checkToken += 1
        val token = checkToken
        candidateChecks = normalized.associateWith { CandidateCheck.Checking }
        checkedOnce = true
        isChecking = true

        checkJob = scope.launch {
            try {
                StreamPreflight.verifyAll(normalized) { result ->
                    if (token == checkToken) {
                        val key = result.candidateUrl.ifBlank { result.url }
                        candidateChecks = candidateChecks + (key to CandidateCheck.Done(result))
                    }
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (token == checkToken) {
                    Toast.makeText(
                        context,
                        e.message ?: "Could not check the stream candidates",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } finally {
                if (token == checkToken) {
                    isChecking = false
                }
            }
        }
    }

    fun publishCandidates(candidates: List<String>, view: WebView?, manual: Boolean) {
        detectedCandidates = candidates
        detectedStreamUrl = candidates.first()
        candidateTitles = emptyMap()
        candidatePageUrls = emptyMap()
        candidateThumbnails = emptyMap()
        detectedTitle = when {
            currentUrl.contains("insecam.org/en/view/") -> {
                val id = currentUrl.substringAfter("/en/view/").takeWhile { it.isDigit() }
                "Insecam Camera #$id"
            }
            currentUrl.contains("opentopia.com") -> "Opentopia Stream"
            else -> view?.title?.ifBlank { "Detected Feed" } ?: "Detected Feed"
        }
        if (manual) {
            resetChecks()
            Toast.makeText(context, "Found ${candidates.size} stream candidate(s)", Toast.LENGTH_SHORT).show()
            showFeedPicker = true
            checkCandidates(candidates)
        }
    }

    fun resolveSkylineFeeds(view: WebView?, fallback: List<String>) {
        if (view == null) return
        isSniffing = true
        val pageAtStart = currentUrl
        Toast.makeText(context, "Skyline directory - resolving the live cameras\u2026", Toast.LENGTH_SHORT).show()
        view.evaluateJavascript(skylineCamsScript) { rawResult ->
            val payload = rawResult?.let {
                runCatching { JSONTokener(it).nextValue() as? String }.getOrNull()
            } ?: ""
            val cams = runCatching {
                val array = JSONArray(payload)
                (0 until array.length()).mapNotNull { index ->
                    val obj = array.optJSONObject(index) ?: return@mapNotNull null
                    val page = obj.optString("u").trim()
                    if (!page.startsWith("http://", true) && !page.startsWith("https://", true)) null
                    else SkylineResolver.CamLink(page, obj.optString("n").trim())
                }
            }.getOrDefault(emptyList()).distinctBy { it.pageUrl }

            scope.launch {
                // The WebView only holds the cards currently rendered, so read the
                // category page as HTML too and merge in every camera it lists.
                val allCams = withContext(Dispatchers.IO) {
                    val fromPage = SkylineResolver.listingCams(pageAtStart)
                    (cams + fromPage).distinctBy { it.pageUrl }.take(SkylineResolver.MAX_CAMS)
                }

                if (allCams.isEmpty()) {
                    isSniffing = false
                    if (pageAtStart != currentUrl) return@launch
                    if (fallback.isNotEmpty()) {
                        publishCandidates(fallback, view, manual = true)
                    } else {
                        resetChecks()
                        showFeedPicker = false
                        Toast.makeText(
                            context,
                            "No Skyline camera links found on this page",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    return@launch
                }

                val feeds = withContext(Dispatchers.IO) {
                    coroutineScope {
                        // Skyline rate-limits bursts of camera-page requests (HTTP 429),
                        // so fetch a couple at a time and leave a small gap between them.
                        val gate = Semaphore(2)
                        allCams.map { cam ->
                            async {
                                gate.withPermit {
                                    delay(350)
                                    SkylineResolver.feedFor(cam)
                                }
                            }
                        }.awaitAll().filterNotNull()
                    }
                }.distinctBy { it.url }

                isSniffing = false
                if (pageAtStart != currentUrl) return@launch

                if (feeds.isNotEmpty()) {
                    candidateTitles = feeds.associate { it.url to it.title }
                    candidatePageUrls = feeds.associate { it.url to it.pageUrl }
                    candidateThumbnails = feeds.mapNotNull { feed ->
                        feed.thumbnailUrl.takeIf { it.isNotBlank() }?.let { feed.url to it }
                    }.toMap()
                    detectedCandidates = feeds.map { it.url }
                    detectedStreamUrl = feeds.first().url
                    detectedTitle = feeds.first().title.ifBlank { "Skyline camera" }
                    resetChecks()
                    Toast.makeText(context, "Found ${feeds.size} live Skyline feed(s)", Toast.LENGTH_SHORT).show()
                    showFeedPicker = true
                    checkCandidates(detectedCandidates)
                } else if (fallback.isNotEmpty()) {
                    publishCandidates(fallback, view, manual = true)
                } else {
                    detectedCandidates = emptyList()
                    detectedStreamUrl = null
                    resetChecks()
                    showFeedPicker = false
                    Toast.makeText(
                        context,
                        "No live feed found for the Skyline cameras on this page",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    fun runSniffer(view: WebView?, manual: Boolean = false) {
        if (view == null) {
            if (manual) Toast.makeText(context, "Page is not ready to scan", Toast.LENGTH_SHORT).show()
            return
        }
        if (manual) {
            isSniffing = true
            Toast.makeText(context, "Scanning this page for stream URLs…", Toast.LENGTH_SHORT).show()
        }
        view.evaluateJavascript(snifferScript) { rawResult ->
            isSniffing = false
            val payload = rawResult?.let {
                runCatching { JSONTokener(it).nextValue() as? String }.getOrNull()
            } ?: ""
            val jsCandidates = runCatching {
                val array = JSONArray(payload)
                (0 until array.length()).mapNotNull { index ->
                    array.optString(index).takeIf {
                        it.startsWith("http://", true) || it.startsWith("https://", true)
                    }
                }
            }.getOrDefault(emptyList())
            // Merge DOM/JS results with media requests seen by the WebView client.
            val networkCandidates = synchronized(interceptedMedia) { interceptedMedia.toList() }
            val candidates = (jsCandidates + networkCandidates)
                .filter { it.startsWith("http://", true) || it.startsWith("https://", true) }
                .distinct()

            val isSkylinePage = currentUrl.contains("skylinewebcams.com", true)
            val hasSkylineManifest = candidates.any { it.contains("hd-auth.skylinewebcams.com", true) }
            when {
                manual && isSkylinePage && !hasSkylineManifest ->
                    resolveSkylineFeeds(view, candidates)
                candidates.isNotEmpty() ->
                    publishCandidates(candidates, view, manual)
                else -> {
                    detectedCandidates = emptyList()
                    detectedStreamUrl = null
                    candidateTitles = emptyMap()
                    candidatePageUrls = emptyMap()
                    candidateThumbnails = emptyMap()
                    resetChecks()
                    showFeedPicker = false
                    if (manual) {
                        Toast.makeText(context, "No stream candidate found on this page", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }

    fun loadFormattedUrl(raw: String) {
        val trimmed = raw.trim()
        val target = when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.contains(".") && !trimmed.contains(" ") -> "http://$trimmed"
            else -> "https://www.google.com/search?q=${URLEncoder.encode(trimmed, "UTF-8")}"
        }
        currentUrl = target
        inputUrl = target
        webView?.loadUrl(target)
        focusManager.clearFocus()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
            .padding(bottom = bottomSafePadding)
    ) {
        // 1. TOP CONTROL BAR (Title & Preset Directory Chips)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(darkSurface)
                .padding(horizontal = 10.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Videocam, null, tint = neonGreen, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "OSINT RECON BROWSER",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(Icons.Default.Close, "Close", tint = Color.Gray, modifier = Modifier.size(18.dp))
                }
            }

            Spacer(Modifier.height(4.dp))

            // Preset directory quick-chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val presets = listOf(
                    "Insecam" to "http://www.insecam.org/en/",
                    "Opentopia" to "https://www.opentopia.com/",
                    "WorldCam" to "https://worldcam.eu/",
                    "Skyline" to "https://www.skylinewebcams.com/en.html",
                    "EarthCam" to "https://www.earthcam.com/",
                    "Shodan Dork" to "https://www.google.com/search?q=inurl:%22view/viewer_index.shtml%22"
                )

                presets.forEach { (label, url) ->
                    AssistChip(
                        onClick = { loadFormattedUrl(url) },
                        label = { Text(label, fontSize = 9.sp, color = Color.White) },
                        colors = AssistChipDefaults.assistChipColors(containerColor = darkCard),
                        border = BorderStroke(1.dp, if (currentUrl.contains(url.take(15))) neonGreen else Color.DarkGray),
                        modifier = Modifier.height(26.dp)
                    )
                }
            }
        }

        // 2. NAVIGATION & URL ADDRESS BAR
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(darkSurface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { webView?.goBack() },
                enabled = canGoBack,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack, "Back",
                    tint = if (canGoBack) Color.White else Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { webView?.goForward() },
                enabled = canGoForward,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward, "Forward",
                    tint = if (canGoForward) Color.White else Color.DarkGray,
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = { webView?.reload() },
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Refresh, "Reload", tint = neonGreen, modifier = Modifier.size(16.dp))
            }

            Spacer(Modifier.width(4.dp))

            // URL Address Input Box
            BasicTextField(
                value = inputUrl,
                onValueChange = { inputUrl = it },
                modifier = Modifier
                    .weight(1f)
                    .height(36.dp)
                    .background(Color.Black, shape = RoundedCornerShape(4.dp))
                    .border(BorderStroke(1.dp, Color.DarkGray), shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp),
                singleLine = true,
                cursorBrush = SolidColor(neonGreen),
                textStyle = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 11.sp,
                    color = Color.White
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { loadFormattedUrl(inputUrl) }),
                decorationBox = { innerTextField ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Box(modifier = Modifier.weight(1f)) {
                            if (inputUrl.isEmpty()) {
                                Text("Enter URL or search...", fontSize = 11.sp, color = Color.Gray)
                            }
                            innerTextField()
                        }
                        IconButton(
                            onClick = { loadFormattedUrl(inputUrl) },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Search, "Go", tint = neonGreen, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            )

            Spacer(Modifier.width(4.dp))

            // Manual Sniff Button
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = neonGreen.copy(alpha = 0.11f),
                border = BorderStroke(1.dp, neonGreen.copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier
                        .height(32.dp)
                        .clickable(enabled = !isSniffing) { runSniffer(webView, manual = true) }
                        .padding(horizontal = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSniffing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            color = neonGreen,
                            strokeWidth = 1.5.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Videocam,
                            contentDescription = "Scan page for stream URLs",
                            tint = neonGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "SNIFF",
                        color = neonGreen,
                        fontSize = 7.5.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        // 3. WEBVIEW DISPLAY
        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                        settings.loadWithOverviewMode = true
                        settings.useWideViewPort = true
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                isLoading = true
                                synchronized(interceptedMedia) { interceptedMedia.clear() }
                                detectedCandidates = emptyList()
                                detectedStreamUrl = null
                                candidateTitles = emptyMap()
                                candidatePageUrls = emptyMap()
                                candidateThumbnails = emptyMap()
                                resetChecks()
                                showFeedPicker = false
                            }

                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                request?.url?.toString()?.let { url ->
                                    if (isNetworkMediaUrl(url)) interceptedMedia.add(url)
                                }
                                return null
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                url?.let {
                                    currentUrl = it
                                    inputUrl = it
                                }
                                canGoBack = view?.canGoBack() == true
                                canGoForward = view?.canGoForward() == true
                                
                                // Run universal stream sniffer
                                runSniffer(view)
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                return false
                            }
                        }

                        webChromeClient = WebChromeClient()
                        loadUrl(currentUrl)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                LinearProgressIndicator(
                    color = neonGreen,
                    trackColor = Color.Transparent,
                    modifier = Modifier.fillMaxWidth().align(Alignment.TopCenter)
                )
            }
        }

        // 4. FLOATING STREAM DETECTED ACTION BANNER
        detectedStreamUrl?.let { streamUrl ->
            val results = candidateChecks.values
                .mapNotNull { (it as? CandidateCheck.Done)?.result }
            val best = StreamPreflight.best(results)
            val playableCount = results.count { it.isPlayable }
            val bannerColor = when {
                isChecking -> orange
                checkedOnce && best == null -> neonRed
                best?.protocol == "snapshot" -> orange
                else -> neonGreen
            }
            val bannerTitle = when {
                isChecking -> "CHECKING FEEDS (${results.size}/${detectedCandidates.size})"
                checkedOnce && best == null -> "NO PLAYABLE FEED"
                best != null -> "$playableCount PLAYABLE FEED${if (playableCount == 1) "" else "S"}"
                detectedCandidates.size > 1 -> "STREAM CANDIDATES (${detectedCandidates.size})"
                else -> "STREAM CANDIDATE"
            }
            val bannerDetail = when {
                isChecking -> "Testing each URL for a real camera frame"
                checkedOnce && best == null ->
                    results.firstOrNull()?.detail ?: "No candidate returned a camera frame"
                best != null -> "$detectedTitle · ${best.detail}"
                else -> "Open the feed list to check each URL and choose one"
            }
            val selectedUrl = best?.url ?: streamUrl
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
                shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(BorderStroke(1.dp, bannerColor), shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Videocam, null, tint = bannerColor, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                bannerTitle,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = bannerColor
                            )
                        }
                        Text(
                            bannerDetail,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = selectedUrl,
                            fontSize = 7.5.sp,
                            color = Color.Gray,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Button(
                        onClick = {
                            showFeedPicker = true
                            when {
                                !checkedOnce -> checkCandidates(detectedCandidates)
                                !isChecking && playableCount == 0 ->
                                    checkCandidates(detectedCandidates, restart = true)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (checkedOnce && best == null) neonRed else neonGreen,
                            disabledContainerColor = neonGreen.copy(alpha = 0.45f)
                        ),
                        shape = RoundedCornerShape(4.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp),
                        enabled = detectedCandidates.isNotEmpty()
                    ) {
                        if (isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                color = Color.Black,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = when {
                                isChecking || !checkedOnce -> "VIEW FEEDS"
                                playableCount > 0 -> "CHOOSE FEED"
                                else -> "RETRY CHECK"
                            },
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showFeedPicker && detectedCandidates.isNotEmpty()) {
        FeedPickerSheet(
            candidates = detectedCandidates,
            labels = candidateTitles,
            checks = candidateChecks,
            isChecking = isChecking,
            onDismiss = { showFeedPicker = false },
            onLaunch = { result -> launchVerified(result) },
            onSave = { result -> saveFeed(result) },
            onRecheck = { checkCandidates(detectedCandidates, restart = true) }
        )
    }
}

private fun isNetworkMediaUrl(url: String): Boolean {
    val lower = url.lowercase()
    if (lower.contains(".m3u8") || lower.contains(".mpd")) return true
    return lower.startsWith("http") && (
        lower.contains("mjpg") ||
            lower.contains("mjpeg") ||
            lower.contains("action=stream") ||
            lower.contains("videostream") ||
            lower.contains("getvideo")
        )
}

private sealed class CandidateCheck {
    data object Checking : CandidateCheck()
    data class Done(val result: StreamPreflight.Result) : CandidateCheck()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPickerSheet(
    candidates: List<String>,
    labels: Map<String, String>,
    checks: Map<String, CandidateCheck>,
    isChecking: Boolean,
    onDismiss: () -> Unit,
    onLaunch: (StreamPreflight.Result) -> Unit,
    onSave: (StreamPreflight.Result) -> Unit,
    onRecheck: () -> Unit
) {
    val neonGreen = Color(0xFF39FF14)
    val orange = Color(0xFFFFA500)
    val neonRed = Color(0xFFFF5252)

    val results = checks.values.mapNotNull { (it as? CandidateCheck.Done)?.result }
    val playable = results.count { it.isPlayable }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFF101010),
        contentColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Color.DarkGray) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 18.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Videocam, null, tint = neonGreen, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "DETECTED FEEDS",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = if (isChecking) {
                            "Checking ${results.size} of ${candidates.size}…"
                        } else {
                            "$playable verified of ${candidates.size}"
                        },
                        color = Color.Gray,
                        fontSize = 9.sp
                    )
                }
                TextButton(onClick = onRecheck, enabled = !isChecking) {
                    Text(
                        text = "RE-CHECK ALL",
                        color = if (isChecking) Color.DarkGray else neonGreen,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isChecking) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = neonGreen,
                    trackColor = Color(0xFF1E1E1E)
                )
            }

            Spacer(Modifier.height(10.dp))
            Text(
                text = "Tap a verified feed to launch it. Thumbnails are frames read during the check.",
                color = Color.Gray,
                fontSize = 9.sp,
                lineHeight = 13.sp
            )
            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier.heightIn(max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(candidates, key = { it }) { url ->
                    FeedCandidateRow(
                        url = url,
                        label = labels[url].orEmpty(),
                        check = checks[url],
                        neonGreen = neonGreen,
                        orange = orange,
                        neonRed = neonRed,
                        onLaunch = onLaunch,
                        onSave = onSave
                    )
                }
            }
        }
    }
}

@Composable
private fun FeedCandidateRow(
    url: String,
    label: String,
    check: CandidateCheck?,
    neonGreen: Color,
    orange: Color,
    neonRed: Color,
    onLaunch: (StreamPreflight.Result) -> Unit,
    onSave: (StreamPreflight.Result) -> Unit
) {
    val result = (check as? CandidateCheck.Done)?.result
    val isCheckingThis = check is CandidateCheck.Checking
    val playable = result?.isPlayable == true
    val statusColor = when {
        playable && result?.protocol == "snapshot" -> orange
        playable -> neonGreen
        isCheckingThis -> orange
        result != null -> neonRed
        else -> Color.Gray
    }
    val statusLabel = when {
        isCheckingThis -> "CHECKING…"
        result == null -> "NOT CHECKED"
        result.isPlayable && (result.protocol == "mjpeg" || result.protocol == "hls") -> "VERIFIED LIVE"
        result.isPlayable -> "SNAPSHOT"
        else -> result.label.uppercase()
    }
    val detail = when {
        isCheckingThis -> "Reading the first camera frame"
        result != null -> result.detail
        else -> "Not probed yet"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = playable) { result?.let(onLaunch) },
        color = if (playable) Color(0xFF16210F) else Color(0xFF171717),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(
            1.dp,
            statusColor.copy(alpha = if (playable) 0.5f else 0.22f)
        )
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(84.dp)
                    .height(54.dp)
                    .background(Color.Black, RoundedCornerShape(6.dp))
                    .border(1.dp, Color(0xFF2A2A2A), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center
            ) {
                val thumbnail = result?.thumbnail
                when {
                    thumbnail != null -> Image(
                        bitmap = thumbnail.asImageBitmap(),
                        contentDescription = "Verified frame",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    isCheckingThis -> CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = orange,
                        strokeWidth = 1.8.dp
                    )
                    else -> Icon(
                        Icons.Default.Videocam,
                        contentDescription = null,
                        tint = Color.DarkGray,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (label.isNotBlank()) {
                    Text(
                        text = label,
                        color = Color.White,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = statusLabel,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.6.sp
                )
                Text(
                    text = detail,
                    color = Color.Gray,
                    fontSize = 8.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = result?.url ?: url,
                    color = Color(0xFF9E9E9E),
                    fontSize = 7.5.sp,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (playable) {
                Spacer(Modifier.width(2.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        onClick = { result?.let(onSave) },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            Icons.Default.BookmarkAdd,
                            contentDescription = "Save to feed library",
                            tint = neonGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Launch feed",
                        tint = statusColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}
