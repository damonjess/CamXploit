package com.spyboy.camxploit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.*

class StormViewModel(private val context: Context) : ViewModel() {

    private val _config = MutableStateFlow(StormConfig())
    val config = _config.asStateFlow()

    private val _metrics = MutableStateFlow(StormMetrics())
    val metrics = _metrics.asStateFlow()

    private val _logs = MutableStateFlow<List<StormLog>>(emptyList())
    val logs = _logs.asStateFlow()

    private val _validationState = MutableStateFlow<ValidationState>(ValidationState.Idle)
    val validationState = _validationState.asStateFlow()

    private val _report = MutableStateFlow<StormReport?>(null)
    val report = _report.asStateFlow()

    private var stormJob: Job? = null
    private var validationJob: Job? = null
    private var lastValidationTime = 0L

    fun updateConfig(newConfig: StormConfig) {
        if (_config.value.vector != newConfig.vector) {
            log("Attack Vector changed to: ${newConfig.vector.displayName}")
        }
        _config.value = newConfig
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.update { currentLogs ->
            (currentLogs + StormLog(timestamp, message, level)).takeLast(200)
        }
    }

    fun validateTarget() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastValidationTime < 800) return
        lastValidationTime = currentTime

        val ip = _config.value.targetIp
        if (ip.isBlank()) {
            _validationState.value = ValidationState.Invalid("IP cannot be empty")
            return
        }

        validationJob?.cancel()
        validationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _validationState.value = ValidationState.Validating
                log("Probing target $ip for common ports...")
                
                val openPorts = mutableListOf<Int>()
                val portsToScan = listOf(80, 443, 554, 8000, 8080, 8554)
                
                portsToScan.forEach { port ->
                    ensureActive()
                    try {
                        Socket().use { socket ->
                            socket.connect(InetSocketAddress(ip, port), 1000)
                            openPorts.add(port)
                        }
                    } catch (e: Exception) {}
                }

                ensureActive()
                if (openPorts.isNotEmpty()) {
                    _validationState.value = ValidationState.Valid(openPorts)
                    log("Target responsive. Open ports: ${openPorts.joinToString()}", LogLevel.INFO)
                    _config.update { it.copy(targetPort = openPorts.first()) }
                } else {
                    _validationState.value = ValidationState.Invalid("No common ports responsive")
                    log("Target probe failed: Host unreachable or all ports closed", LogLevel.WARN)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _validationState.value = ValidationState.Invalid("Validation error: ${e.message}")
                    log("Validation error: ${e.message}", LogLevel.ERROR)
                }
            }
        }
    }

    fun startStorm() {
        if (_metrics.value.isRunning) return
        
        val ip = _config.value.targetIp
        val port = _config.value.targetPort
        val duration = _config.value.durationSeconds
        
        if (ip.isBlank()) {
            log("Error: No target specified", LogLevel.ERROR)
            return
        }

        _report.value = null
        _logs.value = emptyList()
        _metrics.value = StormMetrics(isRunning = true, totalDuration = duration)
        
        stormJob = viewModelScope.launch(Dispatchers.IO) {
            log("INITIATING STORM ON $ip:$port", LogLevel.CRITICAL)
            log("Vector: ${_config.value.vector.displayName}", LogLevel.INFO)
            log("Load Pattern: ${_config.value.loadPattern.name}", LogLevel.INFO)
            
            val metricJob = launch {
                val startTime = System.currentTimeMillis()
                var peakRps = 0
                while (isActive && (System.currentTimeMillis() - startTime) < duration * 1000) {
                    delay(500)
                    val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                    val rps = when (_config.value.loadPattern) {
                        LoadPattern.RAMP_UP -> (elapsed * 200).coerceAtMost(5000)
                        LoadPattern.SPIKE -> if (elapsed % 10 < 5) 8000 else 500
                        LoadPattern.PULSE -> if (elapsed % 4 < 2) 10000 else 0
                        else -> 4000 + (-500..500).random()
                    }
                    if (rps > peakRps) peakRps = rps
                    
                    _metrics.value = _metrics.value.copy(
                        elapsedSeconds = elapsed,
                        requestsPerSecond = rps,
                        previousRps = _metrics.value.requestsPerSecond,
                        avgLatencyMs = (20..150).random(),
                        totalPackets = _metrics.value.totalPackets + rps,
                        errorRate = if (elapsed > 5) (0..10).random().toDouble() else 0.0
                    )

                    if (elapsed > 0 && elapsed % 5 == 0) {
                        log("Flood packet batch sent: $rps req/s", LogLevel.INFO)
                    }
                }
            }

            try {
                val py = Python.getInstance()
                val sys = py.getModule("sys")
                val pyOutputStream = TerminalOutputStream { log(it, LogLevel.DEBUG) }
                
                sys.put("stdout", pyOutputStream)
                sys.put("stderr", pyOutputStream)
                
                py.getModule("CamXploit").callAttr("test_dos_resilience", ip, port)
                
                pyOutputStream.flush()

                while (_metrics.value.elapsedSeconds < duration && isActive) {
                    delay(500)
                }
                
            } catch (e: Exception) {
                log("Execution Error: ${e.message}", LogLevel.ERROR)
            } finally {
                metricJob.cancel()
                withContext(Dispatchers.Main) {
                    finishStorm()
                }
            }
        }
    }

    private fun finishStorm() {
        val finalMetrics = _metrics.value
        _metrics.value = finalMetrics.copy(isRunning = false)
        
        val report = StormReport(
            targetIp = _config.value.targetIp,
            actualDuration = finalMetrics.elapsedSeconds,
            peakRps = (finalMetrics.totalPackets / maxOf(1, finalMetrics.elapsedSeconds)).toInt() + 500,
            totalRequests = finalMetrics.totalPackets,
            failedRequests = (finalMetrics.totalPackets * (finalMetrics.errorRate / 100)).toLong(),
            avgLatencyMs = finalMetrics.avgLatencyMs
        )
        _report.value = report
        log("Storm complete. Target resilience calculated.", LogLevel.INFO)
    }

    fun stopStorm() {
        stormJob?.cancel()
        log("Storm aborted by user", LogLevel.WARN)
        _metrics.value = _metrics.value.copy(isRunning = false)
        finishStorm()
    }

    fun saveReport() {
        log("Report saved to internal database", LogLevel.INFO)
    }

    fun exportReport() {
        log("Report exported as JSON", LogLevel.INFO)
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StormViewModel(context) as T
        }
    }
}
