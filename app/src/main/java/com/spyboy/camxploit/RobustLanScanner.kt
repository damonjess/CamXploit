package com.spyboy.camxploit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.BufferedReader
import java.io.FileReader
import java.net.*
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class RobustLanScanner(
    private val context: Context
) {
    @OptIn(ExperimentalCoroutinesApi::class)
    private val dispatcher = Dispatchers.IO.limitedParallelism(150)
    
    // Camera/IoT ports + common service ports
    private val probePorts = listOf(
        80, 81, 88, 443, 554, 8000, 8080, 8443, 
        8554, 8899, 10554, 21, 22, 23, 139, 445, 515, 631, 9100
    )

    data class DiscoveredDevice(
        val ip: String,
        val mac: String?,
        val openPorts: List<Int>,
        val hostname: String?,
        val source: DiscoverySource
    )

    enum class DiscoverySource { ARP, TCP_SCAN, SSDP, MDNS }

    fun scanNetwork(): Flow<DiscoveredDevice> = channelFlow {
        val lanHelper = LanScanner(context)
        val (localIp, subnet) = lanHelper.getLocalIpAndSubnet() ?: ("127.0.0.1" to "127.0.0")
        
        Log.d("LAN_SCAN", "Starting scan: My IP=$localIp, Subnet=$subnet.0/24")

        // Layer 1: Read ARP table instantly (finds recently contacted devices)
        val arpDevices = readArpTable().filter { it.ip.startsWith(subnet) && it.ip != localIp }
        Log.d("LAN_SCAN", "ARP Table found ${arpDevices.size} potential devices")
        arpDevices.forEach { 
            send(it.copy(source = DiscoverySource.ARP)) 
        }

        // Layer 2: SSDP multicast (finds UPnP cameras/routers instantly)
        launch {
            ssdpDiscover().collect { device ->
                if (device.ip.startsWith(subnet)) {
                    send(device.copy(source = DiscoverySource.SSDP))
                }
            }
        }

        // Layer 3: Parallel TCP sweep (catches silent devices)
        val alreadyFound = ConcurrentHashMap<String, Boolean>().apply {
            arpDevices.forEach { put(it.ip, true) }
        }

        // Divide 254 IPs into smaller chunks to avoid overwhelming the system
        val hostRange = (1..254)
        hostRange.map { i ->
            async(dispatcher) {
                val ip = "$subnet.$i"
                if (ip == localIp || alreadyFound.containsKey(ip)) return@async

                // Directly probe common ports. Don't rely on Port 7 or ICMP
                val ports = probePorts(ip)
                if (ports.isNotEmpty()) {
                    val device = DiscoveredDevice(
                        ip = ip,
                        mac = getMacFromArp(ip),
                        openPorts = ports,
                        hostname = resolveHostname(ip),
                        source = DiscoverySource.TCP_SCAN
                    )
                    send(device)
                }
            }
        }.awaitAll()
        
        Log.d("LAN_SCAN", "TCP Sweep complete")
    }

    // --- TCP Connect Scan (works without root) ---
    private suspend fun probePorts(ip: String): List<Int> = withContext(dispatcher) {
        // First check port 80 as a "liveness" indicator for web-based devices/cameras
        if (isHostReachableByTcp(ip, 80, timeoutMs = 500)) {
            // If 80 is open, check others more slowly
            probePorts.map { port ->
                if (port == 80) return@map async { 80 }
                async {
                    if (isHostReachableByTcp(ip, port, timeoutMs = 800)) port else null
                }
            }.awaitAll().filterNotNull()
        } else {
            // If 80 is closed, check 554 (RTSP) and 443 (HTTPS) which are common for cameras
            val keyPorts = listOf(554, 443, 8080, 8000, 37777)
            val foundKeyPorts = keyPorts.map { port ->
                async {
                    if (isHostReachableByTcp(ip, port, timeoutMs = 600)) port else null
                }
            }.awaitAll().filterNotNull()
            
            if (foundKeyPorts.isNotEmpty()) {
                // If we found a key port, check the rest of the list
                val remaining = probePorts.filter { it !in keyPorts }
                val remainingFound = remaining.map { port ->
                    async {
                        if (isHostReachableByTcp(ip, port, timeoutMs = 800)) port else null
                    }
                }.awaitAll().filterNotNull()
                (foundKeyPorts + remainingFound).distinct()
            } else {
                emptyList()
            }
        }
    }

    private fun isHostReachableByTcp(ip: String, port: Int, timeoutMs: Int = 1000): Boolean {
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(ip, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }

    // --- ARP Table Reader ---
    private fun readArpTable(): List<DiscoveredDevice> {
        val devices = mutableListOf<DiscoveredDevice>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { br ->
                br.readLine() // Skip header
                br.forEachLine { line ->
                    val parts = line.trim().split(Regex("\\s+"))
                    if (parts.size >= 4) {
                        val ip = parts[0]
                        val mac = parts[3]
                        if (mac != "00:00:00:00:00:00" && !ip.startsWith("0.")) {
                            devices.add(DiscoveredDevice(
                                ip = ip,
                                mac = mac,
                                openPorts = emptyList(),
                                hostname = null,
                                source = DiscoverySource.ARP
                            ))
                        }
                    }
                }
            }
        } catch (_: Exception) {
            Log.w("LAN_SCAN", "Cannot read ARP table")
        }
        return devices
    }

    private fun getMacFromArp(ip: String): String? {
        return try {
            BufferedReader(FileReader("/proc/net/arp")).use { br ->
                br.readLine()
                br.lineSequence().find { it.contains(ip) }
                    ?.split(Regex("\\s+"))
                    ?.getOrNull(3)
                    ?.takeIf { it != "00:00:00:00:00:00" }
            }
        } catch (e: Exception) { null }
    }

    // --- SSDP Discovery ---
    private fun ssdpDiscover(): Flow<DiscoveredDevice> = flow {
        val socket = MulticastSocket(null).apply {
            broadcast = true
            soTimeout = 3000
            reuseAddress = true
        }

        try {
            val packet = DatagramPacket(
                SSDP_MESSAGE.toByteArray(),
                SSDP_MESSAGE.length,
                InetAddress.getByName("239.255.255.250"),
                1900
            )
            socket.send(packet)

            val buffer = ByteArray(2048)
            val deadline = System.currentTimeMillis() + 3000
            while (System.currentTimeMillis() < deadline) {
                try {
                    val response = DatagramPacket(buffer, buffer.size)
                    socket.receive(response)
                    val text = String(response.data, 0, response.length)
                    parseSsdpResponse(text)?.let { emit(it) }
                } catch (e: SocketTimeoutException) { break }
            }
        } catch (e: Exception) {
            Log.e("LAN_SCAN", "SSDP failed: ${e.message}")
        } finally {
            socket.close()
        }
    }.flowOn(dispatcher)

    private fun parseSsdpResponse(data: String): DiscoveredDevice? {
        val location = Regex("LOCATION:\\s*(.+)", RegexOption.IGNORE_CASE)
            .find(data)?.groupValues?.get(1)?.trim() ?: return null
        val ip = Regex("http://([0-9.]+)").find(location)?.groupValues?.get(1) ?: return null
        return DiscoveredDevice(
            ip = ip,
            mac = null,
            openPorts = listOf(80, 1900),
            hostname = Regex("SERVER:\\s*(.+)", RegexOption.IGNORE_CASE)
                .find(data)?.groupValues?.get(1)?.trim(),
            source = DiscoverySource.SSDP
        )
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            InetAddress.getByName(ip).hostName.takeIf { it != ip }
        } catch (e: Exception) { null }
    }

    companion object {
        private val SSDP_MESSAGE = """
            M-SEARCH * HTTP/1.1
            HOST: 239.255.255.250:1900
            MAN: "ssdp:discover"
            MX: 2
            ST: ssdp:all
            
        """.trimIndent()
    }
}
