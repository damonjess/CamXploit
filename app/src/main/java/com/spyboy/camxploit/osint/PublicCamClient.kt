package com.spyboy.camxploit.osint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PublicCamClient {

    data class PublicCam(
        val name: String,
        val ip: String,
        val city: String?,
        val country: String?,
        val brand: String?,
        val port: Int
    )

    // Common dorks for ZoomEye to find cameras
    private val cameraDorks = listOf(
        "app:\"Hikvision-IP-Camera\"",
        "app:\"Dahua-DVR\"",
        "app:\"Axis-Camera\"",
        "\"index of\" / \"view/viewer_index.shtml\"",
        "intitle:\"live view\" intitle:axis",
        "inurl:\"/view.shtml\""
    )

    suspend fun fetchPublicCams(apiKey: String, page: Int = 1): List<PublicCam> = withContext(Dispatchers.IO) {
        // Pick a random dork or search for all? Let's search for a broad one
        val query = "app:\"camera\"" 
        val results = ZoomEyeClient.search(apiKey, query, page)
        
        results.map { host ->
            PublicCam(
                name = host.title ?: "Public Camera",
                ip = host.ip,
                city = host.city,
                country = host.country,
                brand = host.service ?: host.title,
                port = host.port
            )
        }
    }
}
