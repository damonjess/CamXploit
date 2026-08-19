package com.spyboy.camxploit

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

/**
 * Data model representing a stream source.
 * Used by PublicCamsPanel and StreamViewerActivity.
 */
@Parcelize
data class StreamSource(
    val id: String = java.util.UUID.randomUUID().toString(),
    val url: String,
    val title: String = "Unknown Camera",
    val location: String = "Unknown",
    val thumbnailUrl: String? = null,
    val protocol: String = "http",   // http, rtsp, rtmp, mms, mjpeg
    val username: String? = null,
    val password: String? = null,
    val brand: String? = null,
    val pageUrl: String = "",
    val streamUrl: String = "",
    val sourceLabel: String = "",
    val verification: String = "Unchecked",
    val contentType: String = "",
    val verifiedAt: Long = 0L
) : Parcelable {

    fun getAuthenticatedUrl(): String {
        val u = if (streamUrl.isNotBlank()) streamUrl else url
        val user = username
        val pass = password
        
        if (user.isNullOrBlank() || pass.isNullOrBlank() || u.contains("@")) return u
        return try {
            if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@")
            else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@")
            else u
        } catch (e: Exception) {
            u
        }
    }

    fun bestPlaybackUrl(): String = getAuthenticatedUrl()
    fun bestThumbnailUrl(): String = thumbnailUrl ?: ""
}
