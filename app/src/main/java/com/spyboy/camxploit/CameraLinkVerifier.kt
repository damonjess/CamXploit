package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * The Python scanner reports a link as a camera when the HTTP status is 200.
 * Routers and captive portals often answer every unknown path with a 200 HTML
 * login/intercept page, which produces convincing but false camera links.
 *
 * This verifier follows the real request and keeps only endpoints that actually
 * return image or multipart MJPEG content. It also reports links that need auth
 * instead of pretending they are live.
 */
object CameraLinkVerifier {

    enum class Verdict { LIVE, LOGIN, REJECTED, UNREACHABLE }

    data class DetectedLink(
        val type: String,
        val url: String,
        val reportedStatus: String
    )

    data class Result(
        val link: DetectedLink,
        val verdict: Verdict,
        val detail: String,
        val finalUrl: String? = null,
        val contentType: String? = null,
        val responseCode: Int = 0
    )

    fun parse(terminalText: String): List<DetectedLink> {
        if (!terminalText.contains("===LINKS_START===")) return emptyList()
        return terminalText
            .substringAfter("===LINKS_START===")
            .substringBefore("===LINKS_END===")
            .lineSequence()
            .mapNotNull { line ->
                val parts = line.split("|")
                if (parts.size < 2 || parts[1].isBlank()) return@mapNotNull null
                DetectedLink(
                    type = parts[0].trim(),
                    url = parts[1].trim(),
                    reportedStatus = parts.getOrNull(2)?.trim().orEmpty()
                )
            }
            .distinctBy { it.url }
            .toList()
    }

    suspend fun verifyAll(links: List<DetectedLink>): List<Result> = coroutineScope {
        val gate = Semaphore(4)
        links.map { link ->
            async(Dispatchers.IO) { gate.withPermit { verify(link) } }
        }.awaitAll()
    }

    private fun verify(link: DetectedLink): Result {
        val requestedUrl = link.url.trim()
        if (!requestedUrl.startsWith("http://", true) && !requestedUrl.startsWith("https://", true)) {
            return Result(link, Verdict.REJECTED, "Not an HTTP stream URL")
        }

        var connection: HttpURLConnection? = null
        return try {
            connection = (URL(requestedUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                useCaches = false
                connectTimeout = 4_000
                readTimeout = 4_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) CamXploit/2.0")
                setRequestProperty(
                    "Accept",
                    "multipart/x-mixed-replace, image/jpeg, image/*, text/html;q=0.1, */*;q=0.1"
                )
            }

            val code = connection.responseCode
            val type = connection.contentType
                ?.substringBefore(';')
                ?.trim()
                ?.lowercase(Locale.US)
                .orEmpty()
            val finalUrl = connection.url.toString()
            val redirected = normalize(finalUrl) != normalize(requestedUrl)
            val redirectNote = if (redirected) " · redirected" else ""

            when {
                code == 401 || code == 403 -> Result(
                    link,
                    Verdict.LOGIN,
                    "Authentication required (HTTP $code)$redirectNote",
                    finalUrl,
                    type,
                    code
                )

                code !in 200..299 -> Result(
                    link,
                    Verdict.UNREACHABLE,
                    "HTTP $code$redirectNote",
                    finalUrl,
                    type,
                    code
                )

                type.contains("multipart") || type.contains("x-mixed-replace") -> Result(
                    link,
                    Verdict.LIVE,
                    "Live MJPEG stream$redirectNote",
                    finalUrl,
                    type,
                    code
                )

                type.startsWith("image/") -> Result(
                    link,
                    Verdict.LIVE,
                    "Image feed ($type)$redirectNote",
                    finalUrl,
                    type,
                    code
                )

                type.contains("text/html") -> Result(
                    link,
                    Verdict.REJECTED,
                    if (redirected) {
                        "Redirected to an HTML page — not a camera feed"
                    } else {
                        "HTML page — not a camera feed"
                    },
                    finalUrl,
                    type,
                    code
                )

                type.contains("xml") || type.contains("json") -> Result(
                    link,
                    Verdict.REJECTED,
                    "Service metadata, not a viewable feed",
                    finalUrl,
                    type,
                    code
                )

                type.isBlank() -> Result(
                    link,
                    Verdict.REJECTED,
                    "No content type returned$redirectNote",
                    finalUrl,
                    type,
                    code
                )

                else -> Result(
                    link,
                    Verdict.REJECTED,
                    "Unsupported content type: $type",
                    finalUrl,
                    type,
                    code
                )
            }
        } catch (e: Exception) {
            Result(
                link,
                Verdict.UNREACHABLE,
                e.message?.take(120) ?: "Connection failed"
            )
        } finally {
            connection?.disconnect()
        }
    }

    private fun normalize(url: String): String =
        url.trim().trimEnd('/').lowercase(Locale.US)
}
