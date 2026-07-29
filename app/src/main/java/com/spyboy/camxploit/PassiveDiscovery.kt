package com.spyboy.camxploit

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class PassiveDiscovery(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager

    /**
     * Listens for SSDP (UPnP) NOTIFY packets on UDP 1900.
     * This is passive; it waits for cameras to announce themselves.
     */
    fun listenSSDP() = callbackFlow {
        val socket = try {
            DatagramSocket(1900).apply {
                soTimeout = 0 // Infinite wait
                reuseAddress = true
            }
        } catch (e: Exception) {
            close(e)
            return@callbackFlow
        }

        val job = CoroutineScope(Dispatchers.IO).launch {
            val buffer = ByteArray(2048)
            while (isActive) {
                try {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    val data = String(packet.data, 0, packet.length)
                    val ip = packet.address.hostAddress
                    if (ip != null && (data.contains("NOTIFY") || data.contains("HTTP/1.1 200 OK"))) {
                        trySend(Pair(ip, data))
                    }
                } catch (e: Exception) {
                    if (isActive) delay(1000)
                }
            }
        }

        awaitClose {
            job.cancel()
            socket.close()
        }
    }

    /**
     * Discovers camera-specific mDNS services.
     */
    fun discoverCameraServices() = callbackFlow {
        val serviceTypes = listOf(
            "_onvif._tcp.",
            "_axis-video._tcp.",
            "_http._tcp.",
            "_rtsp._tcp."
        )

        val listeners = serviceTypes.map { type ->
            val listener = object : NsdManager.DiscoveryListener {
                override fun onDiscoveryStarted(regType: String) {}
                override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                    nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(si: NsdServiceInfo, errorCode: Int) {}
                        override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                            trySend(resolvedServiceInfo)
                        }
                    })
                }
                override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
                override fun onDiscoveryStopped(regType: String) {}
                override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {}
                override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {}
            }
            nsdManager.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
            listener
        }

        awaitClose {
            listeners.forEach { nsdManager.stopServiceDiscovery(it) }
        }
    }
}
