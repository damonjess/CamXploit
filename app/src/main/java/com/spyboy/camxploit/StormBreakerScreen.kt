package com.spyboy.camxploit

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.Locale

import androidx.compose.ui.platform.LocalContext

// ─── Theme Tokens ─────────────────────────────────────────────
private val Background   = Color(0xFF0A0A0A)
private val Surface      = Color(0xFF141414)
private val SurfaceLight = Color(0xFF1E1E1E)
private val AccentRed    = Color(0xFFFF4444)
private val AccentGreen  = Color(0xFF00E676)
private val AccentOrange = Color(0xFFFFAB40)
private val TextPrimary  = Color(0xFFFFFFFF)
private val TextMuted    = Color(0xFF888888)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StormBreakerScreen(
    viewModel: StormViewModel,
    defaultTargetIp: String = ""
) {
    val context     = LocalContext.current
    val config      by viewModel.config.collectAsState()
    val metrics     by viewModel.metrics.collectAsState()
    val logs        by viewModel.logs.collectAsState()
    val validation  by viewModel.validationState.collectAsState()
    val report      by viewModel.report.collectAsState()
    val isRunning   = metrics.isRunning

    LaunchedEffect(defaultTargetIp) {
        viewModel.setTargetIpIfEmpty(defaultTargetIp)
    }
    
    val scrollState = rememberScrollState()
    val haptic = LocalHapticFeedback.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp)
    ) {
        // ── Header ───────────────────────────────────────────
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "STORM BREAKER",
            color = AccentRed,
            fontSize = 26.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp
        )
        Text(
            text = "Network Resilience Auditor",
            color = TextMuted,
            fontSize = 12.sp,
            letterSpacing = 1.sp
        )
        Spacer(modifier = Modifier.height(20.dp))

        // ── Target Input + Validate ──────────────────────────
        TargetRow(
            ip = config.targetIp,
            onIpChange = { viewModel.updateConfig(config.copy(targetIp = it)) },
            validation = validation,
            onValidate = { viewModel.validateTarget() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Attack Vector Grid ───────────────────────────────
        SectionLabel("ATTACK VECTOR")
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement   = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            AttackVector.entries.forEach { vector ->
                val selected = config.vector == vector
                SelectableChip(
                    text = vector.displayName,
                    selected = selected,
                    onClick = { 
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.updateConfig(config.copy(vector = vector)) 
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Config Sliders ───────────────────────────────────
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            TidySlider(
                label = "THREADS",
                value = config.threads,
                range = 1..500,
                onChange = { viewModel.updateConfig(config.copy(threads = it)) },
                modifier = Modifier.weight(1f)
            )
            TidySlider(
                label = "DURATION",
                value = config.durationSeconds,
                range = 5..300,
                suffix = "s",
                onChange = { viewModel.updateConfig(config.copy(durationSeconds = it)) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Load Pattern ─────────────────────────────────────
        SectionLabel("LOAD PATTERN")
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            LoadPattern.entries.forEach { pattern ->
                val selected = config.loadPattern == pattern
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) AccentRed else SurfaceLight)
                        .clickable { 
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            viewModel.updateConfig(config.copy(loadPattern = pattern)) 
                        }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = pattern.name.replace("_", " "),
                        color = if (selected) Color.Black else TextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        letterSpacing = 0.5.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Live Metrics (compact) ─────────────────────────
        AnimatedVisibility(visible = isRunning || metrics.totalPackets > 0) {
            Column {
                CompactMetricsRow(metrics)
                Spacer(modifier = Modifier.height(12.dp))
            }
        }

        // ── Console ──────────────────────────────────────────
        ConsoleBox(logs = logs, modifier = Modifier.height(200.dp))

        Spacer(modifier = Modifier.height(12.dp))

        // ── Big Action Button ────────────────────────────────
        ActionButton(
            isRunning = isRunning,
            onStart = { viewModel.startStorm() },
            onStop  = { viewModel.stopStorm() }
        )

        // ── Report (if finished) ─────────────────────────────
        AnimatedVisibility(visible = report != null) {
            Column {
                Spacer(modifier = Modifier.height(12.dp))
                CompactReportCard(
                    report = report,
                    onSave = { viewModel.saveReport(context) },
                    onExport = { viewModel.exportReport(context) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════════
// COMPONENTS
// ═══════════════════════════════════════════════════════════════

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = AccentRed,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 1.2.sp
    )
}

@Composable
private fun TargetRow(
    ip: String,
    onIpChange: (String) -> Unit,
    validation: ValidationState,
    onValidate: () -> Unit
) {
    Column {
        OutlinedTextField(
            value = ip,
            onValueChange = onIpChange,
            label = { Text("TARGET IP / HOST", fontSize = 11.sp) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = AccentRed,
                unfocusedBorderColor = SurfaceLight,
                focusedLabelColor    = AccentRed,
                unfocusedLabelColor  = TextMuted,
                cursorColor          = AccentRed,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Validation status bar (replaces the weird pill)
        val statusColor = when (validation) {
            is ValidationState.Valid       -> AccentGreen
            is ValidationState.Invalid     -> AccentRed
            is ValidationState.Validating  -> AccentOrange
            else                           -> SurfaceLight
        }
        val statusText = when (validation) {
            is ValidationState.Valid       -> "● Target responsive — ports: ${validation.openPorts.joinToString()}"
            is ValidationState.Invalid     -> "● ${validation.reason}"
            is ValidationState.Validating  -> "● Probing target..."
            else                           -> "● Validate target before starting"
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(Surface)
                .clickable(enabled = validation !is ValidationState.Validating) { onValidate() }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Text(
                text = statusText,
                color = if (validation is ValidationState.Idle) TextMuted else statusColor,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            if (validation is ValidationState.Idle || validation is ValidationState.Invalid) {
                Text(
                    text = "VALIDATE",
                    color = AccentRed,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            } else if (validation is ValidationState.Validating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    color = AccentOrange,
                    strokeWidth = 2.dp
                )
            } else {
                Icon(Icons.Default.CheckCircle, null, tint = AccentGreen, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun SelectableChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp) // Standard touch target height
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AccentRed.copy(alpha = 0.15f) else Surface)
            .border(
                width = 1.dp,
                color = if (selected) AccentRed else Color(0xFF222222),
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = if (selected) AccentRed else TextMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun TidySlider(
    label: String,
    value: Int,
    range: IntRange,
    suffix: String = "",
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = TextMuted, fontSize = 10.sp, letterSpacing = 1.sp)
            Text(
                "$value$suffix",
                color = AccentRed,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = AccentRed,
                activeTrackColor = AccentRed,
                inactiveTrackColor = SurfaceLight
            )
        )
    }
}

@Composable
private fun CompactMetricsRow(metrics: StormMetrics) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        MetricPill(
            label = "RPS",
            value = "${metrics.requestsPerSecond}",
            modifier = Modifier.weight(1f)
        )
        MetricPill(
            label = "ERRORS",
            value = String.format(Locale.getDefault(), "%.1f%%", metrics.errorRate),
            valueColor = if (metrics.errorRate > 10) AccentRed else AccentGreen,
            modifier = Modifier.weight(1f)
        )
        MetricPill(
            label = "PACKETS",
            value = formatCompact(metrics.totalPackets),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Surface)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(label, color = TextMuted, fontSize = 9.sp, letterSpacing = 1.sp)
        Text(value, color = valueColor, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ConsoleBox(
    logs: List<StormLog>,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) listState.animateScrollToItem(logs.lastIndex)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Surface)
            .padding(12.dp)
    ) {
        if (logs.isEmpty()) {
            Text(
                text = "Console ready...",
                color = TextMuted,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(logs, key = { it.hashCode() }) { log ->
                    val color = when (log.level) {
                        LogLevel.INFO     -> AccentGreen
                        LogLevel.WARN     -> AccentOrange
                        LogLevel.ERROR,
                        LogLevel.CRITICAL -> AccentRed
                        LogLevel.DEBUG    -> TextMuted
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = Color(0xFF444444))) {
                                append("[${log.timestamp}] ")
                            }
                            withStyle(SpanStyle(color = color)) {
                                append(log.message)
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier.padding(vertical = 1.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ActionButton(
    isRunning: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit
) {
    Button(
        onClick = if (isRunning) onStop else onStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isRunning) Color(0xFF880000) else AccentRed
        ),
        shape = RoundedCornerShape(14.dp)
    ) {
        Icon(
            imageVector = if (isRunning) Icons.Default.Stop else Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = if (isRunning) "ABORT STORM" else "INITIATE STORM",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            letterSpacing = 1.sp
        )
    }
}

@Composable
private fun CompactReportCard(
    report: StormReport?,
    onSave: () -> Unit,
    onExport: () -> Unit
) {
    if (report == null) return

    val score = ((1 - report.failedRequests.toDouble() / maxOf(report.totalRequests, 1)) * 100)
        .toInt().coerceIn(0, 100)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Surface)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("STORM REPORT", color = AccentRed, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(
                text = "$score% RESILIENCE",
                color = when {
                    score >= 80 -> AccentGreen
                    score >= 50 -> AccentOrange
                    else -> AccentRed
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = SurfaceLight, thickness = 1.dp)
        Spacer(modifier = Modifier.height(8.dp))

        ReportLine("Target", report.targetIp)
        ReportLine("Duration", "${report.actualDuration}s")
        ReportLine("Peak RPS", String.format(Locale.getDefault(), "%,d", report.peakRps))
        ReportLine("Total", String.format(Locale.getDefault(), "%,d", report.totalRequests))
        ReportLine("Failed", String.format(Locale.getDefault(), "%,d", report.failedRequests))

        Spacer(modifier = Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onSave,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGreen),
                border = BorderStroke(1.dp, AccentGreen),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("SAVE", fontSize = 12.sp)
            }
            OutlinedButton(
                onClick = onExport,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentRed),
                border = BorderStroke(1.dp, AccentRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(Icons.Default.Share, null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("EXPORT", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ReportLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = TextMuted, fontSize = 12.sp)
        Text(value, color = TextPrimary, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

// ─── Helper ───────────────────────────────────────────────────
private fun formatCompact(n: Long): String {
    return when {
        n >= 1_000_000 -> String.format(Locale.getDefault(), "%.1fM", n / 1_000_000.0)
        n >= 1_000     -> String.format(Locale.getDefault(), "%.1fK", n / 1_000.0)
        else           -> n.toString()
    }
}
