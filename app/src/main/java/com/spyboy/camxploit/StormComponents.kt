@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.spyboy.camxploit

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StormConfigPanel(
    config: StormConfig,
    onConfigChange: (StormConfig) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Text(
            "ATTACK VECTOR",
            color = Color(0xFFFF4444),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            AttackVector.entries.forEach { vector ->
                FilterChip(
                    selected = config.vector == vector,
                    onClick = { onConfigChange(config.copy(vector = vector)) },
                    label = { Text(vector.displayName) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color(0xFFFF4444).copy(alpha = 0.2f),
                        selectedLabelColor = Color(0xFFFF4444),
                        containerColor = Color(0xFF1A1A1A),
                        labelColor = Color.Gray
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            ConfigSlider(
                label = "THREADS",
                value = config.threads,
                range = 1..500,
                onValueChange = { onConfigChange(config.copy(threads = it)) },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(16.dp))
            ConfigSlider(
                label = "DURATION (s)",
                value = config.durationSeconds,
                range = 5..300,
                onValueChange = { onConfigChange(config.copy(durationSeconds = it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text("LOAD PATTERN", color = Color.Gray, fontSize = 12.sp)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp)
        ) {
            LoadPattern.entries.forEach { pattern ->
                val selected = config.loadPattern == pattern
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selected) Color(0xFFFF4444) else Color(0xFF1A1A1A))
                        .clickable { onConfigChange(config.copy(loadPattern = pattern)) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        pattern.name,
                        color = if (selected) Color.Black else Color.Gray,
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text(
                value.toString(),
                color = Color(0xFFFF4444),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = Color(0xFFFF4444),
                activeTrackColor = Color(0xFFFF4444),
                inactiveTrackColor = Color(0xFF333333)
            )
        )
    }
}

@Composable
fun TargetValidationCard(
    state: ValidationState,
    onValidate: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        colors = CardDefaults.cardColors(
            containerColor = when (state) {
                is ValidationState.Valid -> Color(0xFF0D3B1E)
                is ValidationState.Invalid -> Color(0xFF3B0D0D)
                else -> Color(0xFF1A1A1A)
            }
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (state) {
                is ValidationState.Idle -> {
                    Text("Validate target before starting", color = Color.Gray)
                    Spacer(modifier = Modifier.weight(1f))
                    OutlinedButton(
                        onClick = onValidate,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444))
                    ) { Text("VALIDATE") }
                }
                is ValidationState.Validating -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color(0xFFFF4444),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Probing target...", color = Color.Gray)
                }
                is ValidationState.Valid -> {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF00FF88))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Target Responsive", color = Color(0xFF00FF88), fontWeight = FontWeight.Bold)
                        Text(
                            "Open ports: ${state.openPorts.joinToString()}",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
                is ValidationState.Invalid -> {
                    Icon(Icons.Default.Error, null, tint = Color(0xFFFF4444))
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Target Unreachable", color = Color(0xFFFF4444))
                        Text(state.reason, color = Color.Gray, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LiveMetricsPanel(
    metrics: StormMetrics,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "REQ / SEC",
                value = String.format("%,d", metrics.requestsPerSecond),
                trend = if (metrics.requestsPerSecond > metrics.previousRps) "▲" else "▼",
                trendColor = if (metrics.requestsPerSecond > metrics.previousRps)
                    Color(0xFFFF4444) else Color(0xFF00FF88),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            MetricCard(
                label = "AVG LATENCY",
                value = "${metrics.avgLatencyMs}ms",
                trend = null,
                trendColor = Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            MetricCard(
                label = "ERROR RATE",
                value = String.format("%.1f%%", metrics.errorRate),
                trend = null,
                trendColor = if (metrics.errorRate > 50) Color(0xFFFF4444) else Color(0xFFFFAA00),
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            MetricCard(
                label = "PACKETS SENT",
                value = String.format("%,d", metrics.totalPackets),
                trend = null,
                trendColor = Color.Gray,
                modifier = Modifier.weight(1f)
            )
        }

        if (metrics.isRunning) {
            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { metrics.elapsedSeconds / metrics.totalDuration.toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color(0xFFFF4444),
                trackColor = Color(0xFF333333)
            )
            Text(
                "${metrics.elapsedSeconds}s / ${metrics.totalDuration}s",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}

@Composable
private fun MetricCard(
    label: String,
    value: String,
    trend: String?,
    trendColor: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(label, color = Color.Gray, fontSize = 10.sp, letterSpacing = 1.sp)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    value,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                if (trend != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(trend, color = trendColor, fontSize = 14.sp)
                }
            }
        }
    }
}

@Composable
fun StormConsole(
    logs: List<StormLog>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.lastIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        items(logs) { log ->
            val color = when (log.level) {
                LogLevel.INFO -> Color(0xFF00FF88)
                LogLevel.WARN -> Color(0xFFFFAA00)
                LogLevel.ERROR -> Color(0xFFFF4444)
                LogLevel.CRITICAL -> Color(0xFFFF0000)
                LogLevel.DEBUG -> Color.Gray
            }
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(color = Color(0xFF555555))) {
                        append("[${log.timestamp}] ")
                    }
                    withStyle(SpanStyle(color = color)) {
                        append(log.message)
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(vertical = 2.dp)
            )
        }
    }
}

@Composable
fun StormControls(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (!isRunning) {
            Button(
                onClick = onStart,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF4444)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("INITIATE STORM", fontWeight = FontWeight.Bold)
            }
        } else {
            Button(
                onClick = onStop,
                modifier = Modifier
                    .weight(1f)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFAA0000)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Stop, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("ABORT", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StormReportCard(
    report: StormReport,
    onSave: () -> Unit,
    onExport: () -> Unit,
    modifier: Modifier = Modifier
) {
    val resilienceScore = ((1 - report.failedRequests.toDouble() / maxOf(report.totalRequests, 1)) * 100)
        .toInt()
        .coerceIn(0, 100)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Color(0xFFFF4444).copy(alpha = 0.3f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "STORM REPORT",
                color = Color(0xFFFF4444),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            ReportRow("Target", report.targetIp)
            ReportRow("Duration", "${report.actualDuration}s")
            ReportRow("Peak RPS", String.format("%,d", report.peakRps))
            ReportRow("Total Requests", String.format("%,d", report.totalRequests))
            ReportRow("Failed Requests", String.format("%,d", report.failedRequests))
            ReportRow("Avg Latency", "${report.avgLatencyMs}ms")

            Spacer(modifier = Modifier.height(16.dp))

            // Resilience Score Bar
            Text("TARGET RESILIENCE", color = Color.Gray, fontSize = 11.sp, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(8.dp))

            LinearProgressIndicator(
                progress = { resilienceScore / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp)),
                color = when {
                    resilienceScore >= 80 -> Color(0xFF00FF88)
                    resilienceScore >= 50 -> Color(0xFFFFAA00)
                    else -> Color(0xFFFF4444)
                },
                trackColor = Color(0xFF333333)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("VULNERABLE", color = Color(0xFFFF4444), fontSize = 10.sp)
                Text(
                    "$resilienceScore%",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("RESILIENT", color = Color(0xFF00FF88), fontSize = 10.sp)
            }

            Spacer(modifier = Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onSave,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00FF88)),
                    border = BorderStroke(1.dp, Color(0xFF00FF88))
                ) {
                    Icon(Icons.Default.Save, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("SAVE")
                }

                OutlinedButton(
                    onClick = onExport,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFFF4444)),
                    border = BorderStroke(1.dp, Color(0xFFFF4444))
                ) {
                    Icon(Icons.Default.Share, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("EXPORT JSON")
                }
            }
        }
    }
}

@Composable
private fun ReportRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 13.sp)
        Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
