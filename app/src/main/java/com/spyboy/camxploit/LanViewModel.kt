package com.spyboy.camxploit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LanViewModel(
    private val coordinator: DiscoveryCoordinator,
    private val lanScanner: LanScanner
) : ViewModel() {

    private val _devices = MutableStateFlow<List<LanHost>>(emptyList())
    val devices: StateFlow<List<LanHost>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _progress = MutableStateFlow(0f)
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private var localIp: String? = null

    init {
        localIp = lanScanner.getLocalIpAndSubnet()?.first
        
        viewModelScope.launch {
            coordinator.discoveryFlow.collect { result ->
                updateDeviceList(result)
            }
        }
        viewModelScope.launch {
            coordinator.progressFlow.collect { 
                _progress.value = it 
                if (it >= 1f) _isScanning.value = false
            }
        }
    }

    fun startScan() {
        _devices.value = emptyList()
        _isScanning.value = true
        coordinator.startDiscovery()
    }

    fun stopScan() {
        coordinator.stopDiscovery()
        _isScanning.value = false
    }

    fun probeStream(ip: String, brandName: String?) {
        viewModelScope.launch {
            val brand = when {
                brandName?.lowercase()?.contains("hikvision") == true -> CameraBrand.Hikvision
                brandName?.lowercase()?.contains("dahua") == true -> CameraBrand.Dahua
                brandName?.lowercase()?.contains("axis") == true -> CameraBrand.Axis
                else -> CameraBrand.Generic
            }
            
            val prober = RtspUrlProber()
            val urls = prober.probe(ip, brand)
            if (urls.isNotEmpty()) {
                val currentList = _devices.value.toMutableList()
                val index = currentList.indexOfFirst { it.ip == ip }
                if (index != -1) {
                    currentList[index] = currentList[index].copy(
                        streamUrl = urls.first(),
                        streamUrls = urls
                    )
                    _devices.value = currentList
                }
            }
        }
    }

    private fun updateDeviceList(result: DiscoveryResult) {
        val currentList = _devices.value.toMutableList()
        val existingIndex = currentList.indexOfFirst { it.ip == result.ip }

        if (existingIndex == -1) {
            val mac = result.device?.mac ?: "Unknown"
            var vendor = result.ssdpInfo?.manufacturer ?: lanScanner.getVendor(mac)
            
            if (result.source == "mDNS" && result.rawData?.startsWith("Name: ") == true) {
                val mdnsName = result.rawData.substringAfter("Name: ").substringBefore(",")
                if (vendor == "Unknown Vendor" || vendor.contains("Searching")) {
                    vendor = mdnsName
                }
            }

            val deviceType = result.ssdpInfo?.modelName ?: lanScanner.guessDeviceType(vendor, "Unknown", result.device?.openPorts ?: emptyList())
            
            val isCam = result.source == "ONVIF" || 
                         result.source.startsWith("SSDP") || 
                         result.source == "mDNS" ||
                         result.source == "ARP_PROBE" ||
                         result.ssdpInfo?.friendlyName?.lowercase()?.contains("camera") == true ||
                         result.ssdpInfo?.modelName?.lowercase()?.contains("cam") == true ||
                         (result.device?.openPorts?.any { it in listOf(554, 8554, 8899, 37777, 34567) } == true)

            val host = LanHost(
                ip = result.ip,
                mac = mac,
                vendor = vendor,
                isCamera = isCam || result.playableUrl != null,
                deviceType = deviceType,
                isYourDevice = result.ip == localIp,
                openPorts = result.device?.openPorts ?: emptyList(),
                streamUrl = result.playableUrl,
                streamUrls = result.streamUrls,
                brand = result.device?.vendor ?: result.ssdpInfo?.manufacturer,
                model = result.ssdpInfo?.modelName,
                isOnvif = result.source == "ONVIF"
            )
            currentList.add(host)
        } else {
            val it = currentList[existingIndex]
            val isNowCam = it.isCamera || 
                           result.source == "ONVIF" || 
                           result.source.startsWith("SSDP") || 
                           result.source == "ARP_PROBE" ||
                           result.playableUrl != null
            
            currentList[existingIndex] = it.copy(
                isCamera = isNowCam,
                mac = if (it.mac == "Unknown" || it.mac == null) (result.device?.mac ?: it.mac) else it.mac,
                vendor = result.ssdpInfo?.manufacturer ?: (if (it.vendor == "Unknown Vendor") result.device?.vendor else it.vendor) ?: it.vendor,
                deviceType = result.ssdpInfo?.modelName ?: (if (it.deviceType == "Unknown") lanScanner.guessDeviceType(it.vendor ?: "Unknown", "Unknown", result.device?.openPorts ?: emptyList()) else it.deviceType),
                streamUrl = result.playableUrl ?: it.streamUrl,
                streamUrls = if (result.streamUrls.isNotEmpty()) result.streamUrls else it.streamUrls,
                isOnvif = it.isOnvif || result.source == "ONVIF",
                brand = result.device?.vendor ?: result.ssdpInfo?.manufacturer ?: it.brand,
                model = result.ssdpInfo?.modelName ?: it.model
            )
        }
        
        _devices.value = currentList.sortedBy { it.ip.split(".").lastOrNull()?.toIntOrNull() ?: 0 }
    }

    class Factory(
        private val coordinator: DiscoveryCoordinator,
        private val lanScanner: LanScanner
    ) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LanViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LanViewModel(coordinator, lanScanner) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
