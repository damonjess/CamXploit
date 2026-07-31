package com.spyboy.camxploit

import java.io.OutputStream

/**
 * A shared OutputStream for capturing Python console output (stdout/stderr)
 * and piping it to a callback function for UI display.
 * Includes a write(String) method specifically for Chaquopy compatibility.
 */
class TerminalOutputStream(val onText: (String) -> Unit) : OutputStream() {
    private val buffer = StringBuilder()

    /**
     * Chaquopy looks for a write(String) method when redirecting Python output.
     */
    fun write(text: String) {
        buffer.append(text)
        if (text.contains("\n")) {
            flush()
        }
    }

    override fun write(b: Int) {
        val c = b.toChar()
        buffer.append(c)
        if (c == '\n') {
            flush()
        }
    }

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        val text = String(b, off, len, Charsets.UTF_8)
        write(text)
    }
    
    override fun flush() {
        if (buffer.isNotEmpty()) {
            onText(buffer.toString())
            buffer.clear()
        }
    }
}
