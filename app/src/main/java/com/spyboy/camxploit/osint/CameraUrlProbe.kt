package com.spyboy.camxploit.osint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

/**
 * Probes a camera URL to determine what type of feed it actually serves.
 * Checks Content-Type, handles MJPEG streams and snapshots, and scrapes HTML wrappers if needed.
 */
object CameraUrlProbe {

    data class Result(
        val url: String,
        val contentType: String,
        val isMjpeg: Boolean,
        val isSnapshot: Boolean,
        val isHtml: Boolean
    )

    suspend fun probe(url: String): Result = withContext(Dispatchers.IO) {
        val lowerUrl = url.lowercase()

        // Fast path 1: Check known MJPEG patterns in URL extension or path
        val isKnownMjpegUrl = lowerUrl.endsWith(".mjpg") || lowerUrl.endsWith(".mjpeg") ||
                lowerUrl.endsWith(".mjpq") || lowerUrl.contains("video.cgi") ||
                lowerUrl.contains("videostream.cgi") || lowerUrl.contains("mjpg/video") ||
                lowerUrl.contains("mjpeg/video") || lowerUrl.contains("nphmotionjpeg") ||
                lowerUrl.contains("action=stream")

        if (isKnownMjpegUrl) {
            return@withContext Result(
                url = url,
                contentType = "multipart/x-mixed-replace",
                isMjpeg = true,
                isSnapshot = false,
                isHtml = false
            )
        }

        // Fast path 2: Check known Snapshot pattern
        val isKnownSnapshotUrl = lowerUrl.endsWith(".jpg") || lowerUrl.endsWith(".jpeg") ||
                lowerUrl.endsWith(".png") || lowerUrl.contains("snap.jpg") || lowerUrl.contains("snapshot.cgi")

        if (isKnownSnapshotUrl && !lowerUrl.contains("mjpg") && !lowerUrl.contains("mjpeg")) {
            return@withContext Result(
                url = url,
                contentType = "image/jpeg",
                isMjpeg = false,
                isSnapshot = true,
                isHtml = false
            )
        }

        // Step 1: Probe HTTP headers using GET (read headers without loading full body)
        val contentType = try {
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                setRequestProperty("Accept", "multipart/x-mixed-replace, image/jpeg, text/html, */*")
                instanceFollowRedirects = true
            }
            val type = conn.contentType ?: ""
            conn.disconnect()
            type
        } catch (e: Exception) { "" }

        val typeLower = contentType.lowercase()
        val isMjpeg = typeLower.contains("multipart") || typeLower.contains("mixed-replace") || typeLower.contains("mjpeg")
        val isSnapshot = typeLower.contains("image/jpeg") || typeLower.contains("image/jpg") || typeLower.contains("image/png")
        val isHtml = typeLower.contains("text/html")

        if (isMjpeg || isSnapshot) {
            return@withContext Result(url, contentType, isMjpeg, isSnapshot, isHtml)
        }

        // Step 2: If HTML wrapper page, extract direct image / video feed URL from HTML
        if (isHtml || contentType.isBlank()) {
            val extracted = extractFromHtml(url)
            if (extracted != null && extracted != url) {
                val subLower = extracted.lowercase()
                if (subLower.contains("mjpg") || subLower.contains("mjpeg") || subLower.contains("video")) {
                    return@withContext Result(extracted, "multipart/x-mixed-replace", isMjpeg = true, isSnapshot = false, isHtml = false)
                } else if (subLower.endsWith(".jpg") || subLower.contains("snap")) {
                    return@withContext Result(extracted, "image/jpeg", isMjpeg = false, isSnapshot = true, isHtml = false)
                }
            }
        }

        // Fallback: Default to stream if URL looks like video feed, or treat as HTML web player
        val fallbackIsMjpeg = lowerUrl.contains("video") || lowerUrl.contains("cam") || lowerUrl.contains("mjpg") || lowerUrl.contains("cgi")
        Result(
            url = url,
            contentType = contentType,
            isMjpeg = fallbackIsMjpeg,
            isSnapshot = false,
            isHtml = !fallbackIsMjpeg
        )
    }

    private fun extractFromHtml(pageUrl: String): String? {
        return try {
            val doc = Jsoup.connect(pageUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .timeout(6000)
                .get()

            val candidates = mutableListOf<String>()

            // Look for img tags that point to camera feeds
            doc.select("img").forEach { img ->
                listOf("src", "data-src", "data-original").forEach { attr ->
                    img.attr(attr).takeIf { it.isNotBlank() }?.let { candidates += it }
                }
            }

            // Look for iframes
            doc.select("iframe").forEach { candidates += it.attr("src") }

            // Look for meta refresh
            doc.selectFirst("meta[http-equiv=refresh]")?.attr("content")
                ?.substringAfter("url=", "")?.takeIf { it.isNotBlank() }?.let { candidates += it }

            val base = doc.baseUri().ifBlank { pageUrl }
            val resolved = candidates.map { resolveUrl(base, it) }.filter { it.startsWith("http") }

            // Pick the best candidate
            resolved.firstOrNull {
                it.contains("mjpg", ignoreCase = true) ||
                it.contains("mjpeg", ignoreCase = true) ||
                it.contains("stream", ignoreCase = true) ||
                it.contains("video", ignoreCase = true)
            } ?: resolved.firstOrNull {
                it.contains("cgi-bin", ignoreCase = true)
            } ?: resolved.firstOrNull()
        } catch (e: Exception) {
            null
        }
    }

    private fun resolveUrl(base: String, relative: String): String {
        if (relative.startsWith("http")) return relative
        return try {
            URL(URL(base), relative).toString()
        } catch (e: Exception) {
            relative
        }
    }
}
