package com.spyboy.camxploit

import android.content.Context
import android.os.Build
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

fun extractNmap(context: Context): String {
    val nmapFile = File(context.filesDir, "nmap")
    val nmapDataDir = File(context.filesDir, "nmap_data")
    
    try {
        // 1. Extract Binary if needed (Assets fallback)
        if (!nmapFile.exists() || nmapFile.length() == 0L) {
            try {
                context.assets.open("nmap").use { input ->
                    FileOutputStream(nmapFile).use { out -> input.copyTo(out) }
                }
            } catch (e: Exception) {}
        }
        nmapFile.setExecutable(true, false)

        // 2. Extract Data Files Recursively (nmap-services, nselib, scripts)
        copyAssetFolder(context, "nmap_data", nmapDataDir)

        // 3. Extract OUI Database if present in assets
        val ouiFile = File(context.filesDir, "oui.csv")
        if (!ouiFile.exists() || ouiFile.length() == 0L) {
            try {
                context.assets.open("oui.csv").use { input ->
                    FileOutputStream(ouiFile).use { out -> input.copyTo(out) }
                }
            } catch (e: Exception) {
                // Ignore if oui.csv is not in assets
            }
        }
        
    } catch (e: Exception) {
        e.printStackTrace()
    }
    return nmapFile.absolutePath
}

private fun copyAssetFolder(context: Context, assetPath: String, targetDir: File) {
    if (!targetDir.exists()) targetDir.mkdirs()
    
    val assets = context.assets.list(assetPath) ?: return
    
    for (asset in assets) {
        val fullAssetPath = if (assetPath.isEmpty()) asset else "$assetPath/$asset"
        val targetFile = File(targetDir, asset)
        
        // Try to list as if it's a directory
        val subAssets = context.assets.list(fullAssetPath)
        
        if (subAssets.isNullOrEmpty()) {
            // It's a file
            if (!targetFile.exists() || targetFile.length() == 0L) {
                try {
                    context.assets.open(fullAssetPath).use { input ->
                        FileOutputStream(targetFile).use { out -> input.copyTo(out) }
                    }
                } catch (e: Exception) {
                    // Might be an empty directory that list() thought was a file
                    if (!targetFile.exists()) targetFile.mkdirs()
                }
            }
        } else {
            // It's a directory
            copyAssetFolder(context, fullAssetPath, targetFile)
        }
    }
}

fun findNmap(context: Context): String {
    // Priority 1: Native Lib Dir (libnmap.so) - Correct way for Android 10+
    val nativeLibNmap = File(context.applicationInfo.nativeLibraryDir, "libnmap.so")
    if (nativeLibNmap.exists()) return nativeLibNmap.absolutePath
    
    // Priority 2: Assets extracted to filesDir (Legacy / Fallback)
    val assetNmap = extractNmap(context)
    if (assetNmap.isNotEmpty() && File(assetNmap).exists()) return assetNmap

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
        onOutput("[!] Nmap binary not found.")
        onComplete()
        return
    }

    val nmapFile = File(nmapPath)
    val nmapDataDir = File(context.filesDir, "nmap_data")
    
    // Ensure data is extracted even if using native lib
    if (!nmapDataDir.exists() || (nmapDataDir.list()?.isEmpty() == true)) {
        extractNmap(context)
    }

    CoroutineScope(Dispatchers.IO).launch {
        try {
            if (!nmapFile.canExecute() && nmapPath.startsWith(context.filesDir.path)) {
                withContext(Dispatchers.Main) {
                    if (Build.VERSION.SDK_INT >= 29) {
                        onOutput("[!] Android 10+ detected. Execution from internal storage is blocked.")
                        onOutput("[!] Please ensure libnmap.so is in jniLibs folder.")
                    } else {
                        nmapFile.setExecutable(true, false)
                    }
                }
            }

            val finalArgs = if (!args.contains("--unprivileged")) "--unprivileged $args" else args
            
            // Add -n to disable reverse DNS lookup (fixes /etc/resolv.conf error on Android)
            // and --system-dns as a fallback.
            val optimizedArgs = if (!finalArgs.contains("-n")) {
                "-n --system-dns $finalArgs"
            } else {
                "--system-dns $finalArgs"
            }

            val command = mutableListOf<String>().apply {
                add(nmapPath)
                addAll(optimizedArgs.split("\\s+".toRegex()).filter { it.isNotEmpty() })
            }

            val processBuilder = ProcessBuilder(command).redirectErrorStream(true)
            
            // Tell Nmap where the data files (nmap-services, etc) are located
            processBuilder.environment()["NMAPDIR"] = nmapDataDir.absolutePath
            processBuilder.environment()["LD_LIBRARY_PATH"] = context.applicationInfo.nativeLibraryDir
            
            val process = processBuilder.start()

            process.inputStream
                .bufferedReader()
                .forEachLine { line ->
                    CoroutineScope(Dispatchers.Main).launch { onOutput(line) }
                }
            
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                withContext(Dispatchers.Main) {
                    onOutput("[!] Nmap exited with code $exitCode. Check NMAPDIR: ${nmapDataDir.absolutePath}")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onOutput("[!] Execution Error: ${e.message}")
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
        context, "-T4 --open -F $ip",
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
                "-T4 --open -F $ip",
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
