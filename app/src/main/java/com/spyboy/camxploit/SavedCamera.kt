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
    val isOnline: Boolean = false
)
