package com.spyboy.camxploit

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.net.InetAddress
import java.net.NetworkInterface

data class NetworkDevice(
    val ip: String,
    val hostname: String,
    val mac: String,
    val vendor: String,
    val openPorts: List<Int>
)

class LanScanner(private val context: Context) {

    // Common ports - covers cameras AND regular devices
    private val cameraPorts  = listOf(554, 8554, 37777, 34567)
    private val commonPorts  = listOf(80, 443, 8080, 8443, 22, 21, 445, 5000, 9000)
    private val devicePorts  = listOf(62078, 7000, 49152, 5353) // iPhone, AirPlay, UPnP, mDNS

    private val allPorts get() = (cameraPorts + commonPorts + devicePorts).distinct()

    // MAC prefix → device type (top vendors)
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
        "ec:b5:fa" to "Philips Hue",
        "18:b4:30" to "Nest",
        "64:16:66" to "Nest",
        "ac:84:c6" to "Samsung TV",
        "8c:79:f0" to "Samsung TV",
        "00:e0:4c" to "Realtek (Windows PC)",
        "3c:22:fb" to "Apple Device",
        "a4:c3:f0" to "Apple Device",
        "00:03:93" to "Apple Device",
        "f4:5c:89" to "Apple iPhone/iPad",
        "98:01:a7" to "Apple iPhone/iPad",
        "28:6a:ba" to "Apple iPhone/iPad",
        "00:1a:11" to "Google Device",
        "54:60:09" to "Google Chromecast",
        "6c:ad:f8" to "Google Chromecast",
        "30:fd:38" to "Amazon Echo",
        "fc:a1:83" to "Amazon Echo",
        "44:65:0d" to "Amazon Fire TV",
        "78:e1:03" to "TP-Link Router",
        "14:cc:20" to "TP-Link Router",
        "c8:3a:35" to "Tenda Router",
        "00:1e:e5" to "Cisco Router",
        "00:1c:10" to "Cisco Router",
        "b4:75:0e" to "Huawei Router",
        "00:46:4b" to "NVIDIA Shield",
        "00:04:4b" to "NVIDIA Device"
    )

    fun getLocalIpAndSubnet(): Pair<String, String>? {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val wifiInfo = wifiManager.connectionInfo
            val ipInt = wifiInfo.ipAddress
            if (ipInt == 0) return null
            val ip = String.format(
                "%d.%d.%d.%d",
                ipInt and 0xff,
                ipInt shr 8 and 0xff,
                ipInt shr 16 and 0xff,
                ipInt shr 24 and 0xff
            )
            val subnet = ip.substringBeforeLast(".")
            return Pair(ip, subnet)
        } catch (e: Exception) {
            return null
        }
    }

    suspend fun scanNetwork(
        onProgress: (String) -> Unit,
        onDeviceFound: (NetworkDevice) -> Unit
    ) = withContext(Dispatchers.IO) {

        val (localIp, subnet) = getLocalIpAndSubnet()
            ?: run { onProgress("❌ Could not get local IP"); return@withContext }

        onProgress("📱 Your IP: $localIp")
        onProgress("🌐 Scanning $subnet.1 - $subnet.254 ...\n")

        // Phase 1: Find live hosts with isReachable (finds ALL devices)
        val liveHosts = mutableListOf<String>()
        val pingJobs = (1..254).map { i ->
            async {
                val ip = "$subnet.$i"
                try {
                    val addr = InetAddress.getByName(ip)
                    // timeout 300ms — fast enough for LAN
                    if (addr.isReachable(300)) {
                        synchronized(liveHosts) { liveHosts.add(ip) }
                    }
                } catch (_: Exception) {}
            }
        }
        pingJobs.awaitAll()

        onProgress("✅ Found ${liveHosts.size} live host(s), checking ports...\n")

        // Phase 2: For each live host, get ports + hostname + MAC
        val arpTable = readArpTable()

        liveHosts.sorted().forEach { ip ->
            val openPorts = checkPorts(ip)
            val hostname  = getHostname(ip)
            val mac       = arpTable[ip] ?: "Unknown"
            val vendor    = getVendor(mac)

            val device = NetworkDevice(
                ip       = ip,
                hostname = hostname,
                mac      = mac,
                vendor   = vendor,
                openPorts = openPorts
            )

            onDeviceFound(device)
            onProgress(formatDevice(device))
        }
    }

    private fun checkPorts(ip: String): List<Int> {
        return allPorts.filter { port ->
            try {
                java.net.Socket().use { socket ->
                    socket.connect(java.net.InetSocketAddress(ip, port), 300)
                    true
                }
            } catch (_: Exception) { false }
        }
    }

    private fun getHostname(ip: String): String {
        return try {
            val host = InetAddress.getByName(ip).canonicalHostName
            if (host == ip) "Unknown" else host
        } catch (_: Exception) { "Unknown" }
    }

    private fun readArpTable(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            BufferedReader(FileReader("/proc/net/arp")).use { reader ->
                reader.readLine() // skip header
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