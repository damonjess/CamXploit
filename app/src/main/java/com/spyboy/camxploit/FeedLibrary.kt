package com.spyboy.camxploit

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * Single write path for feeds discovered in the browser or public-camera panels.
 * Existing rows are updated by stream URL instead of duplicated, so saving the
 * same camera twice refreshes its verification and thumbnail.
 */
object FeedLibrary {

    suspend fun save(
        context: Context,
        source: StreamSource,
        frame: Bitmap? = null
    ): SavedCamera = withContext(Dispatchers.IO) {
        val dao = CameraDatabase.getDatabase(context).cameraDao()
        val url = source.streamUrl.ifBlank { source.url }.trim()
        require(url.isNotBlank()) { "Feed has no URL" }

        val existing = dao.getCameraByStreamUrl(url)
            ?: source.pageUrl.takeIf { it.contains("skylinewebcams.com", true) }
                ?.let { dao.getCameraByPageUrl(it) }
        val now = System.currentTimeMillis()
        val title = source.title.ifBlank { existing?.nickname ?: "Saved feed" }
        val thumbnailPath = frame?.let { persistThumbnail(context, url, it) }
            ?: existing?.thumbnailPath
        val sourceLabel = source.sourceLabel.ifBlank {
            existing?.sourceLabel ?: if (source.pageUrl.isBlank()) "Public feed" else "Browser feed"
        }
        val protocol = source.protocol.ifBlank {
            existing?.streamType ?: "http"
        }.uppercase(Locale.US)
        val location = source.location.takeUnless { it.isBlank() || it.equals("Unknown", true) }
            ?: existing?.ip
            ?: source.pageUrl.substringAfter("://", "").substringBefore('/')
        val verified = source.verifiedAt.takeIf { it > 0 } ?: now
        val online = source.verification.contains("verified", true) ||
            source.verification.contains("snapshot", true) ||
            source.verification.contains("live", true)

        val camera = SavedCamera(
            id = existing?.id ?: 0,
            nickname = title,
            ip = location,
            streamUrl = url,
            streamType = protocol,
            brand = source.brand ?: existing?.brand ?: sourceLabel,
            lastSeen = now,
            isOnline = online,
            remoteUrl = existing?.remoteUrl,
            mac = existing?.mac,
            isAutoDiscovered = existing?.isAutoDiscovered ?: false,
            isFeed = true,
            pageUrl = source.pageUrl.ifBlank { existing?.pageUrl.orEmpty() },
            sourceLabel = sourceLabel,
            thumbnailUrl = source.thumbnailUrl ?: existing?.thumbnailUrl,
            thumbnailPath = thumbnailPath,
            verification = source.verification.ifBlank { existing?.verification ?: "Unchecked" },
            contentType = source.contentType.ifBlank { existing?.contentType.orEmpty() },
            lastVerified = verified,
            isFavorite = existing?.isFavorite ?: false,
            candidatesJson = encodeCandidates(source, existing)
        )

        if (existing == null) {
            dao.insertCamera(camera)
        } else {
            dao.updateCamera(camera)
        }
        camera
    }

    suspend fun delete(context: Context, camera: SavedCamera) = withContext(Dispatchers.IO) {
        val dao = CameraDatabase.getDatabase(context).cameraDao()
        dao.deleteCameraById(camera.id)
        camera.thumbnailPath?.let { runCatching { File(it).delete() } }
    }

    private fun persistThumbnail(context: Context, url: String, bitmap: Bitmap): String? {
        return runCatching {
            val dir = File(context.filesDir, "feed_thumbnails").apply { mkdirs() }
            val file = File(dir, Integer.toHexString(url.hashCode()) + ".jpg")
            val maxWidth = 480
            val scaled = if (bitmap.width > maxWidth) {
                val height = (bitmap.height * (maxWidth.toFloat() / bitmap.width)).toInt()
                Bitmap.createScaledBitmap(bitmap, maxWidth, height.coerceAtLeast(1), true)
            } else {
                bitmap
            }
            FileOutputStream(file).use { out ->
                scaled.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            file.absolutePath
        }.getOrNull()
    }

    private fun encodeCandidates(source: StreamSource, existing: SavedCamera?): String {
        val candidates = source.streamCandidates.ifEmpty {
            source.candidateUrls.map { StreamCandidate(url = it) }
        }.ifEmpty {
            existing?.toStreamSource()?.streamCandidates.orEmpty()
        }
        if (candidates.isEmpty()) return existing?.candidatesJson.orEmpty()
        val array = JSONArray()
        candidates.distinctBy { it.url }.forEach { candidate ->
            array.put(JSONObject().apply {
                put("url", candidate.url)
                put("protocol", candidate.protocol)
                put("contentType", candidate.contentType)
                put("verification", candidate.verification)
                put("title", candidate.title)
                put("pageUrl", candidate.pageUrl)
            })
        }
        return array.toString()
    }
}
