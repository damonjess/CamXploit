package com.spyboy.camxploit

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import java.net.InetAddress

class CameraMonitorService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var monitorJob: Job? = null
    private lateinit var cameraDao: CameraDao

    override fun onCreate() {
        super.onCreate()
        cameraDao = CameraDatabase.getDatabase(this).cameraDao()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        isRunning = true
        startForeground(NOTIFICATION_ID, createNotification("Monitoring cameras..."))
        startMonitoring()
        return START_STICKY
    }

    private fun startMonitoring() {
        monitorJob?.cancel()
        monitorJob = serviceScope.launch {
            while (isActive) {
                val cameras = cameraDao.getCameraList()

                updateForegroundNotification(cameras.size)

                for (camera in cameras) {
                    val isReachable = try {
                        InetAddress.getByName(camera.ip).isReachable(1000)
                    } catch (e: Exception) {
                        false
                    }

                    if (camera.isOnline != isReachable) {
                        val status = if (isReachable) "back online" else "went offline"
                        sendNotification("Camera Status Change", "Camera ${camera.ip} (${camera.nickname}) $status")
                        
                        val updatedCamera = camera.copy(isOnline = isReachable, lastSeen = System.currentTimeMillis())
                        cameraDao.updateCamera(updatedCamera)
                    }
                }
                
                delay(5 * 60 * 1000) // 5 minutes
            }
        }
    }

    private fun updateForegroundNotification(count: Int) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification("Monitoring $count cameras"))
    }

    private fun sendNotification(title: String, message: String) {
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_chat)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(System.currentTimeMillis().toInt(), notification)
    }

    private fun createNotification(content: String): Notification {
        val pendingIntent: PendingIntent = Intent(this, MainActivity::class.java).let { notificationIntent ->
            PendingIntent.getActivity(this, 0, notificationIntent, PendingIntent.FLAG_IMMUTABLE)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CamVigil Monitor")
            .setContentText(content)
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Camera Monitor Service Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL_ID = "CameraMonitorServiceChannel"
        const val NOTIFICATION_ID = 2
        var isRunning = false
    }
}
