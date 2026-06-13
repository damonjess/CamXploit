package com.spyboy.camxploit

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sentinel_detections")
data class SentinelDetection(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val cameraIp: String,
    val label: String,
    val confidence: Float,
    val timestamp: Long = System.currentTimeMillis(),
    val frameIndex: Int = 0
)

@Dao
interface SentinelDao {
    @Insert
    suspend fun insert(detection: SentinelDetection)

    @Query("SELECT * FROM sentinel_detections ORDER BY timestamp DESC LIMIT 200")
    fun getAll(): Flow<List<SentinelDetection>>

    @Query("DELETE FROM sentinel_detections")
    suspend fun clearAll()

    @Query("SELECT * FROM sentinel_detections WHERE cameraIp = :ip ORDER BY timestamp DESC LIMIT 100")
    fun getByCamera(ip: String): Flow<List<SentinelDetection>>
}
