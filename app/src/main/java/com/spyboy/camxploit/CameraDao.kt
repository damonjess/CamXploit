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
}
