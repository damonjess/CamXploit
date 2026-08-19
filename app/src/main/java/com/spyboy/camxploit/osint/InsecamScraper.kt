package com.spyboy.camxploit.osint

import kotlinx.coroutines.*
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URL

object InsecamScraper {

    private const val TIMEOUT_MS = 15_000
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

    // In-memory LRU cache: avoids re-scraping the same Insecam page
    private val cache = object : LinkedHashMap<String, ScrapedResult>(100, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ScrapedResult>?): Boolean {
            return size > 200
        }
    }

    /**
     * Scrapes a specific Insecam country page for a list of cameras.
     */
    data class ListingPage(
        val cameras: List<InsecamClient.PublicCamera>,
        val hasNextPage: Boolean
    )

    suspend fun scrapeListing(countryCode: String, page: Int = 1): ListingPage = withContext(Dispatchers.IO) {
        try {
            val url = if (page == 1) {
                "http://www.insecam.org/en/bycountry/${countryCode.uppercase()}/"
            } else {
                "http://www.insecam.org/en/bycountry/${countryCode.uppercase()}/?page=$page"
            }

            val doc: Document = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .get()

            val results = mutableListOf<InsecamClient.PublicCamera>()
            val items = doc.select(".thumbnail, .thumbnail-container, [class*=\"col-\"]")
            
            items.forEach { item ->
                val img = item.selectFirst("img")
                val link = item.selectFirst("a[href*=\"/view/\"]")
                val caption = item.selectFirst(".caption h4, .caption h3, .caption, p")
                val ipMatch = Regex("([0-9]+\\.[0-9]+\\.[0-9]+\\.[0-9]+)").find(item.html())?.value
                
                if (img != null && link != null) {
                    val href = link.attr("href")
                    val idMatch = Regex("/view/(\\d+)").find(href)
                    val cleanId = idMatch?.groupValues?.get(1) ?: ""
                    
                    if (cleanId.isNotEmpty() && results.none { it.id == cleanId }) {
                        results.add(InsecamClient.PublicCamera(
                            id = cleanId,
                            imageUrl = img.attr("src"),
                            location = caption?.text()?.replace("\\s+".toRegex(), " ")?.trim() ?: "Unknown Location",
                            ip = ipMatch ?: "Unknown IP",
                            countryCode = countryCode.uppercase()
                        ))
                    }
                }
            }
            val hasNextPage = doc.select("a[href*='page=']").any { link ->
                val href = link.attr("href")
                val text = link.text().trim()
                href.contains("page=${page + 1}") || text.equals("next", ignoreCase = true) || text == ">"
            }
            // Some directory pages do not expose a usable Next link. Preserve the known
            // six-card page fallback so users can still request the next page; an empty
            // next response then cleanly removes the Load More control.
            ListingPage(results, hasNextPage || results.size >= 6)
        } catch (e: Exception) {
            throw IllegalStateException("Country directory request failed for ${countryCode.uppercase()}: ${e.message}", e)
        }
    }

    suspend fun scrapePage(pageUrl: String): ScrapedResult = withContext(Dispatchers.IO) {
        synchronized(cache) {
            cache[pageUrl]?.let { return@withContext it }
        }

        try {
            val doc: Document = Jsoup.connect(pageUrl)
                .userAgent(USER_AGENT)
                .timeout(TIMEOUT_MS)
                .followRedirects(true)
                .get()

            var imageUrl = doc.selectFirst("img#image0")?.attr("src")
                ?: doc.selectFirst("#camera img")?.attr("src")
                ?: doc.selectFirst(".camera-image img")?.attr("src")
                ?: doc.selectFirst("img[src*=mjpg]")?.attr("src")
                ?: doc.selectFirst("img[src*=mjpeg]")?.attr("src")
                ?: doc.selectFirst("img[src*=cgi-bin]")?.attr("src")
                ?: doc.selectFirst("img[src*=video]")?.attr("src")

            val iframeSrc = doc.selectFirst("iframe")?.attr("src")

            if (imageUrl.isNullOrBlank()) {
                imageUrl = doc.select("img").firstOrNull { img ->
                    val src = img.attr("src")
                    src.contains(":", ignoreCase = true) &&
                    (src.startsWith("http") || src.startsWith("//"))
                }?.attr("src")
            }

            val metaRefresh = doc.selectFirst("meta[http-equiv=refresh]")
                ?.attr("content")
                ?.substringAfter("url=")

            val baseUri = doc.baseUri().ifBlank { pageUrl }
            val resolvedImage = resolveUrl(baseUri, imageUrl)
            val resolvedIframe = resolveUrl(baseUri, iframeSrc)
            val resolvedMeta = resolveUrl(baseUri, metaRefresh)

            val bestStream = when {
                resolvedIframe.isNotBlank() -> resolvedIframe
                resolvedImage.isNotBlank() -> resolvedImage
                resolvedMeta.isNotBlank() -> resolvedMeta
                else -> ""
            }

            val bestThumb = resolvedImage.ifBlank { bestStream }

            val result = ScrapedResult(
                pageUrl = pageUrl,
                streamUrl = bestStream,
                thumbnailUrl = bestThumb,
                title = doc.selectFirst("h1, .camera-title, title")?.text()?.trim()
                    ?: "Live Camera",
                location = doc.selectFirst(".camera-location, .location, [class*=country]")?.text()?.trim()
                    ?: "Unknown"
            )

            synchronized(cache) { cache[pageUrl] = result }
            result
        } catch (e: Exception) {
            e.printStackTrace()
            ScrapedResult(pageUrl = pageUrl, streamUrl = "", thumbnailUrl = "")
        }
    }

    /** Parallel batch scrape — much faster for the Public Cams grid */
    suspend fun scrapeBatch(pageUrls: List<String>): List<ScrapedResult> = coroutineScope {
        pageUrls.map { url ->
            async(Dispatchers.IO) { scrapePage(url) }
        }.awaitAll()
    }

    private fun resolveUrl(base: String, relative: String?): String {
        if (relative.isNullOrBlank()) return ""
        return try {
            URL(URL(base), relative).toString()
        } catch (e: Exception) {
            relative
        }
    }

    data class ScrapedResult(
        val pageUrl: String,
        val streamUrl: String,
        val thumbnailUrl: String,
        val title: String = "",
        val location: String = ""
    )
}
