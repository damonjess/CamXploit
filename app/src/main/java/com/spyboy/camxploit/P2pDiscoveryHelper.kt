package com.spyboy.camxploit

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

class P2pDiscoveryHelper {
    private val tag = "P2pDiscoveryHelper"
    private val p2pPort = 32108
    private val magicNum = 0xF1.toByte()
    private val msgPunchPkt = 0x41.toByte()

    /**
     * Broadcasts P2P search packets and listens for responses.
     * Emits Pairs of (IP, DeviceInfo)
     */
    fun discover(): Flow<Pair<String, String>> = flow {
        val socket = try {
            DatagramSocket().apply {
                broadcast = true
                soTimeout = 2500
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to create DatagramSocket", e)
            return@flow
        }

        val searchPacket1 = byteArrayOf(magicNum, 0x30.toByte(), 0x00, 0x00)
        val searchPacket2 = byteArrayOf(magicNum, 0x32.toByte(), 0x00, 0x00)

        try {
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            
            // Send search packets
            socket.send(DatagramPacket(searchPacket1, searchPacket1.size, broadcastAddr, p2pPort))
            socket.send(DatagramPacket(searchPacket2, searchPacket2.size, broadcastAddr, p2pPort))

            val buffer = ByteArray(1024)
            val startTime = System.currentTimeMillis()
            
            while (System.currentTimeMillis() - startTime < 3000) {
                val packet = DatagramPacket(buffer, buffer.size)
                try {
                    socket.receive(packet)
                    val data = packet.data
                    val len = packet.length
                    
                    if (len < 4) continue
                    if (data[0] != magicNum || data[1] != msgPunchPkt) continue

                    val ip = packet.address.hostAddress ?: continue
                    
                    // Parse Payload (from index 4)
                    // Prefix (4-11), Serial (12-15), Check Code (16-21)
                    val prefix = String(data, 4, 8).trim { it <= ' ' }
                    
                    val serialBuffer = ByteBuffer.wrap(data, 12, 4).order(ByteOrder.BIG_ENDIAN)
                    val serial = serialBuffer.int
                    
                    val checkCode = String(data, 16, 6).trim { it <= ' ' }
                    
                    val uid = "$prefix-${String.format(Locale.US, "%06d", serial)}-$checkCode"
                    
                    // Classification logic
                    val ilnkPrefixes = listOf("VSTD", "VSTF", "QHSV", "EEEE", "ROSS", "ISRP", "GCMN", "ELSA")
                    val isIlnk = ilnkPrefixes.contains(prefix) || checkCode.matches(Regex("[A-F]{5}"))
                    val protocol = if (isIlnk) "iLnkP2P" else "CS2 Network P2P"
                    
                    emit(ip to "P2P Device ($protocol, UID: $uid)")
                } catch (_: java.net.SocketTimeoutException) {
                    break
                } catch (e: Exception) {
                    Log.e(tag, "Error receiving P2P packet", e)
                }
            }
        } finally {
            socket.close()
        }
    }.flowOn(Dispatchers.IO)
}
