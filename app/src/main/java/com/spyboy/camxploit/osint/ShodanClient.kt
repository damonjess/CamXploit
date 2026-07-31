package com.spyboy.camxploit.osint

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ShodanClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(12, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://api.shodan.io"

    data class ShodanHost(
        val ip: String,
        val org: String?,
        val isp: String?,
        val country: String?,
        val city: String?,
        val lastUpdate: String?,
        val ports: List<Int>,
        val banners: List<Banner>,
        val vulns: List<String>,
        val tags: List<String>,
        val os: String?
    )

    data class Banner(
        val port: Int,
        val product: String?,
        val version: String?,
        val data: String?
    )

    suspend fun search(apiKey: String, query: String): List<ShodanHost> = withContext(Dispatchers.IO) {
        val url = "$BASE/shodan/host/search?key=$apiKey&query=${java.net.URLEncoder.encode(query, "UTF-8")}&limit=20"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: return@use emptyList()
            val json = JSONObject(body)
            val matches = json.optJSONArray("matches") ?: return@use emptyList()
            parseMatches(matches)
        }
    }

    suspend fun lookupIp(apiKey: String, ip: String): ShodanHost? = withContext(Dispatchers.IO) {
        val url = "$BASE/shodan/host/$ip?key=$apiKey"
        val req = Request.Builder().url(url).build()
        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: return@use null
            parseHost(JSONObject(body))
        }
    }

    private fun parseMatches(arr: JSONArray): List<ShodanHost> {
        return (0 until arr.length()).mapNotNull { i ->
            try { parseHost(arr.getJSONObject(i)) } catch (_: Exception) { null }
        }
    }

    private fun parseHost(j: JSONObject): ShodanHost {
        val banners = mutableListOf<Banner>()
        val ports = mutableListOf<Int>()
        val dataArr = j.optJSONArray("data")
        if (dataArr != null) {
            for (i in 0 until dataArr.length()) {
                val d = dataArr.getJSONObject(i)
                val p = d.optInt("port", 0)
                if (p > 0) ports += p
                banners += Banner(
                    port = p,
                    product = d.optString("product").takeIf { it.isNotBlank() },
                    version = d.optString("version").takeIf { it.isNotBlank() },
                    data = d.optString("data").takeIf { it.isNotBlank() }?.take(280)
                )
            }
        }

        val vulns = mutableListOf<String>()
        j.optJSONObject("vulns")?.keys()?.forEach { vulns += it }

        return ShodanHost(
            ip = j.optString("ip_str", j.optString("ip", "unknown")),
            org = j.optString("org").takeIf { it.isNotBlank() },
            isp = j.optString("isp").takeIf { it.isNotBlank() },
            country = j.optJSONObject("location")?.optString("country_name")?.takeIf { it.isNotBlank() },
            city = j.optJSONObject("location")?.optString("city")?.takeIf { it.isNotBlank() },
            lastUpdate = j.optString("last_update").takeIf { it.isNotBlank() },
            ports = ports.distinct().sorted(),
            banners = banners,
            vulns = vulns,
            tags = j.optJSONArray("tags")?.let { arr -> (0 until arr.length()).map { arr.getString(it) } } ?: emptyList(),
            os = j.optString("os").takeIf { it.isNotBlank() }
        )
    }

    // ─── Zero-API fallback: Google dork generator ────────────────────
    fun generateDork(query: String): String {
        val q = query.trim().lowercase()
        return when {
            q == "hikvision" -> "inurl:\"doc/page/login.asp\" Hikvision"
            q == "dahua" -> "inurl:\"cgi-bin/login.cgi\" Dahua"
            q == "axis" -> "inurl:\"view/viewer_index.shtml\" Axis"
            q == "exposed rtsp" -> "intitle:\"live view\" intitle:axis"
            q.contains("webcam") -> "inurl:/view.shtml OR inurl:/live.htm"
            q.contains("router") -> "intitle:\"router\" inurl:admin login"
            q.contains("ftp") -> "intitle:\"index of\" \"ftp\""
            else -> "intitle:\"$q\" OR inurl:\"$q\""
        }
    }
}
