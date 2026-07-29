package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

sealed class CameraBrand(
    val displayName: String,
    val rtspUrls: List<String>
) {
    object Hikvision : CameraBrand(
        "Hikvision",
        listOf(
            "rtsp://{ip}:554/Streaming/Channels/101",
            "rtsp://{ip}:554/Streaming/Channels/102"
        )
    )
    object Dahua : CameraBrand(
        "Dahua",
        listOf(
            "rtsp://{ip}:554/cam/realmonitor?channel=1&subtype=0",
            "rtsp://{ip}:554/cam/realmonitor?channel=1&subtype=1"
        )
    )
    object Axis : CameraBrand(
        "Axis",
        listOf("rtsp://{ip}:554/axis-media/media.amp")
    )
    object Generic : CameraBrand("Generic", emptyList())
}

class CameraFingerprinter {

    suspend fun identify(ip: String, openPorts: List<Int>): CameraBrand = withContext(Dispatchers.IO) {
        val httpPorts = openPorts.intersect(listOf(80, 81, 8080, 443))
        
        for (port in httpPorts) {
            try {
                val conn = URL("http://$ip:$port/").openConnection() as HttpURLConnection
                conn.connectTimeout = 1500
                conn.readTimeout = 1500
                conn.instanceFollowRedirects = true
                
                val server = conn.getHeaderField("Server") ?: ""
                val body = try {
                    conn.inputStream.bufferedReader().use { it.readText() }
                } catch (e: Exception) {
                    ""
                }
                val text = "$server $body".lowercase()

                when {
                    "hikvision" in text || "weblib" in text -> return@withContext CameraBrand.Hikvision
                    "dahua" in text || "dnvrs" in text -> return@withContext CameraBrand.Dahua
                    "axis" in text -> return@withContext CameraBrand.Axis
                }
            } catch (e: Exception) { /* ignore */ }
        }
        CameraBrand.Generic
    }
}
