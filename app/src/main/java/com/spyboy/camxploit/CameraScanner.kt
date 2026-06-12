package com.spyboy.camxploit

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL

data class EndpointResult(
    val url: String,
    val type: String, // "MJPEG_STREAM", "SNAPSHOT", "RTSP", "LOGIN_PAGE"
    val httpCode: Int
)

class CameraScanner {

    companion object {
        // Connection + read timeout per probe (ms) — CRITICAL: must be short
        private const val CONNECT_TIMEOUT_MS = 3_000
        private const val READ_TIMEOUT_MS    = 3_000

        // Max concurrent probes — prevents thread exhaustion on large lists
        private const val MAX_PARALLEL = 8
    }

    private val mjpegPaths = listOf(
        "/videostream.cgi",
        "/cgi-bin/mjpg/video.cgi",
        "/cgi-bin/viewer/video.jpg",
        "/control/faststream.jpg",
        "/api/video",
        "/onvif/Media",
        "/MJPEG.cgi",
        "/mjpg/video.mjpg",
        "/video/mjpg.cgi",
        "/stream",
        "/live/ch00_0",
        "/cam/realmonitor",
        "/h264Preview_01_main",
        "/Streaming/Channels/1",
        "/video1",
        "/live",
        "/live.sdp",
        "/CH001.sdp"
    )

    private val snapshotPaths = listOf(
        "/cgi-bin/snapshot.cgi",
        "/snapshot.jpg",
        "/ISAPI/Streaming/channels/101/picture",
        "/ISAPI/Streaming/channels/102/picture",
        "/onvif/snapshot",
        "/snap.jpg",
        "/tmpfs/snap.jpg",
        "/image/jpeg.cgi",
        "/cgi-bin/image.cgi",
        "/Streaming/channels/1/picture",
        "/onvif-http/snapshot",
        "/GetImage.cgi",
        "/capture",
        "/image"
    )

    private val loginPaths = listOf(
        "/",
        "/login",
        "/admin",
        "/web",
        "/doc/page/login.asp",
        "/index.html",
        "/view/viewer_index.shtml",
        "/ViewerFrame",
        "/admin/index.html",
        "/cgi-bin/admin/authLogin.cgi"
    )

    /**
     * Scans all known camera endpoints on the given host:port.
     * Uses bounded parallelism to avoid hanging on slow URLs.
     *
     * @param host      IP or hostname
     * @param port      HTTP port (default 80)
     * @param onResult  Called on Main thread for each found endpoint
     * @param onDone    Called on Main thread when scan is complete
     */
    suspend fun scanEndpoints(
        host: String,
        port: Int = 80,
        onResult: (EndpointResult) -> Unit,
        onDone: () -> Unit
    ) = withContext(Dispatchers.IO) {

        val base = "http://$host:$port"
        val semaphore = Semaphore(MAX_PARALLEL)

        val jobs = mutableListOf<Deferred<Unit>>()

        fun launchProbe(path: String, type: String) {
            jobs += async {
                semaphore.withPermit {
                    val result = probeUrl("$base$path", type)
                    if (result != null) {
                        withContext(Dispatchers.Main) { onResult(result) }
                    }
                }
            }
        }

        mjpegPaths.forEach   { launchProbe(it, "MJPEG_STREAM") }
        snapshotPaths.forEach { launchProbe(it, "SNAPSHOT") }
        loginPaths.forEach   { launchProbe(it, "LOGIN_PAGE") }

        jobs.awaitAll()

        withContext(Dispatchers.Main) { onDone() }
    }

    private fun probeUrl(urlStr: String, type: String): EndpointResult? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout    = CONNECT_TIMEOUT_MS
                readTimeout       = READ_TIMEOUT_MS
                requestMethod     = "GET"
                instanceFollowRedirects = false
                // Don't download the body — we only care about the status code
                setRequestProperty("Range", "bytes=0-0")
            }
            val code = conn.responseCode
            conn.disconnect()
            // Accept 200, 204, 206, 301, 302, 401, 403 — anything that proves
            // the server acknowledged the path. Exclude -1 (no response) and 404.
            if (code in 200..206 || code in 301..302 || code == 401 || code == 403) {
                EndpointResult(urlStr, type, code)
            } else null
        } catch (_: Exception) {
            null // timeout, refused, etc. — just skip
        }
    }
}
