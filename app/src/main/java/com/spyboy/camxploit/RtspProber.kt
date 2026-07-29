package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

class RtspProber {

    /**
     * Probes an RTSP URL by sending a DESCRIBE request.
     * Returns true if the server responds with 200 OK or 401 Unauthorized.
     */
    suspend fun probe(urlStr: String, timeoutMs: Int = 2000): Boolean = withContext(Dispatchers.IO) {
        try {
            // Parse URL (e.g., rtsp://192.168.1.100:554/path)
            val cleanUrl = urlStr.removePrefix("rtsp://")
            val hostPort = cleanUrl.substringBefore("/")
            
            val host = hostPort.substringBefore(":")
            val port = hostPort.substringAfter(":", "554").toIntOrNull() ?: 554

            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                socket.soTimeout = timeoutMs

                val request = "DESCRIBE $urlStr RTSP/1.0\r\n" +
                        "CSeq: 1\r\n" +
                        "User-Agent: CamXploit\r\n" +
                        "Accept: application/sdp\r\n" +
                        "\r\n"

                socket.getOutputStream().write(request.toByteArray())
                
                val response = ByteArray(1024)
                val read = socket.getInputStream().read(response)
                if (read > 0) {
                    val respStr = String(response, 0, read)
                    // 200 OK means path exists and is open
                    // 401 Unauthorized means path exists but needs auth
                    return@withContext respStr.contains("RTSP/1.0 200") || 
                                     respStr.contains("RTSP/1.0 401")
                }
            }
        } catch (_: Exception) {
            // Ignore connection errors
        }
        false
    }
}
