package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.util.UUID

data class OnvifDeviceInfo(
    val ip: String,
    val epAddress: String?,
    val types: String?,
    val xAddrs: String?
)

class OnvifProber {

    /**
     * Probes for ONVIF devices using WS-Discovery (UDP 3702).
     */
    suspend fun probe(timeoutMs: Int = 3000): List<OnvifDeviceInfo> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<OnvifDeviceInfo>()
        val uuid = UUID.randomUUID().toString()
        val probeMessage = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" 
                        xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" 
                        xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery" 
                        xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                <s:Header>
                    <wsa:MessageID>uuid:$uuid</wsa:MessageID>
                    <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
                    <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>
                </s:Header>
                <s:Body>
                    <wsd:Probe>
                        <wsd:Types>dn:NetworkVideoTransmitter</wsd:Types>
                    </wsd:Probe>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val socket = DatagramSocket().apply { soTimeout = 500 }
            val group = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(probeMessage.toByteArray(), probeMessage.length, group, 3702)
            
            // Send multiple times to improve reliability
            repeat(3) {
                socket.send(packet)
            }

            val receiveData = ByteArray(8192)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address.hostAddress
                    if (ip != null) {
                        devices.add(parseOnvifResponse(ip, response))
                    }
                } catch (e: java.net.SocketTimeoutException) {
                    // Check if we still have time
                    continue
                } catch (e: Exception) {
                    break
                }
            }
            socket.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        devices.distinctBy { it.ip }
    }

    private fun parseOnvifResponse(ip: String, xml: String): OnvifDeviceInfo {
        val epAddress = Regex("""<[a-z0-9:]*?Address>(.*?)</[a-z0-9:]*?Address>""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
        val types = Regex("""<[a-z0-9:]*?Types>(.*?)</[a-z0-9:]*?Types>""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
        val xAddrs = Regex("""<[a-z0-9:]*?XAddrs>(.*?)</[a-z0-9:]*?XAddrs>""", RegexOption.IGNORE_CASE).find(xml)?.groupValues?.get(1)
        return OnvifDeviceInfo(ip, epAddress, types, xAddrs)
    }

    /**
     * Attempts to fetch the RTSP stream URI from an ONVIF device.
     */
    suspend fun getStreamUri(xAddr: String): String? = withContext(Dispatchers.IO) {
        val body = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:trt="http://www.onvif.org/ver10/media/wsdl">
              <s:Body>
                <trt:GetStreamUri>
                  <trt:StreamSetup>
                    <trt:Stream>RTP-Unicast</trt:Stream>
                    <trt:Transport><trt:Protocol>RTSP</trt:Protocol></trt:Transport>
                  </trt:StreamSetup>
                  <trt:ProfileToken>profile_1</trt:ProfileToken>
                </trt:GetStreamUri>
              </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val conn = (URL(xAddr).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 3000
                readTimeout = 3000
                doOutput = true
                setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
            }

            conn.outputStream.use { it.write(body.toByteArray()) }

            if (conn.responseCode == 200) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                return@withContext Regex("""<[a-z0-9:]*?Uri>(.*?)</[a-z0-9:]*?Uri>""", RegexOption.IGNORE_CASE)
                    .find(response)?.groupValues?.get(1)
            }
        } catch (_: Exception) { }
        null
    }
}
