package com.spyboy.camxploit.osint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object CensysClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private const val BASE_URL = "https://search.censys.io/api/v2/hosts/search"

    data class Host(
        val ip: String,
        val services: List<Service>,
        val location: String?,
        val autonomousSystem: String?
    )

    data class Service(
        val port: Int,
        val serviceName: String?,
        val banner: String?
    )

    suspend fun search(apiId: String, apiSecret: String, query: String): List<Host> = withContext(Dispatchers.IO) {
        val credential = Credentials.basic(apiId, apiSecret)
        val url = "$BASE_URL?q=${java.net.URLEncoder.encode(query, "UTF-8")}&per_page=20"

        val request = Request.Builder()
            .url(url)
            .header("Authorization", credential)
            .header("Accept", "application/json")
            .build()

        client.newCall(request).execute().use { response ->
            val body = response.body?.string() ?: throw Exception("Empty response")
            if (!response.isSuccessful) {
                val errorMsg = try {
                    JSONObject(body).optString("error", "API Error ${response.code}")
                } catch (_: Exception) {
                    "HTTP Error ${response.code}"
                }
                throw Exception(errorMsg)
            }

            val json = JSONObject(body)
            val results = json.optJSONObject("result")?.optJSONArray("hits") ?: return@use emptyList()

            (0 until results.length()).map { i ->
                val hit = results.getJSONObject(i)
                val servicesArr = hit.optJSONArray("services")
                val services = mutableListOf<Service>()
                if (servicesArr != null) {
                    for (j in 0 until servicesArr.length()) {
                        val s = servicesArr.getJSONObject(j)
                        services.add(
                            Service(
                                port = s.optInt("port"),
                                serviceName = s.optString("service_name"),
                                banner = s.optString("banner_hex") // Or banner if available
                            )
                        )
                    }
                }

                val locationObj = hit.optJSONObject("location")
                val locStr = listOfNotNull(
                    locationObj?.optString("city"),
                    locationObj?.optString("country")
                ).joinToString(", ").takeIf { it.isNotBlank() }

                Host(
                    ip = hit.optString("ip"),
                    services = services,
                    location = locStr,
                    autonomousSystem = hit.optJSONObject("autonomous_system")?.optString("name")
                )
            }
        }
    }
}
