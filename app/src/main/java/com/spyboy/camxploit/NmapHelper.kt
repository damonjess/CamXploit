package com.spyboy.camxploit

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

fun extractNmap(context: Context): String {
    val nmapFile = File(context.filesDir, "nmap")
    if (!nmapFile.exists()) {
        try {
            context.assets.open("nmap").use { input ->
                FileOutputStream(nmapFile).use { out ->
                    input.copyTo(out)
                }
            }
            nmapFile.setExecutable(true)
        } catch (e: Exception) {
            return ""
        }
    }
    return nmapFile.absolutePath
}

fun findNmap(context: Context): String {
    // Try assets first
    val assetNmap = extractNmap(context)
    if (assetNmap.isNotEmpty() &&
        File(assetNmap).exists()) {
        return assetNmap
    }

    // Try system PATH locations
    val systemPaths = listOf(
        "/usr/bin/nmap",
        "/usr/local/bin/nmap",
        "/data/data/com.termux/files/usr/bin/nmap",
        "/system/bin/nmap"
    )

    for (path in systemPaths) {
        if (File(path).exists()) {
            return path
        }
    }

    // Try which command
    try {
        val process = Runtime.getRuntime()
            .exec("which nmap")
        val result = process.inputStream
            .bufferedReader().readLine()
        if (!result.isNullOrEmpty()) {
            return result.trim()
        }
    } catch (e: Exception) {}

    return ""
}

fun runNmap(
    context: Context,
    args: String,
    onOutput: (String) -> Unit,
    onComplete: () -> Unit
) {
    val nmapPath = findNmap(context)
    if (nmapPath.isEmpty()) {
        onOutput("[!] Nmap binary not found in assets or system PATH")
        onComplete()
        return
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            val cmd = "$nmapPath $args"
            val process = ProcessBuilder(
                *cmd.split(" ").toTypedArray()
            )
                .redirectErrorStream(true)
                .start()

            process.inputStream
                .bufferedReader()
                .forEachLine { line ->
                    CoroutineScope(Dispatchers.Main)
                        .launch { onOutput(line) }
                }
            process.waitFor()
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onOutput("[!] Error: ${e.message}")
            }
        } finally {
            withContext(Dispatchers.Main) {
                onComplete()
            }
        }
    }
}

fun quickScan(
    context: Context, ip: String,
    onOutput: (String) -> Unit,
    onComplete: () -> Unit
) {
    runNmap(
        context, "-sV --open -T4 $ip",
        onOutput, onComplete
    )
}

fun subnetScan(
    context: Context, subnet: String,
    onOutput: (String) -> Unit,
    onComplete: () -> Unit
) {
    runNmap(
        context, "-sn $subnet",
        onOutput, onComplete
    )
}

fun cameraScan(
    context: Context, ip: String,
    onOutput: (String) -> Unit,
    onComplete: () -> Unit
) {
    runNmap(
        context,
        "-p 80,443,554,8080,8000,8443,8554," +
                "9000,37777,37778,34567,3702,10554 " +
                "-sV --open -T4 $ip",
        onOutput, onComplete
    )
}

fun aggressiveScan(
    context: Context, ip: String,
    onOutput: (String) -> Unit,
    onComplete: () -> Unit
) {
    runNmap(
        context,
        "-A -T4 --open $ip",
        onOutput, onComplete
    )
}

fun vulnScan(
    context: Context, ip: String,
    onOutput: (String) -> Unit,
    onComplete: () -> Unit
) {
    runNmap(
        context,
        "--script vuln -T4 $ip",
        onOutput, onComplete
    )
}
