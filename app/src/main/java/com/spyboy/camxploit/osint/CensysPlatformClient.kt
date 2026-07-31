package com.spyboy.camxploit.osint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Censys Platform API v3 client.
 * Uses Bearer token auth (Personal Access Token).
 * Uses the Global Search Query endpoint (POST /global/search/query).
 */
class CensysPlatformClient(
    private val apiToken: String,
    private val baseUrl: String = "https://api.platform.censys.io/v3"
) {
    suspend fun searchHosts(
        query: String,
        perPage: Int = 50,
        cursor: String? = null
    ): CensysSearchResult = withContext(Dispatchers.IO) {
        val url = URL("$baseUrl/global/search/query")
        val connection = url.openConnection() as HttpURLConnection
        
        val bodyJson = JSONObject().apply {
            put("query", query)
            put("per_page", perPage) // v3 uses per_page or page_size depending on exact sub-api
            cursor?.let { put("page_token", it) }
        }

        connection.apply {
            requestMethod = "POST"
            setRequestProperty("Authorization", "Bearer $apiToken")
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15000
            readTimeout = 15000
            doOutput = true
            doInput = true
        }

        try {
            connection.outputStream.use { it.write(bodyJson.toString().toByteArray()) }

            val code = connection.responseCode
            if (code != 200) {
                val err = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
                throw Exception("Censys API error ($code): $err")
            }

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parseSearchResult(JSONObject(body))
        } finally {
            connection.disconnect()
        }
    }

    private fun parseSearchResult(json: JSONObject): CensysSearchResult {
        val resultObj = json.optJSONObject("result") ?: JSONObject()
        val hitsArray = resultObj.optJSONArray("hits") ?: org.json.JSONArray()
        val hits = mutableListOf<CensysHost>()

        for (i in 0 until hitsArray.length()) {
            val hit = hitsArray.getJSONObject(i)
            val ip = hit.optString("ip", "")

            val servicesArray = hit.optJSONArray("services") ?: org.json.JSONArray()
            var title = "Unknown Camera"
            var location = "Unknown"
            var port = 80
            var serviceName = "http"

            for (j in 0 until servicesArray.length()) {
                val svc = servicesArray.getJSONObject(j)
                val svcPort = svc.optInt("port", 0)
                val svcName = svc.optString("service_name", "")

                // v3 might have different nesting, but often keeps 'http' object if requested or returned
                val httpObj = svc.optJSONObject("http")
                if (httpObj != null) {
                    val responseObj = httpObj.optJSONObject("response")
                    if (responseObj != null) {
                        val htmlTitle = responseObj.optString("html_title", "")
                        if (htmlTitle.isNotBlank() && title == "Unknown Camera") {
                            title = htmlTitle
                        }
                    }
                }

                if (svcPort > 0 && port == 80) port = svcPort
                if (svcName.isNotBlank()) serviceName = svcName
            }

            val locObj = hit.optJSONObject("location")
            if (locObj != null) {
                val country = locObj.optString("country", "")
                val city = locObj.optString("city", "")
                location = when {
                    city.isNotBlank() && country.isNotBlank() -> "$city, $country"
                    country.isNotBlank() -> country
                    city.isNotBlank() -> city
                    else -> "Unknown"
                }
            }

            hits.add(CensysHost(ip, port, serviceName, title, location))
        }

        val nextCursor = resultObj.optString("next_page_token", "").ifBlank { null }

        return CensysSearchResult(
            query = "", // v3 doesn't return echo query in result usually
            total = resultObj.optInt("total_hits", 0),
            hits = hits,
            nextCursor = nextCursor
        )
    }
}

data class CensysHost(
    val ip: String,
    val port: Int,
    val serviceName: String,
    val title: String,
    val location: String
) {
    fun toStreamSource(): com.spyboy.camxploit.StreamSource {
        val protocol = when {
            port == 443 || serviceName.contains("https", ignoreCase = true) -> "https"
            else -> "http"
        }
        val url = "$protocol://$ip:$port"
        return com.spyboy.camxploit.StreamSource(
            url = url,
            thumbnailUrl = "",
            title = title.ifBlank { "Camera at $ip" },
            location = location,
            protocol = protocol
        )
    }
}

data class CensysSearchResult(
    val query: String,
    val total: Int,
    val hits: List<CensysHost>,
    val nextCursor: String?
)
