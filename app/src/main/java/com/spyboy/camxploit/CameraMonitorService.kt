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
    private lateinit var discoveryCoordinator: DiscoveryCoordinator
    private val scanCache = mutableMapOf<String, Long>() // IP to last scan timestamp
    private val CACHE_EXPIRATION = 5 * 60 * 1000L // 5 minutes

    override fun onCreate() {
        super.onCreate()
        cameraDao = CameraDatabase.getDatabase(this).cameraDao()
        discoveryCoordinator = DiscoveryCoordinator(this)
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
            // Collect discovery results
            launch {
                discoveryCoordinator.discoveryFlow.collect { result ->
                    handleDiscoveryResult(result)
                }
            }

            while (isActive) {
                // 1. Check status of existing cameras
                val cameras = cameraDao.getCameraList()
                updateForegroundNotification(cameras.size)

                for (camera in cameras) {
                    val isReachable = try {
                        java.net.InetAddress.getByName(camera.ip).isReachable(1000)
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
                
                // 2. Start a new discovery cycle
                discoveryCoordinator.start()
                
                delay(5 * 60 * 1000) // Run a full scan every 5 minutes
            }
        }
    }

    private suspend fun handleDiscoveryResult(result: DiscoveryResult) {
        val now = System.currentTimeMillis()
        val lastScan = scanCache[result.ip] ?: 0L

        // Skip if recently scanned (deep probe avoidance as requested)
        if (now - lastScan < CACHE_EXPIRATION) return
        
        // Identify if it's a camera
        val isCam = result.source == "ONVIF" || 
                     result.source.startsWith("SSDP") || 
                     result.playableUrl != null ||
                     result.ssdpInfo?.friendlyName?.lowercase()?.contains("camera") == true ||
                     (result.device?.openPorts?.any { it in listOf(554, 8554, 8899, 37777, 34567) } == true)

        if (!isCam) return

        scanCache[result.ip] = now

        val mac = result.device?.mac ?: LanScanner(this).readArpTable()[result.ip] ?: "Unknown"
        val existingByMac = if (mac != "Unknown") cameraDao.getCameraByMac(mac) else null
        val existingByIp = cameraDao.getCameraByIp(result.ip)

        val existing = existingByMac ?: existingByIp

        if (existing == null) {
            // New camera discovered
            val nickname = result.ssdpInfo?.friendlyName ?: result.ssdpInfo?.modelName ?: "Auto ${result.device?.vendor ?: "Camera"}"
            val newCamera = SavedCamera(
                nickname = nickname,
                ip = result.ip,
                mac = mac,
                brand = result.device?.vendor ?: result.ssdpInfo?.manufacturer ?: "Unknown",
                streamUrl = result.playableUrl ?: "",
                isOnline = true,
                isAutoDiscovered = true,
                lastSeen = now
            )
            cameraDao.insertCamera(newCamera)
            sendNotification("New Camera Discovered", "Found ${newCamera.nickname} at ${newCamera.ip}")
        } else {
            // Update existing camera
            val updated = existing.copy(
                ip = result.ip, // Update IP in case it changed (if matched by MAC)
                mac = if (existing.mac == null || existing.mac == "Unknown") mac else existing.mac,
                isOnline = true,
                lastSeen = now,
                streamUrl = if (existing.streamUrl.isEmpty()) result.playableUrl ?: "" else existing.streamUrl
            )
            cameraDao.updateCamera(updated)
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
            .setContentTitle("CamXploit Monitor")
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
