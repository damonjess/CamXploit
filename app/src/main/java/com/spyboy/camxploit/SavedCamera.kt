package com.spyboy.camxploit

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "saved_cameras")
data class SavedCamera(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val nickname: String,
    val ip: String,
    val port: Int = 80,
    val username: String = "admin",
    val password: String = "admin",
    val streamUrl: String = "",
    val streamType: String = "RTSP",
    val brand: String = "Unknown",
    val lastSeen: Long = System.currentTimeMillis(),
    val isOnline: Boolean = false,
    val remoteUrl: String? = null // New field for internet access
) {
    fun toStreamSource(): StreamSource {
        val finalUrl = remoteUrl ?: streamUrl
        return when (streamType.uppercase()) {
            "RTSP" -> StreamSource.Rtsp(finalUrl, username, password)
            "MJPEG" -> StreamSource.Mjpeg(finalUrl)
            "ONVIF" -> StreamSource.Onvif(finalUrl, "profile_1", username, password)
            else -> if (finalUrl.startsWith("rtsp://")) {
                StreamSource.Rtsp(finalUrl, username, password)
            } else {
                StreamSource.Mjpeg(finalUrl)
            }
        }
    }
}
