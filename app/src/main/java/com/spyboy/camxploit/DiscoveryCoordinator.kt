package com.spyboy.camxploit

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.withPermit

class DiscoveryCoordinator(private val context: Context) {

    private val passiveDiscovery = PassiveDiscovery(context)
    private val lanScanner = LanScanner(context)
    private val onvifProber = OnvifProber()

    data class DiscoveryResult(
        val ip: String,
        val source: String, // "SSDP", "mDNS", "ARP", "PING", "ONVIF"
        val rawData: String? = null,
        val onvifInfo: OnvifDeviceInfo? = null,
        val device: NetworkDevice? = null
    )

    private val _discoveryFlow = MutableSharedFlow<DiscoveryResult>()
    val discoveryFlow: SharedFlow<DiscoveryResult> = _discoveryFlow

    private var scanJob: Job? = null

    private val _progressFlow = MutableStateFlow(0f)
    val progressFlow: StateFlow<Float> = _progressFlow

    fun startDiscovery() {
        scanJob?.cancel()
        _progressFlow.value = 0f
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            // Layer 1: Passive (SSDP + mDNS)
            launch {
                passiveDiscovery.listenSSDP().collect { (ip, data) ->
                    _discoveryFlow.emit(DiscoveryResult(ip, "SSDP", rawData = data))
                }
            }
            launch {
                passiveDiscovery.discoverCameraServices().collect { service ->
                    @Suppress("DEPRECATION")
                    val ip = service.host?.hostAddress ?: return@collect
                    _discoveryFlow.emit(DiscoveryResult(ip, "mDNS", rawData = service.toString()))
                }
            }

            // Layer 2: Active Fast (Ping Sweep + Port Knock)
            launch {
                val (_, subnet) = lanScanner.getLocalIpAndSubnet() ?: return@launch
                
                // Ping sweep
                val liveHosts = (1..254).map { i ->
                    async {
                        val ip = "$subnet.$i"
                        val isAlive = try { java.net.InetAddress.getByName(ip).isReachable(200) } catch (_: Exception) { false }
                        _progressFlow.value = i / 254f * 0.5f // 50% of progress for ping sweep
                        if (isAlive) ip else null
                    }
                }.awaitAll().filterNotNull()

                // Port scan for live hosts
                val semaphore = kotlinx.coroutines.sync.Semaphore(32)
                liveHosts.forEachIndexed { index, ip ->
                    launch {
                        semaphore.withPermit {
                            val openPorts = listOf(81, 554, 8080, 8554, 8899, 37777, 34567, 80, 443).filter { port ->
                                try {
                                    java.net.Socket().use { s ->
                                        s.connect(java.net.InetSocketAddress(ip, port), 200)
                                        true
                                    }
                                } catch (_: Exception) { false }
                            }
                            
                            val device = NetworkDevice(ip, "Unknown", "Unknown", "Unknown", openPorts)
                            _discoveryFlow.emit(DiscoveryResult(ip, "ACTIVE_SCAN", device = device))
                            _progressFlow.value = 0.5f + (index.toFloat() / liveHosts.size * 0.5f)
                        }
                    }
                }
            }

            // Layer 3: Deep (ONVIF Probe)
            launch {
                onvifProber.probe().forEach { onvif ->
                    _discoveryFlow.emit(DiscoveryResult(onvif.ip, "ONVIF", onvifInfo = onvif))
                }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
    }
}
