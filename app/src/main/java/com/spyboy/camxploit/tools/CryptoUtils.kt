package com.spyboy.camxploit.tools

import android.util.Base64
import java.util.*

object CryptoUtils {

    // ─── HASH IDENTIFIER ───────────────────────────────────────────────
    fun identifyHash(input: String): List<HashMatch> {
        val clean = input.trim()
        if (clean.isEmpty()) return emptyList()

        val results = mutableListOf<HashMatch>()
        val len = clean.length

        when {
            len == 32 && isHex(clean) -> results += HashMatch("MD5", 128, "Fast, broken")
            len == 40 && isHex(clean) -> results += HashMatch("SHA-1", 160, "Deprecated")
            len == 56 && isHex(clean) -> results += HashMatch("SHA-224", 224, "Rare")
            len == 64 && isHex(clean) -> results += HashMatch("SHA-256", 256, "Standard")
            len == 96 && isHex(clean) -> results += HashMatch("SHA-384", 384, "Rare")
            len == 128 && isHex(clean) -> results += HashMatch("SHA-512", 512, "High security")
            len == 16 && isHex(clean) -> results += HashMatch("MySQL3 / DES", 64, "Ancient")
            len == 41 && clean.startsWith("*") -> results += HashMatch("MySQL5", 160, "SHA-1 based")
            len == 60 && clean.startsWith("$2") -> results += HashMatch("bcrypt", 184, "Slow, secure")
            len == 68 && clean.startsWith("$6$") -> results += HashMatch("SHA-512 Crypt", 512, "Linux shadow")
            len == 34 && clean.startsWith("$1$") -> results += HashMatch("MD5 Crypt", 128, "Linux shadow")
            len == 32 && isBase64(clean) -> results += HashMatch("Base64-ish 256-bit", 256, "Possible SHA-256")
            len == 64 && clean.all { it in 'a'..'f' || it in '0'..'9' } -> {
                results += HashMatch("SHA-256", 256, "Standard")
                results += HashMatch("RIPEMD-256", 256, "Alternative")
            }
            else -> results += HashMatch("Unknown", 0, "No pattern match")
        }

        // Entropy check hint
        val unique = clean.toSet().size
        if (unique < 8) results += HashMatch("Low Entropy", 0, "Possibly truncated or fake")

        return results.distinctBy { it.name }
    }

    data class HashMatch(val name: String, val bits: Int, val note: String)

    private fun isHex(s: String) = s.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
    private fun isBase64(s: String): Boolean {
        return try {
            Base64.decode(s, Base64.DEFAULT)
            true
        } catch (_: IllegalArgumentException) { false }
    }

    // ─── DECODERS ──────────────────────────────────────────────────────
    sealed class DecodeResult {
        data class Success(val format: String, val text: String, val extra: String? = null) : DecodeResult()
        data class Error(val reason: String) : DecodeResult()
    }

    fun decodeBase64(input: String): DecodeResult {
        return try {
            val bytes = Base64.decode(input.trim(), Base64.DEFAULT)
            val text = String(bytes, Charsets.UTF_8)
            DecodeResult.Success("Base64", text, "Bytes: ${bytes.size}")
        } catch (e: Exception) {
            DecodeResult.Error("Invalid Base64")
        }
    }

    fun decodeHex(input: String): DecodeResult {
        val clean = input.trim().replace(" ", "").replace("0x", "")
        if (clean.length % 2 != 0) return DecodeResult.Error("Odd-length hex")
        return try {
            val bytes = clean.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val text = String(bytes, Charsets.UTF_8)
            DecodeResult.Success("Hex", text, "Bytes: ${bytes.size}")
        } catch (e: Exception) {
            DecodeResult.Error("Invalid hex")
        }
    }

    fun decodeJwt(input: String): DecodeResult {
        val parts = input.trim().split(".")
        if (parts.size != 3) return DecodeResult.Error("JWT requires 3 dot-separated parts")
        return try {
            val header = decodeBase64Url(parts[0])
            val payload = decodeBase64Url(parts[1])
            val sig = parts[2]
            DecodeResult.Success(
                "JWT",
                payload,
                "Header: $header\nSignature (truncated): ${sig.take(16)}…"
            )
        } catch (e: Exception) {
            DecodeResult.Error("Invalid JWT")
        }
    }

    private fun decodeBase64Url(input: String): String {
        var padded = input.replace("-", "+").replace("_", "/")
        when (padded.length % 4) {
            2 -> padded += "=="
            3 -> padded += "="
        }
        return String(Base64.decode(padded, Base64.DEFAULT), Charsets.UTF_8)
    }

    // ─── PASSWORD GENERATOR ──────────────────────────────────────────
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val DIGITS = "0123456789"
    private const val SYMBOLS = "!@#$%^&*()_+-=[]{}|;:,.<>?"

    fun generatePassword(length: Int, useUpper: Boolean, useLower: Boolean, useDigits: Boolean, useSymbols: Boolean): String {
        val pool = buildString {
            if (useUpper) append(UPPER)
            if (useLower) append(LOWER)
            if (useDigits) append(DIGITS)
            if (useSymbols) append(SYMBOLS)
        }
        if (pool.isEmpty()) return ""

        return (1..length)
            .map { pool.random() }
            .joinToString("")
    }

    fun estimateEntropy(password: String): String {
        var pool = 0
        if (password.any { it.isUpperCase() }) pool += 26
        if (password.any { it.isLowerCase() }) pool += 26
        if (password.any { it.isDigit() }) pool += 10
        if (password.any { !it.isLetterOrDigit() }) pool += 32
        if (pool == 0) return "0 bits"

        val entropy = password.length * kotlin.math.log2(pool.toDouble())
        return "%.1f bits".format(Locale.US, entropy)
    }

    fun analyzeString(input: String): Map<String, String> = buildMap {
        put("Length", input.length.toString())
        put("Bytes", input.toByteArray().size.toString())
        put("Lines", input.lines().size.toString())
        put("Entropy", estimateEntropy(input))
        put("Looks like Base64", isBase64(input).toString())
        put("Looks like Hex", isHex(input).toString())
        put("Looks like JWT", input.count { it == '.' }.let { it == 2 }.toString())
    }
}
