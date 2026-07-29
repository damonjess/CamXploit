package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

class HttpFingerprinter {

    companion object {
        private const val TIMEOUT_MS = 2000
    }

    fun match(headers: String, body: String): String? {
        val combinedContext = "$headers\n$body"
        CameraFingerprint.all.forEach { fingerprint ->
            if (fingerprint.matchers.any { it.containsMatchIn(combinedContext) }) {
                return fingerprint.brandName
            }
        }
        return null
    }

    suspend fun identify(host: String, port: Int): String? = withContext(Dispatchers.IO) {
        val pathsToTry = mutableSetOf<String>()
        CameraFingerprint.all.forEach { pathsToTry.addAll(it.paths) }
        
        // Use a set to avoid duplicate requests, but ensure "/" is first
        val sortedPaths = pathsToTry.toList().sortedBy { if (it == "/") 0 else 1 }

        for (path in sortedPaths) {
            val brand = probePath(host, port, path)
            if (brand != null) return@withContext brand
        }
        null
    }

    private fun probePath(host: String, port: Int, path: String): String? {
        return try {
            val url = URL("http://$host:$port$path")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                requestMethod = "GET"
                instanceFollowRedirects = false
            }

            val responseCode = conn.responseCode
            val headers = conn.headerFields.entries.joinToString("\n") { (k, v) -> "$k: ${v.joinToString(",")}" }
            
            val body = if (responseCode == 200) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                ""
            }
            conn.disconnect()

            return match(headers, body)
        } catch (_: Exception) {
            null
        }
    }
}
