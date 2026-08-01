package com.spyboy.camxploit.osint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL

/**
 * Probes a camera URL to determine what type of feed it actually serves.
 * Checks Content-Type, tries common MJPEG path variations, and scrapes HTML wrappers.
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
        // Step 1: HEAD request to check Content-Type
        val headType = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "HEAD"
            conn.connectTimeout = 8000
            conn.readTimeout = 8000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.instanceFollowRedirects = true
            val type = conn.contentType ?: ""
            conn.disconnect()
            type
        } catch (e: Exception) { "" }

        val lower = headType.lowercase()
        val isMjpeg = lower.contains("multipart") || lower.contains("mixed-replace")
        val isSnapshot = lower.contains("image/jpeg") || lower.contains("image/jpg") || lower.contains("image/png")
        val isHtml = lower.contains("text/html")

        // If it's already a clear stream or snapshot, we're done
        if (isMjpeg || isSnapshot) {
            return@withContext Result(url, headType, isMjpeg, isSnapshot, isHtml)
        }

        // Step 2: If HTML (or unknown), try to extract a direct feed from the page
        if (isHtml || headType.isBlank()) {
            val extracted = extractFromHtml(url)
            if (extracted != null && extracted != url) {
                return@withContext probe(extracted) // Re-probe the extracted URL
            }
        }

        // Step 3: Try common MJPEG path variations for cgi-bin/viewer/camera endpoints
        val guessed = guessMjpegUrl(url)
        if (guessed != null) {
            val guessType = try {
                val conn = URL(guessed).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                val t = conn.contentType ?: ""
                conn.disconnect()
                t
            } catch (e: Exception) { "" }

            val gLower = guessType.lowercase()
            if (gLower.contains("multipart") || gLower.contains("mixed-replace") || gLower.contains("image")) {
                return@withContext Result(guessed, guessType, true, false, false)
            }
        }

        // Step 4: Fallback — treat as HTML and let WebView handle it
        Result(url, headType, false, isSnapshot, true)
    }

    private fun extractFromHtml(pageUrl: String): String? {
        return try {
            val doc = Jsoup.connect(pageUrl)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .timeout(10000)
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

    private fun guessMjpegUrl(original: String): String? {
        val guesses = listOf(
            original.replace("viewer", "video.mjpg", ignoreCase = true),
            original.replace("viewer", "mjpg/video.cgi", ignoreCase = true),
            original.replace("camera", "video.mjpg", ignoreCase = true),
            original.replace("camera", "mjpg/video.cgi", ignoreCase = true),
            "$original?action=stream",
            "$original&action=stream",
            original.replace("cgi-bin/camera", "mjpg/video.cgi", ignoreCase = true),
            original.replace("cgi-bin/viewer", "mjpg/video.cgi", ignoreCase = true),
            original.replaceAfterLast("/", "video.mjpg"),
            original.replaceAfterLast("/", "video.cgi"),
            original.replaceAfterLast("/", "stream.mjpg"),
            original.replaceAfterLast("/", "mjpg")
        )
        return guesses.firstOrNull { it != original }
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
