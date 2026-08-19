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
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    /** Fetch camera listings from the main page and category pages */
    suspend fun fetchCameras(limit: Int = 50): List<StreamSource> = withContext(Dispatchers.IO) {
        val cameras = mutableListOf<StreamSource>()
        val sources = listOf("$BASE_URL/", "$BASE_URL/hottest/", "$BASE_URL/newest/", "$BASE_URL/popular/")

        try {
            for (sourceUrl in sources) {
                if (cameras.size >= limit) break
                
                val doc = Jsoup.connect(sourceUrl)
                    .userAgent(USER_AGENT)
                    .timeout(TIMEOUT_MS)
                    .followRedirects(true)
                    .get()

                // Try multiple listing strategies
                val strategies = listOf(
                    "table tr",
                    ".camera", ".webcam", ".cam-item", ".listing-item",
                    ".col-md-4", ".col-sm-6", ".col-lg-3",
                    "[class*=cam]", "[class*=webcam]"
                )

                for (selector in strategies) {
                    if (cameras.size >= limit) break
                    val elements = doc.select(selector)
                    Log.d(TAG, "Source '$sourceUrl' Strategy '$selector' found ${elements.size} elements")
                    for (el in elements) {
                        val cam = parseListingElement(el) ?: continue
                        if (cameras.none { it.pageUrl == cam.pageUrl }) {
                            cameras.add(cam)
                        }
                        if (cameras.size >= limit) break
                    }
                }
            }

            Log.d(TAG, "Total cameras fetched: ${cameras.size}")
            cameras
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch listings", e)
            throw IllegalStateException("Opentopia source failed after loading ${cameras.size} result(s): ${e.message}", e)
        }
    }

    /**
     * Deep-scrape an Opentopia detail page to find the direct camera feed.
     * Returns a Pair<directUrl, isLiveStream> or null if nothing found.
     */
    suspend fun scrapeDetailPage(detailUrl: String): Pair<String, Boolean>? = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Scraping detail page: $detailUrl")
            val doc = Jsoup.connect(detailUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

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

            // Strategy 3: Iframes (often embed the real stream)
            candidates += doc.select("iframe").mapNotNull { 
                it.attr("src").takeIf { src -> src.isNotBlank() && !src.contains("google", ignoreCase = true) }
            }

            // Strategy 4: Meta refresh
            doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                ?.substringAfter("url=", "")
                ?.takeIf { it.isNotBlank() }
                ?.let { candidates += it }

            // Strategy 5: Links to direct streams
            candidates += doc.select("a").mapNotNull { a ->
                val href = a.attr("href")
                if (href.contains("mjpg") || href.contains("mjpeg") || href.contains("stream")) href else null
            }

            // Strategy 6: Any image that points to an external IP (not opentopia assets)
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
        val img = el.selectFirst("img") ?: return null
        val thumbUrl = resolveUrl(img.attr("src"))
        val title = img.attr("alt").ifBlank { img.attr("title") }.ifBlank { "Live Camera" }

        val link = img.closest("a") ?: el.selectFirst("a") ?: return null
        val pageUrl = resolveUrl(link.attr("href"))
        if (pageUrl.isBlank() || !pageUrl.contains("opentopia", ignoreCase = true)) return null

        val location = el.selectFirst(".location, .country, .city, .loc, [class*=location], [class*=country]")?.text()?.trim()
            ?: el.parent()?.selectFirst(".location, .country, .city")?.text()?.trim()
            ?: "Unknown"

        return StreamSource(
            id = UUID.nameUUIDFromBytes(pageUrl.toByteArray(Charsets.UTF_8)).toString(),
            url = pageUrl,
            pageUrl = pageUrl,
            streamUrl = "",
            thumbnailUrl = thumbUrl,
            title = title,
            location = location,
            protocol = "http",
            sourceLabel = "Opentopia"
        )
    }

    private fun resolveUrl(base: String, relative: String?): String {
        if (relative.isNullOrBlank()) return ""
        if (relative.startsWith("http")) return relative
        return try {
            URL(URL(base), relative).toString()
        } catch (e: Exception) {
            relative
        }
    }

    private fun resolveUrl(relative: String): String = resolveUrl(BASE_URL, relative)
}
