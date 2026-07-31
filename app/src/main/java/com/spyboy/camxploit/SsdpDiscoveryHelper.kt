package com.spyboy.camxploit

import java.net.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SsdpDiscoveryHelper {

    private val multicastGroup = InetAddress.getByName("239.255.255.250")
    private val ssdpPort = 1900

    private val searchMessage = """
        M-SEARCH * HTTP/1.1
        HOST: 239.255.255.250:1900
        MAN: "ssdp:discover"
        MX: 2
        ST: ssdp:all
        
    """.trimIndent()

    suspend fun discover(timeoutMs: Int = 3000, onResult: (ip: String, info: String) -> Unit) = withContext(Dispatchers.IO) {
        val socket = MulticastSocket(null).apply {
            broadcast = true
            soTimeout = timeoutMs
            reuseAddress = true
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
                    parseResponse(text)?.let { (ip, info) ->
                        onResult(ip, info)
                    }
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
    }

    private fun parseResponse(data: String): Pair<String, String>? {
        val location = Regex("LOCATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim() ?: return null
        val ip = Regex("http://([0-9.]+)").find(location)?.groupValues?.get(1) ?: return null
        
        // Extract server or description
        val server = Regex("SERVER:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim()
        val usn = Regex("USN:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim()
            
        val info = server ?: usn ?: "SSDP Device"
        return ip to info
    }
}
