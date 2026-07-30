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

    companion object {
        @Volatile
        private var INSTANCE: DiscoveryCoordinator? = null

        fun getInstance(context: Context): DiscoveryCoordinator {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DiscoveryCoordinator(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val tag = "DiscoveryCoordinator"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val scanner = RobustLanScanner(context)
    private val ssdpHelper = SsdpDiscoveryHelper()
    private val mdnsHelper = MdnsDiscoveryHelper(context)
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
                                source = "ssdp",
                                vendor = scanner.guessVendorFromHostname(info)
                            )
                        )
                    })
                } catch (e: Exception) {
                    Log.e(tag, "SSDP discovery error", e)
                }
            }

            // Run mDNS in parallel
            val mdnsJob = launch {
                try {
                    val serviceTypes = listOf("_http._tcp.", "_rtsp._tcp.", "_axis-video._tcp.", "_onvif._tcp.", "_workstation._tcp.")
                    serviceTypes.forEach { type ->
                        launch {
                            mdnsHelper.discoverServices(type).collect { (ip, info) ->
                                addOrMerge(
                                    RobustLanScanner.Device(
                                        ip = ip!!,
                                        mac = null,
                                        hostname = info,
                                        openPorts = emptyList(),
                                        source = "mdns",
                                        vendor = scanner.guessVendorFromHostname(info)
                                    )
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(tag, "mDNS discovery error", e)
                }
            }

            // Run active TCP/ICMP scan
            val scanJob = scanner.scan(
                timeoutMs = 400,
                onResult = { addOrMerge(it) },
                onProgress = { cur, tot -> _progress.value = cur to tot },
                onFinished = { }
            )

            scanJob.join()
            delay(1500) // let late responses trickle in
            ssdpJob.cancelAndJoin()
            mdnsJob.cancelAndJoin()

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
                    val vendor = OuiVendorLookup.lookup(mac) ?: dev.vendor
                    deviceMap[ip] = dev.copy(mac = mac, vendor = vendor)
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
        
        // Recalculate vendor if we have new info
        val currentVendor = dev.vendor ?: (arpMac?.let { OuiVendorLookup.lookup(it) })
            ?: scanner.guessVendorFromHostname(dev.hostname)

        val merged = if (existing != null) {
            val finalMac = arpMac ?: existing.mac
            val finalVendor = currentVendor ?: existing.vendor ?: (finalMac?.let { OuiVendorLookup.lookup(it) })
            
            existing.copy(
                mac = finalMac,
                hostname = dev.hostname ?: existing.hostname,
                openPorts = (existing.openPorts + dev.openPorts).distinct().sorted(),
                source = if (existing.source.contains(dev.source)) existing.source
                         else "${existing.source},${dev.source}",
                vendor = finalVendor
            )
        } else dev.copy(mac = arpMac, vendor = currentVendor)

        deviceMap[dev.ip] = merged
        _devices.value = deviceMap.values.sortedBy { it.ip }

        // Emit for background monitor and logging
        scope.launch {
            val mac = lanScanner.normalizeMac(merged.mac) ?: "Unknown"
            val vendor = merged.vendor ?: lanScanner.getVendor(mac)
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
