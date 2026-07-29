package com.spyboy.camxploit

import android.content.Context
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiscoveryCoordinator(private val context: Context) {

    private val passiveDiscovery = PassiveDiscovery(context)
    private val lanScanner = LanScanner(context)
    private val onvifProber = OnvifProber()
    private val ssdpProber = SsdpProber()

    data class DiscoveryResult(
        val ip: String,
        val source: String, // "SSDP", "mDNS", "ARP", "PING", "ONVIF"
        val rawData: String? = null,
        val onvifInfo: OnvifDeviceInfo? = null,
        val ssdpInfo: SsdpDeviceInfo? = null,
        val device: NetworkDevice? = null,
        val playableUrl: String? = null
    )

    private val _discoveryFlow = MutableSharedFlow<DiscoveryResult>()
    val discoveryFlow: SharedFlow<DiscoveryResult> = _discoveryFlow

    private var scanJob: Job? = null

    private val _progressFlow = MutableStateFlow(0f)
    val progressFlow: StateFlow<Float> = _progressFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startDiscovery() {
        scanJob?.cancel()
        _progressFlow.value = 0f
        scanJob = CoroutineScope(Dispatchers.IO).launch {
            val scanDispatcher = Dispatchers.IO.limitedParallelism(50)
            
            // Layer 1: Passive (SSDP + mDNS)
            launch {
                passiveDiscovery.listenSSDP().collect { (ip, data) ->
                    _discoveryFlow.emit(DiscoveryResult(ip, "SSDP_PASSIVE", rawData = data))
                }
            }
            
            // Layer 1.5: Active SSDP
            launch {
                ssdpProber.search().forEach { ssdp ->
                    _discoveryFlow.emit(DiscoveryResult(ssdp.ip, "SSDP_ACTIVE", ssdpInfo = ssdp))
                }
            }
            launch {
                passiveDiscovery.discoverCameraServices().collect { service ->
                    @Suppress("DEPRECATION")
                    val ip = service.host?.hostAddress ?: return@collect
                    val info = "Name: ${service.serviceName}, Type: ${service.serviceType}"
                    _discoveryFlow.emit(DiscoveryResult(ip, "mDNS", rawData = info))
                }
            }

            // Layer 2: Active Fast (Ping Sweep + Port Knock)
            launch {
                val (_, subnet) = lanScanner.getLocalIpAndSubnet() ?: return@launch
                
                // Ping sweep
                val liveHosts = (1..254).map { i ->
                    async(scanDispatcher) {
                        val ip = "$subnet.$i"
                        val isAlive = try { java.net.InetAddress.getByName(ip).isReachable(200) } catch (_: Exception) { false }
                        _progressFlow.value = i / 254f * 0.5f // 50% of progress for ping sweep
                        if (isAlive) ip else null
                    }
                }.awaitAll().filterNotNull()

                // Port scan for live hosts
                val cameraPorts = listOf(80, 81, 88, 443, 554, 8080, 8443, 8554, 8899, 10554)
                
                liveHosts.forEachIndexed { index, ip ->
                    launch(scanDispatcher) {
                        val openPorts = cameraPorts.filter { port ->
                            try {
                                java.net.Socket().use { s ->
                                    s.connect(java.net.InetSocketAddress(ip, port), 250)
                                    true
                                }
                            } catch (_: Exception) { false }
                        }
                        
                        var brand = "Unknown"
                        if (80 in openPorts) {
                            brand = HttpFingerprinter().identify(ip, 80) ?: "Unknown"
                        } else if (openPorts.isNotEmpty()) {
                            brand = HttpFingerprinter().identify(ip, openPorts.first()) ?: "Unknown"
                        }

                        val playableUrl = if (brand != "Unknown") discoverPlayableUrl(ip, brand, null) else null
                        val device = NetworkDevice(ip, "Unknown", "Unknown", brand, openPorts)
                        _discoveryFlow.emit(DiscoveryResult(ip, "ACTIVE_SCAN", device = device, playableUrl = playableUrl))
                        _progressFlow.value = 0.5f + (index.toFloat() / liveHosts.size * 0.5f)
                    }
                }
            }

            // Layer 3: Deep (ONVIF Probe)
            launch {
                onvifProber.probe().forEach { onvif ->
                    val playableUrl = discoverPlayableUrl(onvif.ip, "ONVIF", onvif)
                    _discoveryFlow.emit(DiscoveryResult(onvif.ip, "ONVIF", onvifInfo = onvif, playableUrl = playableUrl))
                }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
    }

    private suspend fun discoverPlayableUrl(ip: String, brand: String, onvifInfo: OnvifDeviceInfo?): String? = withContext(Dispatchers.IO) {
        val prober = RtspProber()
        
        // 1. Try ONVIF if available
        onvifInfo?.xAddrs?.split(" ")?.firstOrNull()?.let { xAddr ->
            onvifProber.getStreamUri(xAddr)?.let { uri ->
                if (prober.probe(uri)) return@withContext uri
            }
        }

        // 2. Try brand-specific paths
        val fingerprint = CameraFingerprint.all.find { it.brandName.equals(brand, ignoreCase = true) }
        fingerprint?.rtspPaths?.forEach { path ->
            val url = "rtsp://$ip:554$path"
            if (prober.probe(url)) return@withContext url
        }

        // 3. Try common paths as fallback
        val commonPaths = listOf(
            "/Streaming/Channels/101",
            "/cam/realmonitor?channel=1&subtype=0",
            "/live/ch0",
            "/onvif/Media",
            "/mpeg4/ch1/main/av_stream"
        )
        commonPaths.forEach { path ->
            val url = "rtsp://$ip:554$path"
            if (prober.probe(url)) return@withContext url
        }

        null
    }
}
