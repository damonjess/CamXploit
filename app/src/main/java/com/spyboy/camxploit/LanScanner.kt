package com.spyboy.camxploit

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import java.net.NetworkInterface

data class NetworkDevice(
    val ip: String,
    val hostname: String,
    val mac: String,
    val vendor: String,
    val openPorts: List<Int>
)

class LanScanner(private val context: Context) {

    companion object {
        private var cachedVendorMap: Map<String, String>? = null

        private val prefixLine = Regex("^([0-9A-Fa-f]{6,9})\\s+(.+)$")

        private fun loadVendorMap(context: Context): Map<String, String> {
            cachedVendorMap?.let { return it }
            val map = mutableMapOf<String, String>()
            try {
                context.assets.open("nmap_data/nmap-mac-prefixes").bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (line.startsWith("#") || line.isBlank()) return@forEach
                        val match = prefixLine.matchEntire(line.trim()) ?: return@forEach
                        map[match.groupValues[1].uppercase()] = match.groupValues[2].trim()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            cachedVendorMap = map
            return map
        }
    }

    private val vendorMap by lazy { loadVendorMap(context) }

    fun getLocalIpAndSubnet(): Pair<String, String>? {
        return try {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            val network = cm.activeNetwork ?: return fallbackSubnet()
            val props: LinkProperties = cm.getLinkProperties(network) ?: return fallbackSubnet()
            
            val addr = props.linkAddresses
                .firstOrNull { it.address is java.net.Inet4Address && !it.address.isLoopbackAddress }
                ?: return fallbackSubnet()
            
            val ip = addr.address.hostAddress ?: return fallbackSubnet()
            
            // Correctly get subnet from the LinkAddress prefix length if possible,
            // but for home networks /24 is most common.
            val subnet = ip.substringBeforeLast(".")
            Pair(ip, subnet)
        } catch (e: Exception) {
            fallbackSubnet()
        }
    }

    private fun fallbackSubnet(): Pair<String, String>? {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces().toList()
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                
                for (addr in iface.inetAddresses) {
                    if (addr is java.net.Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168") || host.startsWith("10.") || host.startsWith("172.")) {
                            return Pair(host, host.substringBeforeLast("."))
                        }
                    }
                }
            }
            null
        } catch (e: Exception) { null }
    }

    fun normalizeMac(mac: String?): String? {
        if (mac.isNullOrBlank() || mac.equals("Unknown", ignoreCase = true)) return null
        val cleaned = mac.replace(":", "").replace("-", "").replace(".", "").uppercase()
        if (cleaned.length < 6 || cleaned.contains("INCOMPLETE")) return null
        if (cleaned.all { it == '0' }) return null
        // Re-format as AA:BB:CC:DD:EE:FF when possible
        return if (cleaned.length >= 12) {
            cleaned.chunked(2).take(6).joinToString(":")
        } else {
            mac.uppercase()
        }
    }

    fun getVendor(mac: String): String {
        val normalized = normalizeMac(mac) ?: return "Unknown Device"
        val cleanMac = normalized.replace(":", "").replace("-", "").replace(".", "").uppercase()
        if (cleanMac.length < 6) return "Unknown Device"

        // nmap-mac-prefixes uses 6-, 7-, and 9-char prefixes; longest match wins
        for (len in intArrayOf(9, 7, 6)) {
            if (cleanMac.length >= len) {
                vendorMap[cleanMac.substring(0, len)]?.let { return it }
            }
        }
        return "Unknown Device"
    }

    fun readArpTable(): Map<String, String> {
        val arpMap = mutableMapOf<String, String>()
        try {
            java.io.BufferedReader(java.io.FileReader("/proc/net/arp")).use { reader ->
                reader.readLine()
                reader.forEachLine { line ->
                    val parts = line.trim().split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        normalizeMac(parts[3])?.let { arpMap[parts[0]] = it }
                    }
                }
            }
        } catch (_: Exception) {}
        return arpMap
    }

    fun guessDeviceType(vendor: String, hostname: String, openPorts: List<Int>): String {
        val v = vendor.lowercase()
        val h = hostname.lowercase()
        
        return when {
            v.contains("apple") || v.contains("samsung") || v.contains("huawei") || v.contains("google") -> "Phone"
            v.contains("microsoft") || v.contains("dell") || v.contains("hp") || v.contains("lenovo") || v.contains("asus") -> "Computer"
            v.contains("hikvision") || v.contains("dahua") || v.contains("axis") || v.contains("reolink") || openPorts.contains(554) -> "Camera"
            v.contains("tp-link") || v.contains("d-link") || v.contains("netgear") || v.contains("cisco") || v.contains("ubiquiti") -> "Router"
            v.contains("amazon") || v.contains("echo") || v.contains("google home") || v.contains("sonos") -> "Smart Speaker"
            v.contains("sony") || v.contains("lg") || v.contains("panasonic") || v.contains("vizio") || h.contains("tv") -> "TV"
            v.contains("synology") || v.contains("qnap") || openPorts.contains(445) -> "Storage"
            v.contains("raspberry pi") -> "Single Board Computer"
            h.contains("iphone") || h.contains("android") || h.contains("pixel") -> "Phone"
            h.contains("macbook") || h.contains("laptop") || h.contains("desktop") -> "Computer"
            h.contains("printer") || v.contains("canon") || v.contains("epson") || v.contains("brother") -> "Printer"
            else -> "Unknown"
        }
    }
}
