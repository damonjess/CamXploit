package com.spyboy.camxploit

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CameraDao {
    @Query("SELECT * FROM saved_cameras ORDER BY lastSeen DESC")
    fun getAllCameras(): Flow<List<SavedCamera>>

    @Query("SELECT * FROM saved_cameras")
    suspend fun getCameraList(): List<SavedCamera>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCamera(camera: SavedCamera)

    @Delete
    suspend fun deleteCamera(camera: SavedCamera)

    @Update
    suspend fun updateCamera(camera: SavedCamera)

    @Query("SELECT * FROM saved_cameras WHERE ip = :ip LIMIT 1")
    suspend fun getCameraByIp(ip: String): SavedCamera?

    @Query("SELECT * FROM saved_cameras WHERE mac = :mac LIMIT 1")
    suspend fun getCameraByMac(mac: String): SavedCamera?

    @Query("SELECT * FROM saved_cameras WHERE streamUrl = :url LIMIT 1")
    suspend fun getCameraByStreamUrl(url: String): SavedCamera?

    @Query("SELECT * FROM saved_cameras WHERE pageUrl = :pageUrl AND pageUrl != '' LIMIT 1")
    suspend fun getCameraByPageUrl(pageUrl: String): SavedCamera?

    @Query("DELETE FROM saved_cameras WHERE id = :id")
    suspend fun deleteCameraById(id: Int)

    @Query("UPDATE saved_cameras SET isFavorite = :favorite WHERE id = :id")
    suspend fun setFavorite(id: Int, favorite: Boolean)

    @Query("UPDATE saved_cameras SET nickname = :nickname WHERE id = :id")
    suspend fun renameCamera(id: Int, nickname: String)
}
