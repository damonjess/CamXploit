package com.spyboy.camxploit

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
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
            <?xml version="1.0" encoding="utf-8"?>
            <Envelope xmlns:tds="http://www.onvif.org/ver10/device/wsdl" xmlns="http://www.w3.org/2003/05/soap-envelope">
                <Header>
                    <MessageID xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">uuid:$uuid</MessageID>
                    <To xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">urn:schemas-xmlsoap-org:ws:2004:08:discovery</To>
                    <Action xmlns="http://schemas.xmlsoap.org/ws/2004/08/addressing">http://schemas.xmlsoap.org/ws/2004/08/discovery/Probe</Action>
                </Header>
                <Body>
                    <Probe xmlns="http://schemas.xmlsoap.org/ws/2004/08/discovery">
                        <Types>tds:NetworkVideoTransmitter</Types>
                    </Probe>
                </Body>
            </Envelope>
        """.trimIndent()

        try {
            val socket = DatagramSocket().apply { soTimeout = timeoutMs }
            val group = InetAddress.getByName("239.255.255.250")
            val packet = DatagramPacket(probeMessage.toByteArray(), probeMessage.length, group, 3702)
            socket.send(packet)

            val receiveData = ByteArray(4096)
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
        val epAddress = Regex("<Address>(.*?)</Address>").find(xml)?.groupValues?.get(1)
        val types = Regex("<Types>(.*?)</Types>").find(xml)?.groupValues?.get(1)
        val xAddrs = Regex("<XAddrs>(.*?)</XAddrs>").find(xml)?.groupValues?.get(1)
        return OnvifDeviceInfo(ip, epAddress, types, xAddrs)
    }
}
