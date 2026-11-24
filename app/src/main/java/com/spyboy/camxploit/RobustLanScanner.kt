package com.spyboy.camxploit

import android.content.Context
import android.net.ConnectivityManager
import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.FileReader
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class RobustLanScanner(private val context: Context) {

    private val tag = "RobustLanScanner"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Ports commonly used by CCTV / IP cameras
    private val probePorts = intArrayOf(
        80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
        443, 554, 8554, 8000, 8001, 8080, 8443, 8899, 37777, 34567,
        21, 22, 23, 111, 135, 139, 445,
        1024, 1025, 1026, 1027, 1028, 1029, 1030,
        32400, 8008, 8009, 5353, 1900
    )

    data class Device(
        val ip: String,
        val mac: String? = null,
        val hostname: String? = null,
        val openPorts: List<Int> = emptyList(),
        val source: String = "unknown",
        val vendor: String? = null
    )

    /**
     * Start scanning the local subnet.
     *
     * @param timeoutMs   Timeout for each TCP connect / ICMP probe
     * @param onResult    Called (on Main dispatcher) for every newly discovered device
     * @param onProgress  Called (on Main dispatcher) every ~25 hosts
     * @param onFinished  Called (on Main dispatcher) when everything completes
     */
    fun scan(
        timeoutMs: Int = 400,
        onResult: suspend (Device) -> Unit,
        onProgress: suspend (scanned: Int, total: Int) -> Unit,
        onFinished: suspend () -> Unit
    ): Job = scope.launch {

        val targets = enumerateTargets()
        if (targets.isEmpty()) {
            Log.w(tag, "No targets generated")
            onFinished()
            return@launch
        }

        val foundIps = ConcurrentHashMap.newKeySet<String>()
        val progress = AtomicInteger(0)
        val total = targets.size

        Log.i(tag, "Scanning $total hosts with timeout ${timeoutMs}ms…")

        // Use a semaphore to limit concurrent IP scans
        val semaphore = Semaphore(64)

        targets.map { ip ->
            launch {
                semaphore.withPermit {
                    val dev = probeHost(ip, timeoutMs)
                    if (dev != null && foundIps.add(ip)) {
                        onResult(dev)
                    }
                    val done = progress.incrementAndGet()
                    if (done % 10 == 0 || done == total) {
                        onProgress(done, total)
                    }
                }
            }
        }.joinAll()

        onFinished()
    }

    private suspend fun probeHost(ip: String, timeoutMs: Int): Device? = coroutineScope {
        // First check if the host is "alive" to avoid scanning all ports on empty IPs
        if (!isHostAlive(ip, timeoutMs)) return@coroutineScope null

        // Host is alive, now do the full port scan
        val openPorts = mutableListOf<Int>()
        val portSemaphore = Semaphore(10)
        
        val portJobs = probePorts.map { port ->
            async(Dispatchers.IO) {
                portSemaphore.withPermit {
                    if (tcpConnect(ip, port, timeoutMs)) {
                        synchronized(openPorts) { openPorts.add(port) }
                    }
                }
            }
        }
        portJobs.awaitAll()

        val mac = readArp(ip)
        val resolvedHostname = resolveHostname(ip)
        
        val httpPort = when {
            80 in openPorts -> 80
            8080 in openPorts -> 8080
            else -> null
        }
        
        val http = httpPort?.let { httpFingerprint(ip, it) }
        val httpVendor = http?.first
        val httpTitle = http?.second
        val rtspVendor = if (554 in openPorts) rtspFingerprint(ip, 554) else null
        val ftpVendor = if (21 in openPorts) ftpFingerprint(ip, 21) else null
        // A page title ("TP-Link Archer AX55", "Synology DiskStation") tells the
        // user far more than "Network Device".
        val hostname = DeviceIdentifier.cleanName(resolvedHostname, ip) ?: httpTitle
        
        // Fallback chain: HTTP fingerprint -> hostname -> OUI -> reverse-DNS/SSDP-style hints.
        val vendor = httpVendor
            ?: DeviceIdentifier.vendorFromHostname(hostname)
            ?: mac?.let { OuiVendorLookup.lookup(it) }
            ?: rtspVendor
            ?: ftpVendor

        Device(
            ip = ip,
            mac = mac,
            hostname = hostname,
            openPorts = openPorts.sorted(),
            source = if (openPorts.isNotEmpty()) "tcp" else "icmp",
            vendor = vendor
        )
    }

    private suspend fun isHostAlive(ip: String, timeoutMs: Int): Boolean = withContext(Dispatchers.IO) {
        // Quick check on common ports first
        val commonPorts = intArrayOf(80, 443, 554, 8080, 8000)
        for (port in commonPorts) {
            if (tcpConnect(ip, port, timeoutMs)) return@withContext true
        }
        
        // Fallback to ICMP reachability
        try {
            InetAddress.getByName(ip).isReachable(timeoutMs)
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun ftpFingerprint(ip: String, port: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 1500)
                    socket.getInputStream().bufferedReader().use { reader ->
                        DeviceIdentifier.vendorFromText(reader.readLine())
                    }
                }
            } catch (_: Exception) { null }
        }
    }

    private suspend fun rtspFingerprint(ip: String, port: Int): String? {
        return withContext(Dispatchers.IO) {
            try {
                Socket().use { socket ->
                    socket.connect(InetSocketAddress(ip, port), 1500)
                    val out = socket.getOutputStream()
                    val request = "OPTIONS rtsp://$ip:$port RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: CamXploit\r\n\r\n"
                    out.write(request.toByteArray())
                    
                    val reader = socket.getInputStream().bufferedReader()
                    val sb = StringBuilder()
                    var line: String?
                    while (true) {
                        line = reader.readLine()
                        if (line.isNullOrEmpty()) break
                        sb.append(line).append("\n")
                        if (sb.length > 2000) break 
                    }
                    
                    DeviceIdentifier.vendorFromText(sb.toString())
                }
            } catch (_: Exception) { null }
        }
    }

    private suspend fun httpFingerprint(ip: String, port: Int): Pair<String?, String?> {
        return try {
            withContext(Dispatchers.IO) {
                withTimeout(1500) {
                    val url = java.net.URL("http://$ip:$port")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.connectTimeout = 1000
                    conn.readTimeout = 1000
                    conn.instanceFollowRedirects = true

                    val server = conn.getHeaderField("Server")?.lowercase()
                    val title = try {
                        conn.inputStream.bufferedReader().use { it.readText() }
                            .let { html ->
                                Regex("<title>(.*?)</title>", RegexOption.IGNORE_CASE)
                                    .find(html)?.groupValues?.get(1)
                            }
                    } catch (_: Exception) {
                        null
                    }

                    conn.disconnect()

                    val vendor = DeviceIdentifier.vendorFromHttp(title, server)
                    vendor to DeviceIdentifier.cleanName(title, ip)
                }
            }
        } catch (_: Exception) {
            null to null
        }
    }

    private fun tcpConnect(ip: String, port: Int, timeout: Int): Boolean {
        return try {
            Socket().use { s ->
                s.connect(InetSocketAddress(ip, port), timeout)
                true
            }
        } catch (_: Exception) { false }
    }

    /** Determine the /24 subnet from the active Wi-Fi interface, or fall back to common ranges. */
    private fun enumerateTargets(): List<String> {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val network = cm?.activeNetwork
        if (network != null) {
            try {
                val linkProps = cm.getLinkProperties(network)
                val ipv4 = linkProps?.linkAddresses?.firstOrNull { it.address is Inet4Address }
                if (ipv4 != null) {
                    val ip = ipv4.address.hostAddress ?: ""
                    if (ip.isNotEmpty()) {
                        // Home networks are almost always /24
                        val base = ip.substring(0, ip.lastIndexOf('.'))
                        return (1..254).map { "$base.$it" }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to read link properties", e)
            }
        }

        // Fallbacks for common private subnets
        return listOf("192.168.1", "192.168.0", "10.0.0").flatMap { base ->
            (1..254).map { "$base.$it" }
        }
    }

    private fun readArp(ip: String): String? {
        return try {
            BufferedReader(FileReader("/proc/net/arp")).useLines { lines ->
                lines.map { it.split("\\s+".toRegex()) }
                    .firstOrNull { parts ->
                        parts.size >= 4 &&
                        parts[0] == ip &&
                        !parts[3].equals("00:00:00:00:00:00", ignoreCase = true) &&
                        !parts[3].contains("incomplete", ignoreCase = true)
                    }
                    ?.get(3)
                    ?.uppercase()
            }
        } catch (_: Exception) { null }
    }

    private fun resolveHostname(ip: String): String? {
        return try {
            val name = InetAddress.getByName(ip).canonicalHostName
            if (name != ip) name else null
        } catch (_: Exception) { null }
    }

    fun cancel() {
        scope.cancel()
    }
}
