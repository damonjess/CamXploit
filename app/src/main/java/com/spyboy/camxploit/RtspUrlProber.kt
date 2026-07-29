package com.spyboy.camxploit

import kotlinx.coroutines.*
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URI

class RtspUrlProber {

    /**
     * Probes all brand-specific RTSP URLs for a given IP in parallel.
     */
    suspend fun probe(ip: String, brand: CameraBrand): List<String> = withContext(Dispatchers.IO) {
        if (brand == CameraBrand.Generic) return@withContext emptyList()

        brand.rtspUrls.map { template ->
            async {
                val url = template.replace("{ip}", ip)
                if (isRtspEndpointValid(url)) url else null
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Checks if an RTSP endpoint is reachable and valid.
     * Returns true for 200 OK or 401 Unauthorized (which implies the path exists).
     */
    fun isRtspEndpointValid(url: String): Boolean {
        return try {
            val uri = URI.create(url)
            val host = uri.host ?: return false
            val port = if (uri.port == -1) 554 else uri.port
            
            Socket().use { socket ->
                socket.connect(InetSocketAddress(host, port), 2000)
                socket.soTimeout = 2000
                val writer = socket.getOutputStream().bufferedWriter()
                val reader = socket.getInputStream().bufferedReader()

                writer.write("DESCRIBE $url RTSP/1.0\r\n")
                writer.write("CSeq: 1\r\n")
                writer.write("User-Agent: CamXploit\r\n")
                writer.write("Accept: application/sdp\r\n")
                writer.write("\r\n")
                writer.flush()

                val response = reader.readLine() ?: ""
                // 200 OK = works. 401 Unauthorized = also works, just needs password
                response.contains("200") || response.contains("401")
            }
        } catch (_: Exception) { 
            false 
        }
    }
}
