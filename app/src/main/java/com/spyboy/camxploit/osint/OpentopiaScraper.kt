package com.spyboy.camxploit.osint

import android.util.Log
import com.spyboy.camxploit.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.net.URL
import java.util.UUID

object OpentopiaScraper {

    private const val TAG = "OpentopiaScraper"
    private const val BASE_URL = "https://www.opentopia.com"
    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36"

    /** Fetch camera listings from the main page and paginated directory pages */
    suspend fun fetchCameras(limit: Int = 50): List<StreamSource> = withContext(Dispatchers.IO) {
        val cameras = mutableListOf<StreamSource>()
        val seenPageUrls = mutableSetOf<String>()

        // Construct list of page URLs to attempt
        val sources = mutableListOf("$BASE_URL/", "$BASE_URL/showlist.php")
        val numPagesNeeded = (limit / 10).coerceAtLeast(5)
        for (p in 1..numPagesNeeded) {
            sources.add("$BASE_URL/showlist.php?p=$p")
        }

        for (sourceUrl in sources.distinct()) {
            if (cameras.size >= limit) break

            try {
                val response = Jsoup.connect(sourceUrl)
                    .userAgent(USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                    .header("Accept-Language", "en-US,en;q=0.5")
                    .header("Referer", BASE_URL)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .ignoreHttpErrors(true)
                    .execute()

                if (response.statusCode() != 200) {
                    Log.w(TAG, "Source '$sourceUrl' returned HTTP status ${response.statusCode()}")
                    continue
                }

                val doc = response.parse()

                // Try multiple listing strategies
                val strategies = listOf(
                    "a[href*='showcam']",
                    "a[href*='site']",
                    "a[href*='target']",
                    "a[href*='webcam']",
                    "a[href*='camera']",
                    ".thumbnail", ".camera", ".webcam", ".cam-item", ".listing-item",
                    ".col-md-4", ".col-sm-6", ".col-lg-3", "table tr",
                    "[class*=cam]", "a:has(img)"
                )

                for (selector in strategies) {
                    if (cameras.size >= limit) break
                    val elements = doc.select(selector)
                    Log.d(TAG, "Source '$sourceUrl' Strategy '$selector' found ${elements.size} elements")
                    for (el in elements) {
                        val cam = parseListingElement(el) ?: continue
                        if (seenPageUrls.add(cam.pageUrl)) {
                            cameras.add(cam)
                        }
                        if (cameras.size >= limit) break
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch listing from $sourceUrl", e)
            }
        }

        Log.d(TAG, "Total cameras fetched: ${cameras.size}")
        cameras
    }

    /**
     * Deep-scrape an Opentopia detail page to find the direct camera feed.
     * Returns a Pair<directUrl, isLiveStream> or null if nothing found.
     */
    suspend fun scrapeDetailPage(detailUrl: String): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Scraping detail page: $detailUrl")
            val response = Jsoup.connect(detailUrl)
                .userAgent(USER_AGENT)
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.5")
                .header("Referer", BASE_URL)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .ignoreHttpErrors(true)
                .execute()

            if (response.statusCode() != 200) {
                Log.w(TAG, "Detail page '$detailUrl' returned HTTP status ${response.statusCode()}")
                return@withContext null
            }

            val doc = response.parse()

            // Log the page title to confirm we got the right page
            Log.d(TAG, "Page title: ${doc.title()}")
            Log.d(TAG, "Page has ${doc.select("img").size} images, ${doc.select("iframe").size} iframes")

            val candidates = mutableListOf<String>()

            // Strategy 1: Explicit camera image IDs/classes
            candidates += listOf(
                "img#main-image", "img#camera-image", "img#cam", "img#image0",
                "img.camera-image", "img.main-image", "img.webcam",
                ".camera-view img", ".webcam-view img", "#camera-container img"
            ).mapNotNull { doc.selectFirst(it)?.attr("src") }

            // Strategy 2: Images with stream-like URLs (more specific first)
            val allImages = doc.select("img")
            candidates += allImages.mapNotNull { img ->
                val src = img.attr("src")
                val dataSrc = img.attr("data-src")
                val dataOriginal = img.attr("data-original")
                
                listOf(src, dataSrc, dataOriginal).firstOrNull { url ->
                    url.isNotBlank() && (
                        url.contains("mjpg", ignoreCase = true) ||
                        url.contains("mjpeg", ignoreCase = true) ||
                        url.contains("cgi-bin", ignoreCase = true) ||
                        url.contains("video", ignoreCase = true) ||
                        url.contains("stream", ignoreCase = true) ||
                        url.contains("live", ignoreCase = true) ||
                        url.contains("current", ignoreCase = true) ||
                        url.contains("snapshot", ignoreCase = true)
                    )
                }
            }

            // Strategy 3: Any image in a "cam" named container that isn't a logo
            candidates += doc.select("[class*=cam] img, [id*=cam] img").mapNotNull { it.attr("src") }

            // Strategy 4: Iframes (often embed the real stream)
            candidates += doc.select("iframe").mapNotNull { 
                it.attr("src").takeIf { src -> src.isNotBlank() && !src.contains("google", ignoreCase = true) }
            }

            // Strategy 5: Meta refresh
            doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                ?.substringAfter("url=", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { candidates += it }

            // Strategy 6: Links to direct streams
            candidates += doc.select("a").mapNotNull { a ->
                val href = a.attr("href")
                if (href.contains("mjpg") || href.contains("mjpeg") || href.contains("stream")) href else null
            }

            // Strategy 7: Any image that points to an external IP (not opentopia assets)
            candidates += allImages.mapNotNull { img ->
                val src = img.attr("src")
                if (src.contains(":") && (src.startsWith("http") || src.startsWith("//")) 
                    && !src.contains("opentopia.com") 
                    && !src.contains("google")
                    && !src.contains("gstatic")) {
                    src
                } else null
            }

            // Resolve all candidates to absolute URLs
            val baseUri = doc.baseUri().ifBlank { detailUrl }
            val resolved = candidates.map { resolveUrl(baseUri, it) }
                .filter { it.isNotBlank() && it.startsWith("http") }
                .filter { url ->
                    val lower = url.lowercase()
                    !lower.contains("logo") && 
                    !lower.contains("header") && 
                    !lower.contains("footer") &&
                    !lower.contains("banner") &&
                    !lower.contains("icon") &&
                    !lower.contains("avatar") &&
                    !lower.contains("button") &&
                    !lower.contains("background") &&
                    !lower.contains("advert") &&
                    !lower.contains("spacer") &&
                    !lower.contains("theme") &&
                    !lower.contains("placeholder") &&
                    !lower.contains("favicon") &&
                    !lower.endsWith(".gif") // Usually not a camera stream if GIF
                }
                .distinct()

            Log.d(TAG, "Found ${resolved.size} candidate URLs: $resolved")

            // Pick the best one
            val bestStream = resolved.firstOrNull { 
                it.contains("mjpg", ignoreCase = true) || 
                it.contains("mjpeg", ignoreCase = true) ||
                it.contains("cgi-bin", ignoreCase = true)
            } ?: resolved.firstOrNull {
                it.contains("stream", ignoreCase = true) ||
                it.contains("live", ignoreCase = true) ||
                it.contains("video", ignoreCase = true)
            } ?: resolved.firstOrNull {
                it.matches(Regex(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*")) // direct IP
            } ?: resolved.firstOrNull()

            if (bestStream != null) {
                val isLive = bestStream.contains("mjpg", ignoreCase = true) ||
                             bestStream.contains("mjpeg", ignoreCase = true) ||
                             bestStream.contains("cgi-bin", ignoreCase = true) ||
                             bestStream.contains("stream", ignoreCase = true)
                Log.d(TAG, "Selected stream: $bestStream (isLive=$isLive)")
                Pair(bestStream, isLive)
            } else {
                Log.w(TAG, "No stream URL found, falling back to detail page")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to scrape detail page", e)
            null
        }
    }

    private fun parseListingElement(el: Element): StreamSource? {
        val img = if (el.tagName() == "img") el else (el.selectFirst("img") ?: el.closest("a")?.selectFirst("img") ?: el.parent()?.selectFirst("img"))
        val link = if (el.tagName() == "a") el else (el.closest("a") ?: el.selectFirst("a") ?: el.parent()?.selectFirst("a") ?: el.parent()?.closest("a"))

        val rawHref = link?.attr("href") ?: ""
        if (rawHref.isBlank() || rawHref == "#" || rawHref.startsWith("javascript:")) return null

        val pageUrl = resolveUrl(BASE_URL, rawHref)
        val normalizedPageUrl = pageUrl.trimEnd('/')

        if (normalizedPageUrl.isBlank() ||
            normalizedPageUrl == BASE_URL ||
            normalizedPageUrl == "$BASE_URL/index.php" ||
            normalizedPageUrl == "$BASE_URL/showlist.php" ||
            normalizedPageUrl == "$BASE_URL/search.php" ||
            !normalizedPageUrl.contains("opentopia", ignoreCase = true)
        ) {
            return null
        }

        val rawThumb = img?.attr("src")?.ifBlank { img.attr("data-src") }
            ?: img?.attr("data-original")
        val thumbUrl = if (!rawThumb.isNullOrBlank()) resolveUrl(BASE_URL, rawThumb) else ""

        val title = img?.attr("alt")?.ifBlank { null }
            ?: img?.attr("title")?.ifBlank { null }
            ?: link?.attr("title")?.ifBlank { null }
            ?: el.selectFirst(".title, .name, h3, h4, h5, b, strong")?.text()?.ifBlank { null }
            ?: "Live Camera"

        val location = el.selectFirst(".location, .country, .city, .loc, [class*=location], [class*=country]")?.text()?.trim()
            ?: el.parent()?.selectFirst(".location, .country, .city")?.text()?.trim()
            ?: link?.selectFirst(".location, .country, .city")?.text()?.trim()
            ?: "Unknown"

        return StreamSource(
            id = UUID.nameUUIDFromBytes(normalizedPageUrl.toByteArray(Charsets.UTF_8)).toString(),
            url = normalizedPageUrl,
            pageUrl = normalizedPageUrl,
            streamUrl = "",
            thumbnailUrl = thumbUrl,
            title = title,
            location = location,
            protocol = "http",
            sourceLabel = "Opentopia",
        )
    }

    private fun resolveUrl(base: String, relative: String?): String {
        if (relative.isNullOrBlank()) return ""
        if (relative.startsWith("http")) return relative
        return try {
            URL(URL(base), relative).toString()
        } catch (_: Exception) {
            relative
        }
    }
}


