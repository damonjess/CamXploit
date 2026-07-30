package com.spyboy.camxploit

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LanViewModel(application: Application) : AndroidViewModel(application) {

    private val coordinator = DiscoveryCoordinator.getInstance(application)
    private val lanScanner = LanScanner(application.applicationContext)

    /** Observe this in your Compose UI with `val devices by viewModel.devices.collectAsState()` */
    val devices: StateFlow<List<LanHost>> = coordinator.devices
        .map { devices ->
            val localIp = lanScanner.getLocalIpAndSubnet()?.first
            devices.map { dev ->
                val mac = lanScanner.normalizeMac(dev.mac) ?: "Unknown"
                val vendor = dev.vendor ?: lanScanner.getVendor(mac)
                val deviceType = lanScanner.guessDeviceType(vendor, dev.hostname ?: "Unknown", dev.openPorts)
                
                val isCam = dev.source.contains("ssdp") || 
                             dev.openPorts.any { it in listOf(554, 8554, 8899, 37777, 34567) }

                LanHost(
                    ip = dev.ip,
                    mac = mac,
                    vendor = vendor,
                    hostname = dev.hostname,
                    isCamera = isCam,
                    deviceType = deviceType,
                    isYourDevice = dev.ip == localIp,
                    openPorts = dev.openPorts,
                    brand = vendor,
                    source = dev.source
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val isScanning = coordinator.scanning
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val progress: StateFlow<Float> = coordinator.progress
        .map { (current, total) ->
            if (total > 0) current.toFloat() / total.toFloat() else 0f
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0f)

    fun startScan() {
        viewModelScope.launch { coordinator.start() }
    }

    fun stopScan() {
        coordinator.stop()
    }

    fun probeStream(ip: String, brandName: String?) {
        // Implementation for manual probe if needed, matching old ViewModel logic
    }

    override fun onCleared() {
        super.onCleared()
        coordinator.stop()
    }

    // Factory removed as we now use AndroidViewModel which has a default factory
}
