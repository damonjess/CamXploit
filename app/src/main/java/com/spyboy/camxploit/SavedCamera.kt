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
    val remoteUrl: String? = null, // New field for internet access
    val mac: String? = null, // To uniquely identify even if IP changes
    val isAutoDiscovered: Boolean = false // To distinguish between manual and auto
) {
    fun toStreamSource(): StreamSource {
        val finalUrl = remoteUrl ?: streamUrl
        val protocol = streamType.lowercase()
        return StreamSource(
            url = finalUrl,
            title = nickname,
            location = ip,
            protocol = protocol,
            username = username,
            password = password,
            brand = brand
        )
    }
}
