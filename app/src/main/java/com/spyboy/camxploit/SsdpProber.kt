package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.URL
import java.util.concurrent.TimeUnit

data class SsdpDeviceInfo(
    val ip: String,
    val location: String?,
    val server: String?,
    val friendlyName: String? = null,
    val modelName: String? = null,
    val manufacturer: String? = null
)

class SsdpProber {

    /**
     * Sends active M-SEARCH probes and listens for responses.
     */
    suspend fun search(timeoutMs: Int = 4000): List<SsdpDeviceInfo> = withContext(Dispatchers.IO) {
        val devices = mutableMapOf<String, SsdpDeviceInfo>()
        
        val searchTargets = listOf(
            "ssdp:all",
            "urn:schemas-upnp-org:device:Camera:1",
            "urn:schemas-upnp-org:device:DigitalSecurityCamera:1"
        )

        try {
            val socket = DatagramSocket().apply { soTimeout = 1000 }
            val group = InetAddress.getByName("239.255.255.250")
            
            searchTargets.forEach { target ->
                val ssdpRequest = """
                    M-SEARCH * HTTP/1.1
                    HOST: 239.255.255.250:1900
                    MAN: "ssdp:discover"
                    MX: 2
                    ST: $target
                    
                """.trimIndent()
                val packet = DatagramPacket(ssdpRequest.toByteArray(), ssdpRequest.length, group, 1900)
                socket.send(packet)
            }

            val receiveData = ByteArray(2048)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address.hostAddress
                    
                    if (ip != null) {
                        val location = Regex("LOCATION: (.*)", RegexOption.IGNORE_CASE).find(response)?.groupValues?.get(1)?.trim()
                        val server = Regex("SERVER: (.*)", RegexOption.IGNORE_CASE).find(response)?.groupValues?.get(1)?.trim()
                        
                        if (!devices.containsKey(ip) || devices[ip]?.friendlyName == null) {
                            var deviceInfo = SsdpDeviceInfo(ip, location, server)
                            
                            if (location != null) {
                                val deepInfo = fetchDeviceDescriptor(location)
                                if (deepInfo != null) {
                                    deviceInfo = deviceInfo.copy(
                                        friendlyName = deepInfo.friendlyName,
                                        modelName = deepInfo.modelName,
                                        manufacturer = deepInfo.manufacturer
                                    )
                                }
                            }
                            devices[ip] = deviceInfo
                        }
                    }
                } catch (e: Exception) {
                    // Timeout or socket error, keep going until total timeout
                }
            }
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        devices.values.toList()
    }

    private suspend fun fetchDeviceDescriptor(url: String): SsdpDeviceInfo? = withContext(Dispatchers.IO) {
        try {
            val connection = URL(url).openConnection()
            connection.connectTimeout = 2000
            connection.readTimeout = 2000
            val xml = connection.getInputStream().bufferedReader().use { it.readText() }
            
            val friendlyName = Regex("<friendlyName>(.*?)</friendlyName>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
            val modelName = Regex("<modelName>(.*?)</modelName>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
            val manufacturer = Regex("<manufacturer>(.*?)</manufacturer>", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
            
            SsdpDeviceInfo("", null, null, friendlyName, modelName, manufacturer)
        } catch (e: Exception) {
            null
        }
    }
}
