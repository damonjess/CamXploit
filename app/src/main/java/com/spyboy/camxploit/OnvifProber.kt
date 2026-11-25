package com.spyboy.camxploit

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.URL
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

data class OnvifDeviceInfo(
    val ip: String,
    val epAddress: String?,
    val types: String?,
    val xAddrs: String?
) {
    val deviceService: String?
        get() = xAddrs?.trim()?.substringBefore(' ')?.takeIf {
            it.startsWith("http://", true) || it.startsWith("https://", true)
        }
}

data class OnvifProfile(
    val token: String,
    val name: String,
    val encoding: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val fps: Int = 0
) {
    val label: String
        get() = buildString {
            append(name.ifBlank { token })
            if (encoding.isNotBlank()) append(" · ").append(encoding)
            if (width > 0 && height > 0) append(" · ${width}x$height")
            if (fps > 0) append(" @ ${fps}fps")
        }
}

data class OnvifCapabilities(
    val mediaXAddr: String? = null,
    val ptzXAddr: String? = null
)

class OnvifProber {

    /**
     * Probes for ONVIF devices using WS-Discovery (UDP 3702).
     */
    suspend fun probe(timeoutMs: Int = 3000): List<OnvifDeviceInfo> = withContext(Dispatchers.IO) {
        val devices = mutableListOf<OnvifDeviceInfo>()
        val uuid = UUID.randomUUID().toString()
        val probeMessage = """
            <?xml version="1.0" encoding="UTF-8"?>
            <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope" 
                        xmlns:wsa="http://schemas.xmlsoap.org/ws/2004/08/addressing" 
                        xmlns:wsd="http://schemas.xmlsoap.org/ws/2005/04/discovery" 
                        xmlns:dn="http://www.onvif.org/ver10/network/wsdl">
                <s:Header>
                    <wsa:MessageID>uuid:$uuid</wsa:MessageID>
                    <wsa:To>urn:schemas-xmlsoap-org:ws:2005:04:discovery</wsa:To>
                    <wsa:Action>http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</wsa:Action>
                </s:Header>
                <s:Body>
                    <wsd:Probe>
                        <wsd:Types>dn:NetworkVideoTransmitter</wsd:Types>
                    </wsd:Probe>
                </s:Body>
            </s:Envelope>
        """.trimIndent()

        try {
            val socket = DatagramSocket().apply { soTimeout = 500 }
            val group = InetAddress.getByName("239.255.255.250")
            val payload = probeMessage.toByteArray()
            val packet = DatagramPacket(payload, payload.size, group, 3702)

            repeat(3) {
                socket.send(packet)
            }

            val receiveData = ByteArray(8192)
            val startTime = System.currentTimeMillis()
            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val receivePacket = DatagramPacket(receiveData, receiveData.size)
                    socket.receive(receivePacket)
                    val response = String(receivePacket.data, 0, receivePacket.length)
                    val ip = receivePacket.address.hostAddress
                    if (ip != null) {
                        devices.add(parseOnvifResponse(ip, response))
                    }
                } catch (_: java.net.SocketTimeoutException) {
                    continue
                } catch (_: Exception) {
                    break
                }
            }
            socket.close()
        } catch (_: Exception) {
        }
        devices.distinctBy { it.ip }
    }

    private fun parseOnvifResponse(ip: String, xml: String): OnvifDeviceInfo {
        val epAddress = tag(xml, "Address")
        val types = tag(xml, "Types")
        val xAddrs = tag(xml, "XAddrs")
        return OnvifDeviceInfo(ip, epAddress, types, xAddrs)
    }

    /**
     * Gets the media profiles. The device service often exposes its media
     * endpoint through GetCapabilities; if that fails we try the same endpoint.
     */
    suspend fun getProfiles(
        xAddr: String,
        username: String = "",
        password: String = ""
    ): List<OnvifProfile> = withContext(Dispatchers.IO) {
        val capabilities = getCapabilities(xAddr, username, password)
        getProfilesAt(capabilities.mediaXAddr ?: xAddr, username, password)
    }

    /** Gets profiles from a known media service endpoint (no extra capability call). */
    suspend fun getProfilesAt(
        mediaEndpoint: String,
        username: String = "",
        password: String = ""
    ): List<OnvifProfile> = withContext(Dispatchers.IO) {
        val body = """
            <trt:GetProfiles/>
        """.trimIndent()
        val response = soap(
            mediaEndpoint,
            body,
            username,
            password,
            "http://www.onvif.org/ver10/media/wsdl/GetProfiles"
        ) ?: return@withContext emptyList()
        parseProfiles(response)
    }

    /** Kept for callers that only have a device endpoint and use the old default profile. */
    suspend fun getStreamUri(xAddr: String): String? =
        getStreamUri(xAddr, "profile_1", "", "")

    suspend fun getStreamUri(
        mediaXAddr: String,
        profileToken: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        val body = """
            <trt:GetStreamUri>
              <trt:StreamSetup>
                <trt:Stream>RTP-Unicast</trt:Stream>
                <trt:Transport><trt:Protocol>RTSP</trt:Protocol></trt:Transport>
              </trt:StreamSetup>
              <trt:ProfileToken>${xmlEscape(profileToken)}</trt:ProfileToken>
            </trt:GetStreamUri>
        """.trimIndent()
        val response = soap(
            mediaXAddr,
            body,
            username,
            password,
            "http://www.onvif.org/ver10/media/wsdl/GetStreamUri"
        ) ?: return@withContext null
        tag(response, "Uri")?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun getSnapshotUri(
        mediaXAddr: String,
        profileToken: String,
        username: String,
        password: String
    ): String? = withContext(Dispatchers.IO) {
        val body = """
            <trt:GetSnapshotUri>
              <trt:ProfileToken>${xmlEscape(profileToken)}</trt:ProfileToken>
            </trt:GetSnapshotUri>
        """.trimIndent()
        val response = soap(
            mediaXAddr,
            body,
            username,
            password,
            "http://www.onvif.org/ver10/media/wsdl/GetSnapshotUri"
        ) ?: return@withContext null
        tag(response, "Uri")?.trim()?.takeIf { it.isNotBlank() }
    }

    suspend fun getCapabilities(
        deviceXAddr: String,
        username: String = "",
        password: String = ""
    ): OnvifCapabilities = withContext(Dispatchers.IO) {
        val body = """
            <tds:GetCapabilities>
              <tds:Category>All</tds:Category>
            </tds:GetCapabilities>
        """.trimIndent()
        val response = soap(
            deviceXAddr,
            body,
            username,
            password,
            "http://www.onvif.org/ver10/device/wsdl/GetCapabilities"
        ) ?: return@withContext OnvifCapabilities()

        val mediaBlock = blockTag(response, "Media")
        val ptzBlock = blockTag(response, "PTZ")
        OnvifCapabilities(
            mediaXAddr = mediaBlock?.let { tag(it, "XAddr")?.trim() }
                ?.takeIf { it.isNotBlank() },
            ptzXAddr = ptzBlock?.let { tag(it, "XAddr")?.trim() }
                ?.takeIf { it.isNotBlank() }
        )
    }

    suspend fun ptzContinuousMove(
        ptzXAddr: String,
        profileToken: String,
        pan: Float,
        tilt: Float,
        zoom: Float,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <tptz:ContinuousMove>
              <tptz:ProfileToken>${xmlEscape(profileToken)}</tptz:ProfileToken>
              <tptz:Velocity>
                <tt:PanTilt x="$pan" y="$tilt"/>
                <tt:Zoom x="$zoom"/>
              </tptz:Velocity>
            </tptz:ContinuousMove>
        """.trimIndent()
        soap(
            ptzXAddr,
            body,
            username,
            password,
            "http://www.onvif.org/ver20/ptz/wsdl/ContinuousMove"
        ) != null
    }

    suspend fun ptzStop(
        ptzXAddr: String,
        profileToken: String,
        username: String,
        password: String
    ): Boolean = withContext(Dispatchers.IO) {
        val body = """
            <tptz:Stop>
              <tptz:ProfileToken>${xmlEscape(profileToken)}</tptz:ProfileToken>
              <tptz:PanTilt>true</tptz:PanTilt>
              <tptz:Zoom>true</tptz:Zoom>
            </tptz:Stop>
        """.trimIndent()
        soap(
            ptzXAddr,
            body,
            username,
            password,
            "http://www.onvif.org/ver20/ptz/wsdl/Stop"
        ) != null
    }

    private fun soap(
        endpoint: String,
        body: String,
        username: String,
        password: String,
        action: String
    ): String? {
        var connection: HttpURLConnection? = null
        return try {
            val envelope = """
                <?xml version="1.0" encoding="UTF-8"?>
                <s:Envelope xmlns:s="http://www.w3.org/2003/05/soap-envelope"
                            xmlns:trt="http://www.onvif.org/ver10/media/wsdl"
                            xmlns:tt="http://www.onvif.org/ver10/schema"
                            xmlns:tds="http://www.onvif.org/ver10/device/wsdl"
                            xmlns:tptz="http://www.onvif.org/ver20/ptz/wsdl">
                ${securityHeader(username, password)}
                <s:Body>$body</s:Body>
                </s:Envelope>
            """.trimIndent()

            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 4_000
                readTimeout = 6_000
                doOutput = true
                setRequestProperty("Content-Type", "application/soap+xml; charset=utf-8")
                setRequestProperty("SOAPAction", "\"$action\"")
                setRequestProperty("Accept", "application/soap+xml, text/xml")
            }
            connection.outputStream.use { it.write(envelope.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode !in 200..299) {
                return null
            }
            val response = connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                val builder = StringBuilder()
                val buffer = CharArray(8 * 1024)
                var total = 0
                while (total < 1024 * 1024) {
                    val read = reader.read(buffer)
                    if (read <= 0) break
                    builder.append(buffer, 0, read)
                    total += read
                }
                builder.toString()
            }
            response.takeIf { it.contains("Envelope", ignoreCase = true) }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { connection?.disconnect() }
        }
    }

    private fun securityHeader(username: String, password: String): String {
        if (username.isBlank()) return ""
        val nonceBytes = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val created = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(Date())
        val digest = MessageDigest.getInstance("SHA-1").apply {
            update(nonceBytes)
            update(created.toByteArray(Charsets.UTF_8))
            update(password.toByteArray(Charsets.UTF_8))
        }.digest()
        val nonce = Base64.encodeToString(nonceBytes, Base64.NO_WRAP)
        val passwordDigest = Base64.encodeToString(digest, Base64.NO_WRAP)

        return """
            <s:Header>
              <wsse:Security s:mustUnderstand="1"
                  xmlns:wsse="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-secext-1.0.xsd">
                <wsse:UsernameToken>
                  <wsse:Username>${xmlEscape(username)}</wsse:Username>
                  <wsse:Password Type="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-username-token-profile-1.0#PasswordDigest">$passwordDigest</wsse:Password>
                  <wsse:Nonce EncodingType="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-soap-message-security-1.0#Base64Binary">$nonce</wsse:Nonce>
                  <wsu:Created xmlns:wsu="http://docs.oasis-open.org/wss/2004/01/oasis-200401-wss-wssecurity-utility-1.0.xsd">$created</wsu:Created>
                </wsse:UsernameToken>
              </wsse:Security>
            </s:Header>
        """.trimIndent()
    }

    private fun parseProfiles(xml: String): List<OnvifProfile> {
        val profileRegex = Regex(
            """<(?:\w+:)?Profiles\b[^>]*\btoken="([^"]+)"[^>]*>(.*?)</(?:\w+:)?Profiles>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return profileRegex.findAll(xml).mapNotNull { match ->
            val token = match.groupValues.getOrNull(1)?.trim().orEmpty()
            if (token.isBlank()) return@mapNotNull null
            val block = match.groupValues.getOrNull(2).orEmpty()
            OnvifProfile(
                token = token,
                name = tag(block, "Name")?.trim().orEmpty(),
                encoding = tag(block, "Encoding")?.trim().orEmpty(),
                width = tag(block, "Width")?.trim()?.toIntOrNull() ?: 0,
                height = tag(block, "Height")?.trim()?.toIntOrNull() ?: 0,
                fps = tag(block, "FrameRateLimit")?.trim()?.toDoubleOrNull()?.toInt() ?: 0
            )
        }.distinctBy { it.token }.toList()
    }

    private fun tag(xml: String, name: String): String? {
        val regex = Regex(
            """<(?:\w+:)?${Regex.escape(name)}\b[^>]*>(.*?)</(?:\w+:)?${Regex.escape(name)}>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return regex.find(xml)?.groupValues?.getOrNull(1)
    }

    private fun blockTag(xml: String, name: String): String? {
        val regex = Regex(
            """<(?:\w+:)?${Regex.escape(name)}\b[^>]*>(.*?)</(?:\w+:)?${Regex.escape(name)}>""",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        )
        return regex.find(xml)?.groupValues?.getOrNull(1)
    }

    private fun xmlEscape(value: String): String =
        value.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
}
