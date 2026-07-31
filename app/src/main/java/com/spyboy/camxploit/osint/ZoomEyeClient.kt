package com.spyboy.camxploit.osint

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ZoomEyeClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(12, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val BASE = "https://api.zoomeye.ai"

    data class Host(
        val ip: String,
        val port: Int,
        val service: String?,
        val title: String?,
        val banner: String?,
        val country: String?,
        val city: String?,
        val os: String?
    )

    data class UserInfo(
        val basicPoints: Int,
        val extraPoints: Int,
        val role: String?
    )

    suspend fun getUserInfo(apiKey: String): UserInfo = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("$BASE/v2/userinfo")
            .header("API-KEY", apiKey)
            .post("{}".toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(req).execute().use { res ->
            val body = res.body?.string() ?: throw Exception("Empty response")
            if (!res.isSuccessful) throw Exception("Failed to get balance: ${res.code}")
            
            val json = JSONObject(body)
            val data = json.optJSONObject("data")
            val sub = data?.optJSONObject("subscription")
            
            UserInfo(
                // Use optInt but handle string values just in case
                basicPoints = sub?.optString("points")?.toIntOrNull() ?: sub?.optInt("points", 0) ?: 0,
                extraPoints = sub?.optString("zoomeye_points")?.toIntOrNull() ?: sub?.optInt("zoomeye_points", 0) ?: 0,
                role = data?.optString("plan")
            )
        }
    }

    suspend fun search(apiKey: String, query: String, page: Int = 1): List<Host> = withContext(Dispatchers.IO) {
        try {
            val qBase64 = Base64.encodeToString(query.toByteArray(), Base64.NO_WRAP)
            val jsonBody = JSONObject().apply {
                put("qbase64", qBase64)
                put("page", page)
                put("pagesize", 10)
                put("sub_type", "v4") // Explicitly search for host assets
            }
            
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val req = Request.Builder()
                .url("$BASE/v2/search")
                .header("API-KEY", apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .header("User-Agent", "CamXploit/1.0 (Android; Mobile)")
                .post(jsonBody.toString().toRequestBody(mediaType))
                .build()

            client.newCall(req).execute().use { res ->
                val body = res.body?.string()
                if (!res.isSuccessful) {
                    val errorMsg = body?.let {
                        try { 
                            val j = JSONObject(it)
                            if (res.code == 402) "Insufficient credits. Check your ZoomEye account."
                            else j.optString("message", "API Error ${res.code}") 
                        }
                        catch (_: Exception) { "API Error ${res.code}" }
                    } ?: "HTTP Error ${res.code}"
                    throw Exception(errorMsg)
                }

                val json = JSONObject(body ?: return@use emptyList())
                val data = json.optJSONArray("data") ?: return@use emptyList()

                (0 until data.length()).mapNotNull { i ->
                    try {
                        val obj = data.getJSONObject(i)
                        val geo = obj.optJSONObject("geoinfo")
                        Host(
                            ip = obj.optString("ip", "unknown"),
                            port = obj.optInt("port", 0),
                            service = obj.optString("service")?.takeIf { it.isNotBlank() },
                            title = obj.optString("title")?.takeIf { it.isNotBlank() },
                            banner = obj.optString("banner")?.takeIf { it.isNotBlank() }?.take(200),
                            country = geo?.optString("country")?.takeIf { it.isNotBlank() },
                            city = geo?.optString("city")?.takeIf { it.isNotBlank() },
                            os = obj.optString("os").takeIf { it.isNotBlank() }
                        )
                    } catch (_: Exception) { null }
                }
            }
        } catch (e: Exception) {
            throw e
        }
    }
}
