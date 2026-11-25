package com.spyboy.camxploit

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject

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
    val isAutoDiscovered: Boolean = false, // To distinguish between manual and auto
    // Feed-library metadata. Browser-saved and public feeds set isFeed = true;
    // LAN discoveries keep it false so the library can filter the two groups.
    @ColumnInfo(defaultValue = "0") val isFeed: Boolean = false,
    @ColumnInfo(defaultValue = "''") val pageUrl: String = "",
    @ColumnInfo(defaultValue = "''") val sourceLabel: String = "",
    val thumbnailUrl: String? = null,
    val thumbnailPath: String? = null,
    @ColumnInfo(defaultValue = "''") val verification: String = "",
    @ColumnInfo(defaultValue = "''") val contentType: String = "",
    @ColumnInfo(defaultValue = "0") val lastVerified: Long = 0L,
    @ColumnInfo(defaultValue = "0") val isFavorite: Boolean = false,
    @ColumnInfo(defaultValue = "''") val candidatesJson: String = ""
) {
    fun toStreamSource(): StreamSource {
        val finalUrl = streamUrl.ifBlank { remoteUrl.orEmpty() }.ifBlank { pageUrl }
        val protocol = streamType.lowercase()
        return StreamSource(
            url = finalUrl,
            title = nickname,
            location = ip,
            thumbnailUrl = thumbnailUrl,
            protocol = protocol,
            username = username,
            password = password,
            brand = brand,
            pageUrl = pageUrl,
            streamUrl = streamUrl,
            candidateUrls = decodeCandidates(candidatesJson).map { it.url },
            streamCandidates = decodeCandidates(candidatesJson),
            sourceLabel = sourceLabel,
            verification = verification.ifBlank { "Unchecked" },
            contentType = contentType,
            verifiedAt = lastVerified
        )
    }
}

private fun decodeCandidates(json: String): List<StreamCandidate> {
    if (json.isBlank()) return emptyList()
    return runCatching {
        val array = JSONArray(json)
        (0 until array.length()).mapNotNull { index ->
            val obj = array.optJSONObject(index) ?: return@mapNotNull null
            val url = obj.optString("url").trim()
            if (url.isBlank()) null else StreamCandidate(
                url = url,
                protocol = obj.optString("protocol"),
                contentType = obj.optString("contentType"),
                verification = obj.optString("verification"),
                title = obj.optString("title"),
                pageUrl = obj.optString("pageUrl")
            )
        }
    }.getOrDefault(emptyList())
}
