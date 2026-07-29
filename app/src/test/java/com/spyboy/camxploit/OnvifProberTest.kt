package com.spyboy.camxploit

import org.junit.Assert.assertEquals
import org.junit.Test

class OnvifProberTest {

    private val prober = OnvifProber()

    @Test
    fun testParseOnvifResponse() {
        val xml = """
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery">
                <s:Body>
                    <wsd:ProbeMatches>
                        <wsd:ProbeMatch>
                            <wsa:EndpointReference>
                                <wsa:Address>uuid:12345</wsa:Address>
                            </wsa:EndpointReference>
                            <wsd:Types>dn:NetworkVideoTransmitter</wsd:Types>
                            <wsd:XAddrs>http://192.168.1.100:80/onvif/device_service</wsd:XAddrs>
                        </wsd:ProbeMatch>
                    </wsd:ProbeMatches>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        val info = prober.javaClass.getDeclaredMethod("parseOnvifResponse", String::class.java, String::class.java).apply {
            isAccessible = true
        }.invoke(prober, "192.168.1.100", xml) as OnvifDeviceInfo

        assertEquals("192.168.1.100", info.ip)
        assertEquals("uuid:12345", info.epAddress)
        assertEquals("dn:NetworkVideoTransmitter", info.types)
        assertEquals("http://192.168.1.100:80/onvif/device_service", info.xAddrs)
    }

    @Test
    fun testParseOnvifResponseAlternativePrefix() {
        val xml = """
            <Envelope>
                <Body>
                    <ProbeMatch>
                        <Address>uuid:67890</Address>
                        <Types>NetworkVideoTransmitter</Types>
                        <XAddrs>http://10.0.0.5/onvif/service</XAddrs>
                    </ProbeMatch>
                </Body>
            </Envelope>
        """.trimIndent()

        val info = prober.javaClass.getDeclaredMethod("parseOnvifResponse", String::class.java, String::class.java).apply {
            isAccessible = true
        }.invoke(prober, "10.0.0.5", xml) as OnvifDeviceInfo

        assertEquals("uuid:67890", info.epAddress)
        assertEquals("NetworkVideoTransmitter", info.types)
        assertEquals("http://10.0.0.5/onvif/service", info.xAddrs)
    }
}
