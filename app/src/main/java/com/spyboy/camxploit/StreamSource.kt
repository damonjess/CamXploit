package com.spyboy.camxploit

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

sealed interface StreamSource : Parcelable {
    val url: String

    fun getAuthenticatedUrl(): String {
        val u = url
        val user = when (this) {
            is Rtsp -> username
            is Onvif -> username
            else -> null
        }
        val pass = when (this) {
            is Rtsp -> password
            is Onvif -> password
            else -> null
        }
        
        if (user.isNullOrBlank() || pass.isNullOrBlank() || u.contains("@")) return u
        return try {
            if (u.startsWith("rtsp://")) u.replace("rtsp://", "rtsp://$user:$pass@")
            else if (u.startsWith("http://")) u.replace("http://", "http://$user:$pass@")
            else u
        } catch (e: Exception) {
            u
        }
    }

    @Parcelize
    data class Mjpeg(override val url: String) : StreamSource

    @Parcelize
    data class Rtsp(
        override val url: String,
        val username: String? = null,
        val password: String? = null
    ) : StreamSource

    @Parcelize
    data class Onvif(
        override val url: String,
        val profileToken: String,
        val username: String? = null,
        val password: String? = null
    ) : StreamSource
}
