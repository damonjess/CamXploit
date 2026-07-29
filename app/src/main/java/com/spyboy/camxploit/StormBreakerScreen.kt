package com.spyboy.camxploit

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun StormBreakerScreen(
    viewModel: StormViewModel
) {
    val config by viewModel.config.collectAsState()
    val metrics by viewModel.metrics.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val validation by viewModel.validationState.collectAsState()
    val report by viewModel.report.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0A0A))
            .verticalScroll(rememberScrollState())
    ) {
        // Header
        Text(
            "STORM BREAKER",
            color = Color(0xFFFF4444),
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier = Modifier.padding(16.dp)
        )

        // Target Input
        OutlinedTextField(
            value = config.targetIp,
            onValueChange = { viewModel.updateConfig(config.copy(targetIp = it)) },
            label = { Text("TARGET IP / HOST") },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF4444),
                unfocusedBorderColor = Color(0xFF333333),
                focusedLabelColor = Color(0xFFFF4444)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Validation
        TargetValidationCard(
            state = validation,
            onValidate = { viewModel.validateTarget() }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Config Panel
        StormConfigPanel(
            config = config,
            onConfigChange = { viewModel.updateConfig(it) }
        )

        HorizontalDivider(color = Color(0xFF222222), modifier = Modifier.padding(horizontal = 16.dp))

        // Live Metrics (only during test)
        if (metrics.isRunning || metrics.totalPackets > 0L) {
            LiveMetricsPanel(metrics = metrics)
        }

        // Console
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp, max = 400.dp)
                .background(Color(0xFF0D0D0D))
                .padding(vertical = 8.dp)
        ) {
            StormConsole(logs = logs)
        }

        // Controls
        StormControls(
            isRunning = metrics.isRunning,
            onStart = { viewModel.startStorm() },
            onStop = { viewModel.stopStorm() }
        )

        // Report (after completion)
        report?.let {
            StormReportCard(
                report = it,
                onSave = { viewModel.saveReport() },
                onExport = { viewModel.exportReport() }
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}
