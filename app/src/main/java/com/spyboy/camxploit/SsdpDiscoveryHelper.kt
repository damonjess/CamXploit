package com.spyboy.camxploit

import java.net.*

class SsdpDiscoveryHelper {

    private val multicastGroup = InetAddress.getByName("239.255.255.250")
    private val ssdpPort = 1900

    private val searchMessage = """
        M-SEARCH * HTTP/1.1
        HOST: 239.255.255.250:1900
        MAN: "ssdp:discover"
        MX: 2
        ST: urn:schemas-upnp-org:device:Camera:1
        
    """.trimIndent()

    suspend fun discover(timeoutMs: Int = 3000): List<SsdpDevice> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val devices = mutableListOf<SsdpDevice>()
        val socket = MulticastSocket(null).apply {
            broadcast = true
            soTimeout = timeoutMs
        }

        try {
            val packet = DatagramPacket(
                searchMessage.toByteArray(),
                searchMessage.length,
                multicastGroup,
                ssdpPort
            )
            socket.send(packet)

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + timeoutMs

            while (System.currentTimeMillis() < deadline) {
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val text = String(response.data, 0, response.length)
                    parseResponse(text)?.let { devices.add(it) }
                } catch (e: SocketTimeoutException) { 
                    break 
                } catch (e: Exception) {
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            socket.close()
        }
        devices.distinctBy { it.ip }
    }

    private fun parseResponse(data: String): SsdpDevice? {
        val location = Regex("LOCATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim() ?: return null
        val ip = Regex("http://([0-9.]+)").find(location)?.groupValues?.get(1) ?: return null
        return SsdpDevice(ip, location)
    }
}

data class SsdpDevice(val ip: String, val locationUrl: String)
