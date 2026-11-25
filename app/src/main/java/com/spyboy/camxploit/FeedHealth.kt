package com.spyboy.camxploit

import com.spyboy.camxploit.osint.SkylineResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.InetAddress
import java.net.URL

/** Result of checking a saved feed or LAN camera. */
data class FeedCheck(
    val online: Boolean,
    val verification: String,
    val contentType: String = "",
    val resolvedUrl: String = ""
)

/**
 * Checks a saved camera using the right method for its type:
 * - Skyline pages get a fresh HLS token before verification.
 * - Other HTTP feeds use StreamPreflight (HLS / MJPEG / snapshot).
 * - RTSP and LAN cameras use host reachability.
 */
object FeedHealth {

    suspend fun check(camera: SavedCamera): FeedCheck = withContext(Dispatchers.IO) {
        if (camera.pageUrl.contains("skylinewebcams.com", ignoreCase = true)) {
            val fresh = SkylineResolver.freshManifest(camera.pageUrl)
            if (!fresh.isNullOrBlank()) {
                val result = StreamPreflight.verifyAll(listOf(fresh)).firstOrNull()
                return@withContext FeedCheck(
                    online = result?.isPlayable == true,
                    verification = result?.label ?: "Unavailable",
                    contentType = result?.contentType.orEmpty(),
                    resolvedUrl = fresh
                )
            }
        }

        val url = camera.streamUrl.ifBlank { camera.remoteUrl.orEmpty() }
        if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
            val result = StreamPreflight.verifyAll(listOf(url)).firstOrNull()
            return@withContext FeedCheck(
                online = result?.isPlayable == true,
                verification = result?.label ?: "Unavailable",
                contentType = result?.contentType.orEmpty(),
                resolvedUrl = result?.url.orEmpty()
            )
        }

        val host = camera.ip.takeIf { it.isNotBlank() && !it.contains(" ") }
            ?: runCatching { URL(url).host }.getOrNull()
        val reachable = try {
            host != null && InetAddress.getByName(host).isReachable(1_000)
        } catch (_: Exception) {
            false
        }
        FeedCheck(
            online = reachable,
            verification = if (reachable) "Reachable" else "Unreachable",
            resolvedUrl = url
        )
    }
}
