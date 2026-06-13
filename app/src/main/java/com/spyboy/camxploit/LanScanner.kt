package com.spyboy.camxploit

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.NetworkInterface

data class NetworkDevice(
    val ip: String,
    val hostname: String,
    val mac: String,
    val vendor: String,
    val openPorts: List<Int>
)

class LanScanner(private val context: Context) {

    private val cameraPorts = listOf(554, 8554, 37777, 34567)
    private val commonPorts = listOf(80, 443, 8080, 8443, 22, 21, 445, 5000, 9000)
    private val devicePorts = listOf(62078, 7000, 49152, 5353)
    private val allPorts get() = (cameraPorts + commonPorts + devicePorts).distinct()

    private val macVendors = mapOf(
        "00:08:22" to "InPro (IP Camera)",
        "00:40:8c" to "Axis Camera",
        "ac:cc:8e" to "Axis Camera",
        "00:1d:fa" to "Hikvision Camera",
        "bc:ad:28" to "Hikvision Camera",
        "4c:11:bf" to "Hikvision Camera",
        "00:0b:5d" to "Dahua Camera",
        "38:af:29" to "Dahua Camera",
        "b0:c5:54" to "Reolink Camera",
        "00:50:f2" to "Microsoft Device",
        "dc:a6:32" to "Raspberry Pi",
        "b8:27:eb" to "Raspberry Pi",
        "f0:9f:c2" to "Ubiquiti",
        "24:a4:3c" to "Ubiquiti",
        "00:17:88" to "Philips Hue",
        "18:b4:30" to "Nest",
        "ac:84:c6" to "Samsung TV",
        "00:e0:4c" to "Realtek (Windows PC)",
        "3c:22:fb" to "Apple Device",
        "a4:c3:f0" to "Apple Device",
        "f4:5c:89" to "Apple iPhone/iPad",
        "00:1a:11" to "Google Device",
        "54:60:09" to "Google Chromecast",
        "30:fd:38" to "Amazon Echo",
        "44:65:0d" to "Amazon Fire TV",
        "78:e1:03" to "TP-Link Router",
        "c8:3a:35" to "Tenda Router",
        "00:1e:e5" to "Cisco Router",
        "b4:75:0e" to "Huawei Router",
        "00:46:4b" to "NVIDIA Shield"
    )

    fun getLocalIpAndSubnet(): Pair<String, String>? {
        return try {
            // Android 10+ compatible method via ConnectivityManager
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return fallbackSubnet()
            val props: LinkProperties = cm.getLinkProperties(network) ?: return fallbackSubnet()
            val addr = props.linkAddresses
                .firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress }
                ?: return fallbackSubnet()
            val ip = addr.address.hostAddress ?: return fallbackSubnet()
            val subnet = ip.substringBeforeLast(".")
            Pair(ip, subnet)
        } catch (e: Exception) {
            fallbackSubnet()
        }
    }

    private fun fallbackSubnet(): Pair<String, String>? {
        // Try reading from network interfaces directly
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .firstOrNull { addr ->
                    addr is java.net.Inet4Address &&
                    !addr.isLoopbackAddress &&
                    addr.hostAddress?.startsWith("192.168") == true
                }?.let {
                    val ip = it.hostAddress ?: return null
                    Pair(ip, ip.substringBeforeLast("."))
                }
        } catch (e: Exception) { null }
    }

    suspend fun scanNetwork(
        onProgress: (String) -> Unit,
        onDeviceFound: (NetworkDevice) -> Unit
    ) = coroutineScope {
        withContext(Dispatchers.IO) {

            val (localIp, subnet) = getLocalIpAndSubnet()
                ?: run { onProgress("❌ Could not get local IP"); return@withContext }

            onProgress("📱 Your IP: $localIp")
            onProgress("🌐 Scanning $subnet.1 - $subnet.254 ...\n")

            // Phase 1: parallel ping sweep
            val liveHosts = (1..254).map { i ->
                async {
                    val ip = "$subnet.$i"
                    try {
                        if (InetAddress.getByName(ip).isReachable(300)) ip else null
                    } catch (_: Exception) { null }
                }
            }.awaitAll().filterNotNull()

            onProgress("✅ Found ${liveHosts.size} live host(s), checking ports...\n")

            val arpTable = readArpTable()

            // Phase 2: parallel port checking (16 hosts at a time)
            val semaphore = Semaphore(16)
            liveHosts.sorted().map { ip ->
                async {
                    semaphore.acquire()
                    try {
                        // Check all ports for this host in parallel
                        val openPorts = allPorts.map { port ->
                            async {
                                try {
                                    Socket().use { s ->
                                        s.connect(InetSocketAddress(ip, port), 300)
                                        port
                                    }
                                } catch (_: Exception) { null }
                            }
                        }.awaitAll().filterNotNull()

                        val hostname = try {
                            val h = InetAddress.getByName(ip).canonicalHostName
                            if (h == ip) "Unknown" else h
                        } catch (_: Exception) { "Unknown" }

                        val mac = arpTable[ip] ?: "Unknown"
                        val vendor = getVendor(mac)

                        val device = NetworkDevice(ip, hostname, mac, vendor, openPorts)
                        withContext(Dispatchers.Main) {
                            onDeviceFound(device)
                            onProgress(formatDevice(device))
                        }
                    } finally {
                        semaphore.release()
                    }
                }
            }.awaitAll()
        }
    }

    private fun readArpTable(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
                reader.forEachLine { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4 && parts[3] != "00:00:00:00:00:00") {
                        arpMap[parts[0]] = parts[3].uppercase()
                    }
                }
            }
        } catch (_: Exception) {}
        return arpMap
    }

    private fun getVendor(mac: String): String {
        if (mac == "Unknown") return "Unknown Device"
        val prefix = mac.lowercase().substring(0, minOf(8, mac.length))
        return macVendors.entries.firstOrNull {
            prefix.startsWith(it.key.lowercase())
        }?.value ?: "Unknown Device"
    }

    private fun formatDevice(device: NetworkDevice): String {
        val sb = StringBuilder()
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📍 IP      : ${device.ip}")
        sb.appendLine("🏷️  Name    : ${device.hostname}")
        sb.appendLine("🔌 MAC     : ${device.mac}")
        sb.appendLine("🏭 Device  : ${device.vendor}")
        if (device.openPorts.isNotEmpty()) {
            sb.appendLine("🔓 Ports   : ${device.openPorts.joinToString(", ")}")
            if (device.openPorts.any { it in cameraPorts + listOf(80, 8080) }) {
                sb.appendLine("📷 Possible camera detected!")
            }
        }
        return sb.toString()
    }
}
