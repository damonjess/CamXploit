package com.spyboy.camxploit

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

class DiscoveryCoordinator(private val context: Context) {

    private val tag = "DiscoveryCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scanner = RobustLanScanner(context)
    private val ssdpHelper = SsdpDiscoveryHelper()
    private val lanScanner = LanScanner(context)

    private val _devices = MutableStateFlow<List<RobustLanScanner.Device>>(emptyList())
    val devices: StateFlow<List<RobustLanScanner.Device>> = _devices.asStateFlow()

    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    private val _progress = MutableStateFlow(0 to 0) // current, total
    val progress: StateFlow<Pair<Int, Int>> = _progress.asStateFlow()

    private val _discoveryFlow = MutableSharedFlow<DiscoveryResult>()
    val discoveryFlow: SharedFlow<DiscoveryResult> = _discoveryFlow.asSharedFlow()

    private val deviceMap = ConcurrentHashMap<String, RobustLanScanner.Device>()

    fun start() {
        if (_scanning.value) return
        _scanning.value = true
        deviceMap.clear()
        _devices.value = emptyList()
        _progress.value = 0 to 0

        scope.launch {
            // Run SSDP in parallel (finds UPnP cameras / routers)
            val ssdpJob = launch {
                try {
                    ssdpHelper.discover(onResult = { ip, info ->
                        addOrMerge(
                            RobustLanScanner.Device(
                                ip = ip,
                                mac = null,
                                hostname = info,
                                openPorts = emptyList(),
                                source = "ssdp"
                            )
                        )
                    })
                } catch (e: Exception) {
                    Log.e(tag, "SSDP discovery error", e)
                }
            }

            // Run active TCP/ICMP scan
            val scanJob = scanner.scan(
                timeoutMs = 1000,
                onResult = { addOrMerge(it) },
                onProgress = { cur, tot -> _progress.value = cur to tot },
                onFinished = { }
            )

            scanJob.join()
            delay(800) // let late SSDP responses trickle in
            ssdpJob.cancelAndJoin()

            enrichMacsFromArp()
            _scanning.value = false
        }
    }

    private fun enrichMacsFromArp() {
        val arpTable = lanScanner.readArpTable()
        if (arpTable.isEmpty()) return

        var changed = false
        deviceMap.forEach { (ip, dev) ->
            if (dev.mac == null) {
                arpTable[ip]?.let { mac ->
                    deviceMap[ip] = dev.copy(mac = mac)
                    changed = true
                }
            }
        }
        if (changed) {
            _devices.value = deviceMap.values.sortedBy { it.ip }
        }
    }

    private fun addOrMerge(dev: RobustLanScanner.Device) {
        val arpMac = dev.mac ?: lanScanner.readArpTable()[dev.ip]
        val existing = deviceMap[dev.ip]
        val merged = if (existing != null) {
            existing.copy(
                mac = arpMac ?: existing.mac,
                hostname = dev.hostname ?: existing.hostname,
                openPorts = (existing.openPorts + dev.openPorts).distinct().sorted(),
                source = if (existing.source.contains(dev.source)) existing.source
                         else "${existing.source},${dev.source}"
            )
        } else dev.copy(mac = arpMac)

        deviceMap[dev.ip] = merged
        _devices.value = deviceMap.values.sortedBy { it.ip }

        // Emit for background monitor and logging
        scope.launch {
            val mac = lanScanner.normalizeMac(merged.mac) ?: "Unknown"
            val vendor = lanScanner.getVendor(mac)
            val networkDevice = NetworkDevice(
                ip = merged.ip,
                hostname = merged.hostname ?: "Unknown",
                mac = mac,
                vendor = vendor,
                openPorts = merged.openPorts
            )
            _discoveryFlow.emit(DiscoveryResult(
                ip = merged.ip,
                source = merged.source,
                device = networkDevice
            ))
        }
    }

    fun stop() {
        scanner.cancel()
        scope.cancel()
    }
}
