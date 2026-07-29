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

class RobustLanScanner(private val context: Context) {

    private val tag = "RobustLanScanner"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Ports commonly used by CCTV / IP cameras
    private val probePorts = intArrayOf(
        80, 81, 82, 83, 84, 85, 86, 87, 88, 89,
        443, 554, 8000, 8080, 8443,
        21, 22, 23,
        1024, 1025, 1026, 1027, 1028, 1029, 1030
    )

    data class Device(
        val ip: String,
        val mac: String? = null,
        val hostname: String? = null,
        val openPorts: List<Int> = emptyList(),
        val source: String = "unknown"
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
        timeoutMs: Int = 1000,
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

        Log.i(tag, "Scanning $total hosts…")

        // Process in batches so we don't spawn 10 000 coroutines at once
        targets.chunked(32).forEach { batch ->
            val jobs = batch.map { ip ->
                async {
                    val done = progress.incrementAndGet()
                    if (done % 25 == 0) {
                        onProgress(done, total)
                    }

                    val dev = probeHost(ip, timeoutMs)
                    if (dev != null && foundIps.add(ip)) {
                        onResult(dev)
                    }
                }
            }
            jobs.awaitAll()
        }

        onFinished()
    }

    private suspend fun probeHost(ip: String, timeoutMs: Int): Device? = coroutineScope {
        // 1) TCP connect scan – works on Android even without root
        val openPorts = mutableListOf<Int>()
        val portJobs = probePorts.map { port ->
            async(Dispatchers.IO) {
                if (tcpConnect(ip, port, timeoutMs / 2)) {
                    synchronized(openPorts) { openPorts.add(port) }
                }
            }
        }
        portJobs.awaitAll()

        // 2) ICMP fallback (rarely works on non-root Android, but cheap to try)
        var isReachable = openPorts.isNotEmpty()
        if (!isReachable) {
            isReachable = try {
                withTimeout(timeoutMs.toLong()) {
                    InetAddress.getByName(ip).isReachable(timeoutMs)
                }
            } catch (_: Exception) { false }
        }

        if (isReachable) {
            Device(
                ip = ip,
                mac = readArp(ip),
                hostname = resolveHostname(ip),
                openPorts = openPorts.sorted(),
                source = if (openPorts.isNotEmpty()) "tcp" else "icmp"
            )
        } else {
            null
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
