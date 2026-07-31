package com.spyboy.camxploit

data class LanHost(
    val ip: String,
    val mac: String? = null,
    val vendor: String? = null,
    val hostname: String? = null,
    val deviceType: String = "Unknown",
    val isYourDevice: Boolean = false,
    val openPorts: List<Int> = emptyList(),
    val isCamera: Boolean = false,
    val streamUrl: String? = null,
    val streamUrls: List<String> = emptyList(),
    val brand: String? = null,
    val model: String? = null,
    val isOnvif: Boolean = false,
    val source: String? = null
)

data class DiscoveryResult(
    val ip: String,
    val source: String, // "ssdp", "tcp", "icmp"
    val rawData: String? = null,
    val onvifInfo: OnvifDeviceInfo? = null,
    val ssdpInfo: SsdpDeviceInfo? = null,
    val device: NetworkDevice? = null,
    val playableUrl: String? = null,
    val streamUrls: List<String> = emptyList()
)
