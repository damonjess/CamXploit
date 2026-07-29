package com.spyboy.camxploit

import org.junit.Assert.assertEquals
import org.junit.Test

class HttpFingerprinterTest {

    private val fingerprinter = HttpFingerprinter()

    @Test
    fun testIdentifyHikvision() {
        val headers = "Server: Hikvision-Webs\nWWW-Authenticate: Digest realm=\"Hikvision\""
        val body = "<html>Hikvision</html>"
        assertEquals("Hikvision", fingerprinter.match(headers, body))
    }

    @Test
    fun testIdentifyDahua() {
        val headers = "Server: Dahua-Webs\nWWW-Authenticate: Digest realm=\"DNVRS\""
        val body = "<html>Dahua</html>"
        assertEquals("Dahua", fingerprinter.match(headers, body))
    }

    @Test
    fun testIdentifyAxis() {
        val headers = "WWW-Authenticate: Basic realm=\"camera\""
        val body = ""
        assertEquals("Axis", fingerprinter.match(headers, body))
    }

    @Test
    fun testIdentifyGenericDvr() {
        val headers = "Server: thttpd/2.25b"
        val body = ""
        assertEquals("Generic DVR", fingerprinter.match(headers, body))
    }

    @Test
    fun testIdentifyGenericChineseDvr() {
        val headers = "WWW-Authenticate: Digest realm=\"IPCamera\""
        val body = ""
        assertEquals("Generic Chinese DVR", fingerprinter.match(headers, body))
    }

    @Test
    fun testIdentifyOnvif() {
        val headers = "Server: gSOAP/2.8"
        val body = ""
        assertEquals("ONVIF device", fingerprinter.match(headers, body))
    }

    @Test
    fun testUnknown() {
        val headers = "Server: Apache"
        val body = "Hello"
        assertEquals(null, fingerprinter.match(headers, body))
    }
}
