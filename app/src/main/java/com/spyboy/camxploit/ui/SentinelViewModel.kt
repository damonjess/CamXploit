package com.spyboy.camxploit.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spyboy.camxploit.RobustLanScanner
import com.spyboy.camxploit.pentest.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class ScanState { Idle, Scanning, Done }

class SentinelViewModel(app: Application) : AndroidViewModel(app) {

    private val sentinel = NetworkSentinel(app.applicationContext)

    private val _devices = MutableStateFlow<List<RobustLanScanner.Device>>(emptyList())
    val devices: StateFlow<List<RobustLanScanner.Device>> = _devices.asStateFlow()

    private val _selectedDevice = MutableStateFlow<RobustLanScanner.Device?>(null)
    val selectedDevice: StateFlow<RobustLanScanner.Device?> = _selectedDevice.asStateFlow()

    private val _scanState = MutableStateFlow(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val _tlsReport = MutableStateFlow<TlsAuditor.TlsReport?>(null)
    val tlsReport: StateFlow<TlsAuditor.TlsReport?> = _tlsReport.asStateFlow()

    private val _webReport = MutableStateFlow<WebSurfaceScanner.WebReport?>(null)
    val webReport: StateFlow<WebSurfaceScanner.WebReport?> = _webReport.asStateFlow()

    private val _diffReport = MutableStateFlow<NetworkSentinel.DiffReport?>(null)
    val diffReport: StateFlow<NetworkSentinel.DiffReport?> = _diffReport.asStateFlow()

    fun setDevices(list: List<RobustLanScanner.Device>) {
        _devices.value = list
    }

    fun selectDevice(dev: RobustLanScanner.Device) {
        _selectedDevice.value = dev
        _tlsReport.value = null
        _webReport.value = null
    }

    fun scanTls(ip: String, ports: List<Int>) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            // Prefer 443, 8443, or the first open port >= 443
            val targetPort = ports.firstOrNull { (it == 443) || (it == 8443) }
                ?: ports.firstOrNull { it > 1024 }
                ?: 443
            _tlsReport.value = TlsAuditor.analyze(ip, targetPort)
            _scanState.value = ScanState.Done
        }
    }

    fun scanWeb(ip: String, ports: List<Int>) {
        viewModelScope.launch {
            _scanState.value = ScanState.Scanning
            // Try common web ports in order of likelihood
            val webPort = ports.firstOrNull { it == 80 || it == 8080 || it == 443 || it == 8443 || it == 8000 || it == 81 } ?: 80
            _webReport.value = WebSurfaceScanner.scan(ip, webPort)
            _scanState.value = ScanState.Done
        }
    }

    fun saveBaseline() {
        viewModelScope.launch {
            sentinel.saveBaseline(_devices.value)
        }
    }

    fun checkBaseline() {
        viewModelScope.launch {
            _diffReport.value = sentinel.compareWithBaseline(_devices.value)
        }
    }
}
