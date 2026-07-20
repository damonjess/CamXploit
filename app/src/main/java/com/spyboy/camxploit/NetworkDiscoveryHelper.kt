package com.spyboy.camxploit

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.NetworkCapabilities
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.InetAddress
import java.net.URL

class NetworkDiscoveryHelper(private val context: Context) {

    private val nsdManager = context.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    data class NetworkSummary(
        val ssid: String,
        val localIp: String,
        val gateway: String,
        val dns: String,
        val publicIp: String = "Detecting..."
    )

    fun getNetworkSummary(): NetworkSummary {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetwork
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
        val linkProperties = connectivityManager.getLinkProperties(activeNetwork)

        val ssid = if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            wifiManager.connectionInfo.ssid.removeSurrounding("\"")
        } else "Not a WiFi Network"

        val localIp = linkProperties?.linkAddresses?.firstOrNull { it.address is java.net.Inet4Address }?.address?.hostAddress ?: "Unknown"
        val gateway = linkProperties?.routes?.firstOrNull { it.isDefaultRoute }?.gateway?.hostAddress ?: "Unknown"
        val dns = linkProperties?.dnsServers?.joinToString(", ") { it.hostAddress ?: "" } ?: "Unknown"

        return NetworkSummary(ssid, localIp, gateway, dns)
    }

    suspend fun getPublicIp(): String = withContext(Dispatchers.IO) {
        try {
            URL("https://api.ipify.org").readText()
        } catch (e: Exception) {
            "Unknown"
        }
    }

    fun discoverMDNS() = callbackFlow {
        val discoveryListener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) {}
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                nsdManager.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {}
                    override fun onServiceResolved(resolvedServiceInfo: NsdServiceInfo) {
                        trySend(resolvedServiceInfo)
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {}
            override fun onDiscoveryStopped(regType: String) {}
            override fun onStartDiscoveryFailed(regType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
            override fun onStopDiscoveryFailed(regType: String, errorCode: Int) {
                nsdManager.stopServiceDiscovery(this)
            }
        }

        nsdManager.discoverServices("_services._dns-sd._udp", NsdManager.PROTOCOL_DNS_SD, discoveryListener)
        awaitClose { nsdManager.stopServiceDiscovery(discoveryListener) }
    }
    
    // Simple SSDP Discovery (Lightweight implementation)
    suspend fun discoverSSDP(onDeviceFound: (String, String) -> Unit) = withContext(Dispatchers.IO) {
        val ssdpRequest = "M-SEARCH * HTTP/1.1\r\n" +
                "HOST: 239.255.255.250:1900\r\n" +
                "MAN: \"ssdp:discover\"\r\n" +
                "MX: 2\r\n" +
                "ST: ssdp:all\r\n" +
                "\r\n"
        
        try {
            val socket = java.net.DatagramSocket()
            socket.soTimeout = 2000
            val group = InetAddress.getByName("239.255.255.250")
            val packet = java.net.DatagramPacket(ssdpRequest.toByteArray(), ssdpRequest.length, group, 1900)
            socket.send(packet)

            val receiveData = ByteArray(1024)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < 3000) {
                try {
                    val receivePacket = java.net.DatagramPacket(receiveData, receiveData.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address.hostAddress
                    
                    val server = response.lines().firstOrNull { it.startsWith("SERVER:", ignoreCase = true) }?.substringAfter(":")?.trim() ?: "Unknown"
                    if (ip != null) onDeviceFound(ip, server)
                } catch (e: java.net.SocketTimeoutException) {
                    break
                }
            }
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
