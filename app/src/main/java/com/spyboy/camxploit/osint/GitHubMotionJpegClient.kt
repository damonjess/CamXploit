package com.spyboy.camxploit.osint

import com.spyboy.camxploit.StreamSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID

/**
 * Fetches the Public_MotionJPEG_Sources GitHub repo README
 * and parses out direct stream URLs.
 */
object GitHubMotionJpegClient {

    private const val RAW_README = "https://raw.githubusercontent.com/AzwadFawadHasan/Public_MotionJPEG_Sources/main/README.md"

    suspend fun fetchSources(): List<StreamSource> = withContext(Dispatchers.IO) {
        try {
            val connection = URL(RAW_README).openConnection() as HttpURLConnection
            connection.apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                setRequestProperty("User-Agent", "Mozilla/5.0")
                doInput = true
            }

            val code = connection.responseCode
            if (code != 200) {
                throw Exception("GitHub fetch failed: HTTP $code")
            }

            val markdown = connection.inputStream.bufferedReader().use { it.readText() }
            connection.disconnect()

            parseMarkdown(markdown)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun parseMarkdown(md: String): List<StreamSource> {
        val sources = mutableListOf<StreamSource>()
        val lines = md.lines()

        var currentTitle = "Unknown Camera"
        val urlRegex = Regex("\\[([^\\]]+)\\]\\(([^\\)]+)\\)")
        // Matches "### 1. Title" or "1. Title"
        val headingRegex = Regex("^#*\\s*\\d*\\.?\\s*(.+)$")

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue

            // Update title from headings
            if (trimmed.startsWith("#") || (trimmed.isNotEmpty() && trimmed[0].isDigit() && trimmed.contains("."))) {
                val headingMatch = headingRegex.find(trimmed)
                if (headingMatch != null) {
                    val potentialTitle = headingMatch.groupValues[1].trim()
                    // Avoid catching the repo name or generic sections as camera titles
                    if (potentialTitle != "Public MotionJPEG Sources" && 
                        potentialTitle != "MotionJPEG Links" && 
                        potentialTitle != "RTSP Links" &&
                        !potentialTitle.contains("Contributing") &&
                        !potentialTitle.contains("Disclaimer")) {
                        currentTitle = potentialTitle
                    }
                }
            }

            // Look for markdown links
            val matches = urlRegex.findAll(line)
            for (match in matches) {
                val linkText = match.groupValues[1].trim()
                val url = match.groupValues[2].trim()

                // Filter out non-stream URLs
                if (url.contains("auth/login") || url.contains("apple.com") || url.contains("play.google.com")) continue

                // Check for stream characteristics
                val isStream = url.startsWith("rtsp://") || (url.startsWith("http") && (
                    url.contains("mjpg", ignoreCase = true) ||
                    url.contains("mjpeg", ignoreCase = true) ||
                    url.contains("MotionJpeg", ignoreCase = true) ||
                    url.contains("cgi-bin", ignoreCase = true) ||
                    url.contains("video", ignoreCase = true) ||
                    url.contains(".jpg", ignoreCase = true) ||
                    url.contains(".jpeg", ignoreCase = true) ||
                    url.matches(Regex(".*\\d+\\.\\d+\\.\\d+\\.\\d+.*"))
                ))

                if (isStream) {
                    val protocol = when {
                        url.startsWith("rtsp://") -> "rtsp"
                        else -> "mjpeg"
                    }

                    sources.add(
                        StreamSource(
                            id = UUID.randomUUID().toString(),
                            url = url,
                            pageUrl = url,
                            streamUrl = url,
                            thumbnailUrl = if (protocol == "mjpeg") url else null,
                            title = if (linkText.isNotBlank() && !linkText.contains("Stream URL", ignoreCase = true)) linkText else currentTitle,
                            location = "GitHub Curated",
                            protocol = protocol
                        )
                    )
                }
            }
        }

        return sources.distinctBy { it.streamUrl }
    }
}
