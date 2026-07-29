package com.spyboy.camxploit

data class StormConfig(
    val vector: AttackVector = AttackVector.TCP_SYN_FLOOD,
    val threads: Int = 50,
    val durationSeconds: Int = 30,
    val loadPattern: LoadPattern = LoadPattern.SUSTAINED,
    val targetPort: Int = 80,
    val targetIp: String = ""
)

data class StormMetrics(
    val isRunning: Boolean = false,
    val requestsPerSecond: Int = 0,
    val previousRps: Int = 0,
    val avgLatencyMs: Int = 0,
    val errorRate: Double = 0.0,
    val totalPackets: Long = 0,
    val elapsedSeconds: Int = 0,
    val totalDuration: Int = 30
)

data class StormReport(
    val targetIp: String,
    val actualDuration: Int,
    val peakRps: Int,
    val totalRequests: Long,
    val failedRequests: Long,
    val avgLatencyMs: Int,
    val timestamp: Long = System.currentTimeMillis()
)

data class StormLog(
    val timestamp: String,
    val message: String,
    val level: LogLevel = LogLevel.INFO
)

enum class AttackVector(val displayName: String) {
    TCP_SYN_FLOOD("TCP SYN Flood"),
    HTTP_GET_FLOOD("HTTP GET Flood"),
    RTSP_DESCRIBE_FLOOD("RTSP DESCRIBE Flood"),
    CONNECTION_EXHAUSTION("Connection Exhaustion"),
    UDP_AMPLIFICATION("UDP Amplification")
}

enum class LoadPattern { SPIKE, RAMP_UP, SUSTAINED, PULSE }

enum class LogLevel { INFO, WARN, ERROR, CRITICAL, DEBUG }

sealed class ValidationState {
    object Idle : ValidationState()
    object Validating : ValidationState()
    data class Valid(val openPorts: List<Int>) : ValidationState()
    data class Invalid(val reason: String) : ValidationState()
}
