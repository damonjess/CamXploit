package com.spyboy.camxploit

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a stream source.
 * Used by PublicCamsPanel and StreamViewerActivity.
 */
@Parcelize
data class StreamSource(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Unknown Camera",
    val location: String = "Unknown",
    val thumbnailUrl: String? = null,
    val protocol: String = "http",   // http, rtsp, rtmp, mms, mjpeg
    val username: String? = null,
    val password: String? = null,
    val brand: String? = null,
    val pageUrl: String = "",
    val streamUrl: String = "",
    val candidateUrls: List<String> = emptyList(),
    val sourceLabel: String = "",
    val verification: String = "Unchecked",
    val contentType: String = "",
    val verifiedAt: Long = 0L
) : Parcelable {

    fun getAuthenticatedUrl(): String {
        val u = if (streamUrl.isNotBlank()) streamUrl else url
        val user = username
        val pass = password

        if (user.isNullOrBlank() || pass.isNullOrBlank() || u.contains("@")) return u
        return try {
            if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@")
            else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@")
            else u
        } catch (e: Exception) {
            u
        }
    }

    fun bestPlaybackUrl(): String = getAuthenticatedUrl()
    fun bestThumbnailUrl(): String = thumbnailUrl ?: ""
}

/**
 * Picks the playback protocol for a scanned URL.
 *
 * Snapshot endpoints must never be treated as MJPEG: the grabber would wait for
 * multipart frames that never arrive and the viewer would spin forever. Plain
 * HTML/root pages are marked "web" so the viewer probes and then renders them in
 * the WebView instead of starting a frame grabber.
 */
fun protocolForUrl(url: String): String {
    val lower = url.trim().lowercase()
    if (lower.isEmpty()) return "mjpeg"

    return when {
        lower.startsWith("rtsp://") -> "rtsp"
        lower.startsWith("rtmp://") || lower.startsWith("mms://") -> "rtmp"
        isSnapshotUrl(url) -> "snapshot"
        isMjpegUrl(url) -> "mjpeg"
        lower.startsWith("http://") || lower.startsWith("https://") -> {
            if (looksLikeWebPage(lower)) "web" else "mjpeg"
        }
        else -> "web"
    }
}

/** True for single-JPEG endpoints such as /snapshot.jpg or Hikvision's .../picture. */
fun isSnapshotUrl(url: String): Boolean {
    val lower = url.lowercase()
    val path = lower.substringBefore('?').substringBefore('#')

    // Stream markers win over an image-looking suffix: many cameras expose
    // multipart MJPEG at endpoints such as /cgi-bin/faststream.jpg?stream=half.
    if (isMjpegUrl(url)) return false
    if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".bmp")
    ) return true

    if (lower.contains("snapshot") || lower.contains("action=snapshot")) return true
    if (lower.contains("snap.jpg") || lower.contains("current.jpg") || lower.contains("still.jpg")) return true
    if (lower.contains("/image.jpg") || lower.contains("/jpg/image") || lower.contains("axis-cgi/jpg")) return true
    if (lower.contains("onvif-http/snapshot") || lower.contains("onvif/snapshot")) return true
    if (path.endsWith("/picture") || lower.contains("/picture?")) return true
    if (path.contains("/cgi-bin/") &&
        (lower.contains("snap") || lower.contains("image") || lower.contains("jpg"))
    ) return true

    return false
}

/** True for URLs whose path/extension/query asks for a multipart MJPEG feed. */
private fun isMjpegUrl(url: String): Boolean {
    val lower = url.lowercase()

    // Definitive stream markers win over a page-like extension: many cameras use
    // endpoints such as /view.php?action=stream for their multipart feed.
    if (lower.contains("mjpg") ||
        lower.contains("mjpeg") ||
        lower.contains("action=stream") ||
        lower.contains("videostream") ||
        lower.contains("video.cgi") ||
        lower.contains("nphmotionjpeg") ||
        lower.contains("axis-cgi/mjpg") ||
        lower.contains("faststream") ||
        lower.contains("getvideo")
    ) return true

    val path = lower.substringBefore('?').substringBefore('#')
    if (path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".php") ||
        path.endsWith(".asp") || path.endsWith(".aspx") || path.endsWith(".jsp")
    ) return false
    // A plain image suffix is a snapshot unless one of the definitive stream
    // markers above matched it.
    if (path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") ||
        path.endsWith(".bmp")
    ) return false

    return lower.contains("/video") || lower.contains("/stream") || lower.contains("?stream")
}

private fun looksLikeWebPage(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#')
    if (path.endsWith(".html") || path.endsWith(".htm") || path.endsWith(".php") ||
        path.endsWith(".asp") || path.endsWith(".aspx") || path.endsWith(".jsp") ||
        path.endsWith(".cgi") || path.endsWith("/")
    ) return true

    val afterScheme = path.substringAfter("://", "")
    return !afterScheme.contains('/')
}
