package com.spyboy.camxploit

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.net.HttpURLConnection
import java.net.URL

data class EndpointResult(
    val url: String,
    val type: String, // "MJPEG_STREAM", "SNAPSHOT", "RTSP", "LOGIN_PAGE"
    val httpCode: Int,
    val brand: String? = null
)

class CameraScanner {

    companion object {
        private const val CONNECT_TIMEOUT_MS = 2_500
        private const val READ_TIMEOUT_MS    = 2_500
        private const val MAX_PARALLEL = 12
    }

    private val commonMjpegPaths = listOf(
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
        "/CH001.sdp",
        "/video.mjpg",
        "/mjpg/1/video.mjpg",
        "/mjpeg/stream.cgi",
        "/cgi-bin/mjpg/video.cgi?channel=1",
        "/ISAPI/Streaming/channels/101/httpPreview",
        "/video1s1.mjpg"
    )

    private val commonSnapshotPaths = listOf(
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
        "/image",
        "/jpg/image.jpg",
        "/cgi-bin/api.cgi?cmd=Snap&channel=0",
        "/onvif/device_service",
        "/cgi-bin/snapshot.cgi?channel=1",
        "/doc/page/login.asp"
    )

    private val commonRtspPaths = listOf(
        "/Streaming/Channels/101",
        "/cam/realmonitor?channel=1&subtype=0",
        "/h264Preview_01_main",
        "/axis-media/media.amp",
        "/stream1",
        "/stream2",
        "/live0",
        "/videoMain",
        "/live/ch0",
        "/mpeg4/ch1/main/av_stream",
        "/live/main",
        "/Streaming/Channels/1",
        "/unicast",
        "/video1",
        "/medias2",
        "/onvif/Media",
        "/live/ch00_0",
        "/ch0.264"
    )

    private val vendorPaths = mapOf(
        "hikvision" to listOf(
            "/ISAPI/Streaming/channels/101/picture" to "SNAPSHOT",
            "/ISAPI/Streaming/channels/101/httpPreview" to "MJPEG_STREAM",
            "/Streaming/Channels/101" to "RTSP"
        ),
        "dahua" to listOf(
            "/cgi-bin/snapshot.cgi" to "SNAPSHOT",
            "/cgi-bin/mjpg/video.cgi" to "MJPEG_STREAM",
            "/cam/realmonitor?channel=1&subtype=0" to "RTSP"
        ),
        "axis" to listOf(
            "/jpg/image.jpg" to "SNAPSHOT",
            "/axis-media/media.amp?videocodec=mjpeg" to "MJPEG_STREAM",
            "/axis-media/media.amp" to "RTSP"
        ),
        "reolink" to listOf(
            "/cgi-bin/api.cgi?cmd=Snap&channel=0" to "SNAPSHOT",
            "/video1s1.mjpg" to "MJPEG_STREAM",
            "/h264Preview_01_main" to "RTSP"
        ),
        "tp-link" to listOf(
            "/snapshot.jpg" to "SNAPSHOT",
            "/stream1" to "RTSP"
        )
    )

    suspend fun scanEndpoints(
        host: String,
        port: Int = 80,
        vendor: String? = null,
        onResult: (EndpointResult) -> Unit,
        onDone: () -> Unit
    ) = withContext(Dispatchers.IO) {

        val baseHttp = "http://$host:$port"
        val semaphore = Semaphore(MAX_PARALLEL)
        val jobs = mutableListOf<Deferred<Unit>>()
        
        val visited = mutableSetOf<String>()

        fun launchProbe(path: String, type: String) {
            val fullUrl = if (type == "RTSP") "rtsp://$host:554$path" else "$baseHttp$path"
            if (visited.contains(fullUrl)) return
            visited.add(fullUrl)

            jobs += async {
                semaphore.withPermit {
                    if (type == "RTSP") {
                        // RTSP prober — simple check if port 554 is open and path is plausible
                        // In a real app, we'd send an OPTIONS request, but here we just
                        // report it if we're scanning a camera.
                        // For now, we only probe HTTP. RTSP paths are "discovered" by being suggested.
                        // But we can check if port 554 is open.
                        if (isPortOpen(host, 554)) {
                            withContext(Dispatchers.Main) { 
                                onResult(EndpointResult(fullUrl, type, 200)) 
                            }
                        }
                    } else {
                        val result = probeUrl(fullUrl, type)
                        if (result != null) {
                            withContext(Dispatchers.Main) { onResult(result) }
                        }
                    }
                }
            }
        }

        // 1. Prioritize vendor-specific paths
        vendor?.lowercase()?.let { v ->
            vendorPaths.entries.firstOrNull { v.contains(it.key) }?.value?.forEach { (path, type) ->
                launchProbe(path, type)
            }
        }

        // 2. Launch common probes
        commonMjpegPaths.forEach { launchProbe(it, "MJPEG_STREAM") }
        commonSnapshotPaths.forEach { launchProbe(it, "SNAPSHOT") }
        commonRtspPaths.forEach { launchProbe(it, "RTSP") }

        jobs.awaitAll()
        withContext(Dispatchers.Main) { onDone() }
    }

    private fun isPortOpen(host: String, port: Int): Boolean {
        return try {
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), 1000)
                true
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun probeUrl(urlStr: String, type: String): EndpointResult? {
        return try {
            val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                connectTimeout    = CONNECT_TIMEOUT_MS
                readTimeout       = READ_TIMEOUT_MS
                requestMethod     = "GET"
                instanceFollowRedirects = false
                setRequestProperty("Range", "bytes=0-0")
            }
            val code = conn.responseCode
            val contentType = conn.contentType?.lowercase() ?: ""
            
            val headers = conn.headerFields.entries.joinToString("\n") { (k, v) -> "$k: ${v.joinToString(",")}" }
            val body = if (code == 200) {
                try { conn.inputStream.bufferedReader().use { it.readText() } } catch (_: Exception) { "" }
            } else ""

            conn.disconnect()

            // Heuristics to confirm it's a camera
            val brand = HttpFingerprinter().match(headers, body)

            val isLikelyMedia = contentType.contains("image") || 
                               contentType.contains("video") || 
                               contentType.contains("multipart/x-mixed-replace") ||
                               code == 401 || code == 403 // Auth required is a good sign for cameras

            if (code in 200..206 || code == 401 || code == 403) {
                if (isLikelyMedia || type == "LOGIN_PAGE") {
                    EndpointResult(urlStr, type, code, brand)
                } else null
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
