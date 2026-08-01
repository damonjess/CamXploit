package com.spyboy.camxploit.osint

import com.spyboy.camxploit.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URL
import java.util.UUID

/**
 * Opentopia scraper — no API key needed.
 * Parses the public webcam listings and extracts direct links / thumbnails.
 */
object OpentopiaScraper {

    private const val BASE_URL = "https://www.opentopia.com"
    private const val LIST_URL = "$BASE_URL/"
    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // In-memory LRU cache: avoids re-scraping the same Opentopia detail page
    private val detailCache = object : java.util.LinkedHashMap<String, String>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 200
        }
    }

    suspend fun fetchCameras(limit: Int = 50): List<StreamSource> = withContext(Dispatchers.IO) {
        try {
            val doc: Document = Jsoup.connect(LIST_URL)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

            val cameras = mutableListOf<StreamSource>()

            // Strategy 1: Look for camera entries in table rows (classic Opentopia layout)
            val rows = doc.select("table tr")
            for (row in rows) {
                val cam = parseRow(row) ?: continue
                cameras.add(cam)
                if (cameras.size >= limit) break
            }

            // Strategy 2: If no table rows, look for div-based cards
            if (cameras.isEmpty()) {
                val cards = doc.select(".camera, .webcam, .cam-item, .listing-item, .col-md-4, .col-sm-6")
                for (card in cards) {
                    val cam = parseCard(card) ?: continue
                    cameras.add(cam)
                    if (cameras.size >= limit) break
                }
            }

            // Strategy 3: Look for any link that points to a webcam detail page
            if (cameras.isEmpty()) {
                val links = doc.select("a[href*=webcam], a[href*=camera], a[href*=/cam/]")
                for (link in links) {
                    val cam = parseLink(link) ?: continue
                    cameras.add(cam)
                    if (cameras.size >= limit) break
                }
            }

            cameras
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Scrapes an Opentopia detail page (e.g. /webcam/19968) to find the direct stream URL.
     * Returns the direct feed URL, or the original page URL if extraction fails.
     */
    suspend fun scrapeDetailPage(detailUrl: String): String = withContext(Dispatchers.IO) {
        synchronized(detailCache) {
            detailCache[detailUrl]?.let { return@withContext it }
        }

        try {
            val doc = Jsoup.connect(detailUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

            // Strategy 1: Main camera image / live feed
            var streamUrl = doc.selectFirst("img#main-image")?.attr("src")
                ?: doc.selectFirst("img#image0")?.attr("src")
                ?: doc.selectFirst("img.camera-image")?.attr("src")
                ?: doc.selectFirst("img[src*=mjpg]")?.attr("src")
                ?: doc.selectFirst("img[src*=mjpeg]")?.attr("src")
                ?: doc.selectFirst("img[src*=cgi-bin]")?.attr("src")
                ?: doc.selectFirst("img[src*=video]")?.attr("src")
                ?: doc.selectFirst("img[src*=snapshot]")?.attr("src")
                ?: doc.selectFirst("img[src*=live]")?.attr("src")

            // Strategy 2: Iframe embedding the actual stream
            val iframeSrc = doc.selectFirst("iframe")?.attr("src")

            // Strategy 3: Any image that points to an external IP (likely the camera)
            if (streamUrl.isNullOrBlank()) {
                streamUrl = doc.select("img").firstOrNull { img ->
                    val src = img.attr("src")
                    src.contains(":", ignoreCase = true) &&
                    (src.startsWith("http") || src.startsWith("//")) &&
                    !src.contains("opentopia.com") // ignore site assets
                }?.attr("src")
            }

            // Strategy 4: Look for a direct "live view" link
            if (streamUrl.isNullOrBlank()) {
                streamUrl = doc.select("a").firstOrNull { a ->
                    val href = a.attr("href")
                    href.contains("mjpg") || href.contains("mjpeg") || href.contains("stream")
                }?.attr("href")
            }

            val baseUri = doc.baseUri().ifBlank { detailUrl }
            val resolvedStream = resolveUrl(baseUri, streamUrl)
            val resolvedIframe = resolveUrl(baseUri, iframeSrc)

            val result = when {
                resolvedStream.isNotBlank() -> resolvedStream
                resolvedIframe.isNotBlank() -> resolvedIframe
                else -> detailUrl
            }
            
            synchronized(detailCache) { detailCache[detailUrl] = result }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            detailUrl
        }
    }

    /** Parse a table row from the classic layout */
    private fun parseRow(row: Element): StreamSource? {
        val cells = row.select("td")
        if (cells.size < 2) return null

        val imgCell = cells.firstOrNull { it.selectFirst("img") != null } ?: return null
        val img = imgCell.selectFirst("img") ?: return null
        val thumbUrl = resolveUrl(img.attr("src"))
        val title = img.attr("alt").ifBlank { img.attr("title") }.ifBlank { "Live Camera" }

        val link = imgCell.selectFirst("a") ?: row.selectFirst("a") ?: return null
        val pageUrl = resolveUrl(link.attr("href"))

        val location = cells.getOrNull(1)?.text()?.trim()
            ?: cells.getOrNull(2)?.text()?.trim()
            ?: "Unknown"

        return StreamSource(
            id = UUID.randomUUID().toString(),
            url = pageUrl,
            pageUrl = pageUrl,
            streamUrl = pageUrl,
            thumbnailUrl = thumbUrl,
            title = title,
            location = location,
            protocol = "http"
        )
    }

    /** Parse a div-based card */
    private fun parseCard(card: Element): StreamSource? {
        val img = card.selectFirst("img") ?: return null
        val thumbUrl = resolveUrl(img.attr("src"))
        val title = img.attr("alt").ifBlank { card.selectFirst("h2, h3, h4, .title, .cam-title")?.text() }
            ?.ifBlank { "Live Camera" } ?: "Live Camera"

        val link = card.selectFirst("a") ?: return null
        val pageUrl = resolveUrl(link.attr("href"))

        val location = card.selectFirst(".location, .country, .city, .loc, p")?.text()?.trim()
            ?: "Unknown"

        return StreamSource(
            id = UUID.randomUUID().toString(),
            url = pageUrl,
            pageUrl = pageUrl,
            streamUrl = pageUrl,
            thumbnailUrl = thumbUrl,
            title = title,
            location = location,
            protocol = "http"
        )
    }

    /** Parse a bare link that points to a camera page */
    private fun parseLink(link: Element): StreamSource? {
        val pageUrl = resolveUrl(link.attr("href"))
        val img = link.selectFirst("img")
        val thumbUrl = img?.let { resolveUrl(it.attr("src")) } ?: ""
        val title = img?.attr("alt")?.ifBlank { link.text() }?.ifBlank { "Live Camera" } ?: "Live Camera"
        val location = link.parent()?.selectFirst(".location, .country, .city, .loc, p")?.text()?.trim()
            ?: "Unknown"

        return StreamSource(
            id = UUID.randomUUID().toString(),
            url = pageUrl,
            pageUrl = pageUrl,
            streamUrl = pageUrl,
            thumbnailUrl = thumbUrl,
            title = title,
            location = location,
            protocol = "http"
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
