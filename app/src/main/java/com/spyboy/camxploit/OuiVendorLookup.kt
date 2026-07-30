package com.spyboy.camxploit

import android.content.Context
import android.util.Log
import java.io.BufferedReader
import java.io.InputStreamReader

object OuiVendorLookup {
    private const val TAG = "OuiVendorLookup"
    private val ouiMap = mutableMapOf<String, String>()

    fun load(context: Context) {
        if (ouiMap.isNotEmpty()) return
        try {
            // Place oui_compact.txt in app/src/main/assets/
            context.assets.open("oui_compact.txt").use { stream ->
                BufferedReader(InputStreamReader(stream)).useLines { lines ->
                    lines.forEach { line ->
                        if (line.isBlank() || line.startsWith("#")) return@forEach
                        val parts = line.split("\t", limit = 2)
                        if (parts.size == 2) {
                            ouiMap[parts[0].uppercase().trim()] = parts[1].trim()
                        }
                    }
                }
            }
            Log.i(TAG, "Loaded ${ouiMap.size} OUIs")
            if (ouiMap.containsKey("6C198F")) {
                Log.i(TAG, "Verified D-Link OUI in map")
            } else {
                Log.w(TAG, "D-Link OUI (6C198F) NOT found in loaded map!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "CRITICAL: Could not load OUI database", e)
        }
    }

    fun lookup(mac: String?): String? {
        if (mac.isNullOrBlank()) return null
        
        // Normalize: AA:BB:CC:DD:EE:FF → AABBCCDD...
        val clean = mac.uppercase()
            .replace(":", "")
            .replace("-", "")
            .replace(".", "")
            .trim()
            
        if (clean.length < 6) return null
        
        // Longest prefix match wins (36-bit/9-char, 28-bit/7-char, 24-bit/6-char)
        val lengths = listOf(9, 7, 6)
        for (len in lengths) {
            if (clean.length >= len) {
                val prefix = clean.substring(0, len)
                ouiMap[prefix]?.let { return it }
            }
        }
        
        return null
    }
}