package com.spyboy.camxploit

import android.content.Context
import android.net.wifi.WifiManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class DiscoveryCoordinator(private val context: Context) {

    private val passiveDiscovery = PassiveDiscovery(context)
    private val onvifProber = OnvifProber()
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private var multicastLock: WifiManager.MulticastLock? = null

    data class DiscoveryResult(
        val ip: String,
        val source: String, // "SSDP", "mDNS", "ARP", "PING", "ONVIF"
        val rawData: String? = null,
        val onvifInfo: OnvifDeviceInfo? = null,
        val ssdpInfo: SsdpDeviceInfo? = null,
        val device: NetworkDevice? = null,
        val playableUrl: String? = null,
        val streamUrls: List<String> = emptyList()
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

        // Acquire Multicast Lock to receive SSDP/mDNS
        try {
            multicastLock?.release()
            multicastLock = wifiManager.createMulticastLock("CamXploitDiscovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        scanJob = CoroutineScope(Dispatchers.IO).launch {
            val robustScanner = RobustLanScanner(context)
            val lanScanner = LanScanner(context)
            val fingerprinter = CameraFingerprinter()

            // Main Robust Discovery Layer (ARP, SSDP, TCP Sweep)
            launch {
                robustScanner.scanNetwork().collect { device ->
                    launch {
                        val ip = device.ip
                        val openPorts = device.openPorts
                        
                        val brand = fingerprinter.identify(ip, openPorts)
                        val streamUrls = discoverPlayableUrls(ip, brand, null)
                        
                        val mac = device.mac ?: "Unknown"
                        val vendor = lanScanner.getVendor(mac)
                        
                        val networkDevice = NetworkDevice(ip, device.hostname ?: "Unknown", mac, vendor, openPorts)
                        _discoveryFlow.emit(DiscoveryResult(
                            ip = ip,
                            source = device.source.name,
                            device = networkDevice,
                            playableUrl = streamUrls.firstOrNull(),
                            streamUrls = streamUrls
                        ))
                    }
                }
                _progressFlow.value = 0.8f
            }
            
            // Layer 1.5: Passive mDNS (Still handled separately)
            launch {
                passiveDiscovery.discoverCameraServices().collect { service ->
                    @Suppress("DEPRECATION")
                    val ip = service.host?.hostAddress ?: return@collect
                    val info = "Name: ${service.serviceName}, Type: ${service.serviceType}"
                    _discoveryFlow.emit(DiscoveryResult(ip, "mDNS", rawData = info))
                }
            }

            // Layer 3: Deep (ONVIF Probe)
            launch {
                onvifProber.probe().forEach { onvif ->
                    val streamUrls = discoverPlayableUrls(onvif.ip, CameraBrand.Generic, onvif)
                    _discoveryFlow.emit(DiscoveryResult(
                        ip = onvif.ip, 
                        source = "ONVIF", 
                        onvifInfo = onvif, 
                        playableUrl = streamUrls.firstOrNull(),
                        streamUrls = streamUrls
                    ))
                }
            }
        }
    }

    fun stopDiscovery() {
        scanJob?.cancel()
        scanJob = null
        try {
            multicastLock?.release()
            multicastLock = null
        } catch (e: Exception) {}
    }

    private suspend fun discoverPlayableUrls(ip: String, brand: CameraBrand, onvifInfo: OnvifDeviceInfo?): List<String> = withContext(Dispatchers.IO) {
        val prober = RtspUrlProber()
        val results = mutableListOf<String>()
        
        // 1. Try ONVIF if available
        onvifInfo?.xAddrs?.split(" ")?.firstOrNull()?.let { xAddr ->
            onvifProber.getStreamUri(xAddr)?.let { uri ->
                // Basic check for ONVIF URI
                if (uri.isNotBlank()) results.add(uri)
            }
        }

        // 2. Try brand-specific paths from RtspUrlProber
        results.addAll(prober.probe(ip, brand))

        // 3. Try common paths as fallback if nothing found yet
        if (results.isEmpty()) {
            val commonPaths = listOf(
                "/Streaming/Channels/101",
                "/cam/realmonitor?channel=1&subtype=0",
                "/live/ch0",
                "/onvif/Media",
                "/mpeg4/ch1/main/av_stream",
                "/video.m4v",
                "/live.sdp"
            )
            
            commonPaths.map { path ->
                async {
                    val url = "rtsp://$ip:554$path"
                    if (isEndpointValidInternal(url)) url else null
                }
            }.awaitAll().filterNotNull().forEach { results.add(it) }
        }

        results.distinct()
    }

    private suspend fun isEndpointValidInternal(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val uri = java.net.URI.create(url)
            val host = uri.host ?: return@withContext false
            val port = if (uri.port == -1) 554 else uri.port
            
            java.net.Socket().use { socket ->
                socket.connect(java.net.InetSocketAddress(host, port), 2000)
                socket.soTimeout = 2000
                val writer = socket.getOutputStream().bufferedWriter()
                val reader = socket.getInputStream().bufferedReader()

                writer.write("DESCRIBE $url RTSP/1.0\r\n")
                writer.write("CSeq: 1\r\n")
                writer.write("User-Agent: CamXploit\r\n")
                writer.write("Accept: application/sdp\r\n")
                writer.write("\r\n")
                writer.flush()

                val response = reader.readLine() ?: ""
                response.contains("200") || response.contains("401")
            }
        } catch (_: Exception) {
            false
        }
    }
}
