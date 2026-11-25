package com.spyboy.camxploit.osint

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

/**
 * SkylineWebcams directory cards use preview images, while the live HLS token
 * lives on each camera page. The browser uses this to build the feed picker and
 * the monitor uses it to refresh expired tokens before checking a saved feed.
 */
object SkylineResolver {

    data class CamLink(val pageUrl: String, val name: String)
    data class Feed(
        val url: String,
        val title: String,
        val pageUrl: String = "",
        val thumbnailUrl: String = ""
    )

    const val MAX_CAMS = 40

    /** True for country/region pages, false for an individual camera page. */
    fun isDirectoryPage(url: String): Boolean {
        val path = url.substringBefore('?').substringBefore('#')
        val afterWebcam = path.substringAfter("/webcam/", "")
        if (afterWebcam.isBlank()) return true
        val segments = afterWebcam.removeSuffix(".html")
            .split('/')
            .filter { it.isNotBlank() }
        return segments.size < 4
    }

    private val manifestToken = Regex("""livee?\.m3u8\?a=([A-Za-z0-9]+)""")
    private val camLink = Regex(
        """^https?://(?:www\.)?skylinewebcams\.com/[a-z]{2}/webcam/(?:[^/?#]+/){3,}[^/?#]+\.html$""",
        RegexOption.IGNORE_CASE
    )

    fun extractCamLinks(html: String, pageUrl: String): List<CamLink> {
        return runCatching {
            val doc = Jsoup.parse(html, pageUrl)
            doc.select("a[href]").mapNotNull { anchor ->
                val href = anchor.absUrl("href").ifBlank { anchor.attr("href") }
                if (!camLink.matches(href)) return@mapNotNull null
                val name = (anchor.selectFirst("img")?.attr("alt") ?: "")
                    .ifBlank { anchor.text() }
                    .replace(Regex("""\s+"""), " ")
                    .trim()
                CamLink(href, name)
            }.distinctBy { it.pageUrl }
        }.getOrDefault(emptyList())
    }

    suspend fun listingCams(pageUrl: String): List<CamLink> = withContext(Dispatchers.IO) {
        val html = readPage(pageUrl) ?: return@withContext emptyList()
        extractCamLinks(html, pageUrl)
    }

    suspend fun feedFor(cam: CamLink): Feed? = withContext(Dispatchers.IO) {
        val html = readPage(cam.pageUrl) ?: return@withContext null
        val token = manifestToken.find(html)?.groupValues?.get(1) ?: return@withContext null
        val doc = runCatching { Jsoup.parse(html, cam.pageUrl) }.getOrNull()
        val rawThumb = doc?.selectFirst("meta[property=og:image]")?.attr("content")
            ?.ifBlank { null }
            ?: doc?.selectFirst("img[src*=.webp], img[src*=.jpg]")?.absUrl("src")
        val thumb = rawThumb?.let { value ->
            if (value.startsWith("http")) value else runCatching {
                URL(URL(cam.pageUrl), value).toString()
            }.getOrDefault(value)
        }.orEmpty()
        Feed(
            url = "https://hd-auth.skylinewebcams.com/live.m3u8?a=$token",
            title = cam.name,
            pageUrl = cam.pageUrl,
            thumbnailUrl = thumb
        )
    }

    /** Returns a fresh playable manifest for a saved camera page. */
    suspend fun freshManifest(pageUrl: String): String? =
        feedFor(CamLink(pageUrl, ""))?.url

    /**
     * Skyline serves a public page with per-camera player tokens. Fetch it
     * first without the WebView cookie jar: a logged-in page can carry a sticky
     * "current camera" token on every page, which made all directory feeds look
     * identical. Retry with cookies only if the public request fails.
     */
    private fun readPage(pageUrl: String): String? =
        readPageAttempt(pageUrl, includeCookies = false)
            ?: readPageAttempt(pageUrl, includeCookies = true)

    private fun readPageAttempt(pageUrl: String, includeCookies: Boolean): String? {
        for (attempt in 0..2) {
            var connection: HttpURLConnection? = null
            try {
                connection = (URL(pageUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    useCaches = false
                    connectTimeout = 5_000
                    readTimeout = 7_000
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                            "(KHTML, like Gecko) Chrome/122.0.0.0 Mobile Safari/537.36"
                    )
                    setRequestProperty("Accept", "text/html,application/xhtml+xml")
                    if (includeCookies) {
                        runCatching { CookieManager.getInstance().getCookie(pageUrl) }
                            .getOrNull()
                            ?.takeIf { it.isNotBlank() }
                            ?.let { setRequestProperty("Cookie", it) }
                    }
                }
                val code = connection.responseCode
                if (code == 429) {
                    connection.disconnect()
                    connection = null
                    Thread.sleep(900L * (attempt + 1))
                    continue
                }
                if (code !in 200..299) return null

                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    val builder = StringBuilder()
                    val buffer = CharArray(8 * 1024)
                    var total = 0
                    while (total < 512 * 1024) {
                        val read = reader.read(buffer)
                        if (read <= 0) break
                        builder.append(buffer, 0, read)
                        total += read
                    }
                    return builder.toString()
                }
            } catch (_: Exception) {
                return null
            } finally {
                runCatching { connection?.disconnect() }
            }
        }
        return null
    }
}
