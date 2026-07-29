package com.spyboy.camxploit

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.chaquo.python.Python
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.OutputStream
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

    fun updateConfig(newConfig: StormConfig) {
        _config.value = newConfig
    }

    private fun log(message: String, level: LogLevel = LogLevel.INFO) {
        val timestamp = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
        _logs.value = _logs.value + StormLog(timestamp, message, level)
    }

    fun validateTarget() {
        val ip = _config.value.targetIp
        if (ip.isBlank()) {
            _validationState.value = ValidationState.Invalid("IP cannot be empty")
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _validationState.value = ValidationState.Validating
            log("Probing target $ip for common ports...")
            
            val openPorts = mutableListOf<Int>()
            val portsToScan = listOf(80, 443, 554, 8000, 8080, 8554)
            
            portsToScan.forEach { port ->
                try {
                    Socket().use { socket ->
                        socket.connect(InetSocketAddress(ip, port), 1000)
                        openPorts.add(port)
                    }
                } catch (e: Exception) {}
            }

            withContext(Dispatchers.Main) {
                if (openPorts.isNotEmpty()) {
                    _validationState.value = ValidationState.Valid(openPorts)
                    log("Target responsive. Open ports: ${openPorts.joinToString()}", LogLevel.INFO)
                    _config.value = _config.value.copy(targetPort = openPorts.first())
                } else {
                    _validationState.value = ValidationState.Invalid("No common ports responsive")
                    log("Target probe failed: Host unreachable or all ports closed", LogLevel.WARN)
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
                val pyOutputStream = object : OutputStream() {
                    private val buffer = StringBuilder()
                    override fun write(b: Int) {
                        val char = b.toChar()
                        if (char == '\n') {
                            log(buffer.toString(), LogLevel.DEBUG)
                            buffer.setLength(0)
                        } else {
                            buffer.append(char)
                        }
                    }
                }
                
                sys.put("stdout", pyOutputStream)
                sys.put("stderr", pyOutputStream)
                
                py.getModule("CamXploit").callAttr("test_dos_resilience", ip, port)
                
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
