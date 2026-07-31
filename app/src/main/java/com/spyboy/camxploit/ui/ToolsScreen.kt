package com.spyboy.camxploit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spyboy.camxploit.tools.CryptoUtils

@Composable
fun ToolsScreen() {
    val neonGreen = Color(0xFF39FF14)
    val darkCard = Color(0xFF1A1A1A)
    val cyan = Color(0xFF00FFFF)
    val red = Color(0xFFFF4444)
    val orange = Color(0xFFFFA500)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
            .padding(bottom = 80.dp)
    ) {
        // ── HEADER ──
        Text("TOOLS", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = cyan)
        Text("OFFLINE CRYPTO & DECODER", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        // SECTION 1: HASH IDENTIFIER
        // ═══════════════════════════════════════════════════════════════
        SectionTitle("HASH IDENTIFIER", neonGreen)
        var hashInput by remember { mutableStateOf("") }
        val hashResults = remember(hashInput) { CryptoUtils.identifyHash(hashInput) }

        OutlinedTextField(
            value = hashInput,
            onValueChange = { hashInput = it },
            placeholder = { Text("Paste hash…", color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = neonGreen,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(Modifier.height(8.dp))
        if (hashResults.isNotEmpty()) {
            hashResults.forEach { match ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = darkCard),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(match.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(match.note, color = Color.Gray, fontSize = 11.sp)
                        }
                        if (match.bits > 0) {
                            Surface(color = if (match.bits >= 256) neonGreen else orange, shape = RoundedCornerShape(4.dp)) {
                                Text(" ${match.bits}-bit ", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        // SECTION 2: DECODER (Base64 / Hex / JWT)
        // ═══════════════════════════════════════════════════════════════
        SectionTitle("DECODER", neonGreen)
        var decoderInput by remember { mutableStateOf("") }
        var decodeResult by remember { mutableStateOf<CryptoUtils.DecodeResult?>(null) }
        var selectedDecoder by remember { mutableIntStateOf(0) } // 0=Base64, 1=Hex, 2=JWT

        // Decoder chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("BASE64", "HEX", "JWT").forEachIndexed { index, label ->
                val selected = selectedDecoder == index
                AssistChip(
                    onClick = { selectedDecoder = index },
                    label = { Text(label, color = if (selected) Color.Black else Color.White) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (selected) neonGreen else darkCard
                    )
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = decoderInput,
            onValueChange = { decoderInput = it },
            placeholder = { Text("Paste encoded data…", color = Color.DarkGray) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = darkCard,
                unfocusedContainerColor = darkCard,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedBorderColor = cyan,
                unfocusedBorderColor = Color.DarkGray
            ),
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5
        )

        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                decodeResult = when (selectedDecoder) {
                    0 -> CryptoUtils.decodeBase64(decoderInput)
                    1 -> CryptoUtils.decodeHex(decoderInput)
                    else -> CryptoUtils.decodeJwt(decoderInput)
                }
            },
            colors = ButtonDefaults.buttonColors(containerColor = cyan),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("DECODE", color = Color.Black, fontWeight = FontWeight.Bold)
        }

        decodeResult?.let { res ->
            Spacer(Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = darkCard)) {
                Column(Modifier.padding(12.dp)) {
                    when (res) {
                        is CryptoUtils.DecodeResult.Success -> {
                            val clipboard = LocalClipboardManager.current
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(res.format, color = neonGreen, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(res.text)) }) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = Color.Gray)
                                }
                            }
                            SelectionContainer {
                                Text(
                                    res.text,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                            res.extra?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                        is CryptoUtils.DecodeResult.Error -> {
                            Text(res.reason, color = red, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))

        // ═══════════════════════════════════════════════════════════════
        // SECTION 3: PASSWORD GENERATOR
        // ═══════════════════════════════════════════════════════════════
        SectionTitle("PASSWORD GENERATOR", neonGreen)
        var length by remember { mutableIntStateOf(16) }
        var useUpper by remember { mutableStateOf(true) }
        var useLower by remember { mutableStateOf(true) }
        var useDigits by remember { mutableStateOf(true) }
        var useSymbols by remember { mutableStateOf(true) }
        var generated by remember { mutableStateOf("") }
        var entropy by remember { mutableStateOf("") }

        Card(colors = CardDefaults.cardColors(containerColor = darkCard), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {

                // Length slider
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("LENGTH", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.width(60.dp))
                    Slider(
                        value = length.toFloat(),
                        onValueChange = { length = it.toInt() },
                        valueRange = 6f..64f,
                        steps = 0,
                        colors = SliderDefaults.colors(
                            thumbColor = neonGreen,
                            activeTrackColor = neonGreen,
                            inactiveTrackColor = Color.DarkGray
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    Text("$length", color = neonGreen, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
                }

                // Toggles
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleChip("A-Z", useUpper) { useUpper = it }
                    ToggleChip("a-z", useLower) { useLower = it }
                    ToggleChip("0-9", useDigits) { useDigits = it }
                    ToggleChip("!@#", useSymbols) { useSymbols = it }
                }

                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        generated = CryptoUtils.generatePassword(length, useUpper, useLower, useDigits, useSymbols)
                        entropy = CryptoUtils.estimateEntropy(generated)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = neonGreen),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Refresh, null, tint = Color.Black)
                    Spacer(Modifier.width(8.dp))
                    Text("GENERATE", color = Color.Black, fontWeight = FontWeight.Bold)
                }

                if (generated.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    val clipboard = LocalClipboardManager.current
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D0D))) {
                        Column(Modifier.padding(12.dp)) {
                            SelectionContainer {
                                Text(
                                    generated,
                                    color = Color.White,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 16.sp,
                                    letterSpacing = 1.sp
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Entropy: $entropy", color = Color.Gray, fontSize = 11.sp)
                                IconButton(onClick = { clipboard.setText(AnnotatedString(generated)) }) {
                                    Icon(Icons.Default.ContentCopy, "Copy", tint = neonGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String, color: Color) {
    Text(
        text,
        fontSize = 14.sp,
        color = color,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun ToggleChip(label: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    val neonGreen = Color(0xFF39FF14)
    FilterChip(
        selected = checked,
        onClick = { onToggle(!checked) },
        label = { Text(label, color = if (checked) Color.Black else Color.White, fontSize = 12.sp) },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = neonGreen,
            containerColor = Color(0xFF2A2A2A)
        )
    )
}
