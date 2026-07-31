package com.spyboy.camxploit.osint

object InsecamClient {
    data class PublicCamera(
        val id: String,
        val imageUrl: String?,
        val ip: String?,
        val location: String?,
        val countryCode: String?
    )
}
