package com.spyboy.camxploit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class LanViewModel(
    private val scanner: RobustLanScanner
) : ViewModel() {

    private val _devices = MutableStateFlow<List<RobustLanScanner.DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<RobustLanScanner.DiscoveredDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    fun startScan() {
        viewModelScope.launch {
            _isScanning.value = true
            _devices.value = emptyList()
            
            val foundIps = ConcurrentHashMap<String, Boolean>()
            
            scanner.scanNetwork().collect { device ->
                if (!foundIps.containsKey(device.ip)) {
                    foundIps[device.ip] = true
                    _devices.value = (_devices.value + device).sortedBy { 
                        it.ip.split(".").lastOrNull()?.toIntOrNull() ?: 0
                    }
                }
            }
            
            _isScanning.value = false
        }
    }

    class Factory(private val scanner: RobustLanScanner) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            if (modelClass.isAssignableFrom(LanViewModel::class.java)) {
                @Suppress("UNCHECKED_CAST")
                return LanViewModel(scanner) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class")
        }
    }
}
